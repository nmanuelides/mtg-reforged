package io.github.nmanuelides.mtgreforged.engine

import forge.deck.Deck
import forge.deck.io.DeckSerializer
import forge.localinstance.properties.ForgeConstants
import java.io.File

/** The player's own decks, saved as Forge .dck files in the user's deck folders. */
object UserDecks {

    fun dir(format: Engine.Format) = File(
        when (format) {
            Engine.Format.Constructed -> ForgeConstants.DECK_CONSTRUCTED_DIR
            Engine.Format.Commander -> ForgeConstants.DECK_COMMANDER_DIR
        },
    ).apply { mkdirs() }

    fun list(format: Engine.Format): List<Engine.DeckEntry> =
        dir(format).listFiles { f -> f.extension == "dck" }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { Engine.DeckEntry(it.nameWithoutExtension, it, custom = true) }

    /** Saves [deck] under its name, replacing [previous] if the deck was renamed. */
    fun save(deck: Deck, format: Engine.Format, previous: Engine.DeckEntry? = null): Engine.DeckEntry {
        val safe = deck.name.replace(Regex("""[\/:*?"<>|]"""), "_").trim().ifBlank { "Untitled deck" }
        val file = File(dir(format), "$safe.dck")
        DeckSerializer.writeDeck(deck, file)
        if (previous != null && previous.custom && previous.file != file) previous.file.delete()
        Engine.invalidateDecks(format)
        return Engine.DeckEntry(safe, file, custom = true)
    }

    fun delete(entry: Engine.DeckEntry, format: Engine.Format) {
        if (entry.custom) entry.file.delete()
        Engine.invalidateDecks(format)
    }
}
