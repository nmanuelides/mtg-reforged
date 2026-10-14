package io.github.nmanuelides.mtgreforged.match

import forge.game.card.CardView
import forge.game.phase.PhaseType

/** Immutable snapshot of a card, safe to hand to Compose. */
data class CardUi(
    val id: Int,
    val name: String,
    val imageKey: String?,
    val manaCost: String,
    val typeLine: String,
    val text: String,
    val power: Int?,
    val toughness: Int?,
    val loyalty: String?,
    val isCreature: Boolean,
    val isLand: Boolean,
    val tapped: Boolean,
    val sick: Boolean,
    val faceDown: Boolean,
    val token: Boolean,
    val damage: Int,
    val counters: List<Pair<String, Int>>,
    val attachedToId: Int?,
    val controllerId: Int?,
) {
    companion object {
        fun from(cv: CardView, visible: Boolean = true): CardUi {
            val state = cv.currentState
            val hidden = !visible || cv.isFaceDown && state == null
            val type = state?.type
            return CardUi(
                id = cv.id,
                name = if (hidden) "?" else state?.name ?: cv.name,
                imageKey = when {
                    !visible -> null
                    cv.isFaceDown -> cv.facedownImageKey
                    else -> state?.imageKey
                },
                manaCost = if (hidden) "" else state?.manaCost?.toString().orEmpty(),
                typeLine = if (hidden) "" else type?.toString().orEmpty(),
                text = if (hidden) "" else runCatching { cv.getText(state, null) }.getOrNull().orEmpty().stripHtml(),
                power = if (type?.isCreature == true) state.power else null,
                toughness = if (type?.isCreature == true) state.toughness else null,
                loyalty = state?.loyalty?.takeIf { type?.isPlaneswalker == true },
                isCreature = type?.isCreature == true,
                isLand = type?.isLand == true,
                tapped = cv.isTapped,
                sick = cv.isSick,
                faceDown = cv.isFaceDown,
                token = cv.isToken,
                damage = cv.damage,
                counters = cv.counters?.entrySet()?.map { it.element.toString() to it.count }.orEmpty(),
                attachedToId = cv.attachedTo?.id,
                controllerId = cv.controller?.id,
            )
        }
    }
}

// Forge marks up reminder text with <span> tags meant for its desktop renderer.
private val htmlTag = Regex("<[^>]+>")
private fun String.stripHtml() = replace("<br>", "\n").replace(htmlTag, "").replace("&nbsp;", " ").trim()

data class PlayerUi(
    val id: Int,
    val name: String,
    val isAi: Boolean,
    val isLocal: Boolean,
    val life: Int,
    val poison: Int,
    val handSize: Int,
    val librarySize: Int,
    val hand: List<CardUi>,
    val battlefield: List<CardUi>,
    val graveyard: List<CardUi>,
    val exile: List<CardUi>,
    val command: List<CardUi>,
    /** Combat damage this player has taken from each commander, by commander name. */
    val commanderDamage: List<Pair<String, Int>>,
    val mana: Map<Byte, Int>,
    val isActive: Boolean,
    val hasLost: Boolean,
)

data class StackItemUi(val id: Int, val text: String, val source: CardUi?, val controllerName: String)

data class CombatUi(val attackers: Map<Int, Int?>, val blockers: Map<Int, List<Int>>)

data class GameOverUi(val title: String, val youWon: Boolean, val matchOver: Boolean)

data class PromptBarUi(
    val message: String = "",
    val card: CardUi? = null,
    val okLabel: String = "OK",
    val cancelLabel: String = "Cancel",
    val okEnabled: Boolean = false,
    val cancelEnabled: Boolean = false,
    /** OK would pass priority (skip ahead through phases); the UI asks for a swipe instead of a tap. */
    val passesPriority: Boolean = false,
)

data class MatchUiState(
    /** An earlier point of the game can be restored right now (see ComposeGuiGame.undo). */
    val canUndo: Boolean = false,
    /** Undo was asked while the AI was playing; it happens when the game hands control back. */
    val undoPending: Boolean = false,
    val players: List<PlayerUi> = emptyList(),
    val turn: Int = 0,
    val phase: PhaseType? = null,
    val activePlayerId: Int? = null,
    val stack: List<StackItemUi> = emptyList(),
    val combat: CombatUi? = null,
    val prompt: PromptBarUi = PromptBarUi(),
    val selectableIds: Set<Int> = emptySet(),
    val highlightedIds: Set<Int> = emptySet(),
    /** Players the engine is currently asking us to pick from (start player, targets...). */
    val choosablePlayerIds: Set<Int> = emptySet(),
    /** While declaring attackers: the player (or planeswalker) new attackers will target. */
    val attackTargetId: Int? = null,
    val log: List<String> = emptyList(),
    val gameOver: GameOverUi? = null,
) {
    val me: PlayerUi? get() = players.firstOrNull { it.isLocal }
    val opponents: List<PlayerUi> get() = players.filter { !it.isLocal }
}
