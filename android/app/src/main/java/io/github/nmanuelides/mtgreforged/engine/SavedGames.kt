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
    /** The same state with the player's spells on top of the stack back in hand: undo after resuming. */
    val undoState: String? = null,
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
        ).let { it.copy(undoState = json.optString("undoState").takeIf { u -> u.isNotBlank() } ?: unstackedFromSave(it)) }
    }.getOrNull()

    fun delete() {
        file.delete()
    }

    /**
     * GameState doesn't record the stack. Spells on it are written back as Forge's
     * "putonstack" lines (bottom first, each with its first card or player target); a spell
     * whose target that syntax can't express goes back to its owner's hand instead, so no
     * card is ever lost. Triggered and activated abilities can't be saved and are dropped.
     */
    private fun withStack(state: String, game: Game, undone: Set<forge.game.spellability.SpellAbilityStackInstance> = emptySet()): String {
        val items = game.stack.toList().reversed()
        if (items.isEmpty()) return state
        val players = game.players
        val onStack = HashMap<Int, MutableList<String>>()
        val toHand = HashMap<Int, MutableList<String>>()
        for (item in items) {
            val sa = item.spellAbility ?: continue
            if (!sa.isSpell) continue
            val card = sa.hostCard ?: continue
            val seat = players.indexOf(sa.activatingPlayer).takeIf { it >= 0 } ?: continue
            val name = card.name.replace(":", "^")
            val target = sa.targets?.firstOrNull()
            val targetText = when {
                target == null -> ""
                target is forge.game.card.Card -> "->${target.id}"
                target is forge.game.player.Player && players.indexOf(target) == 0 -> "->human"
                target is forge.game.player.Player && players.indexOf(target) == 1 -> "->ai"
                else -> null
            }
            if (targetText == null || item in undone) toHand.getOrPut(players.indexOf(card.owner).coerceAtLeast(0)) { ArrayList() } += name
            else onStack.getOrPut(seat) { ArrayList() } += name + targetText
        }
        val lines = state.lines().toMutableList()
        toHand.forEach { (seat, names) ->
            val key = "p${seat}hand="
            val i = lines.indexOfFirst { it.startsWith(key, ignoreCase = true) }
            val extra = names.joinToString(";")
            if (i >= 0) lines[i] = lines[i] + (if (lines[i].length > key.length) ";" else "") + extra else lines += key + extra
        }
        onStack.forEach { (seat, spells) -> lines += "p${seat}putonstack=" + spells.joinToString(";") }
        return lines.joinToString("\n")
    }

    /**
     * Captures [game]. Forge's GameState can't hold spells mid-resolution, so callers only
     * save with an empty stack; multiplayer combat can't be restored either, so a save
     * taken mid-combat resumes in the second main phase.
     */
    /**
     * The state from before the human player's spells on top of the stack were cast: those
     * spells back in hand and the lands that paid for them untapped. Null with none there.
     */
    private fun unstacked(base: String, game: Game): String? {
        val top = game.stack.toList().takeWhile { it.isSpell && it.spellAbility?.activatingPlayer?.controller?.isAI == false }
        if (top.isEmpty()) return null
        val paidBy = top.flatMap { si -> si.spellAbility?.payingMana.orEmpty().mapNotNull { it.sourceCard?.id } }.toSet()
        val state = withStack(base, game, top.toSet())
        if (paidBy.isEmpty()) return state
        return state.lines().joinToString("\n") { line ->
            if (!line.contains("battlefield=", ignoreCase = true)) line
            else line.split(";").joinToString(";") { token ->
                val id = Regex("\\|Id:(\\d+)").find(token)?.groupValues?.get(1)?.toIntOrNull()
                if (id in paidBy) token.replace("|Tapped", "") else token
            }
        }
    }

    /** Saves from before undoState: the human seats' spells on the stack go back to hand (lands stay tapped). */
    private fun unstackedFromSave(game: SavedGame): String? {
        val humans = game.seats.indices.filter { game.seats[it].human }.map { "p${it}putonstack=" }
        val lines = game.state.lines()
        val moved = HashMap<Int, List<String>>()
        val kept = lines.filter { line ->
            val seat = humans.indexOfFirst { line.startsWith(it, ignoreCase = true) }
            if (seat < 0) return@filter true
            val i = game.seats.indices.filter { game.seats[it].human }[seat]
            moved[i] = line.substringAfter("=").split(";").map { it.substringBefore("->") }.filter { it.isNotBlank() }
            false
        }
        if (moved.isEmpty()) return null
        val out = kept.toMutableList()
        moved.forEach { (seat, names) ->
            val key = "p${seat}hand="
            val i = out.indexOfFirst { it.startsWith(key, ignoreCase = true) }
            val extra = names.joinToString(";")
            if (i >= 0) out[i] = out[i] + (if (out[i].length > key.length) ";" else "") + extra else out += key + extra
        }
        return out.joinToString("\n")
    }

    fun save(game: Game, format: Engine.Format, decks: List<Engine.DeckEntry>) {
        val base = GameState().apply { initFromGame(game) }.toString()
        val state = withStack(base, game)
        val undoState = unstacked(base, game)
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
                .apply { if (undoState != null) put("undoState", undoState) }
                .toString(),
        )
    }
}
