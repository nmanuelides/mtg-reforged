package io.github.nmanuelides.mtgreforged

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.nmanuelides.mtgreforged.engine.Engine
import io.github.nmanuelides.mtgreforged.engine.PromptHost
import io.github.nmanuelides.mtgreforged.ui.home.BootScreen
import io.github.nmanuelides.mtgreforged.ui.home.HomeScreen
import io.github.nmanuelides.mtgreforged.ui.home.UpdateUi
import io.github.nmanuelides.mtgreforged.ui.match.MatchScreen
import io.github.nmanuelides.mtgreforged.ui.prompts.PromptDialog
import io.github.nmanuelides.mtgreforged.ui.theme.MtgTheme
import io.github.nmanuelides.mtgreforged.update.AppUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val model: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Engine.start(this)

        setContent {
            MtgTheme {
                val status by Engine.status.collectAsStateWithLifecycle()
                val match by Engine.match.collectAsStateWithLifecycle()
                val prompt by PromptHost.current.collectAsStateWithLifecycle()
                val update by model.update.collectAsStateWithLifecycle()

                val game = match
                when {
                    status != Engine.Status.Ready -> BootScreen(status) { Engine.start(this) }
                    game != null -> MatchScreen(game, onLeave = Engine::leaveMatch)
                    else -> HomeScreen(
                        decks = model.decks,
                        update = update,
                        onInstallUpdate = model::installUpdate,
                        onStart = Engine::startMatch,
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

    val decks by lazy { Engine.preconDecks() }

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
