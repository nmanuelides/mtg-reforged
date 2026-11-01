package io.github.nmanuelides.mtgreforged.engine

import forge.localinstance.properties.ForgeConstants
import io.github.nmanuelides.mtgreforged.platform.AppContext
import io.github.nmanuelides.mtgreforged.platform.Log
import io.github.nmanuelides.mtgreforged.platform.Prefs
import io.github.nmanuelides.mtgreforged.platform.prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Keeps the collection and the player's decks the same on every device signed in to the same
 * Google account, through one sync file in the app's private Drive folder. Changes here go up
 * a few seconds after they're made; the home screen pulls whatever other devices did.
 *
 * Both sides are merged against the last state this device synced (the base), so changes made
 * on two devices before they synced are both kept: card counts add up, a deck made or deleted
 * on one side is made or deleted on the other, and a deck edited on both keeps the newer edit.
 */
object CloudSync {
    private const val TAG = "CloudSync"
    private const val KEY_LAST = "lastSync"
    private const val TICK_MS = 3_000L

    data class Status(val connected: Boolean = false, val syncing: Boolean = false, val lastSync: Long = 0L, val error: String? = null)

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private lateinit var prefs: Prefs
    @Volatile private var token: (suspend () -> String?)? = null
    private var watching = false
    @Volatile private var syncedSignature: String? = null

    private val baseFile get() = File(ForgeConstants.USER_DIR, "sync-base.json")

    /** [quietToken] gives a Drive token without asking the player (null when they never connected Drive). */
    fun start(context: AppContext, quietToken: suspend () -> String?) {
        if (!::prefs.isInitialized) {
            prefs = context.prefs("cloud-sync")
            _status.value = _status.value.copy(lastSync = prefs.getLong(KEY_LAST, 0L))
        }
        token = quietToken
        if (watching) return
        watching = true
        // Pushes local changes once they've settled (unchanged for one tick).
        scope.launch {
            var previous: String? = null
            while (true) {
                delay(TICK_MS)
                val now = runCatching { signature() }.getOrNull() ?: continue
                if (now != syncedSignature && now == previous && _status.value.connected) sync()
                previous = now
            }
        }
    }

    /** Syncs in the background (the home screen calls this every time it shows). */
    fun syncSoon(withToken: String? = null) {
        scope.launch { sync(withToken) }
    }

    /** Pulls and pushes now. Returns an error to show, or null. */
    suspend fun sync(withToken: String? = null): String? = lock.withLock {
        val t = withToken ?: runCatching { token?.invoke() }.getOrNull()
        if (t == null) {
            _status.value = _status.value.copy(connected = false)
            return@withLock "Not connected to Google Drive."
        }
        _status.value = _status.value.copy(connected = true, syncing = true, error = null)
        val error = runCatching { syncWith(t) }.exceptionOrNull()?.let {
            Log.w(TAG, "sync failed", it)
            it.message ?: "Couldn't sync."
        }
        val last = if (error == null) System.currentTimeMillis().also { prefs.edit().putLong(KEY_LAST, it).apply() } else _status.value.lastSync
        _status.value = _status.value.copy(syncing = false, lastSync = last, error = error)
        error
    }

    private suspend fun syncWith(token: String) {
        // A retry or two if another device uploads between our download and upload.
        repeat(3) {
            val local = Snapshot.parse(Backup.export())
            val remoteFile = DriveBackup.findSyncFile(token)
            if (remoteFile == null) {
                DriveBackup.createSyncFile(token, local.toJson())
                saveBase(local)
                return
            }
            val remote = Snapshot.parse(DriveBackup.downloadFile(token, remoteFile.id))
            val base = baseFile.takeIf { it.isFile }?.let { runCatching { Snapshot.parse(it.readText()) }.getOrNull() }
            val merged = Snapshot.merge(base, local, remote)
            if (!merged.sameAs(local)) applyLocally(merged, local)
            if (!merged.sameAs(remote)) {
                if (DriveBackup.fileVersion(token, remoteFile.id) != remoteFile.version) return@repeat
                DriveBackup.updateFile(token, remoteFile.id, merged.toJson())
            }
            saveBase(merged)
            return
        }
        error("Another device kept changing the sync; try again.")
    }

    private fun saveBase(snapshot: Snapshot) {
        baseFile.apply { parentFile?.mkdirs() }.writeText(snapshot.toJson())
        syncedSignature = signature()
    }

    /** Writes only what differs: the collection if it changed, decks added, changed or removed. */
    private fun applyLocally(merged: Snapshot, local: Snapshot) {
        if (merged.cards != local.cards) {
            CardCollection.backupFile().apply { parentFile?.mkdirs() }.writeText(merged.collectionText())
            CardCollection.reload()
        }
        val touched = HashSet<Engine.Format>()
        merged.decks.forEach { (key, deck) ->
            if (local.decks[key]?.content == deck.content) return@forEach
            val format = runCatching { Engine.Format.valueOf(deck.format) }.getOrNull() ?: return@forEach
            // Only a bare file name: never write outside the deck folders.
            val name = File(deck.file).name.takeIf { it.endsWith(".dck") } ?: return@forEach
            File(UserDecks.dir(format), name).apply {
                writeText(deck.content)
                if (deck.modified > 0) setLastModified(deck.modified)
            }
            touched += format
        }
        local.decks.keys.filter { it !in merged.decks }.forEach { key ->
            val deck = local.decks.getValue(key)
            val format = runCatching { Engine.Format.valueOf(deck.format) }.getOrNull() ?: return@forEach
            File(UserDecks.dir(format), File(deck.file).name).delete()
            touched += format
        }
        touched.forEach(Engine::invalidateDecks)
    }

    /** Cheap stand-in for "did anything change": sizes and times of the collection and deck files. */
    private fun signature(): String = buildString {
        CardCollection.backupFile().let { append(it.length()).append('@').append(it.lastModified()).append(';') }
        Engine.Format.entries.forEach { format ->
            UserDecks.dir(format).listFiles { f -> f.extension == "dck" }.orEmpty().sortedBy { it.name }.forEach {
                append(format.name).append('/').append(it.name).append(it.length()).append('@').append(it.lastModified()).append(';')
            }
        }
    }

    /** The collection and decks of a backup document, in a form that can be merged. */
    private data class Snapshot(val header: List<String>, val cards: LinkedHashMap<String, Int>, val decks: Map<String, Deck>) {
        data class Deck(val format: String, val file: String, val content: String, val modified: Long) {
            // Two decks are the same deck when their text is; the time only breaks ties.
            override fun equals(other: Any?) = other is Deck && format == other.format && file == other.file && content.trim() == other.content.trim()
            override fun hashCode() = content.trim().hashCode()
        }

        fun sameAs(other: Snapshot) = cards == other.cards && decks == other.decks

        fun collectionText(): String = buildString {
            header.forEach { append(it).append('\n') }
            cards.forEach { (card, n) -> append(n).append(' ').append(card).append('\n') }
        }

        fun toJson(): String {
            val decks = JSONArray()
            this.decks.values.forEach { d -> decks.put(JSONObject().put("format", d.format).put("file", d.file).put("content", d.content).put("modified", d.modified)) }
            return JSONObject()
                .put("app", "MTG Reforged").put("version", 1).put("createdAt", System.currentTimeMillis())
                .put("collection", collectionText()).put("decks", decks).toString(2)
        }

        companion object {
            private val cardLine = Regex("""^(\d+)\s+(.+)$""")
            private val defaultHeader = listOf("[metadata]", "Name=Collection", "[Main]")

            fun parse(text: String): Snapshot {
                val json = JSONObject(text)
                val header = ArrayList<String>()
                val cards = LinkedHashMap<String, Int>()
                json.optString("collection").lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                    val m = cardLine.matchEntire(line)
                    if (m == null) header += line
                    else m.groupValues[2].trim().let { cards[it] = (cards[it] ?: 0) + m.groupValues[1].toInt() }
                }
                val decks = LinkedHashMap<String, Deck>()
                val list = json.optJSONArray("decks") ?: JSONArray()
                for (i in 0 until list.length()) {
                    val d = list.getJSONObject(i)
                    val deck = Deck(d.optString("format"), d.optString("file"), d.optString("content"), d.optLong("modified"))
                    decks[deck.format + "/" + deck.file] = deck
                }
                return Snapshot(header, cards, decks)
            }

            fun merge(base: Snapshot?, local: Snapshot, remote: Snapshot): Snapshot {
                val cards = LinkedHashMap<String, Int>()
                (local.cards.keys + remote.cards.keys + base?.cards?.keys.orEmpty()).forEach { key ->
                    val l = local.cards[key] ?: 0
                    val r = remote.cards[key] ?: 0
                    // Without a base (this device's first sync) a card in both is the same copy, not two.
                    val n = if (base == null) maxOf(l, r) else l + r - (base.cards[key] ?: 0)
                    if (n > 0) cards[key] = n
                }
                val decks = LinkedHashMap<String, Deck>()
                (local.decks.keys + remote.decks.keys).forEach { key ->
                    val b = base?.decks?.get(key)
                    val l = local.decks[key]
                    val r = remote.decks[key]
                    val pick = when {
                        l == r -> l ?: r
                        base != null && l == b -> r // only the other device changed (or deleted) it
                        base != null && r == b -> l // only this one did
                        l == null -> r // edited on one side, deleted on the other: keep the edit
                        r == null -> l
                        else -> if (r.modified > l.modified) r else l
                    }
                    if (pick != null) decks[key] = pick
                }
                val header = local.header.ifEmpty { remote.header }.ifEmpty { defaultHeader }
                return Snapshot(header, cards, decks)
            }
        }
    }
}
