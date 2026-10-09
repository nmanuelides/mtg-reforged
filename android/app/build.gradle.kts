plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val repoRoot: File = rootProject.projectDir.parentFile
val upstreamRes: File = repoRoot.resolve("forge-gui/res")

fun git(vararg args: String): String =
    providers.exec {
        commandLine("git", "-C", repoRoot.absolutePath, *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()

// Monotonic on our branch: every upstream merge or local commit bumps it.
val commitCount = git("rev-list", "--count", "HEAD").toIntOrNull() ?: 1
val commitSha = git("rev-parse", "--short=10", "HEAD").ifEmpty { "unknown" }
val upstreamVersion: String = Regex("<versionCode>(.+)</versionCode>")
    .find(repoRoot.resolve("pom.xml").readText())?.groupValues?.get(1) ?: "0"

android {
    namespace = "io.github.nmanuelides.mtgreforged"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.nmanuelides.mtgreforged"
        minSdk = 26
        targetSdk = 36
        versionCode = commitCount
        versionName = "$upstreamVersion-$commitSha"

        buildConfigField("String", "ENGINE_COMMIT", "\"$commitSha\"")
        buildConfigField("String", "UPDATE_REPO", "\"nmanuelides/mtg-reforged\"")
    }

    signingConfigs {
        create("release") {
            // CI provides a real keystore; locally we fall back to the debug key.
            val keystore = System.getenv("RELEASE_KEYSTORE")?.let(::file)
            if (keystore != null && keystore.exists()) {
                storeFile = keystore
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/*.md",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/versions/**",
                "about.html",
                "about_files/**",
                "jetty-dir.css",
            )
            pickFirsts += listOf("META-INF/services/*")
        }
    }

    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/engineData"))
}

kotlin {
    jvmToolchain(17)
}

// --- Engine data ------------------------------------------------------------
// The rules data (card scripts, editions, formats, AI profiles...) ships inside
// the APK as engine-data.zip, built from the same commit as the engine code so
// both always match. The app extracts it on first run and after every update.

val dataExcludes = listOf(
    "adventure/**", "music/**", "skins/**", "fonts/**",
    "cardsfolder/**", "**/*.xcf", "**/*.psd",
)

val zipCardsfolder by tasks.registering(Zip::class) {
    from(upstreamRes.resolve("cardsfolder"))
    archiveFileName.set("cardsfolder.zip")
    destinationDirectory.set(layout.buildDirectory.dir("intermediates/engineData"))
    isReproducibleFileOrder = true
}

val writeDataManifest by tasks.registering {
    val out = layout.buildDirectory.file("intermediates/engineData/manifest.properties")
    inputs.property("sha", commitSha)
    inputs.property("versionCode", commitCount)
    outputs.file(out)
    doLast {
        out.get().asFile.writeText("engineCommit=$commitSha\nversionCode=$commitCount\nupstreamVersion=$upstreamVersion\n")
    }
}

val packageEngineData by tasks.registering(Zip::class) {
    archiveFileName.set("engine-data.zip")
    destinationDirectory.set(layout.buildDirectory.dir("generated/engineData"))
    from(upstreamRes) {
        into("res")
        exclude(dataExcludes)
        // Translated card names are ~8 MB per language; ship only Spanish (English is built in).
        exclude { it.name.startsWith("cardnames-") && !it.name.contains("es-ES") }
    }
    from(zipCardsfolder) { into("res/cardsfolder") }
    from(writeDataManifest)
}

tasks.named("preBuild") { dependsOn(packageEngineData) }

dependencies {
    implementation(project(":engine:forge-gui"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.palette)
    debugImplementation(libs.compose.ui.tooling)

    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
