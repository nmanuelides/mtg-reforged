package io.github.nmanuelides.mtgreforged.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.nmanuelides.mtgreforged.engine.ImageDownloadService
import io.github.nmanuelides.mtgreforged.engine.ImageDownloader
import io.github.nmanuelides.mtgreforged.ui.components.OrnateButton
import io.github.nmanuelides.mtgreforged.ui.components.OrnateDialog
import io.github.nmanuelides.mtgreforged.ui.components.OrnateStyle
import io.github.nmanuelides.mtgreforged.ui.components.SectionLabel
import io.github.nmanuelides.mtgreforged.ui.components.ornateFrame
import io.github.nmanuelides.mtgreforged.ui.components.paper
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Download card images ahead of time so matches and the deck builder work offline. */
@Composable
fun OfflineImagesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val progress by ImageDownloader.progress.collectAsState()
    val freeMb by produceState(0L) { value = withContext(Dispatchers.IO) { context.filesDir.usableSpace / (1024 * 1024) } }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun start(scope: ImageDownloader.Scope) {
        if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        ImageDownloadService.start(context, scope)
    }

    OrnateDialog(onDismiss = onDismiss, maxWidth = 680.dp) {
        SectionLabel("Offline card images")
        Spacer(Modifier.height(8.dp))
        Text(
            "Download card pictures now so you can play and build decks without a connection. " +
                "Wi-Fi only; pictures you already have are skipped, so you can stop and resume any time.",
            style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft,
        )
        Spacer(Modifier.height(14.dp))

        if (progress.running) {
            Text(
                progress.scope?.label.orEmpty(),
                style = MaterialTheme.typography.titleMedium, color = Palette.Ink,
            )
            Spacer(Modifier.height(8.dp))
            Bar(progress.fraction)
            Spacer(Modifier.height(6.dp))
            Text(
                progress.message ?: "%,d / %,d  ·  %,d new  ·  %,d unavailable".format(
                    Locale.US, progress.done, progress.total, progress.downloaded, progress.failed,
                ),
                style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OrnateButton("Hide", onDismiss, Modifier.weight(1f), accent = Palette.Slate)
                OrnateButton("Stop", { ImageDownloadService.stop(context) }, Modifier.weight(1f), icon = Icons.Default.Stop)
            }
        } else {
            progress.message?.let { last ->
                Text(
                    "$last · %,d new, %,d unavailable".format(Locale.US, progress.downloaded, progress.failed),
                    style = MaterialTheme.typography.bodySmall, color = Palette.Sage,
                )
                Spacer(Modifier.height(10.dp))
            }
            ImageDownloader.Scope.entries.forEach { scope ->
                ScopeCard(scope, freeMb) { start(scope) }
                Spacer(Modifier.height(10.dp))
            }
            OrnateButton("Close", onDismiss, Modifier.fillMaxWidth(), accent = Palette.Slate)
        }
    }
}

@Composable
private fun ScopeCard(scope: ImageDownloader.Scope, freeMb: Long, onStart: () -> Unit) {
    val count by produceState<Int?>(null, scope) {
        value = withContext(Dispatchers.Default) { runCatching { ImageDownloader.keys(scope).size }.getOrNull() }
    }
    Row(
        Modifier.fillMaxWidth().paper(Palette.PaperLight).ornateFrame(Palette.Line, inset = 3.dp, corners = false).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(scope.label, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
            Text(scope.detail, style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
            Text(
                count?.let { n ->
                    val mb = ImageDownloader.estimateMb(n)
                    "%,d pictures · up to %s".format(Locale.US, n, size(mb)) + if (mb > freeMb) " · not enough space" else ""
                } ?: "Counting…",
                style = MaterialTheme.typography.bodySmall,
                color = if (count != null && ImageDownloader.estimateMb(count!!) > freeMb) Palette.Terracotta else Palette.Slate,
            )
        }
        Spacer(Modifier.padding(start = 12.dp))
        OrnateButton(
            "Download", onStart, icon = Icons.Default.CloudDownload, style = OrnateStyle.Primary,
            enabled = count != null && ImageDownloader.estimateMb(count!!) <= freeMb,
        )
    }
}

private fun size(mb: Int) = if (mb >= 1024) "%.1f GB".format(Locale.US, mb / 1024f) else "$mb MB"

@Composable
private fun Bar(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(10.dp).background(Palette.PaperDeep)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(Palette.Terracotta))
    }
}
