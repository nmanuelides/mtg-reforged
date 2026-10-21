package io.github.nmanuelides.mtgreforged.ui.match

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nmanuelides.mtgreforged.match.LogEntryUi
import io.github.nmanuelides.mtgreforged.match.LogKind
import io.github.nmanuelides.mtgreforged.ui.components.ZoomableCard
import io.github.nmanuelides.mtgreforged.ui.components.paper
import io.github.nmanuelides.mtgreforged.ui.components.paperShadow
import io.github.nmanuelides.mtgreforged.ui.home.AvatarImage
import io.github.nmanuelides.mtgreforged.ui.theme.Cinzel
import io.github.nmanuelides.mtgreforged.ui.theme.Palette

/**
 * The game log, newest first. Turns are headers, phase and mana lines stay plain text with the
 * player's avatar, and everything players do (casting, lands, attacks, damage...) is a card with
 * its avatar, card thumbnails (hold one to see it big) and, within the undo history, a button to
 * go back to just before it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSheet(entries: List<LogEntryUi>, onRewind: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Paper) {
        Text(
            "Game log", fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Palette.Ink,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(entries, key = { it.index }) { entry ->
                when (entry.kind) {
                    LogKind.Turn -> TurnHeader(entry)
                    LogKind.Phase, LogKind.Mana, LogKind.Info -> PlainLine(entry)
                    else -> ActionCard(entry, onRewind)
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun Avatar(entry: LogEntryUi, size: Int) {
    val seat = entry.playerSeat
    if (seat != null && entry.playerName != null) {
        AvatarImage(seat, entry.playerName, Modifier.size(size.dp).clip(CircleShape).border(1.dp, Palette.Line, CircleShape))
    } else {
        Box(Modifier.size(size.dp).background(Palette.PaperDeep, CircleShape).border(1.dp, Palette.Line, CircleShape))
    }
}

@Composable
private fun TurnHeader(entry: LogEntryUi) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(Palette.LineStrong))
        Avatar(entry, 22)
        Text(entry.text, fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Palette.Terracotta)
        Box(Modifier.weight(1f).height(1.dp).background(Palette.LineStrong))
    }
}

@Composable
private fun PlainLine(entry: LogEntryUi) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Avatar(entry, 18)
        Text(entry.text, style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
    }
}

/** Label and colour for each kind of action. */
private fun actionLabel(kind: LogKind): Pair<String, Color> = when (kind) {
    LogKind.Cast -> "Cast / activate" to Palette.Terracotta
    LogKind.Resolve -> "Resolves" to Palette.Slate
    LogKind.Land -> "Land" to Palette.Sage
    LogKind.Combat -> "Combat" to Palette.Terracotta
    LogKind.Damage -> "Damage" to Palette.Life
    LogKind.Life -> "Life" to Palette.Plum
    LogKind.Zone -> "Moves" to Palette.Ochre
    else -> "Event" to Palette.InkSoft
}

@Composable
private fun ActionCard(entry: LogEntryUi, onRewind: (Int) -> Unit) {
    val (label, color) = actionLabel(entry.kind)
    Column(
        Modifier
            .fillMaxWidth()
            .paperShadow(offsetY = 2.dp, blur = 8.dp)
            .paper(Palette.PaperLight)
            .border(1.dp, Palette.Line)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(entry, 34)
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(entry.playerName, label.uppercase()).joinToString("  ·  "),
                    fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.sp, color = color,
                )
                Text(entry.text, style = MaterialTheme.typography.bodyMedium, color = Palette.Ink)
            }
        }
        if (entry.cards.isNotEmpty() || entry.canRewind) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                entry.cards.forEach { ZoomableCard(it, 54.dp) }
                Spacer(Modifier.weight(1f))
                if (entry.canRewind) {
                    Row(
                        Modifier
                            .border(1.dp, Palette.Slate)
                            .clickable { onRewind(entry.index) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, tint = Palette.Slate, modifier = Modifier.size(16.dp))
                        Text("Go back to before this", color = Palette.Slate, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
