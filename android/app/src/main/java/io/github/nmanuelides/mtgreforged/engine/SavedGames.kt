package io.github.nmanuelides.mtgreforged.engine

import forge.game.Game
import forge.game.GameState
import forge.game.phase.PhaseType
import forge.game.zone.ZoneType
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
    /** Players chosen or remembered by permanents (see SavedGames.playerChoices). */
    val choices: String? = null,
    /** Positions at the player's last prompts before the save, oldest first: undo after resuming. */
    val history: List<Position> = emptyList(),
) {
    /** A game position as GameState text plus the players permanents chose (GameState skips those). */
    data class Position(val state: String, val choices: String?)

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
        ).let { it.copy(choices = json.optString("choices").takeIf { c -> c.isNotBlank() }, undoState = json.optString("undoState").takeIf { u -> u.isNotBlank() } ?: unstackedFromSave(it)) }
            .let { saved ->
                val list = json.optJSONArray("history")
                val history = (0 until (list?.length() ?: 0)).map { i ->
                    list!!.getJSONObject(i).run { SavedGame.Position(getString("state"), optString("choices").takeIf { c -> c.isNotBlank() }) }
                }
                // Saves without a history: the state before the player's spells on the stack, if any.
                saved.copy(history = history.ifEmpty { listOfNotNull(saved.undoState?.let { u -> SavedGame.Position(u, saved.choices) }) })
            }
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

    /**
     * GameState saves chosen colours, types and cards but not chosen players: those, and players a
     * permanent remembers, by seat. The permanent is found again by controller, name and its place
     * among the same-named ones (card ids change on resume).
     */
    private fun playerChoices(game: Game): String? {
        val players = game.players
        val out = JSONArray()
        players.forEachIndexed { seat, p ->
            val cards = p.getCardsIn(ZoneType.Battlefield).filter { !it.isToken }
            cards.forEach { c ->
                val chosen = c.chosenPlayer?.let { players.indexOf(it) }?.takeIf { it >= 0 }
                val remembered = c.remembered.filterIsInstance<forge.game.player.Player>().map { players.indexOf(it) }.filter { it >= 0 }
                if (chosen == null && remembered.isEmpty()) return@forEach
                out.put(
                    JSONObject().put("seat", seat).put("name", c.name)
                        .put("ordinal", cards.takeWhile { it !== c }.count { it.name == c.name })
                        .put("chosen", chosen ?: -1).put("remembered", JSONArray(remembered)),
                )
            }
        }
        return if (out.length() == 0) null else out.toString()
    }

    /** Puts [SavedGame.choices] back on the resumed game's permanents. */
    fun applyChoices(choices: String?, game: Game) {
        if (choices.isNullOrBlank()) return
        runCatching {
            val players = game.players
            val list = JSONArray(choices)
            for (i in 0 until list.length()) {
                val o = list.getJSONObject(i)
                val p = players.getOrNull(o.getInt("seat")) ?: continue
                val name = o.getString("name")
                val card = p.getCardsIn(ZoneType.Battlefield).filter { !it.isToken && it.name == name }
                    .getOrNull(o.getInt("ordinal")) ?: continue
                players.getOrNull(o.getInt("chosen"))?.let { card.chosenPlayer = it }
                val remembered = o.getJSONArray("remembered")
                for (j in 0 until remembered.length()) players.getOrNull(remembered.getInt(j))?.let { card.addRemembered(it) }
            }
        }
    }

    /** Multiplayer combat can't be restored: such a position resumes in the second main phase. */
    private fun restorable(state: String, game: Game) = if (game.players.size > 2 && game.phaseHandler.inCombat()) {
        state.lines().joinToString("\n") { line ->
            if (line.startsWith("activephase=", ignoreCase = true)) "activephase=${PhaseType.MAIN2.name}" else line
        }
    } else state

    /** The game as it is now, as a position a resumed game can go back to. Game thread, game waiting. */
    fun position(game: Game): SavedGame.Position {
        val base = GameState().apply { initFromGame(game) }.toString()
        return SavedGame.Position(restorable(withStack(base, game), game), playerChoices(game))
    }

    fun save(game: Game, format: Engine.Format, decks: List<Engine.DeckEntry>, history: List<SavedGame.Position> = emptyList()) {
        val base = GameState().apply { initFromGame(game) }.toString()
        val choices = playerChoices(game)
        val undoState = unstacked(base, game)
        val restorable = restorable(withStack(base, game), game)

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
                .apply { if (choices != null) put("choices", choices) }
                .put("history", JSONArray().apply {
                    history.forEach { put(JSONObject().put("state", it.state).apply { if (it.choices != null) put("choices", it.choices) }) }
                })
                .toString(),
        )
    }
}
