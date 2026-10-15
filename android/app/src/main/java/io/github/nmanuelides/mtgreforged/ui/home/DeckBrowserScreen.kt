package io.github.nmanuelides.mtgreforged.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.BiasAlignment
import io.github.nmanuelides.mtgreforged.ui.components.paperShadow
import io.github.nmanuelides.mtgreforged.ui.decks.fromPaper
import io.github.nmanuelides.mtgreforged.ui.components.CardTile
import io.github.nmanuelides.mtgreforged.match.CardUi
import forge.StaticData
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.CompositionLocalProvider
import io.github.nmanuelides.mtgreforged.engine.Favorites
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import io.github.nmanuelides.mtgreforged.ui.components.EntranceScope
import io.github.nmanuelides.mtgreforged.ui.components.landIn
import androidx.compose.ui.platform.LocalConfiguration
import io.github.nmanuelides.mtgreforged.ui.decks.StalePriceWarning
import io.github.nmanuelides.mtgreforged.ui.components.ManaPip
import io.github.nmanuelides.mtgreforged.ui.components.ManaSymbol
import androidx.compose.material.icons.filled.Style
import io.github.nmanuelides.mtgreforged.ui.components.paper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import forge.card.MagicColor
import io.github.nmanuelides.mtgreforged.engine.CardPrice
import io.github.nmanuelides.mtgreforged.engine.CardPrices
import io.github.nmanuelides.mtgreforged.engine.DeckSummary
import io.github.nmanuelides.mtgreforged.engine.Engine.DeckEntry
import io.github.nmanuelides.mtgreforged.ui.components.ArtImage
import io.github.nmanuelides.mtgreforged.ui.components.LocalArt
import io.github.nmanuelides.mtgreforged.ui.components.LocalArtImage
import io.github.nmanuelides.mtgreforged.ui.components.OrnateButton
import io.github.nmanuelides.mtgreforged.ui.components.OrnateDialog
import io.github.nmanuelides.mtgreforged.ui.components.OrnateDivider
import io.github.nmanuelides.mtgreforged.ui.components.OrnateStyle
import io.github.nmanuelides.mtgreforged.ui.components.Pill
import io.github.nmanuelides.mtgreforged.ui.components.SectionLabel
import io.github.nmanuelides.mtgreforged.ui.components.StatBar
import io.github.nmanuelides.mtgreforged.ui.components.ornateFrame
import io.github.nmanuelides.mtgreforged.ui.components.parchment
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val COLOR_NAMES = mapOf(
    MagicColor.WHITE to "White", MagicColor.BLUE to "Blue", MagicColor.BLACK to "Black",
    MagicColor.RED to "Red", MagicColor.GREEN to "Green",
)

private fun colorOf(c: Byte) = when (c) {
    MagicColor.WHITE -> Palette.Ochre
    MagicColor.BLUE -> Palette.Slate
    MagicColor.BLACK -> Palette.Plum
    MagicColor.RED -> Palette.Terracotta
    else -> Palette.Sage
}

private fun wallpaperFor(color: Byte?): LocalArt = when (color) {
    MagicColor.WHITE -> LocalArt.white
    MagicColor.BLUE -> LocalArt.blue
    MagicColor.BLACK -> LocalArt.black
    MagicColor.RED -> LocalArt.red
    MagicColor.GREEN -> LocalArt.green
    else -> listOf(LocalArt.SolRing)
}.random()

/** Character-sheet style deck chooser: one deck at a time, with arrows and a search list. */
@Composable
fun DeckBrowserScreen(
    title: String,
    decks: List<DeckEntry>,
    format: io.github.nmanuelides.mtgreforged.engine.Engine.Format? = null,
    initial: DeckEntry?,
    onSelect: (DeckEntry) -> Unit,
    onBack: () -> Unit,
    onNew: () -> Unit = {},
    onImport: () -> Unit = {},
    onEdit: (DeckEntry) -> Unit = {},
    onDelete: (DeckEntry) -> Unit = {},
) {
    var index by remember(decks) { mutableIntStateOf(decks.indexOf(initial).coerceAtLeast(0)) }
    var searching by remember { mutableStateOf(false) }
    var mineOnly by remember { mutableStateOf(false) }
    var carousel by remember { mutableStateOf<Int?>(null) }
    BackHandler(onBack = onBack)
    val entry = decks.getOrNull(index) ?: return
    val favVersion by Favorites.version.collectAsState()
    val favorite = remember(entry, favVersion) { format != null && Favorites.isFavorite(format, entry) }
    val favoriteStar: @Composable (Modifier) -> Unit = { m ->
        if (format != null) {
            OrnateButton(
                "", { Favorites.toggle(format, entry) }, m.width(52.dp),
                icon = if (favorite) Icons.Default.Star else Icons.Default.StarBorder, accent = Palette.Ochre, frameColor = Palette.Ochre,
            )
        }
    }
    CompositionLocalProvider(LocalFavoriteStar provides favoriteStar) {

    val prices by CardPrices.version.collectAsState()
    val summary by produceState<DeckSummary?>(null, entry, prices) {
        value = withContext(Dispatchers.IO) { runCatching { DeckSummary.cached(entry) }.getOrNull() }
    }

    EntranceScope {
    BoxWithConstraints(Modifier.fillMaxSize().parchment().ornateFrame(inset = 6.dp)) {
        val wide = maxWidth > maxHeight
        val content: @Composable (Modifier, Modifier) -> Unit = { artModifier, sheetModifier ->
            // Tablet: the sheet places the card itself, in a grid with the stats.
            if (!wide) ArtPanel(summary, artModifier, onOpen = { carousel = index })
            DeckSheet(
                art = { m -> ArtPanel(summary, m, onOpen = { carousel = index }, top = true) },
                title = title,
                summary = summary,
                position = "${index + 1} / ${decks.size}",
                onPrev = { index = (index - 1 + decks.size) % decks.size },
                onNext = { index = (index + 1) % decks.size },
                onSearch = { mineOnly = false; searching = true },
                onBack = onBack,
                onSelect = { onSelect(entry) },
                wide = wide,
                modifier = sheetModifier,
                manage = {
                    // Deck management: new / import always, edit / delete for the player's own decks.
                    // Coloured frames and icons so these read as actions, not as part of the sheet.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SectionLabel("Manage decks", color = Palette.InkSoft)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OrnateButton(
                                "New", onNew, Modifier.weight(1f), icon = Icons.Default.Add,
                                accent = Palette.Sage, frameColor = Palette.Sage, height = 44.dp,
                            )
                            OrnateButton(
                                "Import", onImport, Modifier.weight(1f), icon = Icons.Default.FileDownload,
                                accent = Palette.Slate, frameColor = Palette.Slate, height = 44.dp,
                            )
                            if (entry.custom) {
                                OrnateButton(
                                    "Delete", { onDelete(entry) }, Modifier.weight(1f), icon = Icons.Default.Delete,
                                    accent = Palette.Terracotta, frameColor = Palette.Terracotta, height = 44.dp,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (entry.custom) {
                                OrnateButton(
                                    "Edit", { onEdit(entry) }, Modifier.weight(1f), icon = Icons.Default.Edit,
                                    accent = Palette.Ochre, frameColor = Palette.Ochre, height = 44.dp,
                                )
                            }
                            OrnateButton(
                                "My decks", { mineOnly = true; searching = true }, Modifier.weight(1f), icon = Icons.Default.Style,
                                accent = Palette.Plum, frameColor = Palette.Plum, height = 44.dp,
                                enabled = decks.any { it.custom },
                            )
                            if (format != null) {
                                OrnateButton(
                                    "Favorite", { Favorites.toggle(format, entry) }, Modifier.weight(1f),
                                    icon = if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                                    style = if (favorite) OrnateStyle.Primary else OrnateStyle.Secondary,
                                    accent = Palette.Ochre, frameColor = Palette.Ochre, height = 44.dp,
                                )
                            }
                        }
                    }
                },
            )
        }
        if (wide) {
            Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(14.dp)) {
                // A narrow portrait of the deck's art; the sheet gets the rest of the width.
                content(Modifier, Modifier.fillMaxSize())
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(14.dp)) {
                content(
                    Modifier.fillMaxWidth().weight(0.28f).landIn(0),
                    Modifier.fillMaxWidth().weight(0.72f).padding(top = 10.dp),
                )
            }
        }
    }

    }

    if (searching) {
        DeckSearchDialog(
            if (mineOnly) decks.filter { it.custom } else decks,
            title = if (mineOnly) "My decks" else "Decks",
            onPick = { index = decks.indexOf(it); searching = false },
            onPreview = { searching = false; carousel = decks.indexOf(it) },
            onDismiss = { searching = false },
        )
    }
    }
    carousel?.let { start ->
        DeckCarousel(
            decks, start,
            onPage = { index = it },
            onSelect = { carousel = null; onSelect(it) },
            onDismiss = { carousel = null },
        )
    }
}

@Composable
private fun ArtPanel(summary: DeckSummary?, modifier: Modifier, onOpen: () -> Unit, top: Boolean = false) {
    // The commander (or signature card) as the whole card, at a size it looks sharp at,
    // standing on a framed paper mount.
    BoxWithConstraints(modifier, contentAlignment = if (top) Alignment.TopCenter else Alignment.Center) {
        val cardWidth = minOf(maxWidth - 40.dp, (maxHeight - 40.dp) * (63f / 88f), 320.dp)
        Box(
            Modifier
                .paperShadow(offsetY = 4.dp, blur = 14.dp, cover = Palette.PaperLight)
                .paper(Palette.PaperLight)
                .ornateFrame(Palette.LineStrong, inset = 4.dp, topGem = true)
                .clickable(onClick = onOpen)
                .padding(16.dp),
        ) {
            AnimatedContent(summary?.artCard, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "card") { name ->
                val card = remember(name) { name?.let { runCatching { StaticData.instance().commonCards.getCard(it) }.getOrNull() } }
                if (card != null) CardTile(remember(card) { CardUi.fromPaper(card) }, cardWidth, showTapped = false)
                else Box(Modifier.width(cardWidth).aspectRatio(63f / 88f).background(Palette.PaperDeep))
            }
        }
    }
}

/** The favourite-deck star, provided by the browser for the sheet header. */
private val LocalFavoriteStar = androidx.compose.runtime.staticCompositionLocalOf<@Composable (Modifier) -> Unit> { {} }

@Composable
private fun FavoriteStar(modifier: Modifier) = LocalFavoriteStar.current(modifier)

@Composable
private fun DeckSheet(
    title: String,
    summary: DeckSummary?,
    position: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onSelect: () -> Unit,
    wide: Boolean,
    modifier: Modifier,
    manage: @Composable () -> Unit = {},
    art: @Composable (Modifier) -> Unit = {},
) {
    val navigation: @Composable (Modifier) -> Unit = { m ->
        Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OrnateButton("", onPrev, Modifier.width(52.dp), icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft)
            OrnateButton(position, onSearch, Modifier.weight(1f), icon = Icons.Default.Search, style = OrnateStyle.Quiet)
            OrnateButton("", onNext, Modifier.width(52.dp), icon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
        }
    }
    if (wide) {
        // Tablet: header across the top, stats beside the key cards, and every action on one bottom row.
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().landIn(2)) {
                Box(Modifier.align(Alignment.Center)) { SheetHeader(title, summary) }
                OrnateButton("", onBack, Modifier.align(Alignment.TopStart).width(52.dp), icon = Icons.AutoMirrored.Filled.ArrowBack)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                // Commander card beside the stats (tops aligned); Manage decks spans under both.
                Column(Modifier.weight(1.6f).fillMaxHeight()) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        art(Modifier.weight(0.42f).fillMaxHeight().landIn(1))
                        Column(Modifier.weight(0.58f).fillMaxHeight()) {
                            summary?.let {
                                Box(Modifier.landIn(3)) { SheetStats(it) }
                                Spacer(Modifier.height(14.dp))
                                Box(Modifier.landIn(5)) { DeckFacts(it) }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.landIn(7)) { manage() }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    SectionLabel("Key cards", Modifier.landIn(3), color = Palette.InkSoft)
                    Spacer(Modifier.height(8.dp))
                    summary?.let { s ->
                        // The key cards stretch to the bottom of the sheet with tall art.
                        var viewing by remember { mutableStateOf<Int?>(null) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            s.featured.forEachIndexed { i, card ->
                                FeaturedCard(card, Modifier.weight(1f).fillMaxWidth().landIn(4 + i).clickable { viewing = i }, tall = true, pace = i)
                            }
                        }
                        viewing?.let { start -> KeyCardsViewer(s.featured, start) { viewing = null } }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.landIn(8), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                navigation(Modifier.weight(1.4f))
                OrnateButton("Select", onSelect, Modifier.weight(1f), style = OrnateStyle.Primary, height = 54.dp, enabled = summary != null)
            }
        }
        return
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().landIn(2)) {
                Box(Modifier.align(Alignment.Center)) { SheetHeader(title, summary) }
                OrnateButton("", onBack, Modifier.align(Alignment.TopStart).width(52.dp), icon = Icons.AutoMirrored.Filled.ArrowBack)
            }
            Spacer(Modifier.height(12.dp))
            summary?.let { s ->
                Box(Modifier.landIn(3)) { SheetStats(s) }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.landIn(4)) { DeckFacts(s) }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    s.featured.forEachIndexed { i, card -> FeaturedCard(card, Modifier.weight(1f).landIn(5 + i)) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.landIn(8)) { manage() }
        }

        Spacer(Modifier.height(10.dp))
        navigation(Modifier.landIn(9))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.landIn(10), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            OrnateButton("Select", onSelect, Modifier.weight(1.3f), style = OrnateStyle.Primary, height = 54.dp, enabled = summary != null)
        }
    }
}

@Composable
private fun SheetHeader(title: String, summary: DeckSummary?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SectionLabel(title)
        Spacer(Modifier.height(6.dp))
        Text(
            summary?.title ?: "…",
            style = if ((summary?.title?.length ?: 0) > 16) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayLarge,
            color = Palette.Terracotta,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** Commander, colours and value: under the stat bars, where there's room to show them big. */
@Composable
private fun DeckFacts(s: DeckSummary) {
    Column(
        Modifier.fillMaxWidth().paper(Palette.PaperLight).ornateFrame(Palette.Line, inset = 3.dp).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        s.commander?.let {
            Column {
                Text("COMMANDER", style = MaterialTheme.typography.labelSmall, color = Palette.InkSoft)
                Text(it, style = MaterialTheme.typography.headlineSmall, color = Palette.Slate, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (s.colors.isEmpty()) ManaSymbol(ManaPip.Colorless, 34.dp)
                s.colors.forEach { ManaSymbol(ManaPip.of(it), 34.dp) }
            }
            Spacer(Modifier.weight(1f))
            s.value?.takeIf { it > 0 }?.let { value ->
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(CardPrice.usd(value), style = MaterialTheme.typography.headlineMedium, color = Palette.Slate)
                        if (CardPrices.stale) StalePriceWarning()
                    }
                    Text("Card Kingdom · basic lands not counted", style = MaterialTheme.typography.labelSmall, color = Palette.InkSoft)
                }
            }
        }
    }
}

@Composable
private fun SheetStats(s: DeckSummary) {
    val total = s.cardCount.coerceAtLeast(1).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatBar("Creatures", s.creatures / total * 1.8f, "${s.creatures}", Palette.Sage, "✦")
        StatBar("Spells", s.spells / total * 1.8f, "${s.spells}", Palette.Slate, "✧")
        StatBar("Lands", s.lands / total * 1.8f, "${s.lands}", Palette.Ochre, "◆")
        StatBar("Mana value", s.averageCmc / 6f, "%.1f".format(s.averageCmc), Palette.Plum, "⬥")
    }
}

@Composable
private fun FeaturedCard(name: String, modifier: Modifier, tall: Boolean = false, pace: Int = 0) {
    // Tall art slides slowly from one side to the other and back, so all of it gets seen;
    // each card at its own speed and starting point so they never move in step.
    val pan by rememberInfiniteTransition(label = "pan").animateFloat(
        -1f, 1f,
        infiniteRepeatable(tween(9000 + pace * 2300, easing = FastOutSlowInEasing), RepeatMode.Reverse, StartOffset(pace * 1700)),
        label = "pan",
    )
    Column(
        modifier
            .paper(Palette.PaperLight)
            .ornateFrame(Palette.Line, inset = 3.dp)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            name.substringBefore(" //"),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            textAlign = TextAlign.Center, maxLines = if (tall) 1 else 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(if (tall) 16.dp else 30.dp),
        )
        Spacer(Modifier.height(6.dp))
        ArtImage(
            name,
            if (tall) Modifier.fillMaxWidth().weight(1f).border(1.dp, Palette.Line)
            else Modifier.fillMaxWidth(0.8f).aspectRatio(1f).clip(CircleShape).border(1.dp, Palette.Line, CircleShape),
            placeholder = Palette.PaperDeep,
            alignment = if (tall) BiasAlignment(0f, pan) else Alignment.Center,
        )
        Spacer(Modifier.height(6.dp))
        OrnateDivider(width = 50.dp, color = Palette.Line)
    }
}

@Composable
private fun DeckSearchDialog(
    decks: List<DeckEntry>,
    title: String,
    onPick: (DeckEntry) -> Unit,
    onPreview: (DeckEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, decks) { decks.filter { it.name.contains(query, ignoreCase = true) } }
    val listHeight = (LocalConfiguration.current.screenHeightDp * 0.62f).dp
    OrnateDialog(onDismiss = onDismiss, maxWidth = 720.dp) {
        SectionLabel(title)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            query, { query = it },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Palette.InkSoft) },
            placeholder = { Text("Search ${decks.size} decks", style = MaterialTheme.typography.bodyMedium) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Palette.Terracotta, unfocusedBorderColor = Palette.Line,
                focusedContainerColor = Palette.PaperLight, unfocusedContainerColor = Palette.PaperLight,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(Modifier.fillMaxWidth().height(listHeight).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.file.path }) { deck -> DeckCard(deck, { onPick(deck) }, { onPreview(deck) }) }
        }
    }
}

/** One deck in the search list: commander thumbnail (tap to see it big), name, colours, value. */
@Composable
private fun DeckCard(deck: DeckEntry, onPick: () -> Unit, onPreview: () -> Unit) {
    val summary by produceState<DeckSummary?>(null, deck) {
        value = withContext(Dispatchers.IO) { runCatching { DeckSummary.cached(deck) }.getOrNull() }
    }
    Row(
        Modifier.fillMaxWidth().paper(Palette.PaperLight).ornateFrame(Palette.Line, inset = 2.dp, corners = false)
            .clickable(onClick = onPick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArtImage(
            summary?.artCard,
            Modifier.size(width = 72.dp, height = 52.dp).border(1.dp, Palette.Line).clickable(onClick = onPreview),
            placeholder = Palette.PaperDeep,
            alignment = Alignment.Center,
        )
        Column(Modifier.weight(1f)) {
            Text(summary?.title ?: deck.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            summary?.commander?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { summary?.colors?.forEach { ManaSymbol(ManaPip.of(it), 18.dp) } }
        Text(
            summary?.value?.takeIf { it > 0 }?.let { CardPrice.usd(it) } ?: "",
            Modifier.width(64.dp), style = MaterialTheme.typography.titleSmall, color = Palette.Slate, textAlign = TextAlign.End,
        )
    }
}

/** The key cards shown whole, swiping between them. */
@Composable
private fun KeyCardsViewer(names: List<String>, start: Int, onDismiss: () -> Unit) {
    val pager = androidx.compose.foundation.pager.rememberPagerState(start) { names.size }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.72f))
                .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            val cardWidth = minOf((maxHeight - 80.dp) * (63f / 88f), maxWidth * 0.8f, 440.dp)
            androidx.compose.foundation.pager.HorizontalPager(
                pager, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = (maxWidth - cardWidth) / 2), pageSpacing = 28.dp,
            ) { page ->
                val card = remember(page) { runCatching { StaticData.instance().commonCards.getCard(names[page]) }.getOrNull() }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (card != null) CardTile(remember(card) { CardUi.fromPaper(card) }, cardWidth, showTapped = false)
                }
            }
        }
    }
}
