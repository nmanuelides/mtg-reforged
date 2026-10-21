import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Properties
import javax.imageio.ImageIO

// MTG Reforged for Windows: the same UI and game code as the Android app (../shared), on
// Compose for Desktop, with the Windows side of the platform layer in src/main/kotlin.
// Installer: ./gradlew :desktop:packageReleaseExe (lands in android/dist as well).

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

val repoRoot: File = rootProject.projectDir.parentFile

fun git(vararg args: String): String =
    providers.exec {
        commandLine("git", "-C", repoRoot.absolutePath, *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()

// Same numbering as the APK (see app/build.gradle.kts): the version counts our releases and
// the build number (commit count) is what the updater compares.
val commitCount = git("rev-list", "--count", "HEAD").toIntOrNull() ?: 1
val commitSha = git("rev-parse", "--short=10", "HEAD").ifEmpty { "unknown" }
val upstreamVersion: String = Regex("<versionCode>(.+)</versionCode>")
    .find(repoRoot.resolve("pom.xml").readText())?.groupValues?.get(1) ?: "0"
val apkCount: Int = Regex("""(?m)^apk=(\d+)""").find(rootProject.file("version.properties").readText())?.groupValues?.get(1)?.toInt() ?: 0
val appVersion: String = "${apkCount / 100}.${"%02d".format(apkCount % 100)}.0" + System.getenv("APP_VERSION_SUFFIX").orEmpty().trim()
// Windows installers need MAJOR.MINOR.BUILD with BUILD <= 65535 and growing with every
// release (so a newer installer upgrades the old one in place).
val installerVersion = "${apkCount / 100}.${apkCount % 100}.${commitCount % 65536}"

// Google Drive backups sign in with an OAuth client of type "Desktop app" (Google Cloud
// project MTG-Reforged). Not committed: CI passes them as secrets, locally use
// ~/.android-keys/google-desktop-oauth.properties (clientId=..., clientSecret=...).
val oauth = Properties().apply {
    val file = File(System.getProperty("user.home"), ".android-keys/google-desktop-oauth.properties")
    if (file.isFile) file.inputStream().use(::load)
}
val googleClientId = System.getenv("GOOGLE_DESKTOP_CLIENT_ID") ?: oauth.getProperty("clientId").orEmpty()
val googleClientSecret = System.getenv("GOOGLE_DESKTOP_CLIENT_SECRET") ?: oauth.getProperty("clientSecret").orEmpty()

val generateBuildConfig by tasks.registering {
    val out = layout.buildDirectory.dir("generated/buildConfig")
    val values = mapOf(
        "VERSION_NAME" to "\"$appVersion\"",
        "VERSION_CODE" to "$commitCount",
        "ENGINE_COMMIT" to "\"$commitSha\"",
        "ENGINE_VERSION" to "\"$upstreamVersion\"",
        "UPDATE_REPO" to "\"nmanuelides/mtg-reforged\"",
        "GOOGLE_CLIENT_ID" to "\"$googleClientId\"",
        "GOOGLE_CLIENT_SECRET" to "\"$googleClientSecret\"",
    )
    inputs.property("values", values)
    outputs.dir(out)
    doLast {
        val file = out.get().file("io/github/nmanuelides/mtgreforged/BuildConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package io.github.nmanuelides.mtgreforged\n\nobject BuildConfig {\n" +
                values.entries.joinToString("") { (k, v) -> "    const val $k = $v\n" } + "}\n",
        )
    }
}

kotlin {
    jvmToolchain(17)
    sourceSets["main"].kotlin.srcDirs("src/main/kotlin", "../shared/src/main/kotlin", generateBuildConfig)
}

// Sound effects play through Forge's own desktop player (software mixer + MP3 decoding),
// compiled straight from upstream forge-gui-desktop like the rest of the engine.
sourceSets["main"].java {
    srcDir(repoRoot.resolve("forge-gui-desktop/src/main/java"))
    include("forge/sound/AudioClip.java", "forge/sound/SoftwareMixer.java")
}

// The Android app's bundled images and fonts, served as classpath resources (see R.kt).
tasks.processResources {
    from("../app/src/main/res/drawable-nodpi") { into("drawable") }
    from("../app/src/main/res/font") { into("font") }
    from("../branding/icon-source.png") { into("icon") }
}

// Engine data travels next to the app (not inside the jar): build/appResources/common is
// installed as the app's resources directory (compose.application.resources.dir).
apply(from = rootProject.file("engine-data.gradle.kts"))
val stageEngineData by tasks.registering(Sync::class) {
    from(tasks.named("packageEngineData"))
    // Shown by the JVM the instant the app starts, before the window is ready (see -splash below).
    from(rootProject.file("branding/splash.png"))
    into(layout.buildDirectory.dir("appResources/common"))
}

dependencies {
    implementation(project(":engine:forge-gui"))
    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.ui)
    implementation(libs.lifecycle.runtime.compose.multiplatform)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.org.json)
    implementation("com.googlecode.soundlibs:jlayer:1.0.1.4")
    implementation("com.sipgate:mp3-wav:1.0.4") { exclude(group = "com.googlecode.soundlibs", module = "jlayer") }
}

compose.desktop {
    application {
        mainClass = "io.github.nmanuelides.mtgreforged.MainKt"
        // Forge holds every card script in memory, and multiplayer AI searches are hungry.
        jvmArgs += listOf("-Xms512m", "-Xmx4g", "-XX:+UseG1GC", "-Dfile.encoding=UTF-8", "-Dsun.java2d.d3d=false")
        // A picture right away while the JVM and Compose start (seconds with nothing on screen
        // otherwise, e.g. after an update); it closes when the first window opens.
        jvmArgs += "-splash:\$APPDIR/resources/splash.png"

        buildTypes.release.proguard {
            // Forge loads a lot through reflection; shrinking would need a long keep list.
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "MTG Reforged"
            packageVersion = installerVersion
            description = "MTG Reforged"
            vendor = "nmanuelides"
            copyright = "Forge rules engine: GPL-3.0. Magic: The Gathering is (c) Wizards of the Coast."
            appResourcesRootDir.set(layout.buildDirectory.dir("appResources"))
            // From :desktop:suggestRuntimeModules, plus what only shows at run time: TLS with
            // elliptic curves (Scryfall, GitHub, Google), more charsets, Spanish date formats.
            modules(
                "java.compiler", "java.instrument", "java.logging", "java.management", "java.naming", "java.net.http",
                "java.scripting", "java.security.jgss", "java.sql", "jdk.httpserver", "jdk.unsupported",
                "jdk.crypto.ec", "jdk.charsets", "jdk.localedata", "jdk.zipfs",
            )
            windows {
                // Never change: it is how a newer installer finds and upgrades the installed app.
                upgradeUuid = "6f1c2a8e-3b7d-4c55-9a0e-2d4b8f1e7c93"
                menuGroup = "MTG Reforged"
                menu = true
                shortcut = true
                dirChooser = true
                // Per-user install: no admin prompt, and the updater can replace it silently.
                perUserInstall = true
                iconFile.set(layout.buildDirectory.file("icon/mtg-reforged.ico"))
            }
        }
    }
}

// Windows icon from the same artwork as the Android launcher icon.
val windowsIcon by tasks.registering {
    val source = rootProject.file("branding/icon-source.png")
    val out = layout.buildDirectory.file("icon/mtg-reforged.ico")
    inputs.file(source)
    outputs.file(out)
    doLast { writeIco(source, out.get().asFile) }
}

/** A multi-size .ico whose entries are PNGs (supported since Windows Vista). */
fun writeIco(source: File, target: File) {
    val image = ImageIO.read(source)
    val sizes = listOf(16, 24, 32, 48, 64, 128, 256)
    val pngs = sizes.map { size ->
        val scaled = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        scaled.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            drawImage(image, 0, 0, size, size, null)
            dispose()
        }
        ByteArrayOutputStream().also { ImageIO.write(scaled, "png", it) }.toByteArray()
    }
    target.parentFile.mkdirs()
    DataOutputStream(target.outputStream().buffered()).use { out ->
        fun short(v: Int) { out.writeByte(v and 0xFF); out.writeByte(v shr 8 and 0xFF) }
        fun int(v: Int) { short(v and 0xFFFF); short(v ushr 16) }
        short(0); short(1); short(sizes.size)
        var offset = 6 + 16 * sizes.size
        sizes.zip(pngs).forEach { (size, png) ->
            out.writeByte(if (size >= 256) 0 else size); out.writeByte(if (size >= 256) 0 else size)
            out.writeByte(0); out.writeByte(0); short(1); short(32); int(png.size); int(offset)
            offset += png.size
        }
        pngs.forEach(out::write)
    }
}

tasks.matching { it.name in setOf("prepareAppResources", "run", "runDistributable", "runRelease") }.configureEach {
    dependsOn(stageEngineData)
}
tasks.matching { (it.name.startsWith("package") && it.name != "packageEngineData") || it.name.startsWith("create") }
    .configureEach { dependsOn(windowsIcon, stageEngineData) }

// Every installer also lands in android/dist as mtg-reforged-v<version>-windows.exe.
val distExe by tasks.registering(Copy::class) {
    from(layout.buildDirectory.dir("compose/binaries/main-release/exe"))
    into(rootProject.layout.projectDirectory.dir("dist"))
    include("*.exe")
    rename { "mtg-reforged-v$appVersion-windows.exe" }
}
tasks.matching { it.name == "packageReleaseExe" }.configureEach { finalizedBy(distExe) }
