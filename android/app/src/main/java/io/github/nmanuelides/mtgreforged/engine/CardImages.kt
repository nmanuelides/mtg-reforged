package io.github.nmanuelides.mtgreforged.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import forge.ImageKeys
import forge.item.IPaperCard
import forge.gui.GuiBase
import forge.localinstance.properties.ForgeConstants
import forge.util.ImageUtil
import forge.util.ScryfallRateLimiter
import io.github.nmanuelides.mtgreforged.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

/**
 * Card image cache. Looks for the file where Forge keeps downloaded images; if it is
 * missing, asks Forge's image fetcher to download it and waits for the callback.
 */
object CardImages {
    private const val MAX_WIDTH = 488 // Scryfall "normal" width; plenty for a phone.

    private val memory = object : LruCache<String, Bitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }
    private val missing: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet())
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefetchJob: Job? = null

    private fun cacheSizeKb() = (Runtime.getRuntime().maxMemory() / 1024 / 6).toInt()

    fun cached(key: String): Bitmap? = memory.get(key)

    suspend fun load(key: String): Bitmap? {
        memory.get(key)?.let { return it }
        if (key in missing) return null
        val file = withContext(Dispatchers.IO) { findFile(key) } ?: fetch(key)
        val bitmap = file?.let { withContext(Dispatchers.IO) { decode(it) } }
        if (bitmap == null) missing += key else memory.put(key, bitmap)
        return bitmap
    }

    /**
     * Downloads (without decoding) every image in [keys], a few at a time, so cards are
     * already on disk when they show up in a match. Replaces any earlier prefetch.
     */
    fun prefetch(keys: List<String>) {
        prefetchJob?.cancel()
        prefetchJob = prefetchScope.launch {
            keys.distinct().chunked(4).forEach { batch ->
                batch.map { key ->
                    async { runCatching { if (findFile(key) == null) fetch(key) } }
                }.awaitAll()
            }
        }
    }

    enum class DiskResult { AlreadyThere, Downloaded, Failed }

    /** Downloads [key] if it isn't on disk yet, without decoding it (bulk / offline download). */
    suspend fun ensureOnDisk(key: String): DiskResult {
        if (withContext(Dispatchers.IO) { findFile(key) } != null) return DiskResult.AlreadyThere
        return if (fetch(key) != null) DiskResult.Downloaded else DiskResult.Failed
    }

    private suspend fun fetch(key: String): File? {
        // Forge skips printings without a collector number and never calls back; don't wait for it.
        if (hasNoCollectorNumber(key)) return withContext(Dispatchers.IO) { fetchByName(key) }
        return fetchWithForge(key)
    }

    private fun hasNoCollectorNumber(gameKey: String): Boolean {
        if (!gameKey.startsWith(ImageKeys.CARD_PREFIX)) return false
        val card = runCatching { ImageUtil.getPaperCardFromImageKey(gameKey.removeSuffix(ImageKeys.BACKFACE_POSTFIX)) }.getOrNull()
        return card != null && card.collectorNumber == IPaperCard.NO_COLLECTOR_NUMBER
    }

    private suspend fun fetchWithForge(key: String): File? = withContext(Dispatchers.Main) {
        // fetchImage silently returns without a callback in several cases, hence the timeout.
        withTimeoutOrNull(30_000) {
            suspendCancellableCoroutine { cont ->
                GuiBase.getInterface().imageFetcher.fetchImage(key) {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
        // The lookup before the download marked this card as missing; forget that.
        synchronized(ImageKeys::class.java) { ImageKeys.clearMissingCards() }
        withContext(Dispatchers.IO) { findFile(key) ?: fetchByName(key) }
    }

    /**
     * Fallback for printings Forge can't build a Scryfall URL for (no collector number):
     * Scryfall's exact-name lookup, saved where Forge would have. Scryfall has no Alchemy
     * rebalanced cards ("A-Name"), so those show the original card's image.
     */
    private fun fetchByName(gameKey: String): File? {
        if (!gameKey.startsWith(ImageKeys.CARD_PREFIX) || SPEC_FACES.keys.any { gameKey.endsWith(it) }) return null
        val back = gameKey.endsWith(ImageKeys.BACKFACE_POSTFIX)
        val card = ImageUtil.getPaperCardFromImageKey(gameKey.removeSuffix(ImageKeys.BACKFACE_POSTFIX)) ?: return null
        val fileKey = (if (back) card.cardAltImageKey else card.cardImageKey)?.takeIf { it.isNotBlank() } ?: return null
        val dest = File(ForgeConstants.CACHE_CARD_PICS_DIR, "$fileKey.jpg")
        val name = card.name.removePrefix("A-")
        val url = "https://api.scryfall.com/cards/named?exact=" + URLEncoder.encode(name, "UTF-8") +
            "&format=image&version=normal" + if (back) "&face=back" else ""
        if (ScryfallRateLimiter.shouldSkip(url)) return null
        ScryfallRateLimiter.acquire(url)
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "MTGReforged/${BuildConfig.VERSION_NAME}")
        connection.setRequestProperty("Accept", "*/*")
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                ScryfallRateLimiter.noteIfRateLimited(connection.responseCode, url, connection.getHeaderField("Retry-After"))
                null
            } else {
                val tmp = File("${dest.path}.tmp").apply { parentFile?.mkdirs() }
                connection.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(dest)
                synchronized(ImageKeys::class.java) { ImageKeys.clearMissingCards() }
                findFile(gameKey) ?: dest.takeIf { it.exists() }
            }
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Game keys ("c:Name|SET|n", optionally with a face suffix) must be turned into the
     * card's file key before ImageKeys can find the file; same as the libGDX ImageCache.
     */
    private fun findFile(gameKey: String): File? {
        var key = gameKey
        val altState = key.endsWith(ImageKeys.BACKFACE_POSTFIX)
        val specColor = SPEC_FACES.entries.firstOrNull { key.endsWith(it.key) }?.value
        if (altState) key = key.removeSuffix(ImageKeys.BACKFACE_POSTFIX)
        if (specColor != null) key = key.dropLast(ImageKeys.SPECFACE_W.length)
        key = when {
            key.startsWith(ImageKeys.CARD_PREFIX) -> ImageUtil.getPaperCardFromImageKey(key)?.let { card ->
                when {
                    altState -> card.cardAltImageKey
                    specColor != null -> ImageUtil.getImageKey(card, specColor, true)
                    else -> card.cardImageKey
                }
            } ?: key
            key.startsWith(ImageKeys.TOKEN_PREFIX) -> ImageUtil.getPaperTokenFromImageKey(key)?.cardImageKey ?: key
            else -> key
        }
        // ImageKeys keeps unsynchronised caches; prefetch and on-screen loads run in parallel.
        return if (key.isBlank()) null else synchronized(ImageKeys::class.java) { ImageKeys.getImageFile(key) }
    }

    private val SPEC_FACES = mapOf(
        ImageKeys.SPECFACE_W to "white", ImageKeys.SPECFACE_U to "blue", ImageKeys.SPECFACE_B to "black",
        ImageKeys.SPECFACE_R to "red", ImageKeys.SPECFACE_G to "green",
    )

    private fun decode(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_WIDTH) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Lets cards that failed (e.g. while offline) try again; see [Connectivity]. */
    fun forgetMissing() = missing.clear()

    fun clear() {
        memory.evictAll()
        missing.clear()
    }
}
