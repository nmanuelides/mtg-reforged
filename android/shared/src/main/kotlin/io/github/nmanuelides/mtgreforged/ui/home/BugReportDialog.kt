package io.github.nmanuelides.mtgreforged.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nmanuelides.mtgreforged.engine.Relay
import io.github.nmanuelides.mtgreforged.platform.rememberDriveToken
import io.github.nmanuelides.mtgreforged.ui.components.OrnateButton
import io.github.nmanuelides.mtgreforged.ui.components.OrnateDialog
import io.github.nmanuelides.mtgreforged.ui.components.OrnateStyle
import io.github.nmanuelides.mtgreforged.ui.components.SectionLabel
import io.github.nmanuelides.mtgreforged.ui.decks.fieldColors
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_REPORT = 1000

/**
 * Sends a problem to the developer. It goes with the player's Google account (the one used for
 * backups), so the developer knows who wrote and can answer; connecting it is asked for on Send.
 */
@Composable
fun BugReportDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val driveToken = rememberDriveToken()
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    OrnateDialog(onDismiss = onDismiss, maxWidth = 560.dp) {
        SectionLabel("Report a bug")
        Spacer(Modifier.height(10.dp))
        if (sent) {
            Text("Thanks! Your report was sent. The developer may answer you at your Google email.", style = MaterialTheme.typography.bodyMedium, color = Palette.Ink)
            Spacer(Modifier.height(14.dp))
            OrnateButton("Done", onDismiss, Modifier.fillMaxWidth(), style = OrnateStyle.Primary)
            return@OrnateDialog
        }
        Text(
            "Tell the developer what happened and what you were doing. Your report is sent with your Google name and email, so you can get an answer.",
            style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            text, { text = it.take(MAX_REPORT) },
            label = { Text("What went wrong?") }, minLines = 5, maxLines = 8,
            colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.Terracotta)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OrnateButton("Cancel", onDismiss, Modifier.weight(1f), accent = Palette.Slate)
            OrnateButton(
                if (sending) "Sending…" else "Send",
                {
                    scope.launch {
                        sending = true
                        error = null
                        val token = runCatching { driveToken() }.getOrNull()
                        if (token == null) {
                            error = "Connect your Google account to send a report, so the developer can answer you."
                        } else {
                            val problem = withContext(Dispatchers.IO) { Relay.reportBug(token, text.trim()) }
                            if (problem == null) sent = true else error = problem
                        }
                        sending = false
                    }
                },
                Modifier.weight(1f), style = OrnateStyle.Primary, enabled = text.isNotBlank() && !sending,
            )
        }
    }
}
