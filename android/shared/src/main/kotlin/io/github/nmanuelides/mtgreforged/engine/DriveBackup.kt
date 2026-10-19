package io.github.nmanuelides.mtgreforged.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Backups in the user's Google Drive, in the app's private folder (drive.appdata: the app
 * sees only its own files there and nothing else in the user's Drive). Every backup is named
 * mtg-reforged-backup-<yyyy-MM-dd-HHmmss>.json and carries its card and deck counts as file
 * properties, so the restore list can describe each one without downloading it. Getting the
 * access token is up to each platform (see platform.rememberDriveToken).
 */
object DriveBackup {
    const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    const val PREFIX = "mtg-reforged-backup-"

    data class Entry(val id: String, val name: String, val createdAt: Long, val cards: Int, val decks: Int)

    suspend fun upload(token: String): Entry = withContext(Dispatchers.IO) {
        val body = Backup.export()
        val cards = CardCollection.cards().values.sum()
        val decks = Engine.Format.entries.sumOf { f -> UserDecks.dir(f).listFiles { x -> x.extension == "dck" }.orEmpty().size }
        val stamp = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())
        val meta = JSONObject()
            .put("name", "$PREFIX$stamp.json")
            .put("parents", JSONArray().put("appDataFolder"))
            .put("mimeType", "application/json")
            .put("appProperties", JSONObject().put("cards", cards.toString()).put("decks", decks.toString()))
        val boundary = "reforged${System.nanoTime()}"
        val payload = buildString {
            append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n").append(meta).append("\r\n")
            append("--$boundary\r\nContent-Type: application/json\r\n\r\n").append(body).append("\r\n")
            append("--$boundary--\r\n")
        }
        val res = call(
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,createdTime,appProperties",
            token, "POST", payload.toByteArray(), "multipart/related; boundary=$boundary",
        )
        entry(JSONObject(res))
    }

    suspend fun list(token: String): List<Entry> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode("name contains '$PREFIX' and trashed = false", "UTF-8")
        val res = call(
            "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$q&orderBy=createdTime%20desc" +
                "&pageSize=100&fields=files(id,name,createdTime,appProperties)",
            token,
        )
        val files = JSONObject(res).optJSONArray("files") ?: JSONArray()
        (0 until files.length()).map { entry(files.getJSONObject(it)) }
    }

    suspend fun restore(token: String, entry: Entry): Backup.Restored = withContext(Dispatchers.IO) {
        Backup.restore(call("https://www.googleapis.com/drive/v3/files/${entry.id}?alt=media", token))
    }

    suspend fun delete(token: String, entry: Entry) = withContext(Dispatchers.IO) {
        call("https://www.googleapis.com/drive/v3/files/${entry.id}", token, "DELETE")
    }

    private fun entry(json: JSONObject): Entry {
        val props = json.optJSONObject("appProperties")
        val created = runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(json.optString("createdTime").substringBefore('.'))?.time
        }.getOrNull() ?: 0L
        return Entry(
            id = json.getString("id"),
            name = json.optString("name"),
            createdAt = created,
            cards = props?.optString("cards")?.toIntOrNull() ?: 0,
            decks = props?.optString("decks")?.toIntOrNull() ?: 0,
        )
    }

    private fun call(url: String, token: String, method: String = "GET", body: ByteArray? = null, type: String? = null): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.setRequestProperty("Authorization", "Bearer $token")
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        if (body != null) {
            c.doOutput = true
            type?.let { c.setRequestProperty("Content-Type", it) }
            c.outputStream.use { it.write(body) }
        }
        try {
            val code = c.responseCode
            if (code !in 200..299) {
                val err = c.errorStream?.bufferedReader()?.readText().orEmpty()
                throw IllegalStateException("Google Drive answered $code ${err.take(200)}")
            }
            return c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }
}
