package io.github.nmanuelides.mtgreforged.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.github.nmanuelides.mtgreforged.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-update from the fork's GitHub Releases. CI merges upstream Forge every day and
 * publishes a release tagged `build-<versionCode>` whose APK bundles the new engine and
 * its matching card data; installing it is all the app needs to update the rules.
 */
class AppUpdater(private val context: Context) {

    data class Release(val versionCode: Int, val name: String, val notes: String, val apkUrl: String, val sizeBytes: Long)

    /** Returns the latest release if it is newer than the running build. */
    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        val json = get("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest") ?: return@withContext null
        val release = JSONObject(json)
        val code = release.optString("tag_name").removePrefix("build-").toIntOrNull() ?: return@withContext null
        if (code <= BuildConfig.VERSION_CODE) return@withContext null
        val assets = release.optJSONArray("assets") ?: return@withContext null
        val apk = (0 until assets.length()).map(assets::getJSONObject)
            .firstOrNull { it.optString("name").endsWith(".apk") } ?: return@withContext null
        Release(
            versionCode = code,
            name = release.optString("name"),
            notes = release.optString("body"),
            apkUrl = apk.getString("browser_download_url"),
            sizeBytes = apk.optLong("size"),
        )
    }

    suspend fun download(release: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val target = File(dir, "mtg-reforged-${release.versionCode}.apk")
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

    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Hands the APK to the system installer; the user confirms the update there. */
    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
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
