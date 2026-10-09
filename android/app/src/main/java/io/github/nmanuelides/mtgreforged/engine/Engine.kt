package io.github.nmanuelides.mtgreforged.engine

import android.content.Context
import android.util.Log
import forge.deck.Deck
import forge.deck.io.DeckSerializer
import forge.game.GameRules
import forge.game.GameType
import forge.game.player.RegisteredPlayer
import forge.gui.GuiBase
import forge.gui.interfaces.IProgressBar
import forge.localinstance.properties.ForgeConstants
import forge.localinstance.properties.ForgePreferences.FPref
import forge.model.FModel
import forge.player.GamePlayerUtil
import forge.util.ThreadUtil
import io.github.nmanuelides.mtgreforged.match.ComposeGuiGame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale

/** Boots the Forge engine once per process and starts matches. */
object Engine {

    sealed interface Status {
        data object NotStarted : Status
        data class InstallingData(val progress: Float) : Status
        data class Loading(val step: String, val progress: Float?) : Status
        data object Ready : Status
        data class Failed(val message: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.NotStarted)
    val status: StateFlow<Status> = _status.asStateFlow()

    private lateinit var gui: AndroidGui

    private val _match = MutableStateFlow<ComposeGuiGame?>(null)
    val match: StateFlow<ComposeGuiGame?> = _match.asStateFlow()

    @Synchronized
    fun start(context: Context) {
        if (_status.value != Status.NotStarted && _status.value !is Status.Failed) return
        _status.value = Status.Loading("Preparing…", null)
        val app = context.applicationContext
        Thread({
            runCatching { boot(app) }.onFailure {
                Log.e("Engine", "Boot failed", it)
                _status.value = Status.Failed(it.message ?: it.toString())
            }
        }, "Game Boot").start()
    }

    private fun boot(context: Context) {
        val forgeDir = File(context.filesDir, "forge").apply { mkdirs() }
        val installer = EngineDataInstaller(context, forgeDir)
        if (!installer.isUpToDate()) {
            installer.install { _status.value = Status.InstallingData(it) }
        }

        GuiBase.setIsAndroid(true)
        GuiBase.setUsingAppDirectory(true)
        gui = AndroidGui(context, forgeDir.path + File.separator)
        GuiBase.setInterface(gui)

        _status.value = Status.Loading("Loading cards…", null)
        FModel.initialize(ProgressBridge) { prefs ->
            if (Locale.getDefault().language == "es") prefs.setPref(FPref.UI_LANGUAGE, "es-ES")
            prefs.setPref(FPref.UI_ENABLE_ONLINE_IMAGE_FETCHER, true)
            // Forge otherwise interrupts the first match to ask for a name.
            if (prefs.getPref(FPref.PLAYER_NAME).isNullOrBlank()) prefs.setPref(FPref.PLAYER_NAME, "Jugador")
            // We draw our own UI; Forge's own deck-generation matrix only slows boot down.
            prefs.setPref(FPref.DECKGEN_CARDBASED, false)
            // No audio backend yet (AndroidGui returns no clips).
            prefs.setPref(FPref.UI_ENABLE_SOUNDS, false)
            prefs.setPref(FPref.UI_ENABLE_MUSIC, false)
            null
        }
        _status.value = Status.Ready
    }

    private object ProgressBridge : IProgressBar {
        private var description = ""
        private var max = 0
        override fun setDescription(s0: String?) {
            description = s0.orEmpty()
            _status.value = Status.Loading(description, null)
        }
        override fun setValue(progress: Int) {
            if (max > 0) _status.value = Status.Loading(description, progress.toFloat() / max)
        }
        override fun reset() {}
        override fun setShowETA(b0: Boolean) {}
        override fun setShowCount(b0: Boolean) {}
        override fun setPercentMode(percentMode0: Boolean) {}
        override fun getMaximum() = max
        override fun setMaximum(maximum0: Int) { max = maximum0 }
    }

    // ---- Decks & matches ---------------------------------------------------------

    data class DeckEntry(val name: String, val file: File)

    enum class Format(val label: String, val maxPlayers: Int) {
        Constructed("Constructed", 4),
        Commander("Commander", 4);

        // GameType's static init needs the engine loaded, so don't touch it eagerly.
        val gameType: GameType
            get() = if (this == Commander) GameType.Commander else GameType.Constructed
    }

    private val deckCache = HashMap<Format, List<DeckEntry>>()

    /** Preconstructed decks that ship with Forge's data. */
    @Synchronized
    fun decks(format: Format): List<DeckEntry> = deckCache.getOrPut(format) {
        val dir = when (format) {
            Format.Constructed -> ForgeConstants.QUEST_PRECON_DIR
            Format.Commander -> ForgeConstants.COMMANDER_PRECON_DIR
        }
        File(dir).listFiles { f -> f.extension == "dck" }.orEmpty()
            // A few constructed precons are really commander decks; keep each list clean.
            .filter { format == Format.Commander || !it.readText().contains("[Commander]") }
            .map { DeckEntry(it.nameWithoutExtension, it) }
            .sortedBy { it.name.lowercase() }
    }

    fun loadDeck(entry: DeckEntry): Deck = DeckSerializer.fromFile(entry.file)

    /** Starts a free-for-all: the local player against one or more AI opponents. */
    fun startMatch(format: Format, player: DeckEntry, opponents: List<DeckEntry>) {
        require(opponents.isNotEmpty() && opponents.size < format.maxPlayers)
        ThreadUtil.invokeInGameThread {
            val playerCount = opponents.size + 1
            val variants = if (format == Format.Commander) setOf(GameType.Commander) else emptySet()
            fun register(entry: DeckEntry) = if (variants.isEmpty()) {
                RegisteredPlayer(loadDeck(entry))
            } else {
                RegisteredPlayer.forVariants(playerCount, variants, loadDeck(entry), null, false, null, null)
            }

            val human = register(player).apply { setPlayer(GamePlayerUtil.getGuiPlayer()) }
            val ais = opponents.map { deck ->
                register(deck).apply { setPlayer(GamePlayerUtil.createAiPlayer()) }
            }
            val guiGame = gui.newGuiGame as ComposeGuiGame
            val hosted = gui.hostMatch()
            guiGame.hostedMatch = hosted
            _match.value = guiGame
            // Fetch every card image in the match up front: ours first, then the opponents'.
            CardImages.prefetch(
                (listOf(human) + ais).flatMap { rp ->
                    val deck = rp.deck
                    (deck.commanders.orEmpty() + deck.main.toFlatList()).map { it.getImageKey(false) }
                },
            )
            hosted.startMatch(matchRules(format.gameType), variants, listOf(human) + ais, human, guiGame)
        }
    }

    /** Mirrors HostedMatch's defaults, minus the "AI can't play these cards" interruption. */
    private fun matchRules(type: GameType) = GameRules(type).apply {
        val prefs = FModel.getPreferences()
        setManaBurn(prefs.getPrefBoolean(FPref.LEGACY_MANABURN))
        setOrderCombatants(prefs.getPrefBoolean(FPref.LEGACY_ORDER_COMBATANTS))
        setGamesPerMatch(prefs.getPrefInt(FPref.UI_MATCHES_PER_GAME))
        setWarnAboutAICards(false)
    }

    fun leaveMatch() {
        val current = _match.value ?: return
        _match.value = null
        PromptHost.cancelAll()
        ThreadUtil.invokeInGameThread { runCatching { current.hostedMatch?.endCurrentGame() } }
    }
}
