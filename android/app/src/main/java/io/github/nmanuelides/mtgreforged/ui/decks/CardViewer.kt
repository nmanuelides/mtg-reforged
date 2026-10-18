package io.github.nmanuelides.mtgreforged.ui.decks

import androidx.compose.runtime.CompositionLocalProvider
import io.github.nmanuelides.mtgreforged.ui.components.OrnateButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.Check
import io.github.nmanuelides.mtgreforged.engine.CardCollection
import forge.StaticData
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
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
    /** Editing the collection: a new printing replaces only the copies of the one on screen. */
    collection: Boolean = false,
    onDismiss: () -> Unit,
) {
    if (cards.isEmpty()) return
    val pager = rememberPagerState(start.coerceIn(0, cards.lastIndex)) { cards.size }
    val prices by CardPrices.version.collectAsState()
    // Printing picked per page with a vertical swipe; until then, the card as listed.
    val picked = remember { mutableStateMapOf<Int, PaperCard>() }
    fun shown(page: Int) = picked[page] ?: cards[page]
    fun swap(page: Int, next: PaperCard) {
        val old = shown(page)
        picked[page] = next
        if (collection) draft.replacePrinting(old, next) else draft.usePrintingIfInDeck(next)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalCollectionMode provides collection) {
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
                val current = shown(pager.currentPage)
                val printings = remember(current.name) { printingsOf(current) }
                Text(
                    "${pager.currentPage + 1} / ${cards.size}",
                    fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f),
                )
                Text(
                    editionLabel(current) + if (printings.size > 1) {
                        "  ·  printing ${printings.indexOf(current) + 1} of ${printings.size}  ·  swipe ↑↓"
                    } else "",
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f),
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
                    val card = shown(page)
                    val pagePrintings = remember(card.name) { printingsOf(card) }
                    var direction by remember { mutableIntStateOf(1) }
                    val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
                    Box(
                        Modifier
                            .graphicsLayer {
                                val s = 1f - 0.12f * offset
                                scaleX = s; scaleY = s
                                alpha = 1f - 0.45f * offset
                            }
                            // Up / down: the next / previous printing. The one showing is the one the deck uses.
                            .pointerInput(page, pagePrintings) {
                                var dragged = 0f
                                detectVerticalDragGestures(
                                    onDragStart = { dragged = 0f },
                                    onVerticalDrag = { change, dy -> dragged += dy; change.consume() },
                                    onDragEnd = {
                                        if (pagePrintings.size > 1 && dragged.absoluteValue > threshold) {
                                            val step = if (dragged < 0) 1 else -1
                                            val now = pagePrintings.indexOf(shown(page)).coerceAtLeast(0)
                                            val next = pagePrintings[(now + step + pagePrintings.size) % pagePrintings.size]
                                            direction = step
                                            swap(page, next)
                                        }
                                    },
                                )
                            },
                    ) {
                        AnimatedContent(
                            card,
                            transitionSpec = {
                                (slideInVertically { it * direction / 3 } + fadeIn()) togetherWith
                                    (slideOutVertically { -it * direction / 3 } + fadeOut())
                            },
                            label = "printing",
                        ) { printing ->
                            val ui = remember(printing) { CardUi.fromPaper(printing) }
                            CardTile(ui, cardWidth, showTapped = false)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                ViewerBar(current, draft, commanderFormat, remember(current, prices) { CardPrices.of(current) }, onDismiss) { other ->
                    swap(pager.currentPage, other)
                }
            }
        }
        }
    }
}

/** Every printing of [card]'s name, newest first. */
private fun printingsOf(card: PaperCard): List<PaperCard> {
    val db = StaticData.instance()
    val all = runCatching { db.commonCards.getAllCards(card.name) }.getOrNull().orEmpty()
    return all.ifEmpty { listOf(card) }
        .map { if (card.isFoil) it.foiled else it }
        .sortedByDescending { db.getCardEdition(it.edition)?.date?.time ?: 0L }
        .let { if (card in it) it else listOf(card) + it }
}

private fun editionLabel(card: PaperCard): String {
    val edition = StaticData.instance().getCardEdition(card.edition)
    val year = edition?.date?.let { java.text.SimpleDateFormat("yyyy", java.util.Locale.US).format(it) }
    return (edition?.name ?: card.edition) + (year?.let { " ($it)" } ?: "")
}

/** Swapping printings only touches the deck when that card is already in it. */
private fun DeckDraft.usePrintingIfInDeck(card: PaperCard) {
    if (countOf(card.name) > 0) usePrinting(card)
}

@Composable
private fun ViewerBar(
    card: PaperCard,
    draft: DeckDraft,
    commanderFormat: Boolean,
    price: io.github.nmanuelides.mtgreforged.engine.CardPrice?,
    onClose: () -> Unit,
    onFinish: (PaperCard) -> Unit = {},
) {
    val collectionMode = LocalCollectionMode.current
    val isCommander = draft.commander.keys.any { it.name == card.name }
    // The collection counts this exact printing; a deck counts the card whatever the printing.
    val count = if (collectionMode) draft.main[card] ?: 0 else draft.countOf(card.name)
    val total = draft.countOf(card.name)
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
                when {
                    collectionMode -> "$count of this printing" + if (total > count) " · $total in all" else ""
                    count == 0 -> "not in deck"
                    draft.main.containsKey(card) || draft.commander.containsKey(card) -> "$count in deck · this printing"
                    else -> "$count in deck · another printing (+ / − switch to this one)"
                },
                style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft,
            )
        }
        PriceTag(price)
        // Finish: the printing on screen, regular or foil (the deck follows the choice when the card is in it).
        // Always offered: Forge allows any printing as foil; the price just shows "—" if Card Kingdom has none.
        run {
            Chip(if (card.isFoil) "✦ Foil" else "Regular", card.isFoil, fontSize = 12) { onFinish(if (card.isFoil) card.unFoiled else card.foiled) }
        }
        // Building a deck: one tap adds a card you don't own yet to your collection.
        if (!LocalCollectionMode.current) {
            val owned by CardCollection.version.collectAsState()
            val has = remember(card.name, owned) { CardCollection.owns(card.name) }
            val scope = rememberCoroutineScope()
            if (has) {
                val (regular, foil) = remember(card.name, owned) { CardCollection.countByFinish(card.name) }
                Icon(Icons.Default.Check, "In your collection", tint = Palette.Sage)
                Text(
                    listOfNotNull(regular.takeIf { it > 0 }?.let { "$it" }, foil.takeIf { it > 0 }?.let { "$it foil" }).joinToString(" + "),
                    style = MaterialTheme.typography.labelMedium, color = Palette.Sage,
                )
            } else {
                OrnateButton(
                    "Collection", {
                        scope.launch(Dispatchers.IO) {
                            val cards = CardCollection.cards().toMutableMap()
                            cards[card] = (cards[card] ?: 0) + 1
                            CardCollection.save(cards)
                        }
                    },
                    icon = Icons.Default.Add, accent = Palette.Sage, frameColor = Palette.Sage, height = 40.dp,
                )
            }
        }
        if (commanderFormat && canLead(card)) {
            IconButton(onClick = { draft.usePrinting(card); if (isCommander) draft.toggleCommander(card) else draft.makeCommander(card) }) {
                Icon(if (isCommander) Icons.Default.Star else Icons.Default.StarBorder, "Commander", tint = Palette.Ochre)
            }
        }
        IconButton(onClick = {
            if (!collectionMode) draft.usePrinting(card)
            draft.remove(card, fromCommander = isCommander && (draft.main[card] ?: 0) == 0)
        }, enabled = count > 0) {
            Icon(Icons.Default.Remove, "Remove one", tint = if (count > 0) Palette.Terracotta else Palette.Line)
        }
        Box(Modifier.size(30.dp).background(Palette.Paper, CircleShape), contentAlignment = Alignment.Center) {
            Text("$count", fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Palette.Ink)
        }
        IconButton(onClick = { if (!collectionMode) draft.usePrinting(card); draft.add(card) }, enabled = !isCommander) {
            Icon(Icons.Default.Add, "Add one", tint = if (!isCommander) Palette.Sage else Palette.Line)
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = Palette.InkSoft) }
    }
}
