package io.github.nmanuelides.mtgreforged.engine

import androidx.compose.ui.graphics.ImageBitmap

/** Decoded images kept in memory, least recently used dropped first, up to [maxKb] of pixels. */
class BitmapCache(private val maxKb: Int) {
    private val map = LinkedHashMap<String, ImageBitmap>(64, 0.75f, true)
    private var sizeKb = 0

    private val ImageBitmap.kb get() = width * height * 4 / 1024

    @Synchronized
    fun get(key: String): ImageBitmap? = map[key]

    @Synchronized
    fun put(key: String, image: ImageBitmap) {
        map.put(key, image)?.let { sizeKb -= it.kb }
        sizeKb += image.kb
        val entries = map.entries.iterator()
        while (sizeKb > maxKb && entries.hasNext()) {
            val eldest = entries.next()
            if (eldest.key == key) continue
            sizeKb -= eldest.value.kb
            entries.remove()
        }
    }

    @Synchronized
    fun evictAll() {
        map.clear()
        sizeKb = 0
    }
}
