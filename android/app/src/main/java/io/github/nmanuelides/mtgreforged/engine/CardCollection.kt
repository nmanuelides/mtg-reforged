package io.github.nmanuelides.mtgreforged.engine

import forge.deck.Deck
import forge.deck.DeckSection
import forge.deck.io.DeckSerializer
import forge.item.PaperCard
import forge.localinstance.properties.ForgeConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * The player's card collection: every printing they own and how many. Stored as a Forge
 * .dck file (main section only) next to the user's decks, so Forge's own reader/writer
 * handle printings and it is easy to back up.
 */
object CardCollection {
    private val file get() = File(ForgeConstants.USER_DIR, "collection.dck")

    @Volatile private var owned: Map<PaperCard, Int>? = null
    @Volatile private var ownedNames: Set<String> = emptySet()

    private val _version = MutableStateFlow(0)
    /** Bumps whenever the collection changes. */
    val version: StateFlow<Int> = _version.asStateFlow()

    /** Every owned printing and its count (loads from disk the first time). */
    @Synchronized
    fun cards(): Map<PaperCard, Int> {
        owned?.let { return it }
        val loaded = runCatching {
            if (!file.isFile) emptyMap()
            else DeckSerializer.fromFile(file).get(DeckSection.Main)?.associate { it.key to it.value }.orEmpty()
        }.getOrDefault(emptyMap())
        set(loaded, write = false)
        return loaded
    }

    /** Names of every owned card, for "only my collection" searches and highlights. */
    fun names(): Set<String> {
        if (owned == null) cards()
        return ownedNames
    }

    fun owns(name: String) = name in names()

    fun count(name: String) = cards().entries.filter { it.key.name == name }.sumOf { it.value }

    @Synchronized
    fun save(cards: Map<PaperCard, Int>) = set(cards.filterValues { it > 0 }, write = true)

    private fun set(cards: Map<PaperCard, Int>, write: Boolean) {
        owned = cards
        ownedNames = cards.keys.map { it.name }.toSet()
        if (write) {
            val deck = Deck("Collection")
            cards.forEach { (c, n) -> deck.getOrCreate(DeckSection.Main).add(c, n) }
            file.parentFile?.mkdirs()
            DeckSerializer.writeDeck(deck, file)
        }
        _version.value++
    }

    /** The collection file, for backups. */
    fun backupFile(): File = file

    /** Forget the in-memory copy, e.g. after a restore replaced the file. */
    @Synchronized
    fun reload() {
        owned = null
        cards()
    }
}
