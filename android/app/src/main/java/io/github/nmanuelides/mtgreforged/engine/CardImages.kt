package io.github.nmanuelides.mtgreforged.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import forge.ImageKeys
import forge.gui.GuiBase
import kotlinx.coroutines.Dispatchers
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
    private val missing = HashSet<String>()

    private fun cacheSizeKb() = (Runtime.getRuntime().maxMemory() / 1024 / 6).toInt()

    fun cached(key: String): Bitmap? = memory.get(key)

    suspend fun load(key: String): Bitmap? {
        memory.get(key)?.let { return it }
        if (key in missing) return null
        val file = withContext(Dispatchers.IO) { ImageKeys.getImageFile(key) } ?: fetch(key)
        val bitmap = file?.let { withContext(Dispatchers.IO) { decode(it) } }
        if (bitmap == null) missing += key else memory.put(key, bitmap)
        return bitmap
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
        withContext(Dispatchers.IO) { ImageKeys.getImageFile(key) }
    }

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
