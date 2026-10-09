package io.github.nmanuelides.mtgreforged.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import forge.ImageKeys
import forge.gui.GuiBase
import forge.util.ImageUtil
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

    private suspend fun fetch(key: String): File? = withContext(Dispatchers.Main) {
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
        withContext(Dispatchers.IO) { findFile(key) }
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

    fun clear() {
        memory.evictAll()
        missing.clear()
    }
}
