package io.github.nmanuelides.mtgreforged

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nmanuelides.mtgreforged.engine.CardPrices
import io.github.nmanuelides.mtgreforged.engine.Engine
import io.github.nmanuelides.mtgreforged.engine.Favorites
import io.github.nmanuelides.mtgreforged.engine.News
import io.github.nmanuelides.mtgreforged.engine.PromptHost
import io.github.nmanuelides.mtgreforged.engine.SavedGame
import io.github.nmanuelides.mtgreforged.engine.SavedGames
import io.github.nmanuelides.mtgreforged.engine.UserDecks
import io.github.nmanuelides.mtgreforged.platform.AppContext
import io.github.nmanuelides.mtgreforged.platform.LocalAppContext
import io.github.nmanuelides.mtgreforged.ui.components.LandingArt
import io.github.nmanuelides.mtgreforged.ui.components.OrnateButton
import io.github.nmanuelides.mtgreforged.ui.components.OrnateDialog
import io.github.nmanuelides.mtgreforged.ui.components.OrnateStyle
import io.github.nmanuelides.mtgreforged.ui.components.SectionLabel
import io.github.nmanuelides.mtgreforged.ui.decks.DeckBuilderScreen
import io.github.nmanuelides.mtgreforged.ui.home.BootScreen
import io.github.nmanuelides.mtgreforged.ui.home.DeckBrowserScreen
import io.github.nmanuelides.mtgreforged.ui.home.HomeScreen
import io.github.nmanuelides.mtgreforged.ui.home.OnlineScreen
import io.github.nmanuelides.mtgreforged.ui.home.UpdateUi
import io.github.nmanuelides.mtgreforged.ui.match.MatchScreen
import io.github.nmanuelides.mtgreforged.ui.prompts.PromptDialog
import io.github.nmanuelides.mtgreforged.ui.theme.MtgTheme
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import io.github.nmanuelides.mtgreforged.ui.theme.ThemeMode
import io.github.nmanuelides.mtgreforged.update.AppUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Everything the app shows, from the boot screen to matches; the same on every platform.
 * Hosts wrap it in [MtgTheme] (MainActivity on Android, the window on Windows).
 */
@Composable
fun ReforgedApp(model: AppModel) {
    val status by Engine.status.collectAsStateWithLifecycle()
    val match by Engine.match.collectAsStateWithLifecycle()
    val prompt by PromptHost.current.collectAsStateWithLifecycle()
    val update by model.update.collectAsStateWithLifecycle()
    val decks by model.decks.collectAsStateWithLifecycle()
    LaunchedEffect(status) { if (status == Engine.Status.Ready) model.loadDecks() }

    var format by rememberSaveable { mutableStateOf(Engine.Format.Commander) }
    var selections by remember { mutableStateOf<Map<Int, Engine.DeckEntry>>(emptyMap()) }
    var browsing by remember { mutableStateOf<Pair<Engine.Format, Int>?>(null) }
    // Deck editor: which deck (null = new) and whether to open on the importer.
    var collecting by remember { mutableStateOf(false) }
    var friends by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Triple<Engine.Format, Engine.DeckEntry?, Boolean>?>(null) }
    var deleting by remember { mutableStateOf<Pair<Engine.Format, Engine.DeckEntry>?>(null) }

    // The boot window shows the same art the landing will open on, then grows
    // into it ("stepping through the window"). Later landings rotate the art.
    val context = LocalAppContext.current
    var landingArt by remember { mutableStateOf(LandingArt.next(context)) }
    var saved by remember { mutableStateOf<SavedGame?>(null) }
    fun nextLanding() {
        landingArt = LandingArt.next(context)
        saved = SavedGames.load()
    }
    LaunchedEffect(status) { if (status == Engine.Status.Ready) saved = SavedGames.load() }
    var booted by remember { mutableStateOf(Engine.status.value == Engine.Status.Ready) }
    var bootWindow by remember { mutableStateOf<Rect?>(null) }
    var landingArea by remember { mutableStateOf<Rect?>(null) }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(status, landingArea) {
        if (booted || status != Engine.Status.Ready || landingArea == null || bootWindow == null) return@LaunchedEffect
        delay(250)
        reveal.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        booted = true
    }

    val game = match
    when {
        game != null -> MatchScreen(game, onLeave = { Engine.leaveMatch(); nextLanding() })
        friends -> OnlineScreen(format, decks[format].orEmpty()) { friends = false }
        collecting -> DeckBuilderScreen(Engine.Format.Constructed, null, false, collection = true) { collecting = false }
        editing != null -> {
            val (fmt, entry, importFirst) = editing!!
            DeckBuilderScreen(fmt, entry, importFirst) { saved ->
                editing = null
                // Freshly saved decks become the player's pick for that slot.
                val slot = browsing?.second
                if (saved != null && slot != null) selections = selections + (slot to saved)
            }
        }
        browsing != null -> {
            val (fmt, slot) = browsing!!
            DeckBrowserScreen(
                title = if (slot < 0) "Your deck" else "Opponent ${slot + 1}",
                decks = decks[fmt].orEmpty(),
                format = fmt,
                initial = selections[slot] ?: Favorites.of(fmt, decks[fmt].orEmpty()).takeIf { slot < 0 },
                onSelect = { selections = selections + (slot to it); browsing = null; nextLanding() },
                onBack = { browsing = null; nextLanding() },
                onNew = { editing = Triple(fmt, null, false) },
                onImport = { editing = Triple(fmt, null, true) },
                onEdit = { editing = Triple(fmt, it, false) },
                onDelete = { deleting = fmt to it },
            )
        }
        else -> Box(Modifier.fillMaxSize()) {
            // The landing sits underneath the boot screen while the window opens,
            // in the same spot it keeps afterwards, so its chrome can animate in.
            if (status == Engine.Status.Ready) {
                HomeScreen(
                    decksFor = { decks[it].orEmpty() },
                    update = update,
                    onInstallUpdate = model::installUpdate,
                    onCheckUpdate = model::checkForUpdateNow,
                    onBrowseDecks = { fmt, slot, _ -> browsing = fmt to slot },
                    selections = selections,
                    onStart = Engine::startMatch,
                    format = format,
                    onFormat = { format = it; selections = emptyMap() },
                    art = landingArt,
                    saved = saved,
                    onContinue = {
                        saved?.let { game ->
                            SavedGames.delete()
                            saved = null
                            Engine.resume(game)
                        }
                    },
                    chromeVisible = booted,
                    onArtBounds = { landingArea = it },
                    onCollection = { collecting = true },
                    onFriends = { friends = true },
                )
            }
            if (!booted) {
                BootScreen(
                    status, onRetry = { Engine.start(context) }, art = landingArt,
                    update = update, onCheckUpdate = model::checkForUpdate, onInstallUpdate = model::installUpdate,
                    reveal = reveal.value, window = bootWindow, target = landingArea,
                    onWindowBounds = { if (reveal.value == 0f) bootWindow = it },
                )
            }
        }
    }
    prompt?.let { PromptDialog(it) }
    deleting?.let { (fmt, entry) ->
        OrnateDialog(onDismiss = { deleting = null }) {
            SectionLabel("Delete deck")
            Spacer(Modifier.height(12.dp))
            Text("Delete \"${entry.name}\"? This can't be undone.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OrnateButton("Keep", { deleting = null }, Modifier.weight(1f), accent = Palette.Slate)
                OrnateButton("Delete", {
                    UserDecks.delete(entry, fmt)
                    selections = selections.filterValues { it != entry }
                    deleting = null
                }, Modifier.weight(1f), style = OrnateStyle.Primary)
            }
        }
    }
}

/** App-wide state behind [ReforgedApp]: decks, updates, prices and news. */
class AppModel(app: AppContext, private val scope: CoroutineScope) {
    private val updater = AppUpdater(app)
    private var release: AppUpdater.Release? = null

    private val _update = MutableStateFlow<UpdateUi>(UpdateUi.None)
    val update: StateFlow<UpdateUi> = _update.asStateFlow()

    init {
        // Card Kingdom prices: load the saved list, refresh it daily on Wi-Fi.
        scope.launch(Dispatchers.IO) { CardPrices.refresh(app) }
        scope.launch(Dispatchers.IO) { News.refresh(app) }
    }

    private val _decks = MutableStateFlow<Map<Engine.Format, List<Engine.DeckEntry>>>(emptyMap())
    val decks: StateFlow<Map<Engine.Format, List<Engine.DeckEntry>>> = _decks.asStateFlow()

    fun loadDecks() {
        if (_decks.value.isNotEmpty()) return
        // Reload whenever the player's own decks change (save / import / delete).
        scope.launch(Dispatchers.IO) {
            Engine.decksVersion.collect { _decks.value = Engine.Format.entries.associateWith(Engine::decks) }
        }
    }

    /** Checks right now and returns what it found (the profile's Check for updates). */
    suspend fun checkForUpdateNow(): UpdateUi {
        if (_update.value is UpdateUi.Downloading) return _update.value
        release = runCatching { updater.check() }.getOrNull()
        _update.value = release?.let { UpdateUi.Available(it.name, it.notes) } ?: UpdateUi.None
        return _update.value
    }

    fun checkForUpdate() {
        if (_update.value is UpdateUi.Downloading) return
        scope.launch {
            release = runCatching { updater.check() }.getOrNull()
            _update.value = release?.let { UpdateUi.Available(it.name, it.notes) } ?: UpdateUi.None
        }
    }

    fun installUpdate() {
        val target = release ?: return
        if (!updater.canInstall()) {
            updater.openInstallPermissionSettings()
            return
        }
        scope.launch {
            runCatching {
                val apk = updater.download(target) { _update.value = UpdateUi.Downloading(it) }
                updater.install(apk)
                _update.value = UpdateUi.Available(target.name, target.notes)
            }.onFailure { _update.value = UpdateUi.Failed(it.message ?: "error") }
        }
    }
}
