package io.github.nmanuelides.mtgreforged.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import forge.item.PaperCard
import io.github.nmanuelides.mtgreforged.engine.CardPrice
import io.github.nmanuelides.mtgreforged.engine.CardPrices
import io.github.nmanuelides.mtgreforged.engine.CardScan
import io.github.nmanuelides.mtgreforged.engine.Engine
import io.github.nmanuelides.mtgreforged.engine.ScanHit
import io.github.nmanuelides.mtgreforged.engine.ScanSet
import io.github.nmanuelides.mtgreforged.match.CardUi
import io.github.nmanuelides.mtgreforged.platform.BackHandler
import io.github.nmanuelides.mtgreforged.platform.CameraTextView
import io.github.nmanuelides.mtgreforged.platform.LocalAppContext
import io.github.nmanuelides.mtgreforged.platform.prefs
import io.github.nmanuelides.mtgreforged.ui.components.CardTile
import io.github.nmanuelides.mtgreforged.ui.components.OrnateButton
import io.github.nmanuelides.mtgreforged.ui.components.OrnateDialog
import io.github.nmanuelides.mtgreforged.ui.components.OrnateStyle
import io.github.nmanuelides.mtgreforged.ui.components.SectionLabel
import io.github.nmanuelides.mtgreforged.ui.components.ornateFrame
import io.github.nmanuelides.mtgreforged.ui.components.paper
import io.github.nmanuelides.mtgreforged.ui.theme.Cinzel
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/** A card waiting in the scanner's carousel: which printing, how many, foil or not. */
private class Scanned(val printings: List<PaperCard>, index: Int) {
    var index by mutableIntStateOf(index)
    var quantity by mutableIntStateOf(1)
    var foil by mutableStateOf(false)
    /** The printing is a guess from the name: a later read of its set code and number may correct it. */
    var guessed = true
    val name: String get() = printings[0].name
    val card: PaperCard get() = printings[index].let { if (foil) it.foiled else it }
}

/** Switches a guessed printing to the one its set code and number say (a manual pick stays). */
private fun usePrinting(item: Scanned, card: PaperCard) {
    if (!item.guessed) return
    val i = item.printings.indexOfFirst { it.edition == card.edition && it.collectorNumber == card.collectorNumber }
    if (i >= 0) { item.index = i; item.guessed = false }
}

/** A recognised card has to show this long out of frame before it can be scanned again. */
private const val AWAY_MS = 1500L

/**
 * Collection: point the camera at cards one after another. Each card recognised shows up in
 * the carousel at the bottom: swipe it left / right for another printing, up / down for how
 * many (the small ones too), and the Foil chip for foil. Nothing is added until Save.
 */
@Composable
internal fun CardScanner(onSave: (Map<PaperCard, Int>) -> Unit, onClose: () -> Unit, lookup: Boolean = false) {
    val context = LocalAppContext.current
    val prefs = remember { context.prefs("scanner") }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }
    var sets by remember { mutableStateOf(emptyList<ScanSet>()) }
    var set by remember { mutableStateOf(prefs.getString("set", null)) }
    var torch by remember { mutableStateOf(false) }
    var pickingSet by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    val items = remember { mutableStateListOf<Scanned>() }
    var focus by remember { mutableStateOf<Scanned?>(null) }

    LaunchedEffect(Unit) {
        sets = withContext(Dispatchers.Default) {
            Engine.awaitAllCards()
            CardScan.prepare()
            CardScan.sets()
        }
        ready = true
    }

    // Recognition: the same card in a row of frames before it counts (2 once its set code and
    // number are read too; with the name alone it waits a few more frames for them, as that
    // small print is read less often); then it's ignored until out of frame for a moment, but
    // a set code and number read meanwhile still correct a guessed printing.
    val busy = remember { AtomicBoolean(false) }
    var streakKey by remember { mutableStateOf<String?>(null) }
    var streak by remember { mutableIntStateOf(0) }
    var streakPrinting by remember { mutableStateOf<PaperCard?>(null) }
    var holding by remember { mutableStateOf<String?>(null) }
    var holdingSeen by remember { mutableStateOf(0L) }
    fun consider(hit: ScanHit?) {
        val now = System.currentTimeMillis()
        if (holding != null && now - holdingSeen > AWAY_MS) holding = null
        if (hit == null) { streakKey = null; streak = 0; return }
        val name = hit.card.name
        if (name == holding) {
            holdingSeen = now
            if (hit.numbered) items.firstOrNull { it.name == name }?.let { usePrinting(it, hit.card) }
            return
        }
        if (name == streakKey) streak++ else { streakKey = name; streak = 1; streakPrinting = null }
        if (hit.numbered) streakPrinting = hit.card
        val numbered = streakPrinting
        val needed = when {
            !hit.nameRead -> 3
            numbered != null -> 2
            else -> 6
        }
        if (streak < needed) return
        val card = numbered ?: hit.card
        holding = name
        holdingSeen = now
        streak = 0
        val existing = items.firstOrNull { it.name == name }
        if (lookup) {
            val printings = printingsOf(card)
            val item = Scanned(printings, printings.indexOf(card).coerceAtLeast(0))
            item.guessed = numbered == null
            items.clear()
            items.add(item)
            focus = item
        } else if (existing != null) {
            existing.quantity++
            if (numbered != null) usePrinting(existing, numbered)
            focus = existing
        } else {
            val printings = printingsOf(card)
            val item = Scanned(printings, printings.indexOf(card).coerceAtLeast(0))
            item.guessed = numbered == null
            items.add(0, item)
            focus = item
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    BackHandler { if (items.isEmpty() || lookup) onClose() else confirmLeave = true }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraTextView(
            Modifier.fillMaxSize(), torch,
            onLines = { lines ->
                if (ready && !pickingSet && busy.compareAndSet(false, true)) {
                    val within = set
                    scope.launch {
                        val hit = withContext(Dispatchers.Default) { runCatching { CardScan.read(lines, within) }.getOrNull() }
                        busy.set(false)
                        consider(hit)
                    }
                }
            },
            onDenied = { denied = true },
        )

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrnateButton("", { if (items.isEmpty() || lookup) onClose() else confirmLeave = true }, Modifier.width(48.dp), icon = Icons.Default.Close)
                OrnateButton(
                    sets.firstOrNull { it.code == set }?.name ?: "Any set", { pickingSet = true }, Modifier.weight(1f),
                    accent = Palette.Plum, frameColor = Palette.Plum, textStyle = MaterialTheme.typography.labelMedium,
                )
                OrnateButton("", { torch = !torch }, Modifier.width(48.dp), icon = if (torch) Icons.Default.FlashOn else Icons.Default.FlashOff)
            }
            Text(
                when {
                    denied -> "The camera permission is needed to scan cards."
                    !ready -> "Getting the cards ready…"
                    else -> "Hold a card inside the frame"
                },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
            )
            // Where to hold the card: as big as fits between the hint and the tray.
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.aspectRatio(63f / 88f).border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(12.dp)))
            }
            // A tapped card comes back to the front, as if just scanned.
            ScanTray(items, focus, lookup, onFocus = { items.remove(it); items.add(0, it); focus = it }, onRemove = { items.remove(it); if (focus === it) focus = items.firstOrNull() })
            val total = items.sumOf { it.quantity }
            if (lookup) Spacer(Modifier.height(12.dp)) else OrnateButton(
                if (total == 0) "Save" else "Save $total card${if (total > 1) "s" else ""}",
                {
                    val cards = HashMap<PaperCard, Int>()
                    items.forEach { cards[it.card] = (cards[it.card] ?: 0) + it.quantity }
                    onSave(cards)
                },
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                style = OrnateStyle.Primary, enabled = total > 0, height = 52.dp,
            )
        }
    }

    if (pickingSet) {
        SetPicker(sets, set, onPick = { code ->
            set = code
            prefs.edit().putString("set", code).apply()
            pickingSet = false
        }, onDismiss = { pickingSet = false })
    }
    if (confirmLeave) {
        OrnateDialog(onDismiss = { confirmLeave = false }) {
            SectionLabel("Leave the scanner")
            Spacer(Modifier.height(12.dp))
            Text("The scanned cards haven't been saved to your collection.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OrnateButton("Keep scanning", { confirmLeave = false }, Modifier.weight(1f), accent = Palette.Slate)
                OrnateButton("Discard", { confirmLeave = false; onClose() }, Modifier.weight(1f))
            }
        }
    }
}

/** The scanned cards: the one in focus big (with its gestures), the rest small beside it. */
@Composable
private fun ScanTray(items: List<Scanned>, focus: Scanned?, lookup: Boolean, onFocus: (Scanned) -> Unit, onRemove: (Scanned) -> Unit) {
    val current = focus ?: items.firstOrNull() ?: return
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).paper(Palette.PaperLight).ornateFrame(inset = 3.dp).padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .pointerInput(current) {
                        var dx = 0f
                        var dy = 0f
                        detectDragGestures(
                            onDragStart = { dx = 0f; dy = 0f },
                            onDragEnd = {
                                val step = 36.dp.toPx()
                                if (abs(dx) > abs(dy) && abs(dx) > step) {
                                    // Left: the next (older) printing; right: back to newer ones.
                                    val n = current.printings.size
                                    current.index = (current.index + if (dx < 0) 1 else -1 + n) % n
                                    current.guessed = false
                                } else if (abs(dy) > step && !lookup) {
                                    current.quantity += if (dy < 0) 1 else -1
                                    if (current.quantity <= 0) onRemove(current)
                                }
                            },
                        ) { change, amount -> change.consume(); dx += amount.x; dy += amount.y }
                    },
            ) {
                // The tile takes the tap itself (an outer clickable never sees it).
                CardTile(remember(current.card, current.foil) { CardUi.fromPaper(current.card) }, if (lookup) 190.dp else 96.dp, showTapped = false, onClick = { current.foil = !current.foil })
                if (!lookup) Text(
                    "×${current.quantity}",
                    Modifier.align(Alignment.BottomEnd).padding(3.dp).background(Palette.Ink.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.OnAccent,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(current.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    editionLabel(current.card) + (current.card.collectorNumber?.let { " · #$it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(if (current.foil) "✦ Foil" else "Regular", current.foil, fontSize = 12) { current.foil = !current.foil }
                    Text("Printing ${current.index + 1} of ${current.printings.size}", style = MaterialTheme.typography.labelSmall, color = Palette.InkSoft)
                }
                val prices by CardPrices.version.collectAsState()
                val price = remember(current.card, current.foil, prices) { CardPrices.of(current.card)?.takeIf { it.exact } }
                Text(
                    price?.let { CardPrice.usd(it.retail) } ?: "No price",
                    style = if (lookup) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleMedium,
                    color = Palette.Ochre, fontWeight = FontWeight.Bold,
                )
                Text(if (lookup) "↔ printing  ·  tap: foil" else "↔ printing  ·  ↕ copies", style = MaterialTheme.typography.labelSmall, color = Palette.InkSoft)
            }
        }
        val others = if (lookup) emptyList() else items.filter { it !== current }
        if (others.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(others, key = { it.name }) { item ->
                    Box(
                        Modifier
                            .pointerInput(item) {
                                var dy = 0f
                                detectVerticalDragGestures(
                                    onDragStart = { dy = 0f },
                                    onDragEnd = {
                                        if (abs(dy) > 24.dp.toPx()) {
                                            item.quantity += if (dy < 0) 1 else -1
                                            if (item.quantity <= 0) onRemove(item)
                                        }
                                    },
                                ) { change, amount -> change.consume(); dy += amount }
                            },
                    ) {
                        CardTile(remember(item.card, item.foil) { CardUi.fromPaper(item.card) }, 56.dp, showTapped = false, onClick = { onFocus(item) })
                        if (item.quantity > 0) {
                            Text(
                                "×${item.quantity}",
                                Modifier.align(Alignment.BottomEnd).background(Palette.Ink.copy(alpha = 0.85f)).padding(horizontal = 3.dp),
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Palette.OnAccent,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Scan from any set, or only one (picked from a searchable list). */
@Composable
private fun SetPicker(sets: List<ScanSet>, current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, sets) {
        sets.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) || it.code.equals(query.trim(), ignoreCase = true) }
    }
    OrnateDialog(onDismiss = onDismiss, maxWidth = 560.dp) {
        SectionLabel("Scan from")
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            query, { query = it }, singleLine = true, colors = fieldColors(),
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Palette.InkSoft) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search", tint = Palette.InkSoft) } },
            placeholder = { Text("Set name or code") }, modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(top = 8.dp)) {
            item {
                SetRow("Any set", null, current == null) { onPick(null) }
            }
            items(shown, key = { it.code }) { s -> SetRow(s.name, s.code, s.code == current) { onPick(s.code) } }
        }
    }
}

@Composable
private fun SetRow(name: String, code: String?, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
            color = if (selected) Palette.Terracotta else Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        code?.let { Text(it, fontFamily = Cinzel, fontSize = 13.sp, color = Palette.InkSoft) }
    }
}
