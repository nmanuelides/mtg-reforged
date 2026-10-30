package io.github.nmanuelides.mtgreforged.ui.components

import io.github.nmanuelides.mtgreforged.platform.IS_DESKTOP
import io.github.nmanuelides.mtgreforged.platform.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * On Windows, Esc closes the modal on top (calls [onDismiss]) instead of reaching the screen
 * behind it, which would treat it as "back" and leave. Call it inside the modal's content.
 */
@Composable
fun EscCloses(onDismiss: () -> Unit) {
    if (IS_DESKTOP) BackHandler(onBack = onDismiss)
}

/** A parchment card with the double rule, used for every dialog in the app. */
@Composable
fun OrnateDialog(
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    maxWidth: Dp = 560.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible,
            usePlatformDefaultWidth = false,
        ),
    ) {
        EscCloses { if (dismissible) onDismiss() }
        // Phones: less margin around and inside the dialog, so its content keeps its width.
        androidx.compose.foundation.layout.BoxWithConstraints(contentAlignment = Alignment.Center) {
            val narrow = this.maxWidth < 480.dp
            Column(
                Modifier
                    .padding(if (narrow) 10.dp else 24.dp)
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .parchment()
                    .ornateFrame(inset = 5.dp, topGem = true)
                    .padding(horizontal = if (narrow) 14.dp else 24.dp, vertical = if (narrow) 16.dp else 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}
