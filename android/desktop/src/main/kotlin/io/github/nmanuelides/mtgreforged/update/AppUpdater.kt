package io.github.nmanuelides.mtgreforged.update

import io.github.nmanuelides.mtgreforged.BuildConfig
import io.github.nmanuelides.mtgreforged.platform.AppContext
import io.github.nmanuelides.mtgreforged.platform.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.system.exitProcess

/**
 * Self-update from the fork's GitHub Releases, like the Android app: every release tagged
 * `build-<versionCode>` also carries the Windows installer (mtg-reforged-v<version>-windows.exe).
 * Installing runs it; it upgrades the installed app in place.
 */
class AppUpdater(private val context: AppContext) {

    data class Release(val versionCode: Int, val name: String, val notes: String, val apkUrl: String, val sizeBytes: Long)

    /** Returns the latest release if it is newer than the running build and has a Windows installer. */
    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        val json = get("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest") ?: return@withContext null
        val release = JSONObject(json)
        val code = release.optString("tag_name").removePrefix("build-").toIntOrNull() ?: return@withContext null
        if (code <= BuildConfig.VERSION_CODE) return@withContext null
        val assets = release.optJSONArray("assets") ?: return@withContext null
        val exe = (0 until assets.length()).map(assets::getJSONObject)
            .firstOrNull { it.optString("name").endsWith(".exe") } ?: return@withContext null
        Release(
            versionCode = code,
            name = release.optString("name"),
            notes = release.optString("body"),
            apkUrl = exe.getString("browser_download_url"),
            sizeBytes = exe.optLong("size"),
        )
    }

    suspend fun download(release: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val target = File(dir, "mtg-reforged-${release.versionCode}-setup.exe")
        val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        try {
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total > 0) onProgress(read.toFloat() / total)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        target
    }

    /** Nothing to allow first on Windows. */
    fun canInstall(): Boolean = true

    fun openInstallPermissionSettings() = Unit

    /**
     * Starts the installer and closes the app so it can replace the files; once the installer
     * finishes, the app opens again. A small script does both: it waits for the installer, then
     * starts the installed app (from wherever this copy runs). It is launched through
     * `cmd /c start`, as a double-click would: started straight from the JVM it stays tied to the
     * app's process and output pipes and dies when the app exits a moment later.
     */
    fun install(installer: File) {
        val app = ProcessHandle.current().info().command().orElse(null)
            ?.takeIf { it.endsWith(".exe", ignoreCase = true) && !it.endsWith("java.exe", ignoreCase = true) }
        val script = File(installer.parentFile, "update-and-reopen.cmd")
        script.writeText(
            buildString {
                append("@echo off\r\n")
                append("start \"\" /wait \"${installer.absolutePath}\"\r\n")
                if (app != null) append("start \"\" \"$app\"\r\n")
            },
        )
        Log.i("Update", "starting ${installer.absolutePath}, then reopening ${app ?: "(unknown, not reopening)"}")
        // Java quotes the empty window title and the path (it has spaces) itself.
        ProcessBuilder("cmd", "/c", "start", "", "/min", script.absolutePath)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
            .waitFor()
        // Give Windows a moment to bring the installer up before the app's files unlock.
        Thread.sleep(1500)
        exitProcess(0)
    }

    private fun get(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        return try {
            if (connection.responseCode == 200) connection.inputStream.bufferedReader().readText() else null
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
