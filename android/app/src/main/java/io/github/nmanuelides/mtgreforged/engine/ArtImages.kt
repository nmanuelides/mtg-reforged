package io.github.nmanuelides.mtgreforged.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import forge.StaticData
import forge.item.PaperCard
import forge.localinstance.properties.ForgeConstants
import forge.util.ImageUtil
import forge.util.ScryfallRateLimiter
import io.github.nmanuelides.mtgreforged.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Card illustrations without the frame (Scryfall "art_crop"), used as the storybook
 * artwork on the home and deck screens. Kept apart from Forge's card image cache.
 */
object ArtImages {
    private val memory = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 10).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }
    private val locks = HashMap<String, Mutex>()
    private val dir: File by lazy { File(ForgeConstants.CACHE_DIR, "art").apply { mkdirs() } }

    fun cached(cardName: String): Bitmap? = memory.get(cardName)

    suspend fun load(cardName: String): Bitmap? {
        memory.get(cardName)?.let { return it }
        val lock = synchronized(locks) { locks.getOrPut(cardName) { Mutex() } }
        return lock.withLock {
            memory.get(cardName) ?: withContext(Dispatchers.IO) {
                val file = File(dir, cardName.replace(Regex("[^A-Za-z0-9]+"), "_") + ".jpg")
                if (!file.isFile) download(cardName, file)
                file.takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path) }
            }?.also { memory.put(cardName, it) }
        }
    }

    private fun download(cardName: String, target: File) {
        val card: PaperCard = runCatching { StaticData.instance().commonCards.getCard(cardName) }.getOrNull() ?: return
        val setCode = StaticData.instance().editions.get(card.edition)?.scryfallCode
        val url = ForgeConstants.URL_PIC_SCRYFALL_DOWNLOAD + ImageUtil.getScryfallDownloadUrl(card, "", setCode, "en", true)
        if (ScryfallRateLimiter.shouldSkip(url)) return
        ScryfallRateLimiter.acquire(url)
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "MTGReforged/${BuildConfig.VERSION_NAME}")
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        try {
            if (connection.responseCode != 200) {
                ScryfallRateLimiter.noteIfRateLimited(connection.responseCode, url, connection.getHeaderField("Retry-After"))
                return
            }
            val tmp = File(target.path + ".tmp")
            connection.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(target)
        } catch (e: Exception) {
            android.util.Log.w("ArtImages", "Failed $url", e)
        } finally {
            connection.disconnect()
        }
    }
}
