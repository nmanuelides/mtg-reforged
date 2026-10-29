package io.github.nmanuelides.mtgreforged.ui.components

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
 * The right-click menu of a card (Windows): copy its picture to the clipboard, or save it to a
 * file the player picks. Place it in the Box that holds the card, so it opens over it.
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
            text = { Text("Copy image", style = MaterialTheme.typography.bodyLarge, color = Palette.Ink) },
            leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = Palette.Ink) },
            onClick = {
                onDismiss()
                withFile { file ->
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { context.copyImageToClipboard(file) }
                        context.showToast(if (ok) "Image copied to the clipboard" else "Couldn't copy the image")
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

/** [action] as a right-click handler on Windows; null on Android, where a right-click keeps holding the card. */
fun rightClick(action: () -> Unit): (() -> Unit)? = if (IS_DESKTOP) action else null
