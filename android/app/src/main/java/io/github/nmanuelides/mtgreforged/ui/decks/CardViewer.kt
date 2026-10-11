package io.github.nmanuelides.mtgreforged.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import forge.item.PaperCard
import io.github.nmanuelides.mtgreforged.engine.CardPrices
import io.github.nmanuelides.mtgreforged.match.CardUi
import io.github.nmanuelides.mtgreforged.ui.components.CardTile
import io.github.nmanuelides.mtgreforged.ui.components.ornateFrame
import io.github.nmanuelides.mtgreforged.ui.components.paper
import io.github.nmanuelides.mtgreforged.ui.components.paperShadow
import io.github.nmanuelides.mtgreforged.ui.theme.Cinzel
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import kotlin.math.absoluteValue

/**
 * A card shown big, over the builder. Swiping left / right walks through [cards] (the
 * search results or the deck, whichever it was opened from); the bar below edits the deck.
 */
@Composable
internal fun CardViewer(
    cards: List<PaperCard>,
    start: Int,
    draft: DeckDraft,
    commanderFormat: Boolean,
    onDismiss: () -> Unit,
) {
    if (cards.isEmpty()) return
    val pager = rememberPagerState(start.coerceIn(0, cards.lastIndex)) { cards.size }
    val prices by CardPrices.version.collectAsState()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            // As big as fits with room for the bar: cards are 63 × 88 mm.
            val cardWidth = minOf((maxHeight - 190.dp) * (63f / 88f), maxWidth * 0.8f, 460.dp)
            val side = (maxWidth - cardWidth) / 2

            Column(Modifier.fillMaxSize().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${pager.currentPage + 1} / ${cards.size}",
                    fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(10.dp))
                HorizontalPager(
                    pager,
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = side),
                    pageSpacing = 28.dp,
                    // One card per swipe, however fast the fling.
                    flingBehavior = PagerDefaults.flingBehavior(pager, pagerSnapDistance = PagerSnapDistance.atMost(1)),
                    verticalAlignment = Alignment.CenterVertically,
                ) { page ->
                    val ui = remember(cards[page]) { CardUi.fromPaper(cards[page]) }
                    val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                    Box(
                        Modifier.graphicsLayer {
                            val s = 1f - 0.12f * offset
                            scaleX = s; scaleY = s
                            alpha = 1f - 0.45f * offset
                        },
                    ) {
                        CardTile(ui, cardWidth, showTapped = false)
                    }
                }
                Spacer(Modifier.height(14.dp))
                val card = cards[pager.currentPage]
                ViewerBar(card, draft, commanderFormat, remember(card, prices) { CardPrices.of(card) }, onDismiss)
            }
        }
    }
}

@Composable
private fun ViewerBar(
    card: PaperCard,
    draft: DeckDraft,
    commanderFormat: Boolean,
    price: io.github.nmanuelides.mtgreforged.engine.CardPrice?,
    onClose: () -> Unit,
) {
    val isCommander = draft.commander.containsKey(card)
    val count = (draft.main[card] ?: 0) + (draft.commander[card] ?: 0)
    Row(
        Modifier
            .widthIn(max = 720.dp)
            .padding(horizontal = 16.dp)
            .paperShadow(offsetY = 4.dp, blur = 12.dp, cover = Palette.PaperLight)
            .paper(Palette.PaperLight)
            .ornateFrame(inset = 3.dp)
            // Taps on the bar shouldn't close the viewer.
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(card.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${card.edition} · " + if (count > 0) "$count in deck" else "not in deck",
                style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft,
            )
        }
        PriceTag(price)
        if (commanderFormat && canLead(card)) {
            IconButton(onClick = { if (isCommander) draft.toggleCommander(card) else draft.makeCommander(card) }) {
                Icon(if (isCommander) Icons.Default.Star else Icons.Default.StarBorder, "Commander", tint = Palette.Ochre)
            }
        }
        IconButton(onClick = { draft.remove(card, fromCommander = isCommander && (draft.main[card] ?: 0) == 0) }, enabled = count > 0) {
            Icon(Icons.Default.Remove, "Remove one", tint = if (count > 0) Palette.Terracotta else Palette.Line)
        }
        Box(Modifier.size(30.dp).background(Palette.Paper, CircleShape), contentAlignment = Alignment.Center) {
            Text("$count", fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Palette.Ink)
        }
        IconButton(onClick = { draft.add(card) }, enabled = !isCommander) {
            Icon(Icons.Default.Add, "Add one", tint = if (!isCommander) Palette.Sage else Palette.Line)
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = Palette.InkSoft) }
    }
}
