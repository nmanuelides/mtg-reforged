package io.github.nmanuelides.mtgreforged.engine

import forge.localinstance.properties.ForgeConstants
import forge.util.ImageFetcher
import forge.util.ScryfallRateLimiter
import io.github.nmanuelides.mtgreforged.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import io.github.nmanuelides.mtgreforged.platform.Log
import io.github.nmanuelides.mtgreforged.platform.MainThread

/**
 * Downloads card images using the URL candidates Forge computes (Scryfall first).
 * Files are stored exactly where Forge's ImageKeys expects them, mirroring the
 * desktop fetcher's naming rules, so [CardImages] can find them afterwards.
 */
class CardImageFetcher : ImageFetcher() {

    override fun getDownloadTask(urls: Array<String>, destPath: String, notifyObservers: Runnable) = Runnable {
        for (url in urls) {
            if (runCatching { fetch(url, destPath) }.onFailure { Log.w(TAG, "Failed $url", it) }.getOrDefault(false)) {
                break
            }
        }
        MainThread.post(notifyObservers)
    }

    private fun fetch(requested: String, destPath: String): Boolean {
        if (requested.startsWith(ForgeConstants.URL_CARDFORGE)) return false
        // Art crops come as WebP from 2027 on (the file keeps Forge's name; decoders sniff the format).
        val urlToDownload = if (ScryfallArt.isArtCrop(requested)) ScryfallArt.resolve(requested) else requested
        val isScryfall = requested.startsWith(ForgeConstants.URL_PIC_SCRYFALL_DOWNLOAD) ||
            requested.startsWith(ForgeConstants.URL_SCRYFALL_CDN)
        var target = if (requested.contains(".fullborder.jpg") || isScryfall) {
            destPath.replace(".full.jpg", ".fullborder.jpg")
        } else destPath
        if (!target.contains(".full") && !target.contains(".artcrop") && isScryfall &&
            !destPath.startsWith(ForgeConstants.CACHE_TOKEN_PICS_DIR)
        ) {
            target = target.replace(".jpg", ".fullborder.jpg")
        }

        if (ScryfallRateLimiter.shouldSkip(urlToDownload)) return false
        ScryfallRateLimiter.acquire(urlToDownload)

        val connection = URL(urlToDownload).openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "*/*")
        connection.setRequestProperty("User-Agent", "MTGReforged/${BuildConfig.VERSION_NAME}")
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                ScryfallRateLimiter.noteIfRateLimited(connection.responseCode, urlToDownload, connection.getHeaderField("Retry-After"))
                return false
            }
            val tmp = File("$target.tmp").apply { parentFile?.mkdirs() }
            connection.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            return tmp.renameTo(File(target))
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "ImageFetcher"
    }
}
