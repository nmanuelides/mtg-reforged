package io.github.nmanuelides.mtgreforged.match

import android.os.Handler
import android.os.Looper
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

/**
 * The per-match bridge between Forge and Compose. Forge pushes updates here from the
 * game thread; we coalesce them and rebuild an immutable [MatchUiState] on the main
 * thread. Player actions are forwarded to the game controller on a game thread, the
 * same way the libGDX frontend does it.
 */
class ComposeGuiGame : AbstractGuiGame() {

    private val main = Handler(Looper.getMainLooper())
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

    @Volatile private var prompt = PromptBarUi()
    @Volatile private var gameOver: GameOverUi? = null
    @Volatile private var refreshQueued = false
    private var cardsById: Map<Int, CardView> = emptyMap()
    private var playersById: Map<Int, PlayerView> = emptyMap()

    // ---- State publishing --------------------------------------------------------

    private fun requestRefresh() {
        if (refreshQueued) return
        refreshQueued = true
        main.postDelayed({
            refreshQueued = false
            rebuild()
        }, 16)
    }

    private fun rebuild() {
        val view = gameView ?: return
        // Game objects are mutated by the game thread; a torn read just means we try again.
        val snapshot = runCatching { buildSnapshot(view) }.getOrElse {
            requestRefresh()
            return
        }
        _state.value = snapshot
    }

    private fun buildSnapshot(view: GameView): MatchUiState {
        val cards = HashMap<Int, CardView>()
        val players = HashMap<Int, PlayerView>()
        fun card(cv: CardView): CardUi {
            cards[cv.id] = cv
            return CardUi.from(cv, mayView(cv))
        }
        fun zone(p: PlayerView, z: ZoneType) = p.getCards(z)?.map(::card).orEmpty()

        val allCommanders = view.players.flatMap { it.commanders.orEmpty() }
        val playerUis = view.players.map { p ->
            players[p.id] = p
            PlayerUi(
                id = p.id,
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
            )
        }.orEmpty()

        val combat = view.combat?.let { c ->
            CombatUi(
                attackers = c.attackers.associate { a -> a.id to c.getDefender(a)?.id },
                blockers = c.attackers.associate { a -> a.id to c.getBlockers(a).orEmpty().map { it.id } },
            )
        }

        val selectable = cards.values.filter { isSelectable(it) }.mapTo(HashSet()) { it.id }
        val highlighted = (cards.values + players.values).filter { isHighlighted(it) }.mapTo(HashSet()) { it.id }

        val log = view.gameLog?.getLogEntries(null)?.take(40)?.map { it.message() }.orEmpty()

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
            prompt = prompt.copy(passesPriority = currentInput() is InputPassPriority),
            selectableIds = selectable,
            highlightedIds = highlighted,
            choosablePlayerIds = choosable,
            attackTargetId = attackTarget,
            log = log,
            gameOver = gameOver,
        )
    }

    /**
     * Players the current input would accept. Forge only flags selectable *cards* to the
     * GUI, so read the waiting input: its list of valid choices, or the spell being
     * targeted. Lets the UI offer one button per player instead of "tap the portrait".
     */
    private fun currentInput() = (gameController as? PlayerControllerHuman)?.inputProxy?.input

    private fun choosablePlayerIds(view: GameView): Set<Int> {
        val input = currentInput() ?: return emptySet()
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
            // On a network guest the controller is Forge's remote one: just send the tap.
            val controller = gameController as? PlayerControllerHuman
                ?: return@onGameThread run { gameController?.selectCard(cv, null, Tap) }
            if (!undeclareElsewhereAttacker(controller, cv)) controller.selectCard(cv, null, Tap)
        }
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

    fun pressOk() = onGameThread { gameController?.selectButtonOk() }
    fun pressCancel() = onGameThread { gameController?.selectButtonCancel() }
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
    override fun openView(myPlayers: TrackableCollection<PlayerView>?) {
        gameOver = null
        // Local matches are already on screen; network ones appear here.
        android.os.Handler(android.os.Looper.getMainLooper()).post { io.github.nmanuelides.mtgreforged.engine.Engine.showMatch(this) }
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

    override fun setSelectables(cards: Iterable<CardView>?, min: Int, max: Int) {
        super.setSelectables(cards, min, max)
        requestRefresh()
    }

    override fun clearSelectables() {
        super.clearSelectables()
        requestRefresh()
    }

    override fun setHighlighted(entities: Iterable<GameEntityView>?, b: Boolean) {
        super.setHighlighted(entities, b)
        requestRefresh()
    }

    override fun getGamestate() = null
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
        PromptHost.ask(Prompt.Message(title ?: "Forge", message.orEmpty()), Unit)
    }

    override fun showErrorDialog(message: String?, title: String?) {
        PromptHost.ask(Prompt.Message(title ?: "Error", message.orEmpty(), isError = true), Unit)
    }

    override fun showConfirmDialog(message: String?, title: String?, yesButtonText: String?, noButtonText: String?, defaultYes: Boolean): Boolean =
        showOptionDialog(message, title, null, listOf(yesButtonText ?: "Yes", noButtonText ?: "No"), if (defaultYes) 0 else 1) == 0

    override fun showOptionDialog(message: String?, title: String?, icon: FSkinProp?, options: List<String>, defaultOption: Int): Int =
        PromptHost.ask(Prompt.Options(title.orEmpty(), message.orEmpty(), options, defaultOption), defaultOption)

    override fun showInputDialog(message: String?, title: String?, icon: FSkinProp?, initialInput: String?, inputOptions: List<String>?, isNumeric: Boolean): String? =
        PromptHost.ask(Prompt.Input(title.orEmpty(), message.orEmpty(), initialInput, inputOptions, isNumeric), null)

    override fun confirm(c: CardView?, question: String?, defaultIsYes: Boolean, options: List<String>): Boolean =
        PromptHost.ask(
            Prompt.Options(c?.name.orEmpty(), question.orEmpty(), options, if (defaultIsYes) 0 else 1, ui(c)),
            if (defaultIsYes) 0 else 1,
        ) == 0

    private fun <T> choose(
        message: String?, min: Int, max: Int, choices: List<T>, selected: List<T>?, display: ((T) -> String)?,
    ): List<T>? {
        if (choices.isEmpty()) return emptyList()
        val items = choices.map { describe(it, display) }
        val pre = selected.orEmpty().mapNotNull { s -> choices.indexOf(s).takeIf { it >= 0 } }.toSet()
        val picked = PromptHost.ask<List<Int>?>(Prompt.Choose(message.orEmpty(), min, max, items, pre), null)
        return picked?.map { choices[it] }
    }

    override fun <T> getChoices(message: String?, min: Int, max: Int, choices: List<T>, selected: List<T>?, display: FSerializableFunction<T, String>?): List<T> =
        choose(message, min, max, choices, selected, display?.let { f -> { t: T -> f.apply(t) } }) ?: emptyList()

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
        private val MY_TURN_STOPS = setOf(PhaseType.MAIN1, PhaseType.COMBAT_DECLARE_ATTACKERS, PhaseType.MAIN2)
        private val OPPONENT_TURN_STOPS = setOf(PhaseType.COMBAT_DECLARE_BLOCKERS, PhaseType.END_OF_TURN)
    }
}
