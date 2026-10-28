package io.github.nmanuelides.mtgreforged.engine

import io.github.nmanuelides.mtgreforged.platform.AppContext
import io.github.nmanuelides.mtgreforged.platform.prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Keeps devices on the same Google account in step through their Drive backups. Each device
 * remembers which backup it last matched (uploaded or restored) and a fingerprint of its
 * collection and decks at that moment: a newer backup restores by itself when nothing changed
 * here since, and when something did, the player chooses which side to keep.
 */
object BackupSync {
    private const val PREFS = "backup-sync"
    private const val KEY_ID = "syncedId"
    private const val KEY_HASH = "syncedHash"

    sealed interface Check {
        /** Nothing newer in Drive (or no backups at all). */
        data object UpToDate : Check
        /** The newer backup was restored, as nothing had changed here. */
        data class Restored(val entry: DriveBackup.Entry) : Check
        /** A newer backup, but this device has changes that aren't backed up. */
        data class Conflict(val entry: DriveBackup.Entry, val text: String) : Check
    }

    /** Looks at the newest Drive backup and restores it if nothing here would be lost. */
    suspend fun check(context: AppContext, token: String): Check = withContext(Dispatchers.IO) {
        val latest = DriveBackup.list(token).firstOrNull() ?: return@withContext Check.UpToDate
        val prefs = context.prefs(PREFS)
        if (latest.id == prefs.getString(KEY_ID, null)) return@withContext Check.UpToDate
        val text = DriveBackup.download(token, latest)
        val local = Backup.export()
        // Already the same collection and decks (e.g. this device made it): just remember it.
        if (fingerprint(text) == fingerprint(local)) {
            markSynced(context, latest.id)
            return@withContext Check.UpToDate
        }
        val synced = prefs.getString(KEY_HASH, null)
        val changed = if (synced != null) fingerprint(local) != synced else !isEmpty(local)
        if (changed) return@withContext Check.Conflict(latest, text)
        apply(context, latest, text)
        Check.Restored(latest)
    }

    /** Makes this device match [text] (the backup [entry]): decks it doesn't have are removed. */
    fun apply(context: AppContext, entry: DriveBackup.Entry, text: String) {
        Backup.restore(text, replace = true)
        markSynced(context, entry.id)
    }

    /** This device now matches the backup [id] (just uploaded or restored). */
    fun markSynced(context: AppContext, id: String) {
        context.prefs(PREFS).edit().putString(KEY_ID, id).putString(KEY_HASH, fingerprint(Backup.export())).apply()
    }

    private fun isEmpty(text: String): Boolean {
        val json = JSONObject(text)
        // The collection is a .dck file: any "<count> <card>" line means it has cards.
        val cards = json.optString("collection").lineSequence().any { it.trim().firstOrNull()?.isDigit() == true }
        return (json.optJSONArray("decks")?.length() ?: 0) == 0 && !cards
    }

    /** The collection and decks of a backup document, ignoring when it was made and deck order. */
    private fun fingerprint(text: String): String {
        val json = JSONObject(text)
        val decks = json.optJSONArray("decks") ?: JSONArray()
        val parts = (0 until decks.length()).map { i ->
            val d = decks.getJSONObject(i)
            d.optString("format") + "/" + d.optString("file") + "\n" + d.optString("content").trim()
        }.sorted()
        val all = json.optString("collection").trim() + "\n\u0000" + parts.joinToString("\n\u0000")
        return MessageDigest.getInstance("SHA-256").digest(all.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
