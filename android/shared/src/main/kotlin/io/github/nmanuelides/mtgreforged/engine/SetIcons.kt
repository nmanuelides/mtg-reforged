package io.github.nmanuelides.mtgreforged.engine

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.PathParser
import forge.StaticData
import forge.localinstance.properties.ForgeConstants
import forge.util.ScryfallRateLimiter
import io.github.nmanuelides.mtgreforged.BuildConfig
import io.github.nmanuelides.mtgreforged.platform.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A set's expansion symbol: its outline in [viewBox] units, ready to scale and tint. */
class SetIcon(val path: Path, val viewBox: Rect)

/**
 * Expansion symbols from Scryfall (an SVG per set), downloaded once into the cache and read as
 * plain paths: they're single-colour shapes, so there's no need for a full SVG renderer.
 */
object SetIcons {
    private val memory = HashMap<String, SetIcon?>()
    private val locks = HashMap<String, Mutex>()
    private val dir: File by lazy { File(ForgeConstants.CACHE_DIR, "sets").apply { mkdirs() } }

    fun cached(code: String): SetIcon? = synchronized(memory) { memory[code] }

    suspend fun load(code: String): SetIcon? {
        synchronized(memory) { if (code in memory) return memory[code] }
        val lock = synchronized(locks) { locks.getOrPut(code) { Mutex() } }
        return lock.withLock {
            synchronized(memory) { if (code in memory) return@withLock memory[code] }
            val icon = withContext(Dispatchers.IO) {
                val file = File(dir, code.replace(Regex("[^A-Za-z0-9]+"), "_") + ".svg")
                if (!file.isFile) download(code, file)
                file.takeIf { it.isFile }?.let { runCatching { parse(it.readText()) }.getOrNull() }
            }
            // A failure is only remembered for this run: the next launch tries again.
            synchronized(memory) { memory[code] = icon }
            icon
        }
    }

    private fun download(code: String, target: File) {
        val scryfall = runCatching { StaticData.instance().editions.get(code)?.scryfallCode }.getOrNull() ?: code
        // The set's record names its icon (sub-sets share their parent's).
        val json = get("https://api.scryfall.com/sets/${scryfall.lowercase()}") ?: return
        val svgUrl = Regex("\"icon_svg_uri\"\\s*:\\s*\"([^\"]+)\"").find(String(json))?.groupValues?.get(1) ?: return
        val svg = get(svgUrl) ?: return
        val tmp = File(target.path + ".tmp")
        tmp.writeBytes(svg)
        tmp.renameTo(target)
    }

    private fun get(url: String): ByteArray? {
        if (ScryfallRateLimiter.shouldSkip(url)) return null
        ScryfallRateLimiter.acquire(url)
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "*/*")
        connection.setRequestProperty("User-Agent", "MTGReforged/${BuildConfig.VERSION_NAME}")
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        return try {
            if (connection.responseCode != 200) {
                ScryfallRateLimiter.noteIfRateLimited(connection.responseCode, url, connection.getHeaderField("Retry-After"))
                null
            } else connection.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            Log.w("SetIcons", "Failed $url", e)
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(svg: String): SetIcon? {
        val box = Regex("viewBox=\"([^\"]+)\"").find(svg)?.groupValues?.get(1)
            ?.trim()?.split(Regex("[\\s,]+"))?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 4 }
            ?: return null
        val path = Path()
        Regex("<path[^>]*?\\sd=\"([^\"]+)\"").findAll(svg).forEach { match ->
            path.addPath(PathParser().parsePathString(match.groupValues[1]).toPath())
        }
        if (path.isEmpty) return null
        if ("evenodd" in svg) path.fillType = PathFillType.EvenOdd
        return SetIcon(path, Rect(box[0], box[1], box[0] + box[2], box[1] + box[3]))
    }
}
