package io.github.nmanuelides.mtgreforged.ui.home

import io.github.nmanuelides.mtgreforged.ui.components.PagerArrowKeys
import io.github.nmanuelides.mtgreforged.ui.components.EscCloses
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import forge.StaticData
import io.github.nmanuelides.mtgreforged.engine.CardPrice
import io.github.nmanuelides.mtgreforged.engine.DeckSummary
import io.github.nmanuelides.mtgreforged.engine.Engine.DeckEntry
import io.github.nmanuelides.mtgreforged.match.CardUi
import io.github.nmanuelides.mtgreforged.ui.components.CardTile
import io.github.nmanuelides.mtgreforged.ui.components.ManaPip
import io.github.nmanuelides.mtgreforged.ui.components.ManaSymbol
import io.github.nmanuelides.mtgreforged.ui.components.OrnateButton
import io.github.nmanuelides.mtgreforged.ui.components.OrnateStyle
import io.github.nmanuelides.mtgreforged.ui.components.ornateFrame
import io.github.nmanuelides.mtgreforged.ui.components.paper
import io.github.nmanuelides.mtgreforged.ui.components.paperShadow
import io.github.nmanuelides.mtgreforged.ui.decks.fromPaper
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import io.github.nmanuelides.mtgreforged.ui.components.mouseDrag

/**
 * Each deck's commander (or signature card) shown big; swiping left / right moves between
 * decks like the deck builder's card viewer, and the browser follows along.
 */
@Composable
fun DeckCarousel(
    decks: List<DeckEntry>,
    start: Int,
    onPage: (Int) -> Unit,
    onSelect: (DeckEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    if (decks.isEmpty()) return
    val pager = rememberPagerState(start.coerceIn(0, decks.lastIndex)) { decks.size }
    LaunchedEffect(pager.currentPage) { onPage(pager.currentPage) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        EscCloses(onDismiss)
        PagerArrowKeys(pager)
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            val cardWidth = minOf((maxHeight - 190.dp) * (63f / 88f), maxWidth * 0.8f, 460.dp)
            val side = (maxWidth - cardWidth) / 2
            Column(Modifier.fillMaxSize().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${pager.currentPage + 1} / ${decks.size}",
                    style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(10.dp))
                HorizontalPager(
                    pager,
                    Modifier.weight(1f).mouseDrag(pager),
                    contentPadding = PaddingValues(horizontal = side),
                    pageSpacing = 28.dp,
                    flingBehavior = PagerDefaults.flingBehavior(pager, pagerSnapDistance = PagerSnapDistance.atMost(1)),
                    verticalAlignment = Alignment.CenterVertically,
                ) { page ->
                    val summary by produceState<DeckSummary?>(null, decks[page]) {
                        value = withContext(Dispatchers.IO) { runCatching { DeckSummary.cached(decks[page]) }.getOrNull() }
                    }
                    val card = remember(summary?.artCard) {
                        summary?.artCard?.let { name -> runCatching { StaticData.instance().commonCards.getCard(name) }.getOrNull() }
                    }
                    val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                    Box(Modifier.graphicsLayer {
                        val s = 1f - 0.12f * offset
                        scaleX = s; scaleY = s
                        alpha = 1f - 0.45f * offset
                    }) {
                        if (card != null) {
                            val ui = remember(card) { CardUi.fromPaper(card) }
                            CardTile(ui, cardWidth, showTapped = false)
                        } else {
                            Box(Modifier.width(cardWidth).aspectRatio(63f / 88f).background(Palette.PaperDeep))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                val deck = decks[pager.currentPage]
                val summary by produceState<DeckSummary?>(null, deck) {
                    value = withContext(Dispatchers.IO) { runCatching { DeckSummary.cached(deck) }.getOrNull() }
                }
                Row(
                    Modifier
                        .widthIn(max = 720.dp)
                        .padding(horizontal = 16.dp)
                        .paperShadow(offsetY = 4.dp, blur = 12.dp, cover = Palette.PaperLight)
                        .paper(Palette.PaperLight)
                        .ornateFrame(inset = 3.dp)
                        .clickable(remember { MutableInteractionSource() }, indication = null) {}
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(summary?.title ?: deck.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        summary?.commander?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    summary?.colors?.forEach { ManaSymbol(ManaPip.of(it), 20.dp) }
                    summary?.value?.takeIf { it > 0 }?.let { Text(CardPrice.usd(it), style = MaterialTheme.typography.titleMedium, color = Palette.Slate) }
                    OrnateButton("Select", { onSelect(deck) }, style = OrnateStyle.Primary, height = 44.dp)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = Palette.InkSoft) }
                }
            }
        }
    }
}
