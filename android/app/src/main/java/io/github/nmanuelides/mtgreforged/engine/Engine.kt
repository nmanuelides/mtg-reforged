package io.github.nmanuelides.mtgreforged.engine

import android.content.Context
import android.os.SystemClock
import android.util.Log
import forge.deck.Deck
import forge.deck.io.DeckSerializer
import forge.game.GameRules
import forge.game.GameState
import forge.game.GameType
import forge.game.player.RegisteredPlayer
import forge.gui.GuiBase
import forge.gui.interfaces.IProgressBar
import forge.localinstance.properties.ForgeConstants
import forge.localinstance.properties.ForgePreferences.FPref
import forge.model.FModel
import forge.player.GamePlayerUtil
import forge.sound.SoundEffectType
import forge.sound.SoundSystem
import forge.util.ThreadUtil
import io.github.nmanuelides.mtgreforged.match.ComposeGuiGame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.Locale

/** Boots the Forge engine once per process and starts matches. */
object Engine {

    sealed interface Status {
        data object NotStarted : Status
        data class InstallingData(val progress: Float) : Status
        data class Loading(val step: String, val progress: Float) : Status
        data object Ready : Status
        data class Failed(val message: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.NotStarted)
    val status: StateFlow<Status> = _status.asStateFlow()

    /**
     * Progress updates arrive from several threads (and Forge queues some onto the UI
     * thread), so a late one could land after Ready and leave the boot screen up forever
     * on slower devices. Once Ready, progress updates are ignored.
     */
    private fun reportProgress(status: Status) {
        _status.update { current -> if (current == Status.Ready) current else status }
    }

    private lateinit var gui: AndroidGui

    private val _match = MutableStateFlow<ComposeGuiGame?>(null)
    val match: StateFlow<ComposeGuiGame?> = _match.asStateFlow()

    @Synchronized
    fun start(context: Context) {
        if (_status.value != Status.NotStarted && _status.value !is Status.Failed) return
        _status.value = Status.Loading("Preparing…", 0f)
        val app = context.applicationContext
        Thread({
            runCatching { boot(app) }.onFailure {
                Log.e("Engine", "Boot failed", it)
                _status.value = Status.Failed(it.message ?: it.toString())
            }
        }, "Game Boot").start()
    }

    /**
     * Boot progress, always moving forward. Installing the card data has real byte counts;
     * Forge's own start-up only reports detail for part of its work, so in between the bar
     * follows how long the previous boot took (capped below 100% until it really is ready).
     */
    private object BootProgress {
        private const val PREFS = "boot"
        private const val KEY_ENGINE_MS = "engineMs"
        @Volatile var overall = 0f
            private set
        @Volatile var engineStart = 0f
        @Volatile var forgeFraction = 0f
        @Volatile var step = "Preparing…"

        fun advance(value: Float) {
            overall = maxOf(overall, value.coerceIn(0f, 1f))
        }

        fun expectedEngineMs(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_ENGINE_MS, 15_000L).coerceAtLeast(3_000L)

        fun remember(context: Context, engineMs: Long) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_ENGINE_MS, engineMs).apply()
        }
    }

    private fun boot(context: Context) {
        val forgeDir = File(context.filesDir, "forge").apply { mkdirs() }
        val installer = EngineDataInstaller(context, forgeDir)
        if (!installer.isUpToDate()) {
            // Unpacking the data is the first ~40% of a fresh boot.
            installer.install {
                BootProgress.advance(it * 0.4f)
                reportProgress(Status.InstallingData(BootProgress.overall))
            }
            BootProgress.engineStart = 0.4f
        }

        GuiBase.setIsAndroid(true)
        GuiBase.setUsingAppDirectory(true)
        gui = AndroidGui(context, forgeDir.path + File.separator)
        GuiBase.setInterface(gui)

        BootProgress.step = "Loading cards…"
        val engineStartedAt = SystemClock.uptimeMillis()
        val expected = BootProgress.expectedEngineMs(context)
        val ticker = Thread({
            while (!Thread.currentThread().isInterrupted) {
                val byTime = (SystemClock.uptimeMillis() - engineStartedAt).toFloat() / expected
                val engine = minOf(0.95f, maxOf(byTime, BootProgress.forgeFraction * 0.6f))
                BootProgress.advance(BootProgress.engineStart + (1f - BootProgress.engineStart) * engine)
                reportProgress(Status.Loading(BootProgress.step, BootProgress.overall))
                try { Thread.sleep(100) } catch (e: InterruptedException) { break }
            }
        }, "Boot progress").apply { isDaemon = true; start() }

        FModel.initialize(ProgressBridge) { prefs ->
            if (Locale.getDefault().language == "es") prefs.setPref(FPref.UI_LANGUAGE, "es-ES")
            prefs.setPref(FPref.UI_ENABLE_ONLINE_IMAGE_FETCHER, true)
            // Forge otherwise interrupts the first match to ask for a name.
            if (prefs.getPref(FPref.PLAYER_NAME).isNullOrBlank()) prefs.setPref(FPref.PLAYER_NAME, "Jugador")
            // We draw our own UI; Forge's own deck-generation matrix only slows boot down.
            prefs.setPref(FPref.DECKGEN_CARDBASED, false)
            // Sound effects play through AndroidAudioClip; there is no music player (yet).
            prefs.setPref(FPref.UI_ENABLE_SOUNDS, true)
            prefs.setPref(FPref.UI_ENABLE_MUSIC, false)
            // Seconds the AI may spend choosing one play before it settles for what it has
            // (Forge's default 5 piles up into minutes on big multiplayer boards).
            prefs.setPref(FPref.MATCH_AI_TIMEOUT, "3")
            null
        }
        ticker.interrupt()
        ticker.join(1_000)
        BootProgress.remember(context, SystemClock.uptimeMillis() - engineStartedAt)
        BootProgress.advance(1f)
        // Warm up every sound effect so the first play of each isn't lost to async loading.
        Thread({ SoundEffectType.entries.forEach { runCatching { SoundSystem.instance.hasResource(it) } } }, "Sounds").start()
        _status.value = Status.Ready
    }

    private object ProgressBridge : IProgressBar {
        private var description = ""
        private var max = 0
        override fun setDescription(s0: String?) {
            description = s0.orEmpty()
            if (description.isNotBlank()) BootProgress.step = description
        }
        override fun setValue(progress: Int) {
            if (max > 0) BootProgress.forgeFraction = maxOf(BootProgress.forgeFraction, progress.toFloat() / max)
        }
        override fun reset() {}
        override fun setShowETA(b0: Boolean) {}
        override fun setShowCount(b0: Boolean) {}
        override fun setPercentMode(percentMode0: Boolean) {}
        override fun getMaximum() = max
        override fun setMaximum(maximum0: Int) { max = maximum0 }
    }

    // ---- Decks & matches ---------------------------------------------------------

    /** A deck on disk; [custom] marks the player's own (editable) decks. */
    data class DeckEntry(val name: String, val file: File, val custom: Boolean = false)

    enum class Format(val label: String, val maxPlayers: Int) {
        Constructed("Constructed", 4),
        Commander("Commander", 4);

        // GameType's static init needs the engine loaded, so don't touch it eagerly.
        val gameType: GameType
            get() = if (this == Commander) GameType.Commander else GameType.Constructed
    }

    private val deckCache = HashMap<Format, List<DeckEntry>>()

    /** The player's own decks first, then the preconstructed ones that ship with Forge. */
    @Synchronized
    fun decks(format: Format): List<DeckEntry> = deckCache.getOrPut(format) { UserDecks.list(format) + precons(format) }

    @Synchronized
    fun invalidateDecks(format: Format) {
        deckCache.remove(format)
        _decksVersion.value++
    }

    private val _decksVersion = MutableStateFlow(0)
    /** Bumps whenever the player's decks change, so lists can reload. */
    val decksVersion: StateFlow<Int> = _decksVersion.asStateFlow()

    private fun precons(format: Format): List<DeckEntry> = run {
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
    /** How the running match was set up, so it can be saved and rebuilt later. */
    private var currentFormat: Format? = null
    private var currentDecks: List<DeckEntry> = emptyList()

    fun startMatch(format: Format, player: DeckEntry, opponents: List<DeckEntry>) {
        require(opponents.isNotEmpty() && opponents.size < format.maxPlayers)
        launch(format, listOf(player) + opponents, aiNames = null, resume = null)
    }

    /** Rebuilds the saved table (same seats, decks and names) and restores its state. */
    fun resume(saved: SavedGame) {
        val decks = saved.seats.map { DeckEntry(it.deckName, File(it.deckPath)) }
        launch(saved.format, decks, aiNames = saved.seats.drop(1).map { it.name }, resume = saved)
    }

    /** True if the match can be saved right now (spells on the stack are saved too, see SavedGames). */
    fun canSave(): Boolean {
        _match.value?.gameView?.game ?: return false
        return currentFormat != null
    }

    fun saveMatch(): Boolean {
        val game = _match.value?.gameView?.game ?: return false
        val format = currentFormat ?: return false
        return runCatching { SavedGames.save(game, format, currentDecks) }
            .onFailure { Log.e("Engine", "Save failed", it) }
            .isSuccess
    }

    /** Seat 0 is the local player; the rest are AI opponents. */
    private fun launch(format: Format, decks: List<DeckEntry>, aiNames: List<String>?, resume: SavedGame?) {
        currentFormat = format
        currentDecks = decks
        ThreadUtil.invokeInGameThread {
            val variants = if (format == Format.Commander) setOf(GameType.Commander) else emptySet()
            fun register(entry: DeckEntry) = if (variants.isEmpty()) {
                RegisteredPlayer(loadDeck(entry))
            } else {
                RegisteredPlayer.forVariants(decks.size, variants, loadDeck(entry), null, false, null, null)
            }

            val human = register(decks[0]).apply { setPlayer(GamePlayerUtil.getGuiPlayer()) }
            val ais = decks.drop(1).mapIndexed { i, deck ->
                val name = aiNames?.getOrNull(i)
                register(deck).apply {
                    setPlayer(if (name != null) GamePlayerUtil.createAiPlayer(name) else GamePlayerUtil.createAiPlayer())
                }
            }
            val guiGame = gui.newGuiGame as ComposeGuiGame
            val hosted = gui.hostMatch()
            guiGame.hostedMatch = hosted
            if (resume != null) {
                // Breeze through "who starts" and mulligans, then lay the saved state over the new game.
                guiGame.autoStart = true
                hosted.setStartGameHook {
                    // Forge's parser chokes on blank lines (state text written on Windows / edited saves).
                    GameState().apply { parse(resume.state.lines().map { it.trim() }.filter { it.isNotBlank() }) }.applyToGame(hosted.game)
                }
            }
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
        // A network match also closes the lobby / connection.
        if (Online.lobby.value != null) Thread { Online.leave() }.start()
    }

    /** Network matches are created by Forge's lobby; their GUI announces itself here when it opens. */
    fun showMatch(game: ComposeGuiGame) {
        if (_match.value !== game) _match.value = game
    }
}
