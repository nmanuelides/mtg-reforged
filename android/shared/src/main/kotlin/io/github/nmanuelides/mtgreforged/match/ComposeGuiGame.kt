package io.github.nmanuelides.mtgreforged.match

import forge.gamemodes.match.input.InputBlock
import forge.game.GameLogEntryType
import forge.game.GameState
import io.github.nmanuelides.mtgreforged.engine.SavedGame
import io.github.nmanuelides.mtgreforged.engine.SavedGames
import forge.localinstance.properties.ForgeConstants
import forge.localinstance.properties.ForgePreferences.FPref
import forge.model.FModel
import forge.game.event.GameEventPlayerStatsChanged
import forge.game.event.GameEventCardStatsChanged
import forge.game.event.GameEventSnapshotRestored
import forge.game.undo.GameMemento
import forge.LobbyPlayer
import forge.card.MagicColor
import forge.deck.CardPool
import forge.game.GameEntityView
import forge.game.GameView
import forge.game.card.CardView
import forge.game.event.GameEvent
import forge.game.phase.PhaseType
import forge.game.player.DelayedReveal
import forge.game.player.IHasIcon
import forge.game.player.PlayerView
import forge.game.spellability.SpellAbilityView
import forge.game.zone.ZoneType
import forge.gamemodes.match.AbstractGuiGame
import forge.gamemodes.net.NetworkGuiGame
import forge.gamemodes.match.HostedMatch
import forge.gamemodes.match.input.InputAttack
import forge.gamemodes.match.input.InputConfirm
import forge.gamemodes.match.input.InputSelectEntitiesFromList
import forge.gamemodes.match.input.InputPassPriority
import forge.game.GameEntity
import forge.game.card.Card
import forge.gamemodes.match.input.InputSelectTargets
import forge.game.player.Player
import forge.game.spellability.SpellAbility
import forge.player.PlayerControllerHuman
import forge.item.PaperCard
import forge.localinstance.skin.FSkinProp
import forge.player.PlayerZoneUpdate
import forge.trackable.TrackableCollection
import forge.util.FSerializableFunction
import forge.util.ITriggerEvent
import forge.util.ThreadUtil
import io.github.nmanuelides.mtgreforged.engine.Prompt
import io.github.nmanuelides.mtgreforged.engine.PromptHost
import io.github.nmanuelides.mtgreforged.engine.PromptItem
import io.github.nmanuelides.mtgreforged.engine.describe
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.github.nmanuelides.mtgreforged.platform.Log
import io.github.nmanuelides.mtgreforged.platform.MainThread

/**
 * The per-match bridge between Forge and Compose. Forge pushes updates here from the
 * game thread; we coalesce them and rebuild an immutable [MatchUiState] on the main
 * thread. Player actions are forwarded to the game controller on a game thread, the
 * same way the libGDX frontend does it.
 */
private const val MAX_UNDO = 20

class ComposeGuiGame : NetworkGuiGame() {

    private val _state = MutableStateFlow(MatchUiState())
    val state: StateFlow<MatchUiState> = _state.asStateFlow()

    private val _feedback = MutableSharedFlow<Feedback>(extraBufferCapacity = 8)
    val feedback: SharedFlow<Feedback> = _feedback

    enum class Feedback { IncorrectAction, Alert }

    var hostedMatch: HostedMatch? = null

    /**
     * When resuming a saved game the pre-game questions (who starts, mulligans) are
     * answered automatically; the saved state replaces their outcome right after.
     */
    @Volatile var autoStart = false

    /** Phones: a priority stop where the player can't play anything passes by itself. */
    @Volatile var passWhenStuck = false

    @Volatile private var prompt = PromptBarUi()
    @Volatile private var gameOver: GameOverUi? = null
    @Volatile private var refreshQueued = false
    private var cardsById: Map<Int, CardView> = emptyMap()
    private var playersById: Map<Int, PlayerView> = emptyMap()

    // ---- State publishing --------------------------------------------------------

    private fun requestRefresh() {
        if (refreshQueued) return
        refreshQueued = true
        // While others (the AI) are thinking, redraw a few times a second instead of every
        // frame: each redraw walks the whole board on the main thread, and on big late-game
        // boards that CPU is better spent on the AI.
        val waitingForMe = runCatching { currentInput() != null }.getOrDefault(true)
        MainThread.postDelayed(if (waitingForMe) 16L else 150L) {
            refreshQueued = false
            rebuild()
        }
    }

    private fun rebuild() {
        val view = gameView ?: return
        // Game objects are mutated by the game thread; a torn read just means we try again.
        val snapshot = runCatching { buildSnapshot(view) }.getOrElse {
            requestRefresh()
            return
        }
        _state.value = snapshot
        // An undo asked for while the opponent played runs as soon as the game waits for us
        // with an empty stack; checked on every refresh, whichever callback brought us here.
        watchPrompt()
    }

    private fun buildSnapshot(view: GameView): MatchUiState {
        val cards = HashMap<Int, CardView>()
        val players = HashMap<Int, PlayerView>()
        // Goad isn't in the card views; read it from the game (the host's; network guests don't have it).
        val goadedIds = runCatching {
            gameView?.game?.getCardsIn(ZoneType.Battlefield)?.filter { it.isGoaded }?.mapTo(HashSet()) { it.id }
        }.getOrNull().orEmpty()
        fun card(cv: CardView): CardUi {
            cards[cv.id] = cv
            val ui = CardUi.from(cv, mayView(cv))
            return if (cv.id in goadedIds) ui.copy(goaded = true) else ui
        }
        fun zone(p: PlayerView, z: ZoneType) = p.getCards(z)?.map(::card).orEmpty()

        val allCommanders = view.players.flatMap { it.commanders.orEmpty() }
        val playerUis = view.players.mapIndexed { seat, p ->
            players[p.id] = p
            PlayerUi(
                id = p.id,
                seat = seat,
                name = p.name,
                isAi = p.isAI,
                isLocal = isLocalPlayer(p),
                life = p.life,
                poison = p.getCounters(forge.game.card.CounterEnumType.POISON),
                handSize = p.getZoneSize(ZoneType.Hand),
                librarySize = p.getZoneSize(ZoneType.Library),
                hand = zone(p, ZoneType.Hand),
                battlefield = zone(p, ZoneType.Battlefield),
                graveyard = zone(p, ZoneType.Graveyard),
                exile = zone(p, ZoneType.Exile),
                command = zone(p, ZoneType.Command),
                commanderDamage = allCommanders.mapNotNull { c ->
                    p.getCommanderDamage(c).takeIf { it > 0 }?.let { c.name to it }
                },
                mana = MagicColor.WUBRGC.associateWith { p.getMana(it) }.filterValues { it > 0 },
                isActive = view.playerTurn == p,
                hasLost = p.hasLost,
            )
        }.let(::seatOrder)

        val stack = view.stack?.map { item ->
            StackItemUi(
                id = item.id,
                text = item.text,
                source = item.sourceCard?.let(::card),
                controllerName = item.activatingPlayer?.name.orEmpty(),
                targets = item.targetCards.orEmpty().map { "c${it.id}" } + item.targetPlayers.orEmpty().map { "p${it.id}" },
            )
        }.orEmpty()

        val combat = view.combat?.let { c ->
            CombatUi(
                attackers = c.attackers.associate { a -> a.id to c.getDefender(a)?.id },
                blockers = c.attackers.associate { a -> a.id to c.getBlockers(a).orEmpty().map { it.id } },
                // Player and card ids can share numbers: keep the kind with the id.
                defenderKeys = c.attackers.mapNotNull { a ->
                    when (val d = c.getDefender(a)) {
                        is PlayerView -> a.id to "p${d.id}"
                        null -> null
                        else -> a.id to "c${d.id}"
                    }
                }.toMap(),
            )
        }

        val selectable = cards.values.filter { isSelectable(it) }.mapTo(HashSet()) { it.id }
        val boardChoice = boardChoice(view, selectable, ::card)
        val highlighted = (cards.values + players.values).filter { isHighlighted(it) }.mapTo(HashSet()) { it.id }
        val zoneChoice = if (boardChoice != null) null else zoneChoice(selectable, highlighted, cards, ::card)

        val entries = view.gameLog?.getLogEntries(null).orEmpty()
        val log = entries.take(40).map { it.message() }
        val logEntries = runCatching { logEntries(view, entries, cards) }.getOrDefault(emptyList())
        val quiet = setOf(GameLogEntryType.PHASE, GameLogEntryType.TURN, GameLogEntryType.MANA, GameLogEntryType.INFORMATION)
        val lastActions = HashMap<String, String>()
        val lastPlay = entries.firstOrNull { it.type() !in quiet }?.message()
        for (entry in entries.take(200)) {
            if (entry.type() in quiet) continue
            val text = entry.message()
            view.players?.forEach { p -> if (p.name !in lastActions && p.name in text) lastActions[p.name] = text }
        }

        cardsById = cards
        playersById = players
        val choosable = runCatching { choosablePlayerIds(view) }.getOrDefault(emptySet())
        // While declaring attackers, which player new attackers go after.
        val attackTarget = (currentInput() as? InputAttack)
            ?.let { runCatching { fieldValue(it, "currentDefender") as? GameEntity }.getOrNull() }?.id
        return MatchUiState(
            players = playerUis,
            turn = view.turn,
            phase = view.phase,
            activePlayerId = view.playerTurn?.id,
            stack = stack,
            combat = combat,
            prompt = blockPrompt(prompt).copy(passesPriority = currentInput() is InputPassPriority),
            blockFocusId = blockFocus.takeIf { currentInput() is InputBlock },
            canUndo = canUndo(),
            undoError = undoError ?: OLD_SAVE_NOTE.takeIf { resumedWithoutHistory && history.size <= 1 },
            boardChoice = boardChoice,
            lastActions = lastActions,
            lastPlay = lastPlay,
            undoPending = undoPending,
            selectableIds = selectable,
            zoneChoice = zoneChoice,
            highlightedIds = highlighted,
            choosablePlayerIds = choosable,
            attackTargetId = attackTarget,
            log = log,
            logEntries = logEntries,
            gameOver = gameOver,
        )
    }

    /**
     * Players the current input would accept. Forge only flags selectable *cards* to the
     * GUI, so read the waiting input: its list of valid choices, or the spell being
     * targeted. Lets the UI offer one button per player instead of "tap the portrait".
     */
    /** Plain instructions while declaring blockers, instead of Forge's terse line. */
    private fun blockPrompt(p: PromptBarUi): PromptBarUi {
        if (currentInput() !is InputBlock) { blockFocus = null; return p }
        val focused = blockFocus?.let { cardsById[it] }
        val text = when {
            focused == null -> "Blockers: tap one of your creatures, then the attacker it should block (or the other way round). Tap a blocker again to take it out. Press OK when done."
            combatAttacking(focused.id) -> "Now tap your creatures that will block ${focused.name}. Tap another attacker to switch."
            else -> "Now tap the attacker that ${focused.name} should block."
        }
        return p.copy(message = text)
    }

    private fun combatAttacking(id: Int) = gameView?.combat?.attackers?.any { it.id == id } == true

    private fun currentInput() = (gameController as? PlayerControllerHuman)?.inputProxy?.input

    private fun choosablePlayerIds(view: GameView): Set<Int> = runCatching { choosablePlayerIdsUnsafe(view) }.getOrDefault(emptySet())

    /**
     * Reads live game objects from the UI thread, so it may only run while the game thread
     * is parked waiting for this player's choice; during anyone else's thinking (AI turns)
     * it would race the engine and corrupt its state (ConcurrentModificationException).
     */
    private fun choosablePlayerIdsUnsafe(view: GameView): Set<Int> {
        val input = currentInput() ?: return emptySet()
        // Inputs that park the game thread waiting for this player (incl. choosing whom to attack).
        if (input !is InputSelectTargets && input !is InputSelectEntitiesFromList<*> && input !is InputAttack) return emptySet()
        val game = view.game ?: return emptySet()
        val ids = HashSet<Int>()
        var type: Class<*>? = input.javaClass
        while (type != null && type != Any::class.java) {
            for (field in type.declaredFields) {
                runCatching {
                    field.isAccessible = true
                    when (val value = field.get(input)) {
                        is Iterable<*> -> value.forEach { if (it is Player) ids += it.id }
                        is SpellAbility -> if (input is InputSelectTargets) {
                            game.players.filter { value.canTarget(it) }.forEach { ids += it.id }
                        }
                    }
                }
            }
            type = type.superclass
        }
        return ids
    }

    /** Local player first, then opponents in turn order after them. */
    private fun seatOrder(players: List<PlayerUi>): List<PlayerUi> {
        val me = players.indexOfFirst { it.isLocal }
        return if (me <= 0) players else players.drop(me) + players.take(me)
    }

    // ---- Player actions (called from the UI) -------------------------------------

    private fun onGameThread(action: () -> Unit) = ThreadUtil.invokeInGameThread(action)

    fun tapCard(id: Int) {
        val cv = cardsById[id] ?: return
        onGameThread {
            if (currentInput() is InputPassPriority) startedPlay = true // see undo()
            // On a network guest the controller is Forge's remote one: just send the tap.
            val controller = gameController as? PlayerControllerHuman
                ?: return@onGameThread run { gameController?.selectCard(cv, null, Tap) }
            if (currentInput() is InputBlock) { tapWhileBlocking(controller, cv); return@onGameThread }
            if (!undeclareElsewhereAttacker(controller, cv)) controller.selectCard(cv, null, Tap)
        }
    }

    // ---- Blocking ---------------------------------------------------------------------
    // Natural order, either way round: tap one of your creatures and then the attacker it
    // blocks, or an attacker and then any number of your creatures. Tapping a creature that
    // already blocks takes it out of combat. The current pick is highlighted.
    @Volatile private var blockFocus: Int? = null

    private fun tapWhileBlocking(controller: PlayerControllerHuman, cv: CardView) {
        val card = controller.getCard(cv) ?: return
        val combat = controller.game.combat ?: return
        val focus = blockFocus?.let { id -> controller.game.findById(id) }
        when {
            combat.isAttacking(card) -> {
                controller.selectCard(cv, null, Tap) // Forge's "current attacker"
                if (focus != null && !combat.isAttacking(focus)) {
                    controller.selectCard(focus.view, null, Tap) // the creature picked first blocks it
                    blockFocus = null
                } else {
                    blockFocus = card.id
                }
            }
            card.controller == controller.player && card.isCreature -> {
                when {
                    combat.isBlocking(card) -> {
                        controller.selectCard(cv, null, RemoveFromCombat)
                        blockFocus = null
                    }
                    focus != null && combat.isAttacking(focus) -> controller.selectCard(cv, null, Tap)
                    else -> blockFocus = if (blockFocus == card.id) null else card.id
                }
            }
        }
        requestRefresh()
    }

    private object RemoveFromCombat : ITriggerEvent {
        override fun getButton() = 3
        override fun getX() = 0
        override fun getY() = 0
    }

    /**
     * Forge only removes an attacker when it attacks the *current* defender; otherwise a
     * tap re-targets it. When the player taps an attacker aimed elsewhere they mean "take
     * it out", so briefly switch to its defender, undeclare it, and switch back.
     */
    private fun undeclareElsewhereAttacker(controller: PlayerControllerHuman, cv: CardView): Boolean {
        val input = currentInput() as? InputAttack ?: return false
        val card = controller.getCard(cv) ?: return false
        val defender = controller.game.combat?.getDefenderByAttacker(card) ?: return false
        val current = fieldValue(input, "currentDefender") as? GameEntity ?: return false
        if (defender == current) return false
        selectDefender(controller, defender)
        controller.selectCard(cv, null, Tap)
        selectDefender(controller, current)
        return true
    }

    private fun selectDefender(controller: PlayerControllerHuman, defender: GameEntity) {
        when (defender) {
            is Player -> controller.selectPlayer(defender.view, Tap)
            is Card -> controller.selectCard(defender.view, null, Tap)
        }
    }

    private fun fieldValue(target: Any, name: String): Any? {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            runCatching { return type!!.getDeclaredField(name).apply { isAccessible = true }.get(target) }
            type = type.superclass
        }
        return null
    }

    fun tapPlayer(id: Int) {
        val pv = playersById[id] ?: return
        onGameThread { gameController?.selectPlayer(pv, Tap) }
    }

    fun pressOk() = onGameThread { rememberChoice(); gameController?.selectButtonOk() }

    // ---- Choosing permanents ----------------------------------------------------------
    // Forge asks to pick cards on the board by highlighting them, which is easy to miss and
    // never says whose cards they are. While such a pick is pending the UI shows the options
    // in a dialog, grouped by controller, plus what was already picked for the same spell.
    private val choiceHistory = ArrayList<String>()
    private var choiceStackId: Int? = null

    private fun selectedInInput(): List<CardView> {
        val input = currentInput() as? InputSelectEntitiesFromList<*> ?: return emptyList()
        val chosen = runCatching { fieldValue(input, "selected") as? Iterable<*> }.getOrNull() ?: return emptyList()
        return chosen.mapNotNull { (it as? Card)?.view }
    }

    private fun rememberChoice() {
        if (currentInput() !is InputSelectEntitiesFromList<*>) return
        selectedInInput().forEach { cv ->
            choiceHistory += cv.name + (cv.controller?.name?.let { " ($it)" } ?: "")
        }
    }

    /**
     * Targets in zones the table doesn't show (e.g. "return target card from your graveyard"):
     * those can only be picked from a carousel of the cards themselves.
     */
    private fun zoneChoice(
        selectable: Set<Int>, highlighted: Set<Int>, cards: Map<Int, CardView>, toUi: (CardView) -> CardUi,
    ): ZoneChoiceUi? {
        // Only while choosing (targets and the like): with priority, cards you could cast from
        // a graveyard are marked too, and those are played from the zone viewer instead.
        val input = currentInput()
        if (input is InputPassPriority || input is InputAttack || input is InputBlock) return null
        val onTable = setOf(ZoneType.Battlefield, ZoneType.Hand)
        val candidates = selectable.mapNotNull { cards[it] }
        val hidden = candidates.filter { it.zone != null && it.zone !in onTable }
        if (hidden.isEmpty()) return null
        val me = gameView?.players?.firstOrNull { isLocalPlayer(it) }?.id
        return ZoneChoiceUi(
            cards = hidden.sortedWith(compareBy({ it.zone?.ordinal }, { it.name })).map { cv ->
                val whose = if (cv.owner?.id == me) "Your" else "${cv.owner?.name ?: "Their"}'s"
                val zone = when (cv.zone) {
                    ZoneType.Graveyard -> "graveyard"
                    ZoneType.Exile -> "exile"
                    ZoneType.Library -> "library"
                    ZoneType.Command -> "command zone"
                    else -> cv.zone?.name?.lowercase() ?: ""
                }
                toUi(cv) to "$whose $zone"
            },
            selectedIds = highlighted,
            alsoOnBoard = candidates.any { it.zone in onTable },
        )
    }

    private fun boardChoice(view: GameView, selectable: Set<Int>, toUi: (CardView) -> CardUi): BoardChoiceUi? {
        val top = view.stack?.firstOrNull()?.id
        if (top != choiceStackId) { choiceStackId = top; choiceHistory.clear() }
        if (currentInput() !is InputSelectEntitiesFromList<*> || selectable.isEmpty()) return null
        val options = selectable.mapNotNull { cardsById[it] }
        val groups = options.groupBy { it.controller?.name ?: "" }
            .map { (owner, cs) -> (if (owner.isEmpty()) "Cards" else "$owner's") to cs.map(toUi) }
        val selected = runCatching { selectedInInput().mapTo(HashSet()) { it.id } }.getOrDefault(emptySet())
        return BoardChoiceUi(groups, selected, choiceHistory.toList())
    }
    fun pressCancel() = onGameThread { gameController?.selectButtonCancel() }

    // ---- Undo ---------------------------------------------------------------------
    // One rule: every time the game asks this player for a play (a priority prompt), the
    // whole game is recorded with Forge's GameMemento, an in-place snapshot of every game
    // object (board, hands, library order, life, mana, the stack with its abilities, phase,
    // ids). Undo goes back to the prompt before the current one, up to MAX_UNDO times.
    //
    // A memento is only laid back while the game thread sits in a priority prompt: there
    // Forge holds nothing about a play on its call stack. The prompt is then restarted, so
    // Forge asks again from the restored state. Asked anywhere else (paying a cost, choosing
    // targets, declaring attackers, the opponent's turn), the player's own play in progress is
    // cancelled where Forge allows it, and the undo runs at the next priority prompt.
    //
    // Forge's own UndoManager is turned off: it records every prompt as well, turns End Turn
    // into Undo, and stops at anything the opponent does.

    /** [position] is the same state as text, so a save can carry the history (null if it failed). */
    private class UndoEntry(val memento: GameMemento, val logSize: Int, val position: SavedGame.Position?)

    /** Newest last; while the player sits in [promptInput], the last entry is that prompt's state. */
    private val history = ArrayList<UndoEntry>()
    private var recorder = GameMemento.Recorder()
    private var historyGame: forge.game.Game? = null

    /** The priority prompt last recorded (or shown again after an undo). */
    @Volatile private var promptInput: InputPassPriority? = null

    /** The prompt shown right after an undo is the restored state itself: not recorded again. */
    private var skipNextRecord = false

    /** The player started a play (a card or ability) from priority and it isn't finished yet. */
    @Volatile private var startedPlay = false

    /** History index an undo asked for away from a priority prompt goes back to (-1: the last of [resumeHistory]). */
    @Volatile private var undoTarget: Int? = null
    private val undoPending get() = undoTarget != null

    /** Why recording failed, shown when the player taps a disabled undo. */
    @Volatile private var undoError: String? = null

    /**
     * A resumed game's positions from before the save (SavedGame.history), oldest first: what
     * lies before the first recorded prompt. Laid back with GameState, one at a time.
     */
    @Volatile var resumeHistory: List<SavedGame.Position> = emptyList()

    /** A resumed game's saved log, put back at its first prompt (SavedGame.log). */
    @Volatile var resumeLog: List<Pair<String, String>> = emptyList()

    /** The resumed save carries no positions (made before v0.64): says why undo is off at first. */
    @Volatile var resumedWithoutHistory = false

    /**
     * True while a saved board is laid over the game (resuming, or undo into a saved position).
     * GameState puts each permanent onto the battlefield for real, so "As this enters, choose..."
     * runs again (Sower of Discord's two players) and Forge has no switch to skip it. Meanwhile
     * every question is answered with its default and nothing is shown; the saved choices are
     * put back right after (SavedGames.applyChoices).
     */
    @Volatile var layingBoard = false

    /** Answers a choice of players asked while [layingBoard] (Forge asks those on the board). */
    private fun answerWhileLayingBoard() {
        val controller = gameController as? PlayerControllerHuman ?: return
        val input = currentInput() as? InputSelectEntitiesFromList<*> ?: return
        val players = input.validChoices.filterIsInstance<forge.game.player.Player>()
        if (players.isEmpty() || players.size != input.validChoices.size) return
        if (input.selected.isEmpty()) controller.selectPlayer(players.first().view, Tap)
        if (currentInput() === input) controller.selectButtonOk()
    }

    init {
        runCatching { FModel.getPreferences().setPref(FPref.UI_UNDO_MODE, ForgeConstants.UNDO_MODE_OFF) }
    }

    /** True while the player sits in the prompt whose state is the last history entry. */
    private fun atRecordedPrompt() = currentInput().let { it is InputPassPriority && it === promptInput }

    /** Where an undo asked now goes: the prompt before this one, or the last one once past it. */
    private fun undoReach() = if (atRecordedPrompt()) history.size - 2 else history.size - 1

    private fun canUndo() = undoReach().let { it >= 0 || (it == -1 && resumeHistory.isNotEmpty()) }

    /** Called on every redraw: a new priority prompt gets recorded, or runs the pending undo. */
    private fun watchPrompt() {
        val input = currentInput() as? InputPassPriority ?: return
        if (input === promptInput || autoStart) return
        onGameThread { onPrompt() }
    }

    private fun onPrompt() {
        val input = currentInput() as? InputPassPriority ?: return
        if (input === promptInput) return
        val game = gameView?.game ?: return // network guests don't hold the game
        if (game !== historyGame) {
            historyGame = game
            history.clear()
            recorder = GameMemento.Recorder()
            skipNextRecord = false
        }
        startedPlay = false
        undoTarget?.let { restoreTo(it, input, game); return }
        promptInput = input
        // A resumed game: its log starts with the saved one (the board set-up's own lines go).
        if (resumeLog.isNotEmpty()) {
            runCatching {
                game.gameLog.truncateTo(0)
                resumeLog.forEach { (type, message) ->
                    game.gameLog.add(runCatching { GameLogEntryType.valueOf(type) }.getOrDefault(GameLogEntryType.INFORMATION), message)
                }
            }
            resumeLog = emptyList()
        }
        if (skipNextRecord) { skipNextRecord = false; return }
        runCatching { UndoEntry(recorder.capture(game), game.gameLog.size(), runCatching { SavedGames.position(game) }.getOrNull()) }
            .onSuccess {
                undoError = null
                history += it
                // Past the first entry, the save's older positions are too far back to jump to.
                while (history.size > MAX_UNDO + 1) { history.removeAt(0); resumeHistory = emptyList() }
            }
            .onFailure { Log.w("Undo", "recording failed", it); undoError = it.message ?: it.javaClass.simpleName }
        requestRefresh()
        // After the recording, so undo still reaches this stop (the restored prompt isn't auto-passed).
        if (passWhenStuck && !resolveAll && nothingToPlay(game)) gameController?.selectButtonOk()
    }

    /** No spell, land or ability (mana abilities aside) the player could use now, and no floating mana. */
    private fun nothingToPlay(game: forge.game.Game): Boolean = runCatching {
        val me = (gameController as? PlayerControllerHuman)?.player ?: return false
        if (!me.manaPool.isEmpty) return false
        game.cardsInGame.none { c -> c.getAllPossibleAbilities(me, true).any { !it.isManaAbility } }
    }.getOrDefault(false)

    fun undo() = onGameThread {
        // Tapping again while it waits for its moment calls the undo off.
        if (undoPending) { undoTarget = null; requestRefresh(); return@onGameThread }
        if (!canUndo()) return@onGameThread
        requestRestore(undoReach())
    }

    /** Back to just before log line [logIndex] (counted from the game's first line), if the undo history reaches it. */
    fun rewindTo(logIndex: Int) = onGameThread {
        if (undoPending) return@onGameThread
        val target = rewindTarget(logIndex) ?: return@onGameThread
        requestRestore(target)
    }

    /** The newest undo point taken before log line [logIndex], or null when the history doesn't reach that far. */
    private fun rewindTarget(logIndex: Int): Int? = runCatching {
        val points = history.toList()
        val target = points.indexOfLast { it.logSize <= logIndex }
        // Going back to the prompt the player sits in changes nothing.
        if (target < 0 || (target == points.lastIndex && atRecordedPrompt())) null else target
    }.getOrNull()

    /** Restores history entry [target] now if the game waits in a priority prompt, otherwise at the next one. */
    private fun requestRestore(target: Int) {
        val game = gameView?.game ?: return
        val input = currentInput()
        if (input is InputPassPriority && input === promptInput) { restoreTo(target, input, game); return }
        undoTarget = target
        when {
            // Declaring attackers: call them all back and skip the attack.
            input is InputAttack -> leaveAttack()
            // Casting or activating (targets, costs, mana): back out of it where Forge allows.
            startedPlay -> backOutOfPlay()
        }
        requestRefresh()
    }

    /** Cancels the current step of the player's own play; called again on each new step. */
    private fun backOutOfPlay() {
        if (!undoPending || !startedPlay) return
        val input = currentInput() ?: return
        if (input is InputPassPriority || input is InputAttack) return
        if (prompt.cancelEnabled) gameController?.selectButtonCancel()
    }

    private fun leaveAttack() {
        val controller = gameController ?: return
        // Cancel undeclares attackers when there are some (and attacks with everything when there
        // are none), so only cancel with attackers declared; OK then declares no attack.
        if (gameView?.combat?.attackers?.any() == true) controller.selectButtonCancel()
        if (currentInput() is InputAttack) controller.selectButtonOk()
    }

    @Volatile private var resolveAll = false

    fun resolveAllNow() = onGameThread { resolveAll = true; gameController?.selectButtonOk() }

    /** Lays history entry [target] (or the resumed game's undo state for -1) over the game, then asks again. */
    private fun restoreTo(target: Int, input: InputPassPriority, game: forge.game.Game) {
        undoTarget = null
        if (target < 0) {
            undoResume(game)
            history.clear()
            skipNextRecord = false
        } else {
            val entry = history.getOrNull(target) ?: return
            runCatching {
                game.fireEvent(GameEventSnapshotRestored(true))
                entry.memento.restore()
                game.gameLog.truncateTo(entry.logSize)
                refreshViews(game)
                game.fireEvent(GameEventSnapshotRestored(false))
                if (entry.memento.problems.isNotEmpty()) Log.w("Undo", "restore problems: ${entry.memento.problems}")
            }.onFailure { Log.w("Undo", "restore failed", it) }
            while (history.size > target + 1) history.removeAt(history.size - 1)
            skipNextRecord = true
        }
        resolveAll = false
        // An undone End Turn must not keep auto-passing the restored turn away.
        (gameController as? PlayerControllerHuman)?.autoPassCancel()
        // The prompt on screen belongs to the state we left: Forge asks again from the restored one.
        promptInput = null
        input.restartPrompt()
        requestRefresh()
    }

    private fun refreshViews(game: forge.game.Game) {
        game.triggerHandler.resetActiveTriggers()
        for (p in game.players) { p.updateAllZonesForView(); p.updateManaForView() }
        game.updateStackForView(); game.updatePhaseForView(); game.updatePlayerTurnForView()
        game.updateTurnForView(); game.updateCombatForView()
        game.fireEvent(GameEventCardStatsChanged(game.cardsInGame))
        game.fireEvent(GameEventPlayerStatsChanged(game.players))
    }

    /**
     * Positions for a save to carry, oldest first: the resumed game's older ones, then every
     * recorded prompt before the one the player sits in (that one is the saved state itself).
     */
    fun savedHistory(): List<SavedGame.Position> = runCatching {
        val entries = history.toList().let { if (atRecordedPrompt()) it.dropLast(1) else it }
        val gap = entries.indexOfLast { it.position == null }
        val positions = entries.drop(gap + 1).mapNotNull { it.position }
        (if (gap >= 0) positions else resumeHistory + positions).takeLast(MAX_UNDO)
    }.getOrDefault(emptyList())

    /** Lays the latest of the resumed game's saved positions over the game. */
    private fun undoResume(game: forge.game.Game) {
        val position = resumeHistory.lastOrNull() ?: return
        resumeHistory = resumeHistory.dropLast(1)
        val text = position.state
        runCatching {
            // GameState clears the players' zones but not the stack.
            game.stack.toList().forEach { game.stack.remove(it) }
            game.stackZone.removeAllCards(true)
            layingBoard = true
            try {
                GameState().apply { parse(text.lines().map { it.trim() }.filter { it.isNotBlank() }) }.applyToGame(game)
            } finally {
                layingBoard = false
            }
            SavedGames.applyChoices(position.choices, game)
            (gameController as? PlayerControllerHuman)?.player?.let { game.phaseHandler.setPriority(it) }
        }.onFailure { Log.w("Undo", "resume undo failed", it) }
    }

    fun concedeGame() = onGameThread { concede() }
    fun continueAfterGame() = onGameThread { hostedMatch?.continueMatch() }
    fun cardById(id: Int): CardView? = cardsById[id]

    private object Tap : ITriggerEvent {
        override fun getButton() = 1
        override fun getX() = 0
        override fun getY() = 0
    }

    // ---- IGuiGame: view updates --------------------------------------------------

    override fun updateCurrentPlayer(player: PlayerView?) = requestRefresh()

    // Network guests: the host streams state changes as deltas; redraw after each one.
    override fun afterDeltaApplied() = requestRefresh()
    override fun openView(myPlayers: TrackableCollection<PlayerView>?) {
        gameOver = null
        // Local matches are already on screen; network ones appear here.
        MainThread.post { io.github.nmanuelides.mtgreforged.engine.Engine.showMatch(this) }
        requestRefresh()
    }

    override fun showPromptMessage(playerView: PlayerView?, message: String?, card: CardView?) {
        prompt = prompt.copy(message = message.orEmpty(), card = card?.let { CardUi.from(it, mayView(it)) })
        requestRefresh()
    }

    private fun autoAnswerPregame() {
        if ((gameView?.turn ?: 0) >= 1) {
            autoStart = false
            return
        }
        onGameThread {
            val controller = gameController as? PlayerControllerHuman ?: return@onGameThread
            when (val input = currentInput()) {
                is InputSelectEntitiesFromList<*> -> controller.player?.let { controller.selectPlayer(it.view, Tap) }
                is InputConfirm -> controller.selectButtonOk()
                else -> if (input != null) controller.selectButtonOk()
            }
        }
    }

    override fun updateButtons(owner: PlayerView?, label1: String?, label2: String?, enable1: Boolean, enable2: Boolean, focus1: Boolean) {
        if (autoStart) autoAnswerPregame()
        if (layingBoard) onGameThread { answerWhileLayingBoard() }
        if (currentInput() is InputAttack) onGameThread { prepareRequiredAttackers() }
        // An undo asked for mid-play keeps backing out of each new step of it.
        if (undoPending) onGameThread { backOutOfPlay() }
        // 3: "Resolve all" keeps passing while things resolve, until the stack is empty.
        if (resolveAll) onGameThread {
            val stackEmpty = gameView?.stack?.isEmpty() != false
            if (stackEmpty) resolveAll = false
            else if (currentInput() is InputPassPriority) gameController?.selectButtonOk()
        }
        prompt = prompt.copy(
            okLabel = label1.orEmpty(), cancelLabel = label2.orEmpty(),
            okEnabled = enable1, cancelEnabled = enable2,
        )
        requestRefresh()
    }

    override fun flashIncorrectAction() { _feedback.tryEmit(Feedback.IncorrectAction) }
    override fun alertUser() { _feedback.tryEmit(Feedback.Alert) }

    override fun finishGame() {
        val view = gameView ?: return
        val won = view.players.any { isLocalPlayer(it) && !it.hasLost }
        gameOver = GameOverUi(
            title = view.winningPlayerName?.let { "$it wins" } ?: "Draw",
            youWon = won,
            matchOver = view.isMatchOver,
        )
        requestRefresh()
    }

    override fun showCombat() = requestRefresh()
    override fun updatePhase(saveState: Boolean) = requestRefresh()
    override fun updateTurn(player: PlayerView?) = requestRefresh()
    override fun updateStack() = requestRefresh()
    override fun updateZones(zonesToUpdate: Iterable<PlayerZoneUpdate>?) = requestRefresh()
    override fun updateCards(cards: Iterable<CardView>?) = requestRefresh()
    override fun updateManaPool(manaPoolUpdate: Iterable<PlayerView>?) = requestRefresh()
    override fun updateLives(livesUpdate: Iterable<PlayerView>?) = requestRefresh()
    override fun refreshCardDetails(cards: Iterable<CardView>?) = requestRefresh()
    override fun handleGameEvent(event: GameEvent?) {
        super.handleGameEvent(event)
        requestRefresh()
    }

    override fun setGameView(gameView0: GameView?) {
        super.setGameView(gameView0)
        requestRefresh()
    }

    // Forge now reports what the prompt marks (selectable / highlighted) as one Selection.
    override fun setSelection(selection: forge.gamemodes.match.Selection?) {
        super.setSelection(selection)
        requestRefresh()
    }

    override fun setPanelSelection(hostCard: CardView?) {}
    override fun setCard(card: CardView?) {}
    override fun setPlayerAvatar(player: LobbyPlayer?, ihi: IHasIcon?) {}

    // Stop where a player usually wants to act; Forge still gives priority whenever
    // something is on the stack.
    override fun isUiSetToSkipPhase(playerTurn: PlayerView, phase: PhaseType): Boolean {
        val myTurn = isLocalPlayer(playerTurn)
        val stops = if (myTurn) MY_TURN_STOPS else OPPONENT_TURN_STOPS
        return phase !in stops
    }

    // ---- IGuiGame: questions -----------------------------------------------------

    private fun ui(cv: CardView?) = cv?.let { CardUi.from(it, mayView(it)) }

    override fun getAbilityToPlay(hostCard: CardView?, abilities: List<SpellAbilityView>, triggerEvent: ITriggerEvent?): SpellAbilityView? {
        if (abilities.isEmpty()) return null
        if (abilities.size == 1) return abilities[0]
        return choose("Choose an ability", 0, 1, abilities, null, null)?.firstOrNull()
    }

    override fun assignCombatDamage(
        attacker: CardView, blockers: List<CardView>, damage: Int,
        defender: GameEntityView?, overrideOrder: Boolean, maySkip: Boolean,
    ): Map<CardView?, Int>? {
        val targets = blockers.map { describe(it, null) } +
            listOfNotNull(defender?.let { PromptItem(it.name ?: "Defender") })
        val amounts = PromptHost.ask<List<Int>?>(
            Prompt.Distribute(
                title = "Assign ${damage} damage from ${attacker.name}",
                source = ui(attacker), total = damage, targets = targets,
                atLeastOne = false, initial = defaultLethal(blockers, damage, defender != null),
                maySkip = maySkip,
            ),
            null,
        ) ?: return null
        val result = HashMap<CardView?, Int>()
        blockers.forEachIndexed { i, b -> if (amounts[i] > 0) result[b] = amounts[i] }
        if (defender != null && amounts.last() > 0) result[null] = amounts.last()
        return result
    }

    private fun defaultLethal(blockers: List<CardView>, damage: Int, hasDefender: Boolean): List<Int> {
        var left = damage
        val amounts = blockers.map { b ->
            val need = (b.lethalDamage).coerceAtLeast(1).coerceAtMost(left)
            left -= need
            need
        }.toMutableList()
        if (hasDefender) amounts += left else if (amounts.isNotEmpty()) amounts[amounts.lastIndex] += left
        return amounts
    }

    override fun assignGenericAmount(
        effectSource: CardView?, target: Map<Any, Int>, amount: Int, atLeastOne: Boolean, amountLabel: String?,
    ): Map<Any, Int> {
        val keys = target.keys.toList()
        val items = keys.map { k ->
            when (k) {
                is CardView -> describe(k, null)
                is GameEntityView -> PromptItem(k.name ?: k.toString())
                is Byte -> PromptItem(MagicColor.toLongString(k))
                else -> PromptItem(k.toString())
            }
        }
        val amounts = PromptHost.ask<List<Int>?>(
            Prompt.Distribute(
                title = "Distribute $amount ${amountLabel.orEmpty()}".trim(),
                source = ui(effectSource), total = amount, targets = items,
                atLeastOne = atLeastOne, initial = keys.map { target[it] ?: 0 }, maySkip = false,
            ),
            null,
        ) ?: return target
        return keys.zip(amounts).toMap()
    }

    override fun message(message: String?, title: String?) {
        if (layingBoard) return
        // Forge's bare "Attack declaration invalid": say which creatures have to attack, and why.
        val text = if (message == "Attack declaration invalid") mustAttackExplanation() ?: message else message.orEmpty()
        PromptHost.ask(Prompt.Message(if (message == "Attack declaration invalid") "These creatures must attack" else title ?: "Forge", text), Unit)
    }

    // ---- Game log for the log sheet -----------------------------------------------

    private val logCardId = Regex("\\((\\d+)\\)")
    private val logCardIdText = Regex("\\s*\\(\\d+\\)")

    private val logDebugTail = Regex("\\s*\\[Card:.*\\]\\s*$")

    /** The newest log lines with their player and cards resolved; [entries] come newest first. */
    private fun logEntries(view: GameView, entries: List<forge.game.GameLogEntry>, cards: Map<Int, CardView>): List<LogEntryUi> {
        val total = entries.size
        val players = view.players.orEmpty().toList()
        val byName = players.withIndex().sortedByDescending { it.value.name.length }
        val game = gameView?.game
        // Cards that can be named in the log, by name; public zones win over hidden ones.
        val known = LinkedHashMap<String, CardView>()
        runCatching {
            val publicZones = setOf(ZoneType.Battlefield, ZoneType.Graveyard, ZoneType.Exile, ZoneType.Stack, ZoneType.Command)
            val all = game?.cardsInGame?.map { it.view }.orEmpty() + cards.values
            all.sortedBy { if (it.zone in publicZones) 0 else 1 }.forEach { cv ->
                val name = cv.currentState?.name ?: cv.name
                if (!name.isNullOrBlank() && name.length >= 3) known.putIfAbsent(name, cv)
            }
        }
        val names = known.keys.sortedByDescending { it.length }
        val playerNames = players.mapTo(HashSet()) { it.name }
        return entries.take(150).mapIndexed { i, e ->
            val index = total - 1 - i
            val raw = e.message()
            val byId = logCardId.findAll(raw).map { it.groupValues[1].toInt() }.mapNotNull { id ->
                cards[id] ?: runCatching { game?.findById(id)?.view }.getOrNull()
            }.toList()
            val text = raw.replace(logCardIdText, "").replace(logDebugTail, "")
            // Cards named in the text (Forge often writes just the name), longest names first.
            val taken = ArrayList<IntRange>()
            val byNameCards = names.filter { it !in playerNames }.mapNotNull { name ->
                val at = text.indexOf(name)
                if (at < 0) return@mapNotNull null
                val range = at until at + name.length
                if (taken.any { it.first <= range.last && range.first <= it.last }) return@mapNotNull null
                taken += range
                known[name]
            }
            val cardViews = (byId + byNameCards).distinctBy { it.id }.take(4)
            val kind = logKind(e.type())
            var who: Pair<Int, PlayerView>? = byName.firstOrNull { it.value.name.isNotBlank() && it.value.name in text }?.let { it.index to it.value }
            // Mana lines name only the card that made the mana: its controller did it.
            if (who == null && kind == LogKind.Mana) {
                val controller = cardViews.firstOrNull()?.controller
                val seat = players.indexOfFirst { it.id == controller?.id }
                if (seat >= 0) who = seat to players[seat]
            }
            LogEntryUi(
                index = index,
                kind = kind,
                text = text,
                playerSeat = who?.first,
                playerName = who?.second?.name,
                playerCommander = who?.second?.commanders?.firstOrNull()?.name,
                cards = cardViews.map { CardUi.from(it, mayView(it)) },
                canRewind = kind != LogKind.Mana && kind != LogKind.Phase && rewindTarget(index) != null,
            )
        }
    }

    private fun logKind(type: GameLogEntryType): LogKind = when (type) {
        GameLogEntryType.TURN -> LogKind.Turn
        GameLogEntryType.PHASE -> LogKind.Phase
        GameLogEntryType.MANA -> LogKind.Mana
        GameLogEntryType.INFORMATION -> LogKind.Info
        GameLogEntryType.STACK_ADD -> LogKind.Cast
        GameLogEntryType.STACK_RESOLVE -> LogKind.Resolve
        GameLogEntryType.LAND -> LogKind.Land
        GameLogEntryType.COMBAT -> LogKind.Combat
        GameLogEntryType.DAMAGE -> LogKind.Damage
        GameLogEntryType.LIFE -> LogKind.Life
        GameLogEntryType.ZONE_CHANGE, GameLogEntryType.DISCARD -> LogKind.Zone
        else -> LogKind.Other
    }

    // ---- Creatures that must attack (goaded, "attacks each combat if able") ----------

    /** Creatures with an attack requirement this combat, with the defender Forge's best legal attack gives each. */
    private fun requiredAttackers(): Map<Card, forge.game.GameEntity?> {
        val combat = gameView?.game?.phaseHandler?.combat ?: return emptyMap()
        val constraints = combat.attackConstraints
        val required = constraints.requirements.filterValues { it.hasRequirement() }.keys
        if (required.isEmpty()) return emptyMap()
        val best = runCatching { constraints.legalAttackers.left }.getOrNull().orEmpty()
        return required.associateWith { best[it] }
    }

    private fun mustAttackExplanation(): String? = runCatching {
        val required = requiredAttackers().keys
        if (required.isEmpty()) return null
        val lines = required.joinToString("\n") { c ->
            val why = when {
                c.isGoaded -> "goaded by ${c.goaded.joinToString(", ") { it.name }}: it must attack, and not them"
                else -> {
                    val sources = combatRequirementSources(c)
                    if (sources.isEmpty()) "it attacks each combat if able" else "because of ${sources.joinToString(", ")}"
                }
            }
            "• ${c.name}: $why"
        }
        "These creatures have to attack this turn if they can:\n\n$lines\n\nThey're marked as attackers already; pick other attackers too if you want."
    }.getOrNull()

    private fun combatRequirementSources(c: Card): List<String> = runCatching {
        val combat = gameView?.game?.phaseHandler?.combat ?: return emptyList()
        combat.attackConstraints.requirements[c]?.causesToAttack?.values()?.mapNotNull { it.hostCard?.name }?.distinct().orEmpty()
    }.getOrDefault(emptyList())

    /** The attack input this combat's required attackers were already declared for. */
    private var preparedAttack: Any? = null

    /** Declares the creatures that must attack, each at a defender it's allowed to attack. Game thread. */
    private fun prepareRequiredAttackers() {
        val input = currentInput() as? InputAttack ?: return
        if (input === preparedAttack) return
        preparedAttack = input
        val controller = gameController as? PlayerControllerHuman ?: return
        val combat = gameView?.game?.phaseHandler?.combat ?: return
        runCatching {
            requiredAttackers().forEach { (card, defender) ->
                if (combat.isAttacking(card)) return@forEach
                when (defender) {
                    is Player -> controller.selectPlayer(defender.view, Tap)
                    is Card -> controller.selectCard(defender.view, null, Tap)
                    else -> {}
                }
                controller.selectCard(card.view, null, Tap)
            }
        }.onFailure { Log.w("Combat", "couldn't declare required attackers", it) }
    }

    override fun showErrorDialog(message: String?, title: String?) {
        PromptHost.ask(Prompt.Message(title ?: "Error", message.orEmpty(), isError = true), Unit)
    }

    override fun showConfirmDialog(message: String?, title: String?, yesButtonText: String?, noButtonText: String?, defaultYes: Boolean): Boolean =
        showOptionDialog(message, title, null, listOf(yesButtonText ?: "Yes", noButtonText ?: "No"), if (defaultYes) 0 else 1) == 0

    override fun showOptionDialog(message: String?, title: String?, icon: FSkinProp?, options: List<String>, defaultOption: Int): Int =
        if (layingBoard) defaultOption else PromptHost.ask(Prompt.Options(title.orEmpty(), message.orEmpty(), options, defaultOption), defaultOption)

    override fun showInputDialog(message: String?, title: String?, icon: FSkinProp?, initialInput: String?, inputOptions: List<String>?, isNumeric: Boolean): String? =
        PromptHost.ask(Prompt.Input(title.orEmpty(), message.orEmpty(), initialInput, inputOptions, isNumeric), null)

    override fun confirm(c: CardView?, question: String?, defaultIsYes: Boolean, options: List<String>): Boolean {
        if (question.orEmpty().contains("may put it into the command zone")) {
            val zone = when (c?.zone) { ZoneType.Exile -> "Exile"; ZoneType.Graveyard -> "Graveyard"; else -> "Where it is" }
            val where = if (zone == "Exile") "was exiled" else "went to the graveyard"
            return PromptHost.ask(
                Prompt.Options("Your commander", "${c?.name.orEmpty()} $where. Where should it go?", listOf("Command zone", zone), 0, ui(c)),
                0,
            ) == 0
        }
        return confirmPlain(c, question, defaultIsYes, options)
    }

    private fun confirmPlain(c: CardView?, question: String?, defaultIsYes: Boolean, options: List<String>): Boolean =
        if (layingBoard) false else PromptHost.ask(
            Prompt.Options(c?.name.orEmpty(), question.orEmpty(), options, if (defaultIsYes) 0 else 1, ui(c)),
            if (defaultIsYes) 0 else 1,
        ) == 0

    private fun <T> choose(
        message: String?, min: Int, max: Int, choices: List<T>, selected: List<T>?, display: ((T) -> String)?,
    ): List<T>? {
        if (choices.isEmpty()) return emptyList()
        // Optional picks ("you may exile a card from your hand") take nothing: the board is only being rebuilt.
        if (layingBoard) return choices.take(min.coerceAtLeast(0))
        val items = choices.map { describe(it, display) }
        val pre = selected.orEmpty().mapNotNull { s -> choices.indexOf(s).takeIf { it >= 0 } }.toSet()
        val picked = PromptHost.ask<List<Int>?>(Prompt.Choose(message.orEmpty(), min, max, items, pre), null)
        return picked?.map { choices[it] }
    }

    override fun <T> getChoices(message: String?, min: Int, max: Int, choices: List<T>, selected: List<T>?, display: FSerializableFunction<T, String>?): List<T> =
        choose(message, min, max, choices, selected, display?.let { f -> { t: T -> f.apply(t) } }) ?: emptyList()

    /**
     * "Pick some of these" (e.g. Fact or Fiction's first pile, cards to keep). Forge routes it
     * to the reordering dialog by default, which made every card count as picked; here it's a
     * real multi-select: the cards you tick are the chosen group, the rest stay behind.
     */
    override fun <T> many(title: String?, topCaption: String?, min: Int, max: Int, sourceChoices: List<T>, destChoices: List<T>?, c: CardView?): List<T> {
        val all = sourceChoices + destChoices.orEmpty()
        val piles = title.orEmpty().contains("pile", ignoreCase = true)
        val message = if (piles) {
            "${title.orEmpty()}: tick the cards for pile 1 — the ones you leave unticked form pile 2."
        } else title.orEmpty()
        return choose(message, min.coerceAtLeast(0), if (max < 0) all.size else max, all, destChoices, null) ?: destChoices.orEmpty()
    }

    override fun <T> order(
        title: String?, top: String?, remainingObjectsMin: Int, remainingObjectsMax: Int,
        sourceChoices: List<T>, destChoices: List<T>?, referenceCard: CardView?,
        sideboardingMode: Boolean, showRememberCheckbox: Boolean,
    ): forge.gui.interfaces.IGuiGame.OrderResult<T> {
        val all = sourceChoices + destChoices.orEmpty()
        val order = PromptHost.ask<List<Int>?>(Prompt.Order(title.orEmpty(), all.map { describe(it, null) }), null)
            ?: all.indices.toList()
        return forge.gui.interfaces.IGuiGame.OrderResult(order.map { all[it] }, false)
    }

    // Sideboarding between games is not part of the MVP: keep the main deck as is.
    override fun sideboard(sideboard: CardPool?, main: CardPool, message: String?): List<PaperCard> = main.toFlatList()

    override fun chooseSingleEntityForEffect(
        title: String?, optionList: List<GameEntityView>, delayedReveal: DelayedReveal?, isOptional: Boolean,
    ): GameEntityView? {
        delayedReveal?.let { reveal(it.messagePrefix ?: "Revealed", it.cards.toList()) }
        if (optionList.isEmpty()) return null
        return choose(title, if (isOptional) 0 else 1, 1, optionList, null) { it.name ?: it.toString() }?.firstOrNull()
    }

    override fun chooseEntitiesForEffect(
        title: String?, optionList: List<GameEntityView>, min: Int, max: Int, delayedReveal: DelayedReveal?,
    ): List<GameEntityView> {
        delayedReveal?.let { reveal(it.messagePrefix ?: "Revealed", it.cards.toList()) }
        return choose(title, min, max, optionList, null) { it.name ?: it.toString() } ?: emptyList()
    }

    override fun manipulateCardList(
        title: String?, cards: Iterable<CardView>, manipulable: Iterable<CardView>?,
        toTop: Boolean, toBottom: Boolean, toAnywhere: Boolean,
    ): List<CardView> {
        val all = cards.toList()
        val order = PromptHost.ask<List<Int>?>(Prompt.Order(title.orEmpty(), all.map { describe(it, null) }), null)
            ?: return all
        return order.map { all[it] }
    }

    companion object {
        private const val OLD_SAVE_NOTE = "This game was saved before v0.64, so it has no steps from before the save. Undo works for the plays you make from now on."
        private val MY_TURN_STOPS = setOf(PhaseType.MAIN1, PhaseType.COMBAT_DECLARE_ATTACKERS, PhaseType.MAIN2)
        private val OPPONENT_TURN_STOPS = setOf(PhaseType.COMBAT_DECLARE_BLOCKERS, PhaseType.END_OF_TURN)
    }
}
