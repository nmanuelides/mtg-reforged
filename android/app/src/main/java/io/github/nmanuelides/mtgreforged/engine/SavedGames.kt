package io.github.nmanuelides.mtgreforged.engine

import forge.game.Game
import forge.game.GameState
import forge.game.phase.PhaseType
import forge.localinstance.properties.ForgeConstants
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A match paused by the player: who sat where with which deck, and Forge's GameState. */
data class SavedGame(
    val format: Engine.Format,
    val seats: List<Seat>,
    val state: String,
    val turn: Int,
    val savedAt: Long,
) {
    data class Seat(val name: String, val human: Boolean, val deckName: String, val deckPath: String)

    val summary: String get() = "${format.label} · Turn $turn · ${seats.size} players"
}

/** Keeps a single saved match on disk, next to Forge's own user data. */
object SavedGames {
    private val file get() = File(ForgeConstants.USER_DIR, "reforged-save.json")

    fun load(): SavedGame? = runCatching {
        val json = JSONObject(file.readText())
        val seats = json.getJSONArray("seats")
        SavedGame(
            format = Engine.Format.valueOf(json.getString("format")),
            seats = (0 until seats.length()).map { i ->
                seats.getJSONObject(i).run {
                    SavedGame.Seat(getString("name"), getBoolean("human"), getString("deckName"), getString("deckPath"))
                }
            },
            state = json.getString("state"),
            turn = json.getInt("turn"),
            savedAt = json.getLong("savedAt"),
        )
    }.getOrNull()

    fun delete() {
        file.delete()
    }

    /**
     * Captures [game]. Forge's GameState can't hold spells mid-resolution, so callers only
     * save with an empty stack; multiplayer combat can't be restored either, so a save
     * taken mid-combat resumes in the second main phase.
     */
    fun save(game: Game, format: Engine.Format, decks: List<Engine.DeckEntry>) {
        val state = GameState().apply { initFromGame(game) }.toString()
        val restorable = if (game.players.size > 2 && game.phaseHandler.inCombat()) {
            state.lines().joinToString("\n") { line ->
                if (line.startsWith("activephase=", ignoreCase = true)) "activephase=${PhaseType.MAIN2.name}" else line
            }
        } else state

        val seats = JSONArray()
        game.players.forEachIndexed { i, p ->
            val deck = decks.getOrNull(i) ?: return
            seats.put(
                JSONObject()
                    .put("name", p.name)
                    .put("human", !p.controller.isAI)
                    .put("deckName", deck.name)
                    .put("deckPath", deck.file.path),
            )
        }
        file.parentFile?.mkdirs()
        file.writeText(
            JSONObject()
                .put("format", format.name)
                .put("seats", seats)
                .put("state", restorable)
                .put("turn", game.phaseHandler.turn)
                .put("savedAt", System.currentTimeMillis())
                .toString(),
        )
    }
}
