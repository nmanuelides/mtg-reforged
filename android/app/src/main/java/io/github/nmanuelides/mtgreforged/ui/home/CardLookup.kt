package io.github.nmanuelides.mtgreforged.ui.home

import io.github.nmanuelides.mtgreforged.ui.components.ornateFrame
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import forge.StaticData
import forge.item.PaperCard
import io.github.nmanuelides.mtgreforged.engine.CardPrice
import io.github.nmanuelides.mtgreforged.engine.CardPrices
import io.github.nmanuelides.mtgreforged.match.CardUi
import io.github.nmanuelides.mtgreforged.ui.components.CardTile
import io.github.nmanuelides.mtgreforged.ui.components.paper
import io.github.nmanuelides.mtgreforged.ui.components.paperShadow
import io.github.nmanuelides.mtgreforged.ui.decks.fieldColors
import io.github.nmanuelides.mtgreforged.ui.decks.fromPaper
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The closed search box sitting on the wallpaper, just above Play. */
@Composable
fun CardSearchPill(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .paperShadow(offsetY = 4.dp, blur = 12.dp, cover = Palette.PaperLight)
            .paper(Palette.PaperLight)
            // Same corner ornaments as the Play plaque.
            .ornateFrame(Palette.LineStrong, inset = 3.dp)
            .clickable(onClick = onOpen)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, null, tint = Palette.InkSoft)
        Spacer(Modifier.width(10.dp))
        Text("Look up a card…", style = MaterialTheme.typography.bodyLarge, color = Palette.InkSoft)
    }
}

/**
 * Full-screen card lookup. The search box rises from where the pill was ([from], in root
 * coordinates) to the top while the home screen darkens behind it; the keyboard opens.
 * Picking a name shows the card big with its price, and every printing with its price.
 */
@Composable
fun CardLookupOverlay(from: Rect?, onClose: () -> Unit) {
    val rise = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<String?>(null) }

    val close: () -> Unit = {
        keyboard?.hide()
        scope.launch { rise.animateTo(0f, tween(260, easing = FastOutSlowInEasing)); onClose() }
    }
    BackHandler { if (picked != null) picked = null else close() }
    LaunchedEffect(Unit) {
        rise.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }

    val names = remember { StaticData.instance().commonCards.uniqueCards.map { it.name }.filterNot { it.startsWith("A-") }.distinct().sorted() }
    val hits = remember(query) {
        val q = query.trim().lowercase()
        if (q.length < 2) emptyList()
        else names.filter { q in it.lowercase() }.sortedBy { !it.lowercase().startsWith(q) }.take(40)
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f * rise.value))
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        val density = LocalDensity.current
        val topY = with(density) { 24.dp.toPx() }
        val startY = from?.top ?: with(density) { maxHeight.toPx() * 0.7f }
        // The box keeps the pill's size and horizontal place; it only moves up.
        val boxWidth = with(density) { from?.width?.toDp() } ?: minOf(maxWidth - 48.dp, 520.dp)
        val boxLeft = with(density) { from?.left?.toDp() } ?: ((maxWidth - boxWidth) / 2)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier
                    .padding(start = boxLeft, top = 12.dp)
                    .graphicsLayer { translationY = (startY - topY) * (1f - rise.value) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    Modifier
                        .width(boxWidth)
                        .paperShadow(offsetY = 4.dp, blur = 12.dp, cover = Palette.PaperLight)
                        .paper(Palette.PaperLight)
                        .ornateFrame(Palette.LineStrong, inset = 3.dp)
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, null, tint = Palette.InkSoft)
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        query, { query = it; picked = null }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Ink),
                        cursorBrush = SolidColor(Palette.Terracotta),
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp).focusRequester(focus),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text("Look up a card…", style = MaterialTheme.typography.bodyLarge, color = Palette.InkSoft)
                            inner()
                        },
                    )
                }
                IconButton(onClick = close, Modifier.background(Palette.PaperLight, RoundedCornerShape(50))) {
                    Icon(Icons.Default.Close, "Close", tint = Palette.Ink)
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxSize().padding(horizontal = 20.dp).graphicsLayer { alpha = rise.value }) {
                val name = picked
                if (name == null) {
                    // Suggestions line up under the search box.
                    Column(Modifier.padding(start = (boxLeft - 20.dp).coerceAtLeast(0.dp)).width(boxWidth)) {
                        hits.forEach { hit ->
                            Text(
                                hit,
                                Modifier.fillMaxWidth().clickable { picked = hit; keyboard?.hide() }.padding(vertical = 10.dp, horizontal = 6.dp),
                                style = MaterialTheme.typography.titleMedium, color = Color.White,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                    CardDetail(name)
                }
            }
        }
    }
}

/** The card big with its price, then every printing with its own price. */
@Composable
private fun CardDetail(name: String) {
    val prices by CardPrices.version.collectAsState()
    val printings by produceState(emptyList<PaperCard>(), name) {
        value = withContext(Dispatchers.Default) {
            val db = StaticData.instance()
            runCatching { db.commonCards.getAllCards(name) }.getOrNull().orEmpty()
                .sortedByDescending { db.getCardEdition(it.edition)?.date?.time ?: 0L }
        }
    }
    if (printings.isEmpty()) return
    val main = remember(printings, prices) { printings.minByOrNull { CardPrices.of(it)?.takeIf { p -> p.exact }?.retail ?: Float.MAX_VALUE } ?: printings.first() }
    LazyVerticalGrid(GridCells.Adaptive(150.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            // The cheapest printing, big and centred, with its price underneath.
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                CardTile(remember(main) { CardUi.fromPaper(main) }, 300.dp, showTapped = false)
                Spacer(Modifier.height(12.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(name, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    val price = remember(main, prices) { CardPrices.cheapest(name) }
                    Text(price?.let { CardPrice.usd(it.retail) } ?: "No price", style = MaterialTheme.typography.displayMedium, color = Palette.Ochre)
                    Text("Cheapest printing at Card Kingdom", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.height(6.dp))
                    Text("${printings.size} printings", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
        items(printings, key = { "${it.edition}|${it.collectorNumber}|${it.artIndex}" }) { p ->
            val price = remember(p, prices) { CardPrices.of(p)?.takeIf { it.exact } }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CardTile(remember(p) { CardUi.fromPaper(p) }, 150.dp, showTapped = false)
                Text(
                    StaticData.instance().getCardEdition(p.edition)?.name ?: p.edition,
                    style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(price?.let { CardPrice.usd(it.retail) } ?: "—", style = MaterialTheme.typography.titleMedium, color = Palette.Ochre)
            }
        }
    }
}
