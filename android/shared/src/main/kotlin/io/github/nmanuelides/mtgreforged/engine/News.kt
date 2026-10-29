package io.github.nmanuelides.mtgreforged.engine

import io.github.nmanuelides.mtgreforged.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import io.github.nmanuelides.mtgreforged.platform.prefs
import io.github.nmanuelides.mtgreforged.platform.AppContext

/**
 * Messages from the developer to every player: android/news.json in the GitHub repo, read
 * when the app opens. Each message shows once (remembered by its id).
 */
object News {
    /** [ids]: every message it stands for (several changelog entries shown as one). */
    data class Item(val id: String, val title: String, val body: String, val link: String?, val ids: List<String> = listOf(id))

    private const val PREFS = "news"
    private const val KEY_SEEN = "seen"

    private val _unseen = MutableStateFlow<List<Item>>(emptyList())
    val unseen: StateFlow<List<Item>> = _unseen.asStateFlow()

    private val url get() = "https://raw.githubusercontent.com/${BuildConfig.UPDATE_REPO}/master/android/news.json"

    /** Call off the main thread. Failures (offline…) just mean no news this time. */
    fun refresh(context: AppContext) {
        val prefs = context.prefs(PREFS)
        val seen = prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty()
        val items = runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.useCaches = false
            try {
                if (connection.responseCode != 200) return
                val json = JSONObject(connection.inputStream.bufferedReader().readText())
                val list = json.optJSONArray("messages") ?: return
                (0 until list.length()).map { i ->
                    val m = list.getJSONObject(i)
                    Item(
                        id = m.getString("id"),
                        title = m.optString("title"),
                        body = m.optString("body"),
                        link = m.optString("link").takeIf { it.isNotBlank() && it != "null" },
                    )
                }
            } finally {
                connection.disconnect()
            }
        }.getOrNull() ?: return
        val fresh = items.filter { it.id !in seen && isForInstalledVersion(it.id) }
        // Every version's changelog missed (after skipping several updates) in one message, newest first.
        val (changelogs, others) = fresh.partition { VERSION.containsMatchIn(it.id) }
        val changelog = when (changelogs.size) {
            0 -> null
            1 -> changelogs[0]
            else -> Item(
                id = "changelog",
                title = "What's new since your last update",
                body = changelogs.reversed().joinToString("\n\n") { it.id + "\n" + it.body },
                link = null,
                ids = changelogs.map { it.id },
            )
        }
        _unseen.value = others + listOfNotNull(changelog)
    }

    /**
     * A changelog entry ("v0.82.0") belongs to that version: it shows only once that version (or a
     * newer one) is installed, not on an older copy that downloads the newer news. Other messages
     * always show.
     */
    private val VERSION = Regex("^v(\\d+)\\.(\\d+)\\.(\\d+)")

    private fun isForInstalledVersion(id: String): Boolean {
        val entry = VERSION.find(id)?.groupValues?.drop(1)?.map { it.toInt() } ?: return true
        val installed = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)").find(BuildConfig.VERSION_NAME)?.groupValues?.drop(1)?.map { it.toInt() } ?: return true
        for (i in 0..2) if (entry[i] != installed[i]) return entry[i] < installed[i]
        return true
    }

    fun markSeen(context: AppContext, item: Item) {
        val prefs = context.prefs(PREFS)
        val seen = prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty() + item.ids
        prefs.edit().putStringSet(KEY_SEEN, seen).apply()
        _unseen.value = _unseen.value.filter { it.id != item.id }
    }
}
