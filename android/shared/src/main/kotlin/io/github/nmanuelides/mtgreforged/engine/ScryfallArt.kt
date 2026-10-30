package io.github.nmanuelides.mtgreforged.engine

import forge.util.ScryfallRateLimiter
import io.github.nmanuelides.mtgreforged.BuildConfig
import io.github.nmanuelides.mtgreforged.platform.Log
import java.net.HttpURLConnection
import java.net.URL

/**
 * Scryfall drops its JPEG art crops ("art_crop") after 2027-01-01; the WebP "art" stays. The
 * format=image redirect isn't documented for the new names, so read the card's JSON and take
 * the WebP link from it, keeping the old link only for cards that have no WebP yet.
 */
object ScryfallArt {
    private const val TAG = "ScryfallArt"

    /** True for a Scryfall image link asking for the art crop. */
    fun isArtCrop(url: String) = "version=art_crop" in url

    /** The WebP art of the card [imageUrl] points at, or [imageUrl] itself if it can't be found. */
    fun resolve(imageUrl: String): String {
        val back = "face=back" in imageUrl
        val json = get(imageUrl.substringBefore('?')) ?: return imageUrl
        // Double-faced cards list their images per face, front first.
        val arts = Regex("\"art\"\\s*:\\s*\"([^\"]+)\"").findAll(json).map { it.groupValues[1] }.toList()
        return (if (back) arts.getOrNull(1) else arts.firstOrNull()) ?: imageUrl
    }

    private fun get(url: String): String? {
        if (ScryfallRateLimiter.shouldSkip(url)) return null
        ScryfallRateLimiter.acquire(url)
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "MTGReforged/${BuildConfig.VERSION_NAME}")
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        return try {
            if (connection.responseCode != 200) {
                ScryfallRateLimiter.noteIfRateLimited(connection.responseCode, url, connection.getHeaderField("Retry-After"))
                null
            } else connection.inputStream.use { String(it.readBytes()) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed $url", e)
            null
        } finally {
            connection.disconnect()
        }
    }
}
