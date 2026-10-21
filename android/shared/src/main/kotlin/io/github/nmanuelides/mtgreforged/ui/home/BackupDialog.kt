package io.github.nmanuelides.mtgreforged.ui.home

import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nmanuelides.mtgreforged.engine.Backup
import io.github.nmanuelides.mtgreforged.engine.DriveBackup
import io.github.nmanuelides.mtgreforged.ui.components.OrnateButton
import io.github.nmanuelides.mtgreforged.ui.components.OrnateDialog
import io.github.nmanuelides.mtgreforged.ui.components.OrnateStyle
import io.github.nmanuelides.mtgreforged.ui.components.SectionLabel
import io.github.nmanuelides.mtgreforged.ui.components.ornateFrame
import io.github.nmanuelides.mtgreforged.ui.components.paper
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import io.github.nmanuelides.mtgreforged.platform.rememberCreateDocument
import io.github.nmanuelides.mtgreforged.platform.rememberDriveToken
import io.github.nmanuelides.mtgreforged.platform.rememberOpenDocument

/**
 * Backups of the collection and the player's decks in their Google Drive (listed with date,
 * card and deck counts), plus a plain file export / import as a fallback.
 */
@Composable
fun BackupDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf<String?>(null) }
    var backups by remember { mutableStateOf<List<DriveBackup.Entry>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<DriveBackup.Entry?>(null) }

    fun driveJob(work: suspend (String) -> Unit): (String) -> Unit = { t ->
        scope.launch {
            busy = true
            failed = false
            runCatching { work(t) }.onFailure { failed = true; status = it.message ?: "Something went wrong" }
            busy = false
        }
    }

    val refresh = driveJob { t -> backups = DriveBackup.list(t) }

    val driveToken = rememberDriveToken()

    /** Runs [action] with a Drive token, asking the user for access the first time. */
    fun withDrive(action: (String) -> Unit) {
        token?.let { action(it); return }
        scope.launch {
            busy = true
            runCatching { driveToken() }
                .onSuccess { t -> token = t; busy = false; action(t) }
                .onFailure { failed = true; status = it.message ?: "Couldn't connect to Google." }
            busy = false
        }
    }

    // List the Drive backups straight away (asks for access the first time).
    LaunchedEffect(Unit) { withDrive(refresh) }

    // Plain-file fallback.
    val saveFile = rememberCreateDocument("application/json") { open ->
        if (open != null) scope.launch {
            failed = false
            status = withContext(Dispatchers.IO) {
                runCatching { open()?.use { it.write(Backup.export().toByteArray()) }; "Backup file saved." }
                    .getOrElse { failed = true; "Couldn't save: ${it.message}" }
            }
        }
    }
    val openFile = rememberOpenDocument { open ->
        if (open != null) scope.launch {
            failed = false
            status = withContext(Dispatchers.IO) {
                runCatching {
                    val text = open()?.use { it.readBytes().decodeToString() }.orEmpty()
                    restoredText(Backup.restore(text))
                }.getOrElse { failed = true; "Couldn't restore: ${it.message}" }
            }
        }
    }

    OrnateDialog(onDismiss = onDismiss, maxWidth = 620.dp) {
        SectionLabel("Backup & restore")
        Spacer(Modifier.height(8.dp))
        Text(
            "Your collection and the decks you made, saved in your Google Drive. Restore them on any device signed in with the same Google account.",
            style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft,
        )
        Spacer(Modifier.height(14.dp))
        OrnateButton(
            "Back up to Google Drive",
            { withDrive(driveJob { t -> DriveBackup.upload(t); status = "Backed up to Google Drive."; backups = DriveBackup.list(t) }) },
            Modifier.fillMaxWidth(), icon = Icons.Default.CloudUpload, style = OrnateStyle.Primary, enabled = !busy,
        )
        Spacer(Modifier.height(14.dp))
        SectionLabel("Your backups", color = Palette.InkSoft)
        Spacer(Modifier.height(6.dp))
        val list = backups
        when {
            busy && list == null -> CircularProgressIndicator(color = Palette.Terracotta)
            list == null -> Text("Connect to Google Drive to see your backups.", style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
            list.isEmpty() -> Text("No backups in your Drive yet.", style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
            else -> Column(
                Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                list.forEach { entry ->
                    Row(
                        Modifier.fillMaxWidth().paper(Palette.PaperLight).ornateFrame(Palette.Line, inset = 2.dp, corners = false).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(dateOf(entry), style = MaterialTheme.typography.titleSmall, color = Palette.Ink)
                            Text(
                                "${entry.cards} cards in collection · ${entry.decks} deck${if (entry.decks == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft,
                            )
                        }
                        OrnateButton("Restore", { confirmRestore = entry }, icon = Icons.Default.Restore, accent = Palette.Slate, height = 40.dp, enabled = !busy)
                    }
                }
            }
        }
        status?.let {
            Spacer(Modifier.height(12.dp))
            StatusBanner(it, failed)
        }
        Spacer(Modifier.height(14.dp))
        Text("Without Google Drive", style = MaterialTheme.typography.labelSmall, color = Palette.InkSoft)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OrnateButton(
                "Save file", { saveFile("${DriveBackup.PREFIX}${SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())}.json") },
                Modifier.weight(1f), icon = Icons.Default.Save, accent = Palette.Slate, height = 40.dp,
            )
            OrnateButton("Open file", { openFile(arrayOf("application/json", "*/*")) }, Modifier.weight(1f), icon = Icons.Default.FileOpen, accent = Palette.Slate, height = 40.dp)
        }
        Spacer(Modifier.height(10.dp))
        OrnateButton("Close", onDismiss, Modifier.fillMaxWidth(), accent = Palette.Slate)
    }

    confirmRestore?.let { entry ->
        OrnateDialog(onDismiss = { confirmRestore = null }, maxWidth = 460.dp) {
            SectionLabel("Restore this backup?")
            Spacer(Modifier.height(8.dp))
            Text(
                "${dateOf(entry)}: ${entry.cards} cards, ${entry.decks} decks. Your collection is replaced and decks with the same name are overwritten.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OrnateButton("Cancel", { confirmRestore = null }, Modifier.weight(1f), accent = Palette.Slate)
                OrnateButton("Restore", {
                    confirmRestore = null
                    withDrive(driveJob { t -> status = restoredText(DriveBackup.restore(t, entry)) })
                }, Modifier.weight(1f), style = OrnateStyle.Primary)
            }
        }
    }
}

private fun dateOf(entry: DriveBackup.Entry): String =
    if (entry.createdAt == 0L) entry.name.removePrefix(DriveBackup.PREFIX).removeSuffix(".json")
    else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.createdAt))

private fun restoredText(r: Backup.Restored) =
    "Restored ${r.decks} deck(s)" + if (r.collection) " and your collection." else "."

/** The outcome of a backup or restore, hard to miss: a full-width plaque, green with a tick or terracotta with a warning. */
@Composable
internal fun StatusBanner(text: String, failed: Boolean) {
    val shown = remember(text) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(text) { shown.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 420f)) }
    val fill = if (failed) Palette.Terracotta else Palette.Sage
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = 0.92f + 0.08f * shown.value; scaleY = scaleX; alpha = shown.value.coerceIn(0f, 1f) }
            .background(fill)
            .border(1.5.dp, Palette.OnAccent.copy(alpha = 0.7f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (failed) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
            contentDescription = null, tint = Palette.OnAccent, modifier = Modifier.size(26.dp),
        )
        Text(text, color = Palette.OnAccent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
