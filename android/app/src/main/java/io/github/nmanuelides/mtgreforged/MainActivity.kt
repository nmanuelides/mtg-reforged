package io.github.nmanuelides.mtgreforged

import android.app.Application
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.nmanuelides.mtgreforged.engine.Connectivity
import io.github.nmanuelides.mtgreforged.engine.Engine
import io.github.nmanuelides.mtgreforged.engine.Favorites
import io.github.nmanuelides.mtgreforged.engine.Profiles
import io.github.nmanuelides.mtgreforged.ui.components.PaperTexture
import io.github.nmanuelides.mtgreforged.ui.theme.MtgTheme
import io.github.nmanuelides.mtgreforged.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {

    private val model: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark status-bar icons over the parchment.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        PaperTexture.load(this)
        Connectivity.watch(this)
        Profiles.init(this)
        Favorites.init(this)
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
                ReforgedApp(model.app)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.app.checkForUpdate()
    }
}

/** Keeps the [AppModel] across configuration changes (rotation). */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    val app = AppModel(application, viewModelScope)
}
