package io.github.nmanuelides.mtgreforged

import android.app.Application
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import io.github.nmanuelides.mtgreforged.ui.components.LandingArt
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.nmanuelides.mtgreforged.engine.Engine
import io.github.nmanuelides.mtgreforged.engine.PromptHost
import io.github.nmanuelides.mtgreforged.ui.home.BootScreen
import io.github.nmanuelides.mtgreforged.ui.home.DeckBrowserScreen
import io.github.nmanuelides.mtgreforged.ui.home.HomeScreen
import io.github.nmanuelides.mtgreforged.ui.home.UpdateUi
import io.github.nmanuelides.mtgreforged.ui.match.MatchScreen
import io.github.nmanuelides.mtgreforged.ui.prompts.PromptDialog
import io.github.nmanuelides.mtgreforged.ui.theme.MtgTheme
import io.github.nmanuelides.mtgreforged.ui.theme.ThemeMode
import io.github.nmanuelides.mtgreforged.update.AppUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val model: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark status-bar icons over the parchment.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        ThemeMode.init(this, systemDark)
        Engine.start(this)

        setContent {
            MtgTheme {
                // Status/navigation bar icons must contrast with the paper in either theme.
                LaunchedEffect(ThemeMode.dark) {
                    val transparent = android.graphics.Color.TRANSPARENT
                    val style = if (ThemeMode.dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                val status by Engine.status.collectAsStateWithLifecycle()
                val match by Engine.match.collectAsStateWithLifecycle()
                val prompt by PromptHost.current.collectAsStateWithLifecycle()
                val update by model.update.collectAsStateWithLifecycle()
                val decks by model.decks.collectAsStateWithLifecycle()
                LaunchedEffect(status) { if (status == Engine.Status.Ready) model.loadDecks() }

                var format by rememberSaveable { mutableStateOf(Engine.Format.Commander) }
                var selections by remember { mutableStateOf<Map<Int, Engine.DeckEntry>>(emptyMap()) }
                var browsing by remember { mutableStateOf<Pair<Engine.Format, Int>?>(null) }

                // The boot window shows the same art the landing will open on, then grows
                // into it ("stepping through the window"). Later landings rotate the art.
                val context = LocalContext.current
                var landingArt by remember { mutableStateOf(LandingArt.next(context)) }
                fun nextLanding() { landingArt = LandingArt.next(context) }
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
                    browsing != null -> {
                        val (fmt, slot) = browsing!!
                        DeckBrowserScreen(
                            title = if (slot < 0) "Your deck" else "Opponent ${slot + 1}",
                            decks = decks[fmt].orEmpty(),
                            initial = selections[slot],
                            onSelect = { selections = selections + (slot to it); browsing = null; nextLanding() },
                            onBack = { browsing = null; nextLanding() },
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
                                onBrowseDecks = { fmt, slot, _ -> browsing = fmt to slot },
                                selections = selections,
                                onStart = Engine::startMatch,
                                format = format,
                                onFormat = { format = it; selections = emptyMap() },
                                art = landingArt,
                                chromeVisible = booted,
                                onArtBounds = { landingArea = it },
                            )
                        }
                        if (!booted) {
                            BootScreen(
                                status, onRetry = { Engine.start(this@MainActivity) }, art = landingArt,
                                reveal = reveal.value, window = bootWindow, target = landingArea,
                                onWindowBounds = { if (reveal.value == 0f) bootWindow = it },
                            )
                        }
                    }
                }
                prompt?.let { PromptDialog(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.checkForUpdate()
    }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val updater = AppUpdater(app)
    private var release: AppUpdater.Release? = null

    private val _update = MutableStateFlow<UpdateUi>(UpdateUi.None)
    val update: StateFlow<UpdateUi> = _update.asStateFlow()

    private val _decks = MutableStateFlow<Map<Engine.Format, List<Engine.DeckEntry>>>(emptyMap())
    val decks: StateFlow<Map<Engine.Format, List<Engine.DeckEntry>>> = _decks.asStateFlow()

    fun loadDecks() {
        if (_decks.value.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _decks.value = Engine.Format.entries.associateWith(Engine::decks)
        }
    }

    fun checkForUpdate() {
        if (_update.value is UpdateUi.Downloading) return
        viewModelScope.launch {
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
        viewModelScope.launch {
            runCatching {
                val apk = updater.download(target) { _update.value = UpdateUi.Downloading(it) }
                updater.install(apk)
                _update.value = UpdateUi.Available(target.name, target.notes)
            }.onFailure { _update.value = UpdateUi.Failed(it.message ?: "error") }
        }
    }
}
