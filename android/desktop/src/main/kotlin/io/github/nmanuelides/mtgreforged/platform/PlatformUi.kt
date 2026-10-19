package io.github.nmanuelides.mtgreforged.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

val LocalAppContext: ProvidableCompositionLocal<AppContext> = staticCompositionLocalOf { error("No DesktopContext provided") }

/**
 * Android's back button, on a PC: Esc (or the mouse's back button) goes to the most recently
 * shown enabled handler, exactly like Android's OnBackPressedDispatcher.
 */
object BackDispatcher {
    internal class Entry(var enabled: Boolean, var onBack: () -> Unit)

    private val entries = mutableListOf<Entry>()

    internal fun add(entry: Entry) = synchronized(entries) { entries += entry }
    internal fun remove(entry: Entry) = synchronized(entries) { entries -= entry }

    /** True if a screen took the back press. */
    fun back(): Boolean {
        val entry = synchronized(entries) { entries.lastOrNull { it.enabled } } ?: return false
        entry.onBack()
        return true
    }
}

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val current by rememberUpdatedState(onBack)
    val entry = remember { BackDispatcher.Entry(enabled) { current() } }
    entry.enabled = enabled
    DisposableEffect(entry) {
        BackDispatcher.add(entry)
        onDispose { BackDispatcher.remove(entry) }
    }
}

/** Android keeps the screen on during matches; Windows' own power settings decide here. */
@Composable
fun KeepScreenOn() = Unit

/** Height of the window in dp. */
@Composable
fun screenHeightDp(): Float = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp().value }

private val images = HashMap<ResId, ImageBitmap>()

/** A bundled image, decoded once and kept (they're small and used all the time). */
fun bundledImage(id: ResId): ImageBitmap = synchronized(images) {
    images.getOrPut(id) { DesktopContext.default().loadImageRes(id) ?: error("Can't decode $id") }
}

@Composable
fun imageRes(id: ResId): ImageBitmap = remember(id) { bundledImage(id) }

@Composable
fun painterRes(id: ResId): Painter = remember(id) { BitmapPainter(bundledImage(id)) }

/** Short messages at the bottom of the window, like Android's toasts (drawn by Main.kt). */
object Toasts {
    var current by mutableStateOf<String?>(null)
        private set
    private var shownAt = 0L

    fun show(text: String) {
        MainThread.post {
            current = text
            val at = System.nanoTime().also { shownAt = it }
            MainThread.postDelayed(3500) { if (shownAt == at) current = null }
        }
    }
}

/** File-name patterns for the Windows file dialog, from MIME types. */
private fun patterns(types: Array<String>): String? {
    if (types.any { it == "*/*" } && types.size == 1) return null
    val ext = types.flatMap {
        when {
            it == "*/*" -> emptyList()
            it.startsWith("image/") -> listOf("png", "jpg", "jpeg", "webp", "bmp", "gif")
            it == "application/json" -> listOf("json")
            it == "text/plain" -> listOf("txt", "dck", "dec")
            else -> emptyList()
        }
    }.distinct()
    return ext.takeIf { it.isNotEmpty() }?.joinToString(";") { "*.$it" }
}

private val lastDir: String? get() = System.getProperty("user.home")?.let { File(it, "Documents").takeIf(File::isDirectory)?.path ?: it }

/**
 * Asks where to save a new file (suggested name passed to the returned function); [onPicked]
 * gets a way to open it for writing, or null if the user backed out.
 */
@Composable
fun rememberCreateDocument(mime: String, onPicked: ((() -> OutputStream?)?) -> Unit): (String) -> Unit {
    val callback by rememberUpdatedState(onPicked)
    return remember(mime) {
        { name ->
            val dialog = FileDialog(null as Frame?, "Save", FileDialog.SAVE).apply {
                directory = lastDir
                file = name
            }
            dialog.isVisible = true
            val picked = dialog.file?.let { File(dialog.directory, it) }
            callback(picked?.let { f -> { FileOutputStream(f) } })
        }
    }
}

/** Asks for a file to open (of the given MIME types); [onPicked] gets a way to read it, or null. */
@Composable
fun rememberOpenDocument(onPicked: ((() -> InputStream?)?) -> Unit): (Array<String>) -> Unit {
    val callback by rememberUpdatedState(onPicked)
    return remember {
        { types ->
            val dialog = FileDialog(null as Frame?, "Open", FileDialog.LOAD).apply {
                directory = lastDir
                patterns(types)?.let { file = it }
            }
            dialog.isVisible = true
            val picked = dialog.file?.let { File(dialog.directory, it) }?.takeIf { it.isFile }
            callback(picked?.let { f -> { FileInputStream(f) } })
        }
    }
}

/** Downloads show their progress in the app itself on Windows; nothing to ask for. */
@Composable
fun rememberNotificationPermission(): () -> Unit = remember { {} }
