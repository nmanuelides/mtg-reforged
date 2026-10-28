package io.github.nmanuelides.mtgreforged.engine

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Backs up the player's collection and their own decks to one JSON document (the user saves
 * it wherever they like, e.g. Google Drive, through Android's file picker) and restores it.
 */
object Backup {
    private const val APP = "MTG Reforged"

    fun export(): String {
        val json = JSONObject()
            .put("app", APP)
            .put("version", 1)
            .put("createdAt", System.currentTimeMillis())
        CardCollection.backupFile().takeIf { it.isFile }?.let { json.put("collection", it.readText()) }
        val decks = JSONArray()
        Engine.Format.entries.forEach { format ->
            UserDecks.dir(format).listFiles { f -> f.extension == "dck" }.orEmpty().forEach { file ->
                decks.put(JSONObject().put("format", format.name).put("file", file.name).put("content", file.readText()))
            }
        }
        return json.put("decks", decks).toString(2)
    }

    data class Restored(val decks: Int, val collection: Boolean)

    /**
     * Writes the backup's collection and decks back (decks with the same name are replaced);
     * with [replace], the player's decks the backup doesn't have are deleted too.
     */
    fun restore(text: String, replace: Boolean = false): Restored {
        val json = JSONObject(text)
        require(json.optString("app") == APP) { "That file isn't an MTG Reforged backup." }
        val collection = json.optString("collection").takeIf { it.isNotBlank() }
        collection?.let {
            CardCollection.backupFile().apply { parentFile?.mkdirs() }.writeText(it)
            CardCollection.reload()
        }
        val decks = json.optJSONArray("decks") ?: JSONArray()
        var restored = 0
        val kept = HashSet<File>()
        for (i in 0 until decks.length()) {
            val deck = decks.getJSONObject(i)
            val format = runCatching { Engine.Format.valueOf(deck.getString("format")) }.getOrNull() ?: continue
            // Only a bare file name: a backup must never write outside the deck folders.
            val name = File(deck.getString("file")).name.takeIf { it.endsWith(".dck") } ?: continue
            File(UserDecks.dir(format), name).also { kept += it.absoluteFile }.writeText(deck.getString("content"))
            restored++
        }
        if (replace) Engine.Format.entries.forEach { format ->
            UserDecks.dir(format).listFiles { f -> f.extension == "dck" }.orEmpty().filter { it.absoluteFile !in kept }.forEach { it.delete() }
        }
        Engine.Format.entries.forEach(Engine::invalidateDecks)
        return Restored(restored, collection != null)
    }
}
