package io.github.nmanuelides.mtgreforged

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
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
        Engine.start(this)

        setContent {
            MtgTheme {
                val status by Engine.status.collectAsStateWithLifecycle()
                val match by Engine.match.collectAsStateWithLifecycle()
                val prompt by PromptHost.current.collectAsStateWithLifecycle()
                val update by model.update.collectAsStateWithLifecycle()
                val decks by model.decks.collectAsStateWithLifecycle()
                LaunchedEffect(status) { if (status == Engine.Status.Ready) model.loadDecks() }

                var format by rememberSaveable { mutableStateOf(Engine.Format.Commander) }
                var selections by remember { mutableStateOf<Map<Int, Engine.DeckEntry>>(emptyMap()) }
                var browsing by remember { mutableStateOf<Pair<Engine.Format, Int>?>(null) }

                val game = match
                when {
                    status != Engine.Status.Ready -> BootScreen(status) { Engine.start(this) }
                    game != null -> MatchScreen(game, onLeave = Engine::leaveMatch)
                    browsing != null -> {
                        val (fmt, slot) = browsing!!
                        DeckBrowserScreen(
                            title = if (slot < 0) "Your deck" else "Opponent ${slot + 1}",
                            decks = decks[fmt].orEmpty(),
                            initial = selections[slot],
                            onSelect = { selections = selections + (slot to it); browsing = null },
                            onBack = { browsing = null },
                        )
                    }
                    else -> HomeScreen(
                        decksFor = { decks[it].orEmpty() },
                        update = update,
                        onInstallUpdate = model::installUpdate,
                        onBrowseDecks = { fmt, slot, _ -> browsing = fmt to slot },
                        selections = selections,
                        onStart = Engine::startMatch,
                        format = format,
                        onFormat = { format = it; selections = emptyMap() },
                    )
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
