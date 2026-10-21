package io.github.nmanuelides.mtgreforged.ui.match

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.nmanuelides.mtgreforged.match.LogEntryUi
import io.github.nmanuelides.mtgreforged.match.LogKind
import io.github.nmanuelides.mtgreforged.ui.components.ManaPip
import io.github.nmanuelides.mtgreforged.ui.components.ManaSymbol
import io.github.nmanuelides.mtgreforged.ui.components.OrnateButton
import io.github.nmanuelides.mtgreforged.ui.components.OrnateStyle
import io.github.nmanuelides.mtgreforged.ui.components.ZoomableCard
import io.github.nmanuelides.mtgreforged.ui.components.paper
import io.github.nmanuelides.mtgreforged.ui.components.paperShadow
import io.github.nmanuelides.mtgreforged.ui.theme.Cinzel
import io.github.nmanuelides.mtgreforged.ui.theme.Palette

/**
 * The game log, newest first. Turns are headers; phases and notices stay plain lines; mana a
 * player makes is a small card; everything else that happens (casting, triggers, lands, attacks,
 * damage...) is a card with who did it, the cards it names (hold one to see it big) and, within
 * the undo history, a button to go back to just before it. Mana and tap symbols are drawn as icons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSheet(entries: List<LogEntryUi>, onRewind: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Paper) {
        Text(
            "Game log", fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Palette.Ink,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(6.dp))
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            items(entries, key = { it.index }) { entry ->
                when (entry.kind) {
                    LogKind.Turn -> TurnHeader(entry)
                    LogKind.Phase, LogKind.Info -> PlainLine(entry)
                    LogKind.Mana -> ManaCard(entry)
                    else -> ActionCard(entry, onRewind)
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

/** The player's portrait, or nothing when the line isn't something a player did. */
@Composable
private fun Portrait(entry: LogEntryUi, size: Int) {
    val seat = entry.playerSeat ?: return
    val name = entry.playerName ?: return
    PlayerPortrait(seat, name, entry.playerCommander, Palette.Slate, Modifier.size(size.dp).border(1.dp, Palette.Line, CircleShape))
}

private val symbol = Regex("\\{([^}]+)\\}")

/** [text] with {W}, {U}, {T}, {2}... drawn as mana and tap symbols. */
@Composable
private fun ManaText(text: String, style: TextStyle, color: Color) {
    val tokens = symbol.findAll(text).map { it.groupValues[1] }.toSet()
    val annotated = buildAnnotatedString {
        var last = 0
        symbol.findAll(text).forEach { m ->
            append(text.substring(last, m.range.first))
            appendInlineContent(m.groupValues[1], m.value)
            last = m.range.last + 1
        }
        append(text.substring(last))
    }
    val inline = tokens.associateWith { token ->
        InlineTextContent(Placeholder(1.25.em, 1.25.em, PlaceholderVerticalAlign.TextCenter)) { SymbolIcon(token) }
    }
    Text(annotated, style = style, color = color, inlineContent = inline)
}

@Composable
private fun SymbolIcon(token: String) {
    val pip = when (token.uppercase()) {
        "W" -> ManaPip.White
        "U" -> ManaPip.Blue
        "B" -> ManaPip.Black
        "R" -> ManaPip.Red
        "G" -> ManaPip.Green
        "C" -> ManaPip.Colorless
        else -> null
    }
    when {
        pip != null -> ManaSymbol(pip, size = 18.dp)
        token.equals("T", ignoreCase = true) -> Box(
            Modifier.fillMaxSize().background(Palette.PaperDeep, CircleShape).border(1.dp, Palette.Line, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Refresh, contentDescription = "tap", tint = Palette.Ink, modifier = Modifier.size(13.dp)) }
        else -> Box(
            Modifier.fillMaxSize().background(Palette.PaperDeep, CircleShape).border(1.dp, Palette.Line, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(token, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, maxLines = 1) }
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
        Portrait(entry, 24)
        Text(entry.text, fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Palette.Terracotta)
        Box(Modifier.weight(1f).height(1.dp).background(Palette.LineStrong))
    }
}

@Composable
private fun PlainLine(entry: LogEntryUi) {
    Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Portrait(entry, 18)
        ManaText(entry.text, MaterialTheme.typography.bodySmall, Palette.InkSoft)
    }
}

/** A card-shaped log entry: light shadow and a thin rule, so the list stays calm. */
private fun Modifier.logCard() = this
    .fillMaxWidth()
    .paperShadow(offsetY = 1.dp, blur = 3.dp)
    .paper(Palette.PaperLight)
    .border(1.dp, Palette.Line)

/** Mana a player made: who, with which card, what it gave. */
@Composable
private fun ManaCard(entry: LogEntryUi) {
    Row(
        Modifier.logCard().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Portrait(entry, 24)
        entry.cards.forEach { ZoomableCard(it, 32.dp) }
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(entry.playerName, "MANA").joinToString("  ·  "),
                fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 1.sp, color = Palette.Ochre,
            )
            ManaText(entry.text, MaterialTheme.typography.bodySmall, Palette.Ink)
        }
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
    Column(Modifier.logCard().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Portrait(entry, 34)
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(entry.playerName, label.uppercase()).joinToString("  ·  "),
                    fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.sp, color = color,
                )
                ManaText(entry.text, MaterialTheme.typography.bodyMedium, Palette.Ink)
            }
        }
        if (entry.cards.isNotEmpty() || entry.canRewind) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                entry.cards.forEach { ZoomableCard(it, 54.dp) }
                Spacer(Modifier.weight(1f))
                if (entry.canRewind) {
                    OrnateButton(
                        "Go back to before this", { onRewind(entry.index) },
                        icon = Icons.Default.History, style = OrnateStyle.Primary, accent = Palette.Slate, height = 40.dp,
                    )
                }
            }
        }
    }
}
