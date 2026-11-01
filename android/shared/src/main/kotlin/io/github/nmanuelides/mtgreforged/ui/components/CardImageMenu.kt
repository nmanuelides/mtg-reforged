package io.github.nmanuelides.mtgreforged.ui.components

import io.github.nmanuelides.mtgreforged.platform.shareImageFile
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.nmanuelides.mtgreforged.engine.CardImages
import io.github.nmanuelides.mtgreforged.match.CardUi
import io.github.nmanuelides.mtgreforged.platform.IS_DESKTOP
import io.github.nmanuelides.mtgreforged.platform.LocalAppContext
import io.github.nmanuelides.mtgreforged.platform.copyImageToClipboard
import io.github.nmanuelides.mtgreforged.platform.rememberCreateDocument
import io.github.nmanuelides.mtgreforged.platform.showToast
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The picture menu of a card (right-click on Windows, long press on Android): copy (Windows) or
 * share (Android) its picture, or save it to a file the player picks. Place it in the Box that
 * holds the card, so it opens over it.
 */
@Composable
fun CardImageMenu(card: CardUi, open: Boolean, onDismiss: () -> Unit) {
    val context = LocalAppContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf<File?>(null) }
    val save = rememberCreateDocument("image/jpeg") { opener ->
        val file = saving ?: return@rememberCreateDocument
        saving = null
        if (opener == null) return@rememberCreateDocument
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { opener()?.use { out -> file.inputStream().use { it.copyTo(out) } } != null }.getOrDefault(false) }
            context.showToast(if (ok) "Image saved" else "Couldn't save the image")
        }
    }
    // The picture may not be on disk yet (never shown, or only in the memory cache).
    val withFile: ((File) -> Unit) -> Unit = { action ->
        val key = card.imageKey
        scope.launch {
            val file = key?.let { CardImages.file(it) }
            if (file == null) context.showToast("The image isn't available") else action(file)
        }
    }
    DropdownMenu(open, onDismiss, containerColor = Palette.PaperLight) {
        DropdownMenuItem(
            text = { Text(if (IS_DESKTOP) "Copy image" else "Share image", style = MaterialTheme.typography.bodyLarge, color = Palette.Ink) },
            leadingIcon = { Icon(if (IS_DESKTOP) Icons.Default.ContentCopy else Icons.Default.Share, null, tint = Palette.Ink) },
            onClick = {
                onDismiss()
                withFile { file ->
                    scope.launch {
                        val name = card.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                        runCatching { withContext(Dispatchers.IO) { context.shareImageFile(file, name) } }
                            .onFailure { context.showToast("Couldn't share the image") }
                    }
                }
            },
        )
        DropdownMenuItem(
            text = { Text("Save image as…", style = MaterialTheme.typography.bodyLarge, color = Palette.Ink) },
            leadingIcon = { Icon(Icons.Default.Download, null, tint = Palette.Ink) },
            onClick = {
                onDismiss()
                withFile { file ->
                    saving = file
                    save(card.name.replace(Regex("[\\\\/:*?\"<>|]"), "_") + "." + file.extension.ifBlank { "jpg" })
                }
            },
        )
    }
}

/**
 * Android: holding a finger still on the card calls [onLongPress] (to open [CardImageMenu]); a
 * swipe or a quick tap goes through untouched. Windows uses right-click instead.
 */
fun Modifier.holdForMenu(onLongPress: () -> Unit): Modifier = if (IS_DESKTOP) this else composed {
    val current by rememberUpdatedState(onLongPress)
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var ended = false
            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation()
                ended = true
            }
            if (!ended) {
                current()
                // The release mustn't also count as a tap.
                do {
                    val event = awaitPointerEvent()
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }
    }
}

/** [action] as a right-click handler on Windows; null on Android, where a right-click keeps holding the card. */
fun rightClick(action: () -> Unit): (() -> Unit)? = if (IS_DESKTOP) action else null
