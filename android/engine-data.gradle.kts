// Builds engine-data.zip: the Forge rules data (card scripts, editions, formats, AI profiles...)
// from the same commit as the engine code, so both always match. Applied by :app (APK asset)
// and :desktop (installed next to the Windows app); each extracts it on first run and after
// every update. Output: build/generated/engineData/engine-data.zip.

val repoRoot: File = rootProject.projectDir.parentFile
val upstreamRes: File = repoRoot.resolve("forge-gui/res")

fun gitOut(vararg args: String): String =
    providers.exec {
        commandLine("git", "-C", repoRoot.absolutePath, *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()

val commitCount = gitOut("rev-list", "--count", "HEAD").toIntOrNull() ?: 1
val commitSha = gitOut("rev-parse", "--short=10", "HEAD").ifEmpty { "unknown" }
val upstreamVersion: String = Regex("<versionCode>(.+)</versionCode>")
    .find(repoRoot.resolve("pom.xml").readText())?.groupValues?.get(1) ?: "0"

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

