package io.github.nmanuelides.mtgreforged.ui.home

import io.github.nmanuelides.mtgreforged.ui.components.LandFrom
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

    val prices by CardPrices.version.collectAsState()
    val summary by produceState<DeckSummary?>(null, entry, prices) {
        value = withContext(Dispatchers.IO) { runCatching { DeckSummary.cached(entry) }.getOrNull() }
    }

    BoxWithConstraints(Modifier.fillMaxSize().parchment().ornateFrame(inset = 6.dp)) {
        val wide = maxWidth > maxHeight
        val content: @Composable (Modifier, Modifier) -> Unit = { artModifier, sheetModifier ->
            ArtPanel(summary, artModifier.clickable { carousel = index })
            DeckSheet(
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
                            OrnateButton(
                                "My decks", { mineOnly = true; searching = true }, Modifier.weight(1f), icon = Icons.Default.Style,
                                accent = Palette.Plum, frameColor = Palette.Plum, height = 44.dp,
                                enabled = decks.any { it.custom },
                            )
                        }
                        // The player's own decks get a second row, so no label gets cut short.
                        if (entry.custom) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OrnateButton(
                                    "Edit", { onEdit(entry) }, Modifier.weight(1f), icon = Icons.Default.Edit,
                                    accent = Palette.Ochre, frameColor = Palette.Ochre, height = 44.dp,
                                )
                                OrnateButton(
                                    "Delete", { onDelete(entry) }, Modifier.weight(1f), icon = Icons.Default.Delete,
                                    accent = Palette.Terracotta, frameColor = Palette.Terracotta, height = 44.dp,
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
                content(
                    Modifier.weight(0.3f).fillMaxHeight().landIn(0, LandFrom.Left),
                    Modifier.weight(0.7f).fillMaxHeight().padding(start = 24.dp).landIn(1, LandFrom.Right),
                )
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(14.dp)) {
                content(
                    Modifier.fillMaxWidth().weight(0.4f).landIn(0, LandFrom.Top),
                    Modifier.fillMaxWidth().weight(0.6f).padding(top = 12.dp).landIn(1, LandFrom.Bottom),
                )
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
private fun ArtPanel(summary: DeckSummary?, modifier: Modifier) {
    val accent = summary?.colors?.firstOrNull()?.let(::colorOf) ?: Palette.Sage
    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .border(2.dp, Palette.LineStrong)
                .padding(6.dp)
                .clip(androidx.compose.ui.graphics.RectangleShape),
        ) {
            // One of the bundled wallpapers in the deck's colour shows until the card art arrives.
            val backdrop = remember(summary?.colors?.firstOrNull()) { wallpaperFor(summary?.colors?.firstOrNull()) }
            LocalArtImage(backdrop, Modifier.fillMaxSize())
            AnimatedContent(summary?.artCard, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "art") { card ->
                ArtImage(card, Modifier.fillMaxSize())
            }
        }
    }
}

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
            SheetHeader(title, summary)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    summary?.let { SheetStats(it); Spacer(Modifier.height(14.dp)); DeckFacts(it) }
                    Spacer(Modifier.weight(1f).height(18.dp))
                    manage()
                }
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    SectionLabel("Key cards", color = Palette.InkSoft)
                    Spacer(Modifier.height(8.dp))
                    summary?.let { s ->
                        // The key cards stretch to the bottom of the sheet with tall art.
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            s.featured.forEach { FeaturedCard(it, Modifier.weight(1f).fillMaxHeight(), tall = true) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                navigation(Modifier.weight(1.4f))
                OrnateButton("Back", onBack, Modifier.weight(0.8f), accent = Palette.Slate, height = 54.dp)
                OrnateButton("Select", onSelect, Modifier.weight(1f), style = OrnateStyle.Primary, height = 54.dp, enabled = summary != null)
            }
        }
        return
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            SheetHeader(title, summary)
            Spacer(Modifier.height(12.dp))
            summary?.let { s ->
                SheetStats(s)
                Spacer(Modifier.height(12.dp))
                DeckFacts(s)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    s.featured.forEach { FeaturedCard(it, Modifier.weight(1f)) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        manage()
        Spacer(Modifier.height(8.dp))
        navigation(Modifier)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            OrnateButton("Back", onBack, Modifier.weight(1f), accent = Palette.Slate, height = 54.dp)
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
private fun FeaturedCard(name: String, modifier: Modifier, tall: Boolean = false) {
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
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(30.dp),
        )
        Spacer(Modifier.height(6.dp))
        ArtImage(
            name,
            if (tall) Modifier.fillMaxWidth().weight(1f).border(1.dp, Palette.Line)
            else Modifier.fillMaxWidth(0.8f).aspectRatio(1f).clip(CircleShape).border(1.dp, Palette.Line, CircleShape),
            placeholder = Palette.PaperDeep,
            alignment = Alignment.Center,
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
