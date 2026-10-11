package io.github.nmanuelides.mtgreforged.ui.decks

import io.github.nmanuelides.mtgreforged.engine.CardCollection
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Popup
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
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

/** Deck sections, in the order they're shown (commander comes first, separately). */
internal val TYPE_GROUPS = listOf("Creatures", "Planeswalkers", "Instants", "Sorceries", "Enchantments", "Artifacts", "Battles", "Lands", "Other")

internal fun typeGroup(card: PaperCard): String {
    val t = card.rules.type
    return when {
        t.isCreature -> "Creatures"
        t.isLand -> "Lands"
        t.isPlaneswalker -> "Planeswalkers"
        t.isBattle -> "Battles"
        t.isInstant -> "Instants"
        t.isSorcery -> "Sorceries"
        t.isArtifact -> "Artifacts"
        t.isEnchantment -> "Enchantments"
        else -> "Other"
    }
}

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
            TYPE_GROUPS.flatMap { group -> cards.filter { typeGroup(it) == group }.sortedWith(byCurve) }
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
    var exporting by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(CardFilter()) }
    // The cards get the whole width; filters or the compact list open beside them on demand.
    var panel by remember { mutableStateOf<SidePanel?>(null) }
    var wideLayout by remember { mutableStateOf(true) }
    var matches by remember { mutableIntStateOf(0) }
    var viewing by remember { mutableStateOf<Pair<List<PaperCard>, Int>?>(null) }
    val open: (List<PaperCard>, Int) -> Unit = { cards, index -> viewing = cards to index }
    val scope = rememberCoroutineScope()
    // Saving keeps the editor open; leaving hands back the last saved deck (if any).
    var saved by remember { mutableStateOf(initial) }
    var savedSignature by remember { mutableStateOf<Any?>(null) }
    fun signature(): Any = Triple(name, draft.commander.toMap(), draft.main.toMap())
    val dirty = signature() != savedSignature
    var justSaved by remember { mutableStateOf(false) }
    LaunchedEffect(justSaved) { if (justSaved) { delay(1800); justSaved = false } }
    BackHandler { onDone(saved) }

    LaunchedEffect(initial) {
        initial?.let { entry ->
            val deck = withContext(Dispatchers.IO) { Engine.loadDeck(entry) }
            draft.load(deck)
            savedSignature = signature()
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
                onBack = { onDone(saved) },
                onImport = { importing = true },
                onExport = { exporting = true },
                canExport = draft.total > 0,
                onSave = {
                    val snapshot = signature()
                    val deck = draft.toDeck(name)
                    scope.launch {
                        saved = withContext(Dispatchers.IO) { UserDecks.save(deck, format, saved) }
                        savedSignature = snapshot
                        justSaved = true
                    }
                },
                canSave = draft.total > 0 && dirty,
                saveLabel = when {
                    justSaved -> "Saved ✓"
                    !dirty && saved != null -> "Saved"
                    else -> "Save"
                },
            )
            Spacer(Modifier.height(10.dp))
            val toggle = { which: SidePanel -> panel = if (panel == which) null else which }
            val cards: @Composable (Modifier) -> Unit = { m ->
                CardSearch(
                    m, draft, format, problem, filter, { filter = it },
                    onOpenFilters = { toggle(SidePanel.Filters) },
                    onOpenList = { toggle(SidePanel.List) },
                    listOpen = panel == SidePanel.List,
                    onCount = { matches = it }, onOpen = open,
                ) { draft.add(it) }
            }
            if (wide) {
                // The panel slides in from the right while its width grows, so the cards shrink with it.
                var shownPanel by remember { mutableStateOf(panel) }
                if (panel != null) shownPanel = panel
                Row(Modifier.fillMaxSize()) {
                    cards(Modifier.weight(1f).fillMaxHeight())
                    AnimatedVisibility(
                        visible = panel != null,
                        enter = expandHorizontally(tween(320), expandFrom = Alignment.Start) +
                            slideInHorizontally(tween(320)) { it / 2 } + fadeIn(tween(220)),
                        exit = shrinkHorizontally(tween(280), shrinkTowards = Alignment.Start) +
                            slideOutHorizontally(tween(280)) { it / 2 } + fadeOut(tween(200)),
                    ) {
                        Box(Modifier.padding(start = 12.dp).width(460.dp).fillMaxHeight()) {
                            when (shownPanel) {
                                SidePanel.Filters -> FilterPanel(
                                    filter, { filter = it }, matches, onDone = { panel = null },
                                    Modifier.fillMaxSize().paper(Palette.PaperLight).ornateFrame(inset = 4.dp).padding(14.dp),
                                )
                                SidePanel.List -> DeckList(draft, format, problem, open, Modifier.fillMaxSize(), onClose = { panel = null })
                                null -> Unit
                            }
                        }
                    }
                }
            } else {
                cards(Modifier.fillMaxSize())
            }
        }
    }

    // Phones: the side panels open as dialogs instead.
    if (panel == SidePanel.Filters && !wideLayout) {
        OrnateDialog(onDismiss = { panel = null }, maxWidth = 640.dp) {
            FilterPanel(filter, { filter = it }, matches, onDone = { panel = null }, Modifier.heightIn(max = 640.dp))
        }
    }
    if (panel == SidePanel.List && !wideLayout) {
        OrnateDialog(onDismiss = { panel = null }, maxWidth = 640.dp) {
            DeckList(draft, format, problem, open, Modifier.heightIn(max = 640.dp), onClose = { panel = null })
        }
    }

    viewing?.let { (cards, index) ->
        CardViewer(cards, index, draft, format == Engine.Format.Commander) { viewing = null }
    }

    if (exporting) ExportDialog(name, draft) { exporting = false }

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
    onExport: () -> Unit,
    canExport: Boolean,
    onSave: () -> Unit,
    canSave: Boolean,
    saveLabel: String,
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
        OrnateButton("Export", onExport, icon = Icons.Default.IosShare, accent = Palette.Slate, enabled = canExport)
        OrnateButton(saveLabel, onSave, style = OrnateStyle.Primary, icon = Icons.Default.Save, enabled = canSave)
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
    format: Engine.Format,
    problem: String?,
    filter: CardFilter,
    onFilter: (CardFilter) -> Unit,
    onOpenFilters: () -> Unit,
    onOpenList: () -> Unit,
    listOpen: Boolean,
    onCount: (Int) -> Unit,
    onOpen: (List<PaperCard>, Int) -> Unit,
    onAdd: (PaperCard) -> Unit,
) {
    val commanderFormat = format == Engine.Format.Commander
    var query by remember { mutableStateOf("") }
    // Show the deck instead of the results without throwing the search away.
    var showDeck by remember { mutableStateOf(false) }
    LaunchedEffect(query, filter) { showDeck = false }
    var results by remember { mutableStateOf<List<PaperCard>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    val prices by CardPrices.version.collectAsState()

    val deckNames = (draft.commander.keys + draft.main.keys).map { it.name }.toSet()
    LaunchedEffect(query, filter, prices, if (filter.within == Within.Deck) deckNames else null) {
        delay(250) // debounce typing
        searching = true
        val (found, count) = withContext(Dispatchers.Default) {
            runCatching { filter.search(query, deck = deckNames, collection = CardCollection.names()) }.getOrElse { emptyList<PaperCard>() to 0 }
        }
        results = found
        total = count
        onCount(count)
        searching = false
    }

    Column(modifier.paper(Palette.PaperLight).ornateFrame(inset = 4.dp).padding(12.dp)) {
        DeckStatus(draft, format, problem, listOpen, onOpenList)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                query, { query = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Palette.InkSoft) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search", tint = Palette.InkSoft) }
                    }
                },
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
                // Long press: put the results aside and look at the deck.
                onLongClick = { showDeck = true },
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
        val idle = query.isBlank() && filter.activeCount == 0
        // Pinch to zoom the card grids: fewer, bigger cards (down to 5 columns on a wide screen).
        var zoomSteps by remember { mutableIntStateOf(0) }
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val base = ((maxWidth + 8.dp) / 126.dp).toInt().coerceAtLeast(1)
        val fewest = if (base > 5) 5 else (base - 2).coerceAtLeast(2).coerceAtMost(base)
        val columns = (base - zoomSteps).coerceIn(fewest, base)
        val tile = (maxWidth - 8.dp * (columns - 1)) / columns
        Column(Modifier.fillMaxSize().pinchColumns(
            onZoomIn = { if (columns > fewest) zoomSteps = base - columns + 1 },
            onZoomOut = { if (columns < base) zoomSteps = base - columns - 1 },
        )) {
        // With nothing searched (or "Your deck" picked) the cards shown are the deck itself.
        if ((idle || showDeck) && (draft.total > 0 || commanderFormat)) {
            if (!idle) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Your deck", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
                    Chip("Back to results · $total", false, fontSize = 12) { showDeck = false }
                }
                Spacer(Modifier.height(4.dp))
            }
            DeckGallery(draft, commanderFormat, onOpen, Modifier.fillMaxSize(), columns, tile)
            return@Column
        }
        if (total > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (total > results.size) "Showing ${results.size} of $total cards" else "$total cards",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft,
                )
                if (draft.total > 0) Chip("Your deck · ${draft.total}", false, fontSize = 12) { showDeck = true }
                SortControl(filter.sort, filter.descending) { sort, descending -> onFilter(filter.copy(sort = sort, descending = descending)) }
            }
            Spacer(Modifier.height(4.dp))
        }
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(results, key = { _, it -> "${it.name}|${it.edition}|${it.artIndex}" }) { index, card ->
                    val ui = remember(card) { CardUi.fromPaper(card) }
                    var held by remember { mutableStateOf(false) }
                    val price = remember(card, prices) { CardPrices.of(card) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.tapOrHold(card.name, onTap = { onOpen(results, index) }, onHold = { h, _ -> held = h })) {
                            CardTile(ui, tile, showTapped = false)
                            if (commanderFormat && canLead(card)) {
                                CommanderStar(draft.commander.containsKey(card), Modifier.align(Alignment.TopStart)) { draft.makeCommander(card) }
                            }
                            HoldZoom(ui, held, caption = price?.let(::zoomCaption))
                        }
                        Row(Modifier.width(tile), verticalAlignment = Alignment.CenterVertically) {
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
    }
}

/** "Sort: Mana value ↑": pick the order from a menu, tap the arrow to flip it. */
@Composable
private fun SortControl(sort: SortBy, descending: Boolean, onChange: (SortBy, Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Sort", style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
        Box {
            Chip(sort.label + "  ▾", false, fontSize = 12) { open = true }
            DropdownMenu(open, { open = false }, containerColor = Palette.PaperLight) {
                SortBy.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option.label, style = MaterialTheme.typography.bodyLarge,
                                color = if (option == sort) Palette.Terracotta else Palette.Ink,
                            )
                        },
                        onClick = { open = false; onChange(option, descending) },
                    )
                }
            }
        }
        Chip(if (descending) "↓" else "↑", false, fontSize = 14) { onChange(sort, !descending) }
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

private enum class SidePanel { Filters, List }

/** Two-finger pinch: steps the column count, leaving one-finger scrolling to the grid. */
private fun Modifier.pinchColumns(onZoomIn: () -> Unit, onZoomOut: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var scale = 1f
        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } >= 2) {
                scale *= event.calculateZoom()
                if (scale > 1.2f) { onZoomIn(); scale = 1f }
                if (scale < 0.83f) { onZoomOut(); scale = 1f }
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

/** The deck as a plain list ("1 Name (SET) 123"), readable by Moxfield, Archidekt and our importer. */
private fun deckText(draft: DeckDraft): String = buildString {
    fun line(card: PaperCard, n: Int) {
        append(n).append(' ').append(card.name).append(" (").append(card.edition).append(')')
        card.collectorNumber?.takeIf { it.isNotBlank() && it != "0" }?.let { append(' ').append(it) }
        append('\n')
    }
    if (draft.commander.isNotEmpty()) {
        append("Commander\n")
        draft.commander.forEach { (c, n) -> line(c, n) }
        append('\n')
    }
    append("Deck\n")
    draft.ordered().filter { it in draft.main }.forEach { line(it, draft.main[it] ?: 0) }
}

@Composable
private fun ExportDialog(name: String, draft: DeckDraft, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var done by remember { mutableStateOf<String?>(null) }
    val text = remember { deckText(draft) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }
                .onSuccess { done = "Saved." }.onFailure { done = "Couldn't save: ${it.message}" }
        }
    }
    OrnateDialog(onDismiss = onDismiss, maxWidth = 520.dp) {
        SectionLabel("Export deck")
        Spacer(Modifier.height(8.dp))
        Text("${draft.total} cards as a plain list, ready for Moxfield, Archidekt, ManaBox or a friend.",
            style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OrnateButton("Copy list", { clipboard.setText(AnnotatedString(text)); done = "Copied to the clipboard." },
                Modifier.weight(1f), icon = Icons.Default.ContentCopy, accent = Palette.Slate)
            OrnateButton("Save .txt", { save.launch("${name.ifBlank { "deck" }}.txt") },
                Modifier.weight(1f), icon = Icons.Default.Save, style = OrnateStyle.Primary)
        }
        done?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Sage)
        }
    }
}

/** One line over the cards: size, legality, value, and the button for the compact list. */
@Composable
private fun DeckStatus(draft: DeckDraft, format: Engine.Format, problem: String?, listOpen: Boolean, onList: () -> Unit) {
    val commander = format == Engine.Format.Commander
    val target = if (commander) 100 else 60
    val prices by CardPrices.version.collectAsState()
    val value = remember(prices, draft.commander.toMap(), draft.main.toMap()) {
        CardPrices.deckValue(draft.commander.entries + draft.main.entries)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "${draft.total} / $target", fontFamily = Cinzel, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            color = if (draft.total == target || (!commander && draft.total >= target)) Palette.Sage else Palette.Ink,
        )
        Text(
            problem ?: "Legal ✓",
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = if (problem == null) Palette.Sage else Palette.Terracotta,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (value > 0) {
            Text(CardPrice.usd(value.toFloat()), fontFamily = Cinzel, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Slate)
        }
        OrnateButton(
            "List", onList, icon = Icons.AutoMirrored.Filled.List,
            style = if (listOpen) OrnateStyle.Primary else OrnateStyle.Secondary,
            accent = Palette.Slate, frameColor = Palette.Slate, height = 44.dp,
        )
    }
}

// ---- Deck gallery ------------------------------------------------------------------

/** The deck as card images, grouped like the list, with copies and −/+ under each card. */
@Composable
private fun DeckGallery(
    draft: DeckDraft,
    commanderFormat: Boolean,
    onOpen: (List<PaperCard>, Int) -> Unit,
    modifier: Modifier,
    columns: Int,
    tile: Dp,
) {
    val main = draft.main.entries.map { it.key to it.value }
    val groups = (
        listOf("Commander" to draft.commander.entries.map { it.key to it.value }) +
            TYPE_GROUPS.map { group -> group to main.filter { typeGroup(it.first) == group } }
        ).filter { it.second.isNotEmpty() }

    LazyVerticalGrid(
        GridCells.Fixed(columns),
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                if (draft.total == 0) "Your deck is empty — search above to add cards"
                else "Your deck · ${draft.total} cards — search above to add more",
                style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft,
            )
        }
        if (commanderFormat && draft.commander.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { CommanderPicker(draft) }
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
                        CardTile(ui, tile, showTapped = false)
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
                    Row(Modifier.width(tile), verticalAlignment = Alignment.CenterVertically) {
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

/** Card Kingdom price, plain text; a ⚠ when the saved list is old (tap it for the date). */
@Composable
internal fun PriceTag(price: CardPrice?) {
    Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            price?.label ?: "—",
            style = MaterialTheme.typography.labelMedium,
            color = if (price == null) Palette.InkSoft else Palette.Ink,
        )
        if (price != null && CardPrices.stale) StalePriceWarning()
    }
}

@Composable
internal fun StalePriceWarning() {
    var open by remember { mutableStateOf(false) }
    Box {
        Icon(
            Icons.Default.WarningAmber, "Old price",
            Modifier.padding(start = 3.dp).size(15.dp).clickable { open = !open },
            tint = Palette.Ochre,
        )
        if (open) {
            val date = remember { java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(CardPrices.fetchedAt)) }
            Popup(alignment = Alignment.BottomCenter, onDismissRequest = { open = false }, properties = PopupProperties(focusable = true)) {
                Text(
                    "Card Kingdom price from $date.\nIt may have changed since.",
                    Modifier.widthIn(max = 240.dp).paper(Palette.PaperLight).ornateFrame(Palette.Ochre, inset = 2.dp, corners = false).padding(10.dp),
                    style = MaterialTheme.typography.bodySmall, color = Palette.Ink,
                )
            }
        }
    }
}

// ---- Deck list ---------------------------------------------------------------------

@Composable
private fun DeckList(
    draft: DeckDraft,
    format: Engine.Format,
    problem: String?,
    onOpen: (List<PaperCard>, Int) -> Unit,
    modifier: Modifier,
    onClose: (() -> Unit)? = null,
) {
    fun openCard(card: PaperCard) { val all = draft.ordered(); onOpen(all, all.indexOf(card).coerceAtLeast(0)) }
    val commander = format == Engine.Format.Commander
    val target = if (commander) 100 else 60
    val main = draft.main.entries.toList()
    val groups = TYPE_GROUPS.map { group -> group to main.filter { typeGroup(it.key) == group } }

    val prices by CardPrices.version.collectAsState()
    val value = remember(prices, draft.commander.toMap(), draft.main.toMap()) {
        CardPrices.deckValue(draft.commander.entries + draft.main.entries)
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
            onClose?.let { IconButton(onClick = it) { Icon(Icons.Default.Close, "Close", tint = Palette.InkSoft) } }
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
