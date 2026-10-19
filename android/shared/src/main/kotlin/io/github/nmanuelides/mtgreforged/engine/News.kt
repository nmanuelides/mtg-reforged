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
    data class Item(val id: String, val title: String, val body: String, val link: String?)

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
        _unseen.value = items.filter { it.id !in seen }
    }

    fun markSeen(context: AppContext, item: Item) {
        val prefs = context.prefs(PREFS)
        val seen = prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty() + item.id
        prefs.edit().putStringSet(KEY_SEEN, seen).apply()
        _unseen.value = _unseen.value.filter { it.id != item.id }
    }
}
