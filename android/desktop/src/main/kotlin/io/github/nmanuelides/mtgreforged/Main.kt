package io.github.nmanuelides.mtgreforged

import io.github.nmanuelides.mtgreforged.platform.ArrowDispatcher
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import java.awt.GraphicsEnvironment
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.nmanuelides.mtgreforged.engine.Connectivity
import io.github.nmanuelides.mtgreforged.engine.Engine
import io.github.nmanuelides.mtgreforged.engine.Favorites
import io.github.nmanuelides.mtgreforged.engine.Profiles
import io.github.nmanuelides.mtgreforged.platform.BackDispatcher
import io.github.nmanuelides.mtgreforged.platform.DesktopContext
import io.github.nmanuelides.mtgreforged.platform.LocalAppContext
import io.github.nmanuelides.mtgreforged.platform.Log
import io.github.nmanuelides.mtgreforged.platform.Toasts
import io.github.nmanuelides.mtgreforged.platform.loadImageRes
import io.github.nmanuelides.mtgreforged.platform.painterRes
import io.github.nmanuelides.mtgreforged.ui.components.PaperTexture
import io.github.nmanuelides.mtgreforged.ui.theme.MtgTheme
import io.github.nmanuelides.mtgreforged.ui.theme.ThemeMode
import kotlin.system.exitProcess

/** MTG Reforged for Windows: the Android app's screens in a desktop window. */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    Thread.setDefaultUncaughtExceptionHandler { thread, e -> Log.e("Crash", "Uncaught in ${thread.name}", e) }
    val context = DesktopContext.default()
    if ("--smoke-test" in args) smokeTest(context)
    PaperTexture.load(context)
    Connectivity.watch(context)
    Profiles.init(context)
    Favorites.init(context)
    ThemeMode.init(context, systemDark = false)
    Engine.start(context)

    application {
        val window = rememberWindowState(placement = startPlacement(), size = startSize(), position = WindowPosition(Alignment.Center))
        Window(
            onCloseRequest = {
                exitApplication()
                // Forge's game and AI threads don't stop on their own.
                exitProcess(0)
            },
            state = window,
            title = "MTG Reforged",
            icon = painterRes("icon/icon-source.png"),
            onPreviewKeyEvent = { event ->
                when {
                    event.type != KeyEventType.KeyDown -> false
                    // Esc is Android's back button.
                    event.key == Key.Escape -> BackDispatcher.back()
                    event.key == Key.F11 -> {
                        window.placement = if (window.placement == WindowPlacement.Fullscreen) WindowPlacement.Maximized else WindowPlacement.Fullscreen
                        true
                    }
                    else -> false
                }
            },
            // After the focused element had its turn: a text field keeps the arrows for its cursor.
            onKeyEvent = { event ->
                when {
                    event.type != KeyEventType.KeyDown -> false
                    event.key == Key.DirectionLeft -> ArrowDispatcher.arrow(-1, 0)
                    event.key == Key.DirectionRight -> ArrowDispatcher.arrow(1, 0)
                    event.key == Key.DirectionUp -> ArrowDispatcher.arrow(0, -1)
                    event.key == Key.DirectionDown -> ArrowDispatcher.arrow(0, 1)
                    else -> false
                }
            },
        ) {
            val scope = rememberCoroutineScope()
            val model = remember { AppModel(context, scope) }
            // Android checks for updates on every return to the app; here, whenever the window gets focus.
            val focused = LocalWindowInfo.current.isWindowFocused
            LaunchedEffect(focused) { if (focused) model.checkForUpdate() }

            CompositionLocalProvider(LocalAppContext provides context) {
                MtgTheme {
                    Box(
                        Modifier.fillMaxSize().onPointerEvent(PointerEventType.Press) {
                            // The mouse's back button works like Esc.
                            if (it.button == PointerButton.Back) BackDispatcher.back()
                        },
                    ) {
                        ReforgedApp(model)
                        ToastHost(Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }
    }
}

/** Screens up to Full HD open maximized; bigger ones get a centred 1920 × 1080 window. */
private fun bigScreen(): Boolean = runCatching {
    val mode = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.displayMode
    mode.width > 1920 || mode.height > 1080
}.getOrDefault(false)

private fun startPlacement() = if (bigScreen()) WindowPlacement.Floating else WindowPlacement.Maximized

/** 1920 × 1080 real pixels, in dp (Windows display scaling makes a dp bigger than a pixel). */
private fun startSize(): DpSize {
    if (!bigScreen()) return DpSize(1366.dp, 860.dp)
    val scale = runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX
    }.getOrDefault(1.0).toFloat().coerceAtLeast(1f)
    return DpSize((1920 / scale).dp, (1080 / scale).dp)
}

/**
 * Boots the engine without a window and exits 0 if it gets ready (CI and quick checks):
 * card data installed, Forge loaded, decks listed, bundled art decodes.
 */
private fun smokeTest(context: DesktopContext): Nothing {
    Engine.start(context)
    val deadline = System.currentTimeMillis() + 15 * 60_000
    while (System.currentTimeMillis() < deadline) {
        when (val status = Engine.status.value) {
            Engine.Status.Ready -> {
                val decks = Engine.Format.entries.associateWith { Engine.decks(it).size }
                val art = context.loadImageRes(R.drawable.paper_texture) != null
                // HTTPS must work with the trimmed Java runtime (card images, prices, updates);
                // being offline is fine, a TLS failure is not.
                val tls = runCatching { java.net.URL("https://api.github.com").openStream().close() }
                    .exceptionOrNull().let { it == null || it !is javax.net.ssl.SSLException }
                Log.i("Smoke", "Ready. Decks: $decks, bundled art: $art, TLS: $tls")
                exitProcess(if (decks.values.all { it > 0 } && art && tls) 0 else 1)
            }
            is Engine.Status.Failed -> {
                Log.e("Smoke", "Boot failed: ${status.message}")
                exitProcess(1)
            }
            else -> Thread.sleep(250)
        }
    }
    Log.e("Smoke", "Boot timed out at ${Engine.status.value}")
    exitProcess(1)
}

/** Android-style toasts: a dark pill near the bottom that fades away. */
@Composable
private fun ToastHost(modifier: Modifier) {
    val text = Toasts.current
    AnimatedVisibility(text != null, modifier.padding(bottom = 48.dp), enter = fadeIn(), exit = fadeOut()) {
        Text(
            text.orEmpty(),
            Modifier
                .widthIn(max = 520.dp)
                .background(Color(0xE6323232), RoundedCornerShape(22.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            color = Color.White,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}
