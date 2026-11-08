package io.github.nmanuelides.mtgreforged.ui.home

import io.github.nmanuelides.mtgreforged.engine.CloudSync
import io.github.nmanuelides.mtgreforged.engine.Relay
import io.github.nmanuelides.mtgreforged.platform.IS_DESKTOP
import io.github.nmanuelides.mtgreforged.platform.prefs
import androidx.compose.material3.Switch
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.collectAsState
import io.github.nmanuelides.mtgreforged.platform.rememberQuietDriveToken
import io.github.nmanuelides.mtgreforged.platform.LocalAppContext
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
    val context = LocalAppContext.current
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
    // Google Drive needs the sharing notice accepted; files saved by hand don't.
    var accepted by remember { mutableStateOf(Relay.shareChoice(context) == true) }
    var asking by remember { mutableStateOf(!accepted) }

    /** Runs [action] with a Drive token, asking the user for access the first time. */
    fun withDrive(action: (String) -> Unit) {
        if (!accepted) { asking = true; return }
        token?.let { action(it); return }
        scope.launch {
            busy = true
            runCatching { driveToken() }
                .onSuccess { t -> token = t; busy = false; action(t) }
                .onFailure { failed = true; status = it.message ?: "Couldn't connect to Google." }
            busy = false
        }
    }

    // List the Drive backups straight away (asks for access the first time), and start syncing.
    if (asking) SharingNotice { asking = false; accepted = Relay.shareChoice(context) == true }
    LaunchedEffect(accepted) { if (accepted) withDrive { t -> refresh(t); CloudSync.syncSoon(t) } }
    val sync by CloudSync.status.collectAsState()

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
            "Your collection and the decks you made sync by themselves through your Google Drive: every change is saved, " +
                "and every device signed in with the same Google account picks it up when it opens the home screen.",
            style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                !accepted -> "Sync and Google backups are off."
                sync.syncing -> "Syncing…"
                sync.error != null -> "Last sync failed: ${sync.error}"
                !sync.connected -> "Sync starts once Google Drive is connected."
                sync.lastSync > 0 -> "Synced ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(sync.lastSync))}"
                else -> "Not synced yet."
            },
            style = MaterialTheme.typography.titleSmall, color = if (sync.error != null) Palette.Terracotta else Palette.Ink,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OrnateButton(
                "Sync now",
                { withDrive(driveJob { t -> status = CloudSync.sync(t) ?: "Synced with your other devices." ; failed = sync.error != null }) },
                Modifier.weight(1f), icon = Icons.Default.Sync, style = OrnateStyle.Primary, enabled = !busy && !sync.syncing,
            )
            OrnateButton(
                "Back up now",
                { withDrive(driveJob { t -> DriveBackup.upload(t); status = "Backup saved to Google Drive."; backups = DriveBackup.list(t) }) },
                Modifier.weight(1f), icon = Icons.Default.CloudUpload, accent = Palette.Slate, enabled = !busy,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Share my account with the developers", style = MaterialTheme.typography.titleSmall, color = Palette.Ink)
                Text("Needed for sync and Google backups: your Google name and email, and your app and system versions.", style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
            }
            Switch(
                accepted,
                { on ->
                    if (on) asking = true else withDrive(driveJob { t ->
                        // Deleted first: the switch only goes off once the relay has forgotten them.
                        if (!withContext(Dispatchers.IO) { Relay.forgetProfile(context, t) }) error("Couldn't delete your shared details. Try again.")
                        Relay.setShareChoice(context, false)
                        accepted = false
                        backups = null
                        status = "Sharing is off, and so are sync and Google backups."
                    })
                },
                enabled = !busy,
            )
        }
        Spacer(Modifier.height(14.dp))
        SectionLabel("Backups (snapshots you can go back to)", color = Palette.InkSoft)
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
                    withDrive(driveJob { t ->
                        // Restored on this device; the sync then carries it to the others.
                        status = restoredText(DriveBackup.restore(t, entry))
                    })
                }, Modifier.weight(1f), style = OrnateStyle.Primary)
            }
        }
    }
}

/**
 * Lets [CloudSync] get a Drive token quietly while the app runs (it never shows Google's screens).
 * Place it once at the app's root.
 */
@Composable
fun CloudSyncHost() {
    val context = LocalAppContext.current
    val quietToken = rememberQuietDriveToken()
    LaunchedEffect(quietToken) { CloudSync.start(context, quietToken) }
}

/** Every time the home screen shows: pick up what other devices changed (and send what this one did). */
@Composable
fun SyncOnHome() {
    LaunchedEffect(Unit) { CloudSync.syncSoon() }
    // Players already connected before the notice existed answer it here.
    val context = LocalAppContext.current
    val sync by CloudSync.status.collectAsState()
    var answered by remember { mutableStateOf(Relay.shareChoice(context) != null) }
    if (sync.connected && !answered) SharingNotice { answered = true }
    // First run: offer to set up sync, so nobody loses their cards without knowing it existed.
    val setup = remember { context.prefs("setup") }
    var wizard by remember { mutableStateOf(!setup.getBoolean("syncWizard", false) && !answered) }
    if (wizard && !sync.connected) SyncWizard {
        wizard = false
        answered = true
        setup.edit().putBoolean("syncWizard", true).apply()
    }
}

/** The first-run steps that turn sync on: why it matters, what it shares, then connecting Google. */
@Composable
private fun SyncWizard(onDone: () -> Unit) {
    val context = LocalAppContext.current
    val scope = rememberCoroutineScope()
    val driveToken = rememberDriveToken()
    var step by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun connect() {
        busy = true
        error = null
        scope.launch {
            runCatching { driveToken() }
                .onSuccess { t -> CloudSync.syncSoon(t); step = 3 }
                .onFailure { error = it.message ?: "Couldn't connect to Google." }
            busy = false
        }
    }
    OrnateDialog(onDismiss = {}, dismissible = false, maxWidth = 540.dp) {
        SectionLabel("Keep your cards safe · ${minOf(step, 2) + 1} of 3")
        Spacer(Modifier.height(10.dp))
        when (step) {
            0 -> {
                Text(
                    "Your collection and your decks live on this device. If you uninstall the app or change " +
                        "${if (IS_DESKTOP) "PC" else "phone"}, they are lost, unless sync is on.",
                    style = MaterialTheme.typography.bodyLarge, color = Palette.Ink,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sync saves them in your own Google Drive after every change, and brings them to every phone, tablet " +
                        "and PC signed in with the same Google account.",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OrnateButton("Not now", { step = 4 }, Modifier.weight(1f), accent = Palette.Slate)
                    OrnateButton("Set up sync", { step = 1 }, Modifier.weight(1f), icon = Icons.Default.Sync, style = OrnateStyle.Primary)
                }
            }
            1 -> {
                Text(sharingText(), style = MaterialTheme.typography.bodyMedium, color = Palette.Ink)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OrnateButton("Not now", { Relay.setShareChoice(context, false); step = 4 }, Modifier.weight(1f), accent = Palette.Slate)
                    OrnateButton("Accept", { Relay.setShareChoice(context, true); step = 2 }, Modifier.weight(1f), style = OrnateStyle.Primary)
                }
            }
            2 -> {
                Text(
                    "Last step: sign in with Google and allow MTG Reforged to keep its files in your Drive. " +
                        "It only sees the files it creates.",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Ink,
                )
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    StatusBanner(it, failed = true)
                }
                Spacer(Modifier.height(14.dp))
                if (busy) CircularProgressIndicator(color = Palette.Terracotta)
                else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OrnateButton("Later", { step = 4 }, Modifier.weight(1f), accent = Palette.Slate)
                    OrnateButton("Connect Google", ::connect, Modifier.weight(1f), style = OrnateStyle.Primary)
                }
            }
            3 -> {
                StatusBanner("Sync is on. Your cards and decks are saved in your Google Drive.", failed = false)
                Spacer(Modifier.height(14.dp))
                OrnateButton("Done", onDone, Modifier.fillMaxWidth(), style = OrnateStyle.Primary)
            }
            else -> {
                Text(
                    "No problem. You can turn sync on at any time with the Backup & restore button at the top of the home screen.",
                    style = MaterialTheme.typography.bodyLarge, color = Palette.Ink,
                )
                Spacer(Modifier.height(14.dp))
                OrnateButton("Got it", onDone, Modifier.fillMaxWidth(), style = OrnateStyle.Primary)
            }
        }
    }
}

private fun sharingText() =
    "MTG Reforged is free. To use sync and Google Drive backups, your Google name and email, the app version and your " +
        "${if (IS_DESKTOP) "Windows" else "Android"} version are shared with the developers. They will NOT be shared with anyone, " +
        "and are used only for practical purposes: fixing errors and improving your experience with the app."

/** Says what sync and Google backups share with the developers, before it happens. Without a yes they stay off. */
@Composable
private fun SharingNotice(onAnswer: () -> Unit) {
    val context = LocalAppContext.current
    val answer = { share: Boolean -> Relay.setShareChoice(context, share); onAnswer() }
    OrnateDialog(onDismiss = {}, dismissible = false, maxWidth = 520.dp) {
        SectionLabel("Your Google account")
        Spacer(Modifier.height(10.dp))
        Text(sharingText(), style = MaterialTheme.typography.bodyMedium, color = Palette.Ink)
        Spacer(Modifier.height(8.dp))
        Text("You can stop at any time in Backup & restore, which also turns these off. Saving a backup to a file works without this.", style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OrnateButton("Not now", { answer(false) }, Modifier.weight(1f), accent = Palette.Slate)
            OrnateButton("Accept", { answer(true) }, Modifier.weight(1f), style = OrnateStyle.Primary)
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
