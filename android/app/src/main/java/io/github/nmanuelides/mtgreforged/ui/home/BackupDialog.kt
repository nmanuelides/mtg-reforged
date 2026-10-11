package io.github.nmanuelides.mtgreforged.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.nmanuelides.mtgreforged.engine.Backup
import io.github.nmanuelides.mtgreforged.ui.components.OrnateButton
import io.github.nmanuelides.mtgreforged.ui.components.OrnateDialog
import io.github.nmanuelides.mtgreforged.ui.components.OrnateStyle
import io.github.nmanuelides.mtgreforged.ui.components.SectionLabel
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Back up / restore the collection and the player's decks as a JSON file (e.g. on Google Drive). */
@Composable
fun BackupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(Backup.export().toByteArray()) }
                    "Backup saved."
                }.getOrElse { "Couldn't save the backup: ${it.message}" }
            }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }.orEmpty()
                    val restored = Backup.restore(text)
                    "Restored ${restored.decks} deck(s)" + if (restored.collection) " and your collection." else "."
                }.getOrElse { "Couldn't restore: ${it.message}" }
            }
        }
    }

    OrnateDialog(onDismiss = onDismiss, maxWidth = 560.dp) {
        SectionLabel("Backup & restore")
        Spacer(Modifier.height(8.dp))
        Text(
            "Saves your collection and the decks you made to one file. Pick Google Drive in the save dialog to keep it in " +
                "your Drive; restore it on any device. Restoring replaces decks with the same name.",
            style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft,
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OrnateButton(
                "Back up", {
                    val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    save.launch("mtg-reforged-backup-$day.json")
                },
                Modifier.weight(1f), icon = Icons.Default.CloudUpload, style = OrnateStyle.Primary,
            )
            OrnateButton("Restore", { open.launch(arrayOf("application/json", "*/*")) }, Modifier.weight(1f), icon = Icons.Default.Restore, accent = Palette.Slate)
        }
        status?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Sage)
        }
    }
}
