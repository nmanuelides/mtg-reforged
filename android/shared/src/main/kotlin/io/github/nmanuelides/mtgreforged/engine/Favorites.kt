package io.github.nmanuelides.mtgreforged.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.github.nmanuelides.mtgreforged.platform.prefs
import io.github.nmanuelides.mtgreforged.platform.AppContext
import io.github.nmanuelides.mtgreforged.platform.Prefs

/** The player's favourite deck per format: their default pick whenever none is chosen. */
object Favorites {
    private lateinit var prefs: Prefs
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun init(context: AppContext) {
        prefs = context.prefs("favorites")
    }

    private fun key(format: Engine.Format) = "deck_${format.name}"

    fun isFavorite(format: Engine.Format, deck: Engine.DeckEntry) =
        ::prefs.isInitialized && prefs.getString(key(format), null) == deck.file.path

    fun toggle(format: Engine.Format, deck: Engine.DeckEntry) {
        val edit = prefs.edit()
        if (isFavorite(format, deck)) edit.remove(key(format)) else edit.putString(key(format), deck.file.path)
        edit.apply()
        _version.value++
    }

    fun of(format: Engine.Format, decks: List<Engine.DeckEntry>): Engine.DeckEntry? {
        if (!::prefs.isInitialized) return null
        val path = prefs.getString(key(format), null) ?: return null
        return decks.firstOrNull { it.file.path == path }
    }
}
