package io.github.nmanuelides.mtgreforged.ui.decks

import io.github.nmanuelides.mtgreforged.ui.components.ManaCost
import io.github.nmanuelides.mtgreforged.ui.components.ManaPip
import io.github.nmanuelides.mtgreforged.ui.components.ManaChip
import io.github.nmanuelides.mtgreforged.ui.components.paperShadow
import io.github.nmanuelides.mtgreforged.ui.components.paper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import forge.StaticData
import forge.card.MagicColor
import forge.deck.Deck
import forge.deck.DeckSection
import forge.item.PaperCard
import io.github.nmanuelides.mtgreforged.engine.CardPrice
import io.github.nmanuelides.mtgreforged.engine.CardPrices
import io.github.nmanuelides.mtgreforged.engine.DeckImport
import io.github.nmanuelides.mtgreforged.engine.Engine
import io.github.nmanuelides.mtgreforged.engine.UserDecks
import io.github.nmanuelides.mtgreforged.match.CardUi
import io.github.nmanuelides.mtgreforged.ui.components.CardTile
import io.github.nmanuelides.mtgreforged.ui.components.HoldZoom
import io.github.nmanuelides.mtgreforged.ui.components.OrnateButton
import io.github.nmanuelides.mtgreforged.ui.components.OrnateDialog
import io.github.nmanuelides.mtgreforged.ui.components.OrnateStyle
import io.github.nmanuelides.mtgreforged.ui.components.SectionLabel
import io.github.nmanuelides.mtgreforged.ui.components.ornateFrame
import io.github.nmanuelides.mtgreforged.ui.components.parchment
import io.github.nmanuelides.mtgreforged.ui.components.tapOrHold
import io.github.nmanuelides.mtgreforged.ui.theme.Cinzel
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val QUICK_TYPES = listOf("Creature", "Instant", "Sorcery", "Artifact", "Enchantment", "Planeswalker", "Land")

internal fun canLead(card: PaperCard) =
    card.rules.canBeCommander() || (card.rules.type.isLegendary && (card.rules.type.isCreature || card.rules.type.isPlaneswalker))

/** Editable deck: copies per card, split into the command zone and the main deck. */
internal class DeckDraft(val commander: SnapshotStateMap<PaperCard, Int>, val main: SnapshotStateMap<PaperCard, Int>) {
    val total get() = commander.values.sum() + main.values.sum()

    // Not `Deck(name).apply { … }`: inside it, `main` would resolve to Forge's Deck.getMain()
    // (the new, empty pool) instead of this draft's cards, and saves would drop the main deck.
    fun toDeck(name: String): Deck {
        val deck = Deck(name)
        commander.forEach { (c, n) -> deck.getOrCreate(DeckSection.Commander).add(c, n) }
        main.forEach { (c, n) -> deck.getOrCreate(DeckSection.Main).add(c, n) }
        return deck
    }

    fun load(deck: Deck) {
        commander.clear()
        main.clear()
        deck.get(DeckSection.Commander)?.forEach { commander[it.key] = it.value }
        deck.get(DeckSection.Main)?.forEach { main[it.key] = it.value }
    }

    fun add(card: PaperCard) {
        main[card] = (main[card] ?: 0) + 1
    }

    fun remove(card: PaperCard, fromCommander: Boolean) {
        val pool = if (fromCommander) commander else main
        val n = (pool[card] ?: 0) - 1
        if (n <= 0) pool.remove(card) else pool[card] = n
    }

    /** Every card in the order the deck is shown: commander, creatures, spells, lands. */
    fun ordered(): List<PaperCard> {
        val byCurve = compareBy<PaperCard>({ it.rules.manaCost.cmc }, { it.name })
        val cards = main.keys.toList()
        return commander.keys.sortedBy { it.name } +
            cards.filter { it.rules.type.isCreature }.sortedWith(byCurve) +
            cards.filter { !it.rules.type.isCreature && !it.rules.type.isLand }.sortedWith(byCurve) +
            cards.filter { it.rules.type.isLand }.sortedWith(byCurve)
    }

    /** Copies of [name] in the deck, whatever the printing. */
    fun countOf(name: String) =
        commander.entries.filter { it.key.name == name }.sumOf { it.value } + main.entries.filter { it.key.name == name }.sumOf { it.value }

    /** Makes [card] the printing the deck uses for its name, keeping copies and commander status. */
    fun usePrinting(card: PaperCard) {
        for (pool in listOf(commander, main)) {
            pool.keys.filter { it.name == card.name && it != card }.forEach { old ->
                val n = pool.remove(old) ?: 0
                pool[card] = (pool[card] ?: 0) + n
            }
        }
    }

    /** Puts [card] in the command zone, taking a copy out of the main deck if it's there. */
    fun makeCommander(card: PaperCard) {
        if (commander.containsKey(card)) return
        if (main.containsKey(card)) remove(card, fromCommander = false)
        commander[card] = 1
    }

    fun toggleCommander(card: PaperCard) {
        if (commander.containsKey(card)) {
            commander.remove(card)
            add(card)
        } else {
            remove(card, fromCommander = false)
            commander[card] = 1
        }
    }
}

internal fun CardUi.Companion.fromPaper(card: PaperCard): CardUi {
    val rules = card.rules
    return CardUi(
        id = System.identityHashCode(card),
        name = card.name,
        imageKey = card.getImageKey(false),
        manaCost = rules.manaCost.toString(),
        typeLine = rules.type.toString(),
        text = rules.oracleText.orEmpty().replace("\\n", "\n"),
        power = rules.power?.toIntOrNull(),
        toughness = rules.toughness?.toIntOrNull(),
        loyalty = rules.initialLoyalty?.takeIf { it.isNotBlank() },
        isCreature = rules.type.isCreature,
        isLand = rules.type.isLand,
        tapped = false, sick = false, faceDown = false, token = false, damage = 0,
        counters = emptyList(), attachedToId = null, controllerId = null,
    )
}

/**
 * Deck editor: search the whole card pool on one side, the deck on the other. Opens on
 * [initial] (a saved deck) or empty; [importFirst] opens straight into the importer.
 */
@Composable
fun DeckBuilderScreen(
    format: Engine.Format,
    initial: Engine.DeckEntry?,
    importFirst: Boolean,
    onDone: (Engine.DeckEntry?) -> Unit,
) {
    val draft = remember { DeckDraft(mutableStateMapOf(), mutableStateMapOf()) }
    var name by remember { mutableStateOf(initial?.name ?: "New deck") }
    var importing by remember { mutableStateOf(importFirst) }
    var problem by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(CardFilter()) }
    var filtering by remember { mutableStateOf(false) }
    var wideLayout by remember { mutableStateOf(true) }
    var matches by remember { mutableIntStateOf(0) }
    var viewing by remember { mutableStateOf<Pair<List<PaperCard>, Int>?>(null) }
    val open: (List<PaperCard>, Int) -> Unit = { cards, index -> viewing = cards to index }
    val scope = rememberCoroutineScope()
    BackHandler { onDone(null) }

    LaunchedEffect(initial) {
        initial?.let { entry ->
            val deck = withContext(Dispatchers.IO) { Engine.loadDeck(entry) }
            draft.load(deck)
        }
    }
    // Forge's own legality check for the format, re-run as the deck changes.
    LaunchedEffect(draft.total, draft.commander.size) {
        delay(300)
        problem = withContext(Dispatchers.Default) {
            runCatching { format.gameType.deckFormat.getDeckConformanceProblem(draft.toDeck(name)) }.getOrNull()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().parchment()) {
        val wide = maxWidth > maxHeight
        wideLayout = wide
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp)) {
            Header(
                name = name, onName = { name = it }, format = format,
                onBack = { onDone(null) },
                onImport = { importing = true },
                onSave = {
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) { UserDecks.save(draft.toDeck(name), format, initial) }
                        onDone(saved)
                    }
                },
                canSave = draft.total > 0,
            )
            Spacer(Modifier.height(10.dp))
            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CardSearch(Modifier.weight(0.58f).fillMaxHeight(), draft, format == Engine.Format.Commander, filter, { filter = it }, { filtering = !filtering }, { matches = it }, open) { draft.add(it) }
                    // On tablets the filters take the deck's place, so results update beside them.
                    if (filtering) {
                        FilterPanel(
                            filter, { filter = it }, matches, onDone = { filtering = false },
                            Modifier.weight(0.42f).fillMaxHeight().paper(Palette.PaperLight).ornateFrame(inset = 4.dp).padding(14.dp),
                        )
                    } else {
                        DeckList(draft, format, problem, open, Modifier.weight(0.42f).fillMaxHeight())
                    }
                }
            } else {
                CardSearch(Modifier.fillMaxWidth().weight(0.55f), draft, format == Engine.Format.Commander, filter, { filter = it }, { filtering = true }, { matches = it }, open) { draft.add(it) }
                Spacer(Modifier.height(8.dp))
                DeckList(draft, format, problem, open, Modifier.fillMaxWidth().weight(0.45f))
            }
        }
    }

    if (filtering && !wideLayout) {
        OrnateDialog(onDismiss = { filtering = false }, maxWidth = 640.dp) {
            FilterPanel(filter, { filter = it }, matches, onDone = { filtering = false }, Modifier.heightIn(max = 640.dp))
        }
    }

    viewing?.let { (cards, index) ->
        CardViewer(cards, index, draft, format == Engine.Format.Commander) { viewing = null }
    }

    if (importing) {
        ImportDialog(
            commanderFormat = format == Engine.Format.Commander,
            onDismiss = { importing = false },
            onImported = { result ->
                draft.load(result.deck)
                if (initial == null || name == "New deck") name = result.deck.name
                importing = false
            },
        )
    }
}

@Composable
private fun Header(
    name: String,
    onName: (String) -> Unit,
    format: Engine.Format,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onSave: () -> Unit,
    canSave: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OrnateButton("", onBack, Modifier.width(52.dp), icon = Icons.AutoMirrored.Filled.ArrowBack)
        OutlinedTextField(
            name, onName, singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge,
            label = { Text("${format.label} deck", style = MaterialTheme.typography.labelSmall) },
            colors = fieldColors(),
            modifier = Modifier.weight(1f),
        )
        OrnateButton("Import", onImport, icon = Icons.Default.FileDownload, accent = Palette.Slate)
        OrnateButton("Save", onSave, style = OrnateStyle.Primary, icon = Icons.Default.Save, enabled = canSave)
    }
}

@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.Terracotta, unfocusedBorderColor = Palette.Line,
    focusedContainerColor = Palette.PaperLight, unfocusedContainerColor = Palette.PaperLight,
    focusedTextColor = Palette.Ink, unfocusedTextColor = Palette.Ink,
)

// ---- Search ------------------------------------------------------------------------

@Composable
private fun CardSearch(
    modifier: Modifier,
    draft: DeckDraft,
    commanderFormat: Boolean,
    filter: CardFilter,
    onFilter: (CardFilter) -> Unit,
    onOpenFilters: () -> Unit,
    onCount: (Int) -> Unit,
    onOpen: (List<PaperCard>, Int) -> Unit,
    onAdd: (PaperCard) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PaperCard>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    val prices by CardPrices.version.collectAsState()

    LaunchedEffect(query, filter, prices) {
        delay(250) // debounce typing
        searching = true
        val (found, count) = withContext(Dispatchers.Default) {
            runCatching { filter.search(query) }.getOrElse { emptyList<PaperCard>() to 0 }
        }
        results = found
        total = count
        onCount(count)
        searching = false
    }

    Column(modifier.paper(Palette.PaperLight).ornateFrame(inset = 4.dp).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                query, { query = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Palette.InkSoft) },
                placeholder = {
                    Text(if (filter.exactName) "Exact card name" else "Search cards by name or rules text", style = MaterialTheme.typography.bodyMedium)
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = fieldColors(),
                modifier = Modifier.weight(1f),
            )
            val active = filter.activeCount
            OrnateButton(
                if (active > 0) "Filters · $active" else "Filters", onOpenFilters,
                icon = Icons.Default.Tune, accent = Palette.Plum, frameColor = Palette.Plum, height = 56.dp,
            )
        }
        Spacer(Modifier.height(8.dp))
        // Quick toggles for the most used filters; the panel has the rest.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            ManaPip.WUBRG.forEach { pip ->
                val color = pip.color
                ManaChip(pip, color in filter.colors) {
                    onFilter(filter.copy(colors = if (color in filter.colors) filter.colors - color else filter.colors + color))
                }
            }
            Spacer(Modifier.width(6.dp))
            QUICK_TYPES.forEach { type ->
                Chip(type.take(5), type in filter.types.values && filter.types.mode != GroupMode.Not) {
                    onFilter(filter.copy(types = filter.types.toggle(type)))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (total > 0) {
            Text(
                if (total > results.size) "Showing ${results.size} of $total cards" else "$total cards",
                style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft,
            )
            Spacer(Modifier.height(4.dp))
        }
        val idle = query.isBlank() && filter.activeCount == 0
        // With nothing searched, this side shows the deck itself as cards.
        if (idle && draft.total > 0) {
            DeckGallery(draft, commanderFormat, onOpen, Modifier.fillMaxSize())
            return@Column
        }
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                GridCells.Adaptive(118.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(results, key = { _, it -> "${it.name}|${it.edition}|${it.artIndex}" }) { index, card ->
                    val ui = remember(card) { CardUi.fromPaper(card) }
                    var held by remember { mutableStateOf(false) }
                    val price = remember(card, prices) { CardPrices.of(card) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.tapOrHold(card.name, onTap = { onOpen(results, index) }, onHold = { h, _ -> held = h })) {
                            CardTile(ui, 118.dp, showTapped = false)
                            if (commanderFormat && canLead(card)) {
                                CommanderStar(draft.commander.containsKey(card), Modifier.align(Alignment.TopStart)) { draft.makeCommander(card) }
                            }
                            HoldZoom(ui, held, caption = price?.let(::zoomCaption))
                        }
                        Row(Modifier.width(118.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { PriceTag(price) }
                            Icon(
                                Icons.Default.Add, "Add to deck",
                                Modifier.size(32.dp).clickable { onAdd(card) }.padding(4.dp),
                                tint = Palette.Sage,
                            )
                        }
                    }
                }
            }
            if (searching) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Palette.Terracotta)
            if (!searching && results.isEmpty()) {
                Text(
                    if (idle) "Type a name or pick filters." else "No cards match.",
                    Modifier.align(Alignment.Center), color = Palette.InkSoft,
                )
            }
        }
    }
}

@Composable
internal fun Chip(label: String, selected: Boolean, fontSize: Int = 11, onClick: () -> Unit) {
    Text(
        label,
        Modifier
            .paperShadow(offsetY = 1.dp, blur = 3.dp, cover = if (selected) Palette.Terracotta else Palette.Paper, darkness = 90)
            .paper(if (selected) Palette.Terracotta else Palette.Paper)
            .ornateFrame(if (selected) Palette.OnAccent.copy(alpha = 0.6f) else Palette.Line, inset = 2.dp, corners = false)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        fontFamily = Cinzel, fontSize = fontSize.sp, fontWeight = FontWeight.Bold,
        color = if (selected) Palette.OnAccent else Palette.InkSoft,
    )
}

// ---- Deck gallery ------------------------------------------------------------------

/** The deck as card images, grouped like the list, with copies and −/+ under each card. */
@Composable
private fun DeckGallery(draft: DeckDraft, commanderFormat: Boolean, onOpen: (List<PaperCard>, Int) -> Unit, modifier: Modifier) {
    val main = draft.main.entries.map { it.key to it.value }
    val groups = listOf(
        "Commander" to draft.commander.entries.map { it.key to it.value },
        "Creatures" to main.filter { it.first.rules.type.isCreature },
        "Spells" to main.filter { !it.first.rules.type.isCreature && !it.first.rules.type.isLand },
        "Lands" to main.filter { it.first.rules.type.isLand },
    ).filter { it.second.isNotEmpty() }

    LazyVerticalGrid(
        GridCells.Adaptive(118.dp),
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Your deck · ${draft.total} cards — search above to add more",
                style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft,
            )
        }
        groups.forEach { (label, cards) ->
            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("$label · ${cards.sumOf { it.second }}") }
            items(cards.sortedBy { it.first.rules.manaCost.cmc * 1000 + it.first.name.first().code }, key = { label + it.first.name + it.first.edition }) { (card, n) ->
                val commander = label == "Commander"
                val ui = remember(card) { CardUi.fromPaper(card) }
                var held by remember { mutableStateOf(false) }
                val prices by CardPrices.version.collectAsState()
                val price = remember(card, prices) { CardPrices.of(card) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val openHere = { val all = draft.ordered(); onOpen(all, all.indexOf(card).coerceAtLeast(0)) }
                    Box(Modifier.tapOrHold(card.name, onTap = { openHere() }, onHold = { h, _ -> held = h })) {
                        CardTile(ui, 118.dp, showTapped = false)
                        if (n > 1) {
                            Text(
                                "×$n",
                                Modifier.align(Alignment.TopEnd).padding(4.dp)
                                    .background(Palette.Terracotta, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
                                fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Palette.OnAccent,
                            )
                        }
                        if (commanderFormat && canLead(card)) {
                            CommanderStar(commander, Modifier.align(Alignment.TopStart)) { draft.toggleCommander(card) }
                        }
                        HoldZoom(ui, held, caption = price?.let(::zoomCaption))
                    }
                    Row(Modifier.width(118.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Remove, "Remove one",
                            Modifier.size(30.dp).clickable { draft.remove(card, fromCommander = commander) }.padding(4.dp),
                            tint = Palette.Terracotta,
                        )
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { PriceTag(price) }
                        if (!commander) {
                            Icon(
                                Icons.Default.Add, "Add one",
                                Modifier.size(30.dp).clickable { draft.add(card) }.padding(4.dp),
                                tint = Palette.Sage,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- Commander -----------------------------------------------------------------------

/** Round ☆ badge on a card tile: filled when it's the commander, tap to (un)set. */
@Composable
private fun CommanderStar(selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.padding(4.dp).size(34.dp)
            .background(if (selected) Palette.Ochre else Palette.PaperLight.copy(alpha = 0.92f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (selected) Icons.Default.Star else Icons.Default.StarBorder, "Commander",
            Modifier.size(22.dp), tint = if (selected) Palette.OnAccent else Palette.Ochre,
        )
    }
}

/** Shown while a Commander deck has no commander: one tap on a candidate sets it. */
@Composable
private fun CommanderPicker(draft: DeckDraft) {
    val candidates = draft.main.keys.filter(::canLead).sortedBy { it.name }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .paper(Palette.Paper).ornateFrame(Palette.Ochre, inset = 3.dp, corners = false).padding(12.dp),
    ) {
        Text("Choose your commander", style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
        Spacer(Modifier.height(6.dp))
        if (candidates.isEmpty()) {
            Text(
                "No card in this deck can be a commander. Search for yours on the left and tap its ☆.",
                style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft,
            )
        } else {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                candidates.forEach { card -> Chip("☆ ${card.name}", false, fontSize = 13) { draft.makeCommander(card) } }
            }
        }
    }
}

// ---- Prices ------------------------------------------------------------------------

private fun zoomCaption(price: CardPrice) =
    price.label + (if (price.buylist > 0) "  ·  buylist ${CardPrice.usd(price.buylist)}" else "") +
        (if (price.inStock == 0) "  ·  out of stock" else "")

/** Card Kingdom price, plain text. */
@Composable
internal fun PriceTag(price: CardPrice?) {
    Text(
        price?.label ?: "—",
        Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = if (price == null) Palette.InkSoft else Palette.Ink,
    )
}

// ---- Deck list ---------------------------------------------------------------------

@Composable
private fun DeckList(draft: DeckDraft, format: Engine.Format, problem: String?, onOpen: (List<PaperCard>, Int) -> Unit, modifier: Modifier) {
    fun openCard(card: PaperCard) { val all = draft.ordered(); onOpen(all, all.indexOf(card).coerceAtLeast(0)) }
    val commander = format == Engine.Format.Commander
    val target = if (commander) 100 else 60
    val main = draft.main.entries.toList()
    val groups = listOf(
        "Creatures" to main.filter { it.key.rules.type.isCreature },
        "Spells" to main.filter { !it.key.rules.type.isCreature && !it.key.rules.type.isLand },
        "Lands" to main.filter { it.key.rules.type.isLand },
    )

    val prices by CardPrices.version.collectAsState()
    val value = remember(prices, draft.commander.toMap(), draft.main.toMap()) {
        (draft.commander.entries + draft.main.entries).sumOf { (c, n) -> (CardPrices.of(c)?.retail ?: 0f).toDouble() * n }
    }
    Column(modifier.paper(Palette.PaperLight).ornateFrame(inset = 4.dp).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${draft.total} / $target", fontFamily = Cinzel, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = if (draft.total == target || (!commander && draft.total >= target)) Palette.Sage else Palette.Ink)
            Spacer(Modifier.width(10.dp))
            Text(
                problem ?: "Legal ✓",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = if (problem == null) Palette.Sage else Palette.Terracotta,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (value > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(CardPrice.usd(value.toFloat()), fontFamily = Cinzel, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Slate)
                    Text("Card Kingdom", style = MaterialTheme.typography.labelSmall, color = Palette.InkSoft)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            if (commander) {
                item { SectionLabel("Commander") }
                if (draft.commander.isEmpty()) {
                    item { CommanderPicker(draft) }
                }
                items(draft.commander.entries.toList(), key = { "c" + it.key.name }) { (card, n) ->
                    DeckRow(card, n, isCommander = true, showStar = true, onOpen = { openCard(card) },
                        onAdd = {}, onRemove = { draft.remove(card, fromCommander = true) }, onStar = { draft.toggleCommander(card) })
                }
            }
            groups.forEach { (label, cards) ->
                if (cards.isEmpty()) return@forEach
                item { Spacer(Modifier.height(6.dp)); SectionLabel("$label · ${cards.sumOf { it.value }}") }
                items(cards.sortedBy { it.key.rules.manaCost.cmc * 1000 + it.key.name.first().code }, key = { "m" + it.key.name + it.key.edition }) { (card, n) ->
                    DeckRow(card, n, isCommander = false, showStar = commander && canLead(card), onOpen = { openCard(card) },
                        onAdd = { draft.add(card) }, onRemove = { draft.remove(card, fromCommander = false) }, onStar = { draft.toggleCommander(card) })
                }
            }
        }
    }
}

@Composable
private fun DeckRow(
    card: PaperCard,
    count: Int,
    isCommander: Boolean,
    showStar: Boolean,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onStar: () -> Unit,
) {
    val ui = remember(card) { CardUi.fromPaper(card) }
    var held by remember { mutableStateOf(false) }
    val prices by CardPrices.version.collectAsState()
    val price = remember(card, prices) { CardPrices.of(card) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).tapOrHold(card.name, onTap = { onOpen() }, onHold = { h, _ -> held = h }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).background(Palette.Paper, CircleShape), contentAlignment = Alignment.Center) {
            Text("$count", fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Palette.Ink)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(card.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            ManaCost(ui.manaCost, 17.dp)
        }
        PriceTag(price)
        if (showStar) {
            IconButton(onClick = onStar) {
                Icon(if (isCommander) Icons.Default.Star else Icons.Default.StarBorder, "Commander", tint = Palette.Ochre)
            }
        }
        IconButton(onClick = onRemove) { Icon(Icons.Default.Remove, "Remove one", tint = Palette.Terracotta) }
        if (!isCommander) IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add one", tint = Palette.Sage) }
        Box { HoldZoom(ui, held, caption = price?.let(::zoomCaption)) }
    }
}

// ---- Import ------------------------------------------------------------------------

@Composable
private fun ImportDialog(commanderFormat: Boolean, onDismiss: () -> Unit, onImported: (DeckImport.Result) -> Unit) {
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<DeckImport.Result?>(null) }
    val scope = rememberCoroutineScope()

    OrnateDialog(onDismiss = { if (!busy) onDismiss() }, maxWidth = 720.dp) {
        SectionLabel("Import deck")
        Spacer(Modifier.height(10.dp))
        Text(
            "Paste a card list (\"1 Sol Ring\", \"4x Lightning Bolt (M10) 146\", with optional Commander / Sideboard headers) " +
                "or a public Moxfield or ManaBox deck link.",
            style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            text, { text = it; error = null },
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
        )
        pending?.let { result ->
            Spacer(Modifier.height(8.dp))
            Text(
                "Couldn't find ${result.unknown.size} card(s): " + result.unknown.take(8).joinToString(", ") +
                    if (result.unknown.size > 8) "…" else "",
                style = MaterialTheme.typography.bodySmall, color = Palette.Terracotta,
            )
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Terracotta)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OrnateButton("Cancel", onDismiss, Modifier.weight(1f), accent = Palette.Slate, enabled = !busy)
            if (pending != null) {
                OrnateButton("Use anyway", { onImported(pending!!) }, Modifier.weight(1f), style = OrnateStyle.Primary)
            } else if (busy) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.Terracotta) }
            } else {
                OrnateButton(
                    "Import",
                    {
                        busy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { runCatching { DeckImport.import(text, "Imported deck", commanderFormat) } }
                            busy = false
                            result.onSuccess { r -> if (r.unknown.isEmpty()) onImported(r) else pending = r }
                                .onFailure { error = it.message ?: "Import failed" }
                        }
                    },
                    Modifier.weight(1f), style = OrnateStyle.Primary, enabled = text.isNotBlank(),
                )
            }
        }
    }
}
