package io.github.nmanuelides.mtgreforged.platform

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import java.io.InputStream
import java.io.OutputStream

val LocalAppContext: ProvidableCompositionLocal<AppContext> get() = LocalContext

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) = androidx.activity.compose.BackHandler(enabled, onBack)

/** The ← / → keys move carousels on Windows; phones have none. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun ArrowKeys(onArrow: (Int) -> Unit) = Unit

/** Keeps the screen awake while shown. */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** Height of the screen (the window, on desktop) in dp. */
@Composable
fun screenHeightDp(): Float = LocalConfiguration.current.screenHeightDp.toFloat()

@Composable
fun imageRes(id: ResId): ImageBitmap = ImageBitmap.imageResource(id)

@Composable
fun painterRes(id: ResId): Painter = painterResource(id)

/**
 * Asks where to save a new file (suggested name passed to the returned function); [onPicked]
 * gets a way to open it for writing, or null if the user backed out.
 */
@Composable
fun rememberCreateDocument(mime: String, onPicked: ((() -> OutputStream?)?) -> Unit): (String) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
        onPicked(uri?.let { { context.contentResolver.openOutputStream(it) } })
    }
    return { name -> launcher.launch(name) }
}

/** Asks for a file to open (of the given MIME types); [onPicked] gets a way to read it, or null. */
@Composable
fun rememberOpenDocument(onPicked: ((() -> InputStream?)?) -> Unit): (Array<String>) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        onPicked(uri?.let { { context.contentResolver.openInputStream(it) } })
    }
    return { types -> launcher.launch(types) }
}

/** Asks (once, on Android 13+) to show the download progress notification. */
@Composable
fun rememberNotificationPermission(): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return { if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}
