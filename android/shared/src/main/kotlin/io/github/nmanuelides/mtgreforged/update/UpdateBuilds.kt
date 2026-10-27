package io.github.nmanuelides.mtgreforged.update

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.nmanuelides.mtgreforged.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * A newer version GitHub is building right now: [version] ("v1.02.0", if the build names one),
 * started at [startedAt] and expected to take [expectedMs] (what recent builds took).
 */
data class PendingBuild(val version: String?, val startedAt: Long, val expectedMs: Long) {
    /** How far along it should be by now; never quite done until the release is out. */
    fun progress(now: Long = System.currentTimeMillis()): Float =
        ((now - startedAt).toFloat() / expectedMs).coerceIn(0f, 0.99f)
}

/** Watches the GitHub workflow that builds and publishes new versions. */
object UpdateBuilds {
    private const val WORKFLOW = "reforged-android.yml"
    private val VERSION = Regex("""^v(\d+)\.(\d+)\.\d+""")

    /** The build running now, if it makes a version newer than this one. */
    suspend fun current(): PendingBuild? = withContext(Dispatchers.IO) {
        val json = get("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/actions/workflows/$WORKFLOW/runs?per_page=10")
            ?: return@withContext null
        val runs = JsonParser.parseString(json).asJsonObject.getAsJsonArray("workflow_runs")?.map { it.asJsonObject }.orEmpty()
        val active = runs.firstOrNull { it.text("status") != "completed" } ?: return@withContext null
        val title = active.text("display_title").orEmpty()
        val version = VERSION.find(title)
        // A build of the version already installed (or older) is no update.
        if (version != null) {
            val built = version.groupValues[1].toInt() * 100 + version.groupValues[2].toInt()
            val mine = VERSION.find("v" + BuildConfig.VERSION_NAME)?.let { it.groupValues[1].toInt() * 100 + it.groupValues[2].toInt() }
            if (mine != null && built <= mine) return@withContext null
        }
        val took = runs.filter { it.text("conclusion") == "success" }
            .mapNotNull { r -> time(r.text("run_started_at"))?.let { start -> time(r.text("updated_at"))?.minus(start) } }
            .filter { it > 0 }
        PendingBuild(
            version?.value,
            time(active.text("run_started_at")) ?: System.currentTimeMillis(),
            if (took.isEmpty()) 10 * 60_000L else took.average().toLong(),
        )
    }

    private fun JsonObject.text(key: String): String? = get(key)?.takeIf { !it.isJsonNull }?.asString

    private fun time(iso: String?): Long? = iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

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
