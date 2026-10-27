package io.github.nmanuelides.mtgreforged.ui.decks

import io.github.nmanuelides.mtgreforged.platform.CAN_SCAN_CARDS
import androidx.compose.material.icons.filled.PhotoCamera
import io.github.nmanuelides.mtgreforged.ui.components.MenuItem
import io.github.nmanuelides.mtgreforged.ui.components.OverflowMenu
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.horizontalScroll
import io.github.nmanuelides.mtgreforged.ui.components.EntranceScope
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.produceState
import androidx.compose.material.icons.filled.Savings
import androidx.compose.ui.composed
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Check
import io.github.nmanuelides.mtgreforged.ui.components.landIn
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import io.github.nmanuelides.mtgreforged.engine.CardCollection
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
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
import io.github.nmanuelides.mtgreforged.platform.BackHandler
import io.github.nmanuelides.mtgreforged.platform.LocalAppContext
import io.github.nmanuelides.mtgreforged.platform.rememberCreateDocument
import io.github.nmanuelides.mtgreforged.platform.shareText
import io.github.nmanuelides.mtgreforged.ui.components.horizontalDragScroll

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

    /** Moves the copies of exactly [old] to [new] (another printing or finish), leaving other printings alone. */
    fun replacePrinting(old: PaperCard, new: PaperCard) {
        if (old == new) return
        for (pool in listOf(commander, main)) {
            val n = pool.remove(old) ?: continue
            pool[new] = (pool[new] ?: 0) + n
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

fun CardUi.Companion.fromPaper(card: PaperCard): CardUi {
    val rules = card.rules
    return CardUi(
        foil = card.isFoil,
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
    /** Edit the player's card collection instead of a deck: no format rules, no commander. */
    collection: Boolean = false,
    onDone: (Engine.DeckEntry?) -> Unit,
) {
    val draft = remember { DeckDraft(mutableStateMapOf(), mutableStateMapOf()) }
    var name by remember { mutableStateOf(initial?.name ?: "New deck") }
    var scanning by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(importFirst) }
    var exporting by remember { mutableStateOf(false) }
    var cheapening by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(if (collection) CardFilter(within = Within.Collection) else CardFilter()) }
    // The cards get the whole width; filters or the compact list open beside them on demand.
    var panel by remember { mutableStateOf<SidePanel?>(null) }
    var wideLayout by remember { mutableStateOf(true) }
    var matches by remember { mutableIntStateOf(0) }
    // The cards, where to start, and whether swiping changes the owned copy (collection only).
    var viewing by remember { mutableStateOf<Triple<List<PaperCard>, Int, Boolean>?>(null) }
    val open: (List<PaperCard>, Int) -> Unit = { cards, index -> viewing = Triple(cards, index, collection) }
    // Search results: in the collection, from "All cards" they're a catalogue to add from, so
    // swiping just browses printings; from "My collection" they're your own copies.
    val openResults: (List<PaperCard>, Int) -> Unit = { cards, index ->
        viewing = Triple(cards, index, collection && filter.within == Within.Collection)
    }
    val scope = rememberCoroutineScope()
    // Saving keeps the editor open; leaving hands back the last saved deck (if any).
    var saved by remember { mutableStateOf(initial) }
    var savedSignature by remember { mutableStateOf<Any?>(null) }
    fun signature(): Any = Triple(name, draft.commander.toMap(), draft.main.toMap())
    val dirty = signature() != savedSignature
    var justSaved by remember { mutableStateOf(false) }
    LaunchedEffect(justSaved) { if (justSaved) { delay(1800); justSaved = false } }
    var confirmExit by remember { mutableStateOf(false) }
    val leave: () -> Unit = { if (dirty && (collection || draft.total > 0)) confirmExit = true else onDone(saved) }
    BackHandler { leave() }

    LaunchedEffect(initial) {
        if (collection) {
            name = "My collection"
            val owned = withContext(Dispatchers.IO) { CardCollection.cards() }
            draft.main.clear()
            draft.main.putAll(owned)
            savedSignature = signature()
        }
        initial?.let { entry ->
            val deck = withContext(Dispatchers.IO) { Engine.loadDeck(entry) }
            draft.load(deck)
            savedSignature = signature()
        }
    }
    // Forge's own legality check for the format, re-run as the deck changes.
    LaunchedEffect(draft.total, draft.commander.size) {
        if (collection) return@LaunchedEffect
        delay(300)
        problem = withContext(Dispatchers.Default) {
            runCatching { format.gameType.deckFormat.getDeckConformanceProblem(draft.toDeck(name)) }.getOrNull()
        }
    }

    val clearCollection: (() -> Unit)? = if (!collection) null else {
        {
            draft.main.clear()
            scope.launch {
                withContext(Dispatchers.IO) { CardCollection.save(emptyMap()) }
                savedSignature = signature()
            }
        }
    }
    EntranceScope {
    fun saveNow(after: () -> Unit = {}) {
        val snapshot = signature()
        val deck = draft.toDeck(name)
        scope.launch {
            if (collection) withContext(Dispatchers.IO) { CardCollection.save(snapshotMain(draft)) }
            else saved = withContext(Dispatchers.IO) { UserDecks.save(deck, format, saved) }
            savedSignature = snapshot
            justSaved = true
            after()
        }
    }
    if (scanning) {
        // Scanned cards join the collection and it's saved right away.
        CardScanner(
            onSave = { cards ->
                cards.forEach { (card, n) -> draft.main[card] = (draft.main[card] ?: 0) + n }
                scanning = false
                saveNow()
            },
            onClose = { scanning = false },
        )
        return@EntranceScope
    }
    if (confirmExit) {
        OrnateDialog(onDismiss = { confirmExit = false }, maxWidth = 480.dp) {
            SectionLabel("Unsaved changes")
            Spacer(Modifier.height(10.dp))
            Text(
                if (collection) "Your collection has changes that aren't saved." else "This deck has changes that aren't saved.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft,
            )
            Spacer(Modifier.height(14.dp))
            OrnateButton("Save & leave", { confirmExit = false; saveNow { onDone(saved) } }, Modifier.fillMaxWidth(), style = OrnateStyle.Primary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OrnateButton("Stay", { confirmExit = false }, Modifier.weight(1f), accent = Palette.Slate)
                OrnateButton("Leave without saving", { confirmExit = false; onDone(saved) }, Modifier.weight(1.4f), accent = Palette.Terracotta)
            }
        }
    }
    CompositionLocalProvider(LocalCollectionMode provides collection, LocalClearCollection provides clearCollection) {
    BoxWithConstraints(Modifier.fillMaxSize().parchment()) {
        val wide = maxWidth > maxHeight
        wideLayout = wide
        val compactHeader = maxWidth < 600.dp
        CompositionLocalProvider(LocalCompactBuilder provides compactHeader) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(if (compactHeader) 8.dp else 12.dp)) {
            Box(Modifier.landIn(0)) {
            Header(
                name = name, onName = { name = it }, format = format,
                onBack = leave,
                onImport = { importing = true },
                onExport = { exporting = true },
                onCheapest = { cheapening = true },
                compact = compactHeader,
                canExport = draft.total > 0,
                onSave = { saveNow() },
                onScan = if (collection && CAN_SCAN_CARDS) ({ scanning = true }) else null,
                canSave = draft.total > 0 && dirty,
                saveLabel = when {
                    justSaved -> "Saved ✓"
                    !dirty && (saved != null || collection) -> "Saved"
                    else -> "Save"
                },
            )
            }
            Spacer(Modifier.height(10.dp))
            val toggle = { which: SidePanel -> panel = if (panel == which) null else which }
            val cards: @Composable (Modifier) -> Unit = { m ->
                CardSearch(
                    m, draft, format, problem, filter, { filter = it },
                    onOpenFilters = { toggle(SidePanel.Filters) },
                    onOpenList = { toggle(SidePanel.List) },
                    listOpen = panel == SidePanel.List,
                    onCount = { matches = it }, onOpen = open, onOpenResults = openResults,
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
                                    collectionAvailable = CardCollection.names().isNotEmpty(),
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
    }

    }
    }
    // Phones: the side panels open as dialogs instead.
    if (panel == SidePanel.Filters && !wideLayout) {
        OrnateDialog(onDismiss = { panel = null }, maxWidth = 640.dp) {
            FilterPanel(filter, { filter = it }, matches, onDone = { panel = null }, Modifier.heightIn(max = 640.dp), CardCollection.names().isNotEmpty())
        }
    }
    if (panel == SidePanel.List && !wideLayout) {
        OrnateDialog(onDismiss = { panel = null }, maxWidth = 640.dp) {
            DeckList(draft, format, problem, open, Modifier.heightIn(max = 640.dp), onClose = { panel = null })
        }
    }

    viewing?.let { (cards, index, replaceOwned) ->
        CardViewer(cards, index, draft, format == Engine.Format.Commander, collection, replaceOwned) { viewing = null }
    }

    if (exporting) ExportDialog(name, draft) { exporting = false }
    if (cheapening) CheapestDialog(draft) { cheapening = false }

    if (importing) {
        ImportDialog(
            commanderFormat = format == Engine.Format.Commander,
            onDismiss = { importing = false },
            onImported = { result ->
                if (collection) {
                    // Imports add to the collection instead of replacing it.
                    listOf(DeckSection.Commander, DeckSection.Main, DeckSection.Sideboard).forEach { section ->
                        result.deck.get(section)?.forEach { (card, n) -> draft.main[card] = (draft.main[card] ?: 0) + n }
                    }
                } else {
                    draft.load(result.deck)
                    if (initial == null || name == "New deck") name = result.deck.name
                }
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
    onCheapest: () -> Unit,
    compact: Boolean = false,
    onSave: () -> Unit,
    canSave: Boolean,
    saveLabel: String,
    /** Collection on Android: opens the card scanner. */
    onScan: (() -> Unit)? = null,
) {
    if (compact) {
        // Phones: back, name and Save stay; import / export / cheapest go in the menu.
        val collection = LocalCollectionMode.current
        val clear = LocalClearCollection.current
        var clearing by remember { mutableIntStateOf(0) }
        ClearCollectionDialog(clearing, { clearing = it }) { clear?.invoke() }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OrnateButton("", onBack, Modifier.width(44.dp).padding(top = 8.dp), icon = Icons.AutoMirrored.Filled.ArrowBack, height = 56.dp)
            OutlinedTextField(
                name, onName, singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium,
                label = { Text(if (collection) "Collection" else "${format.label} deck", style = MaterialTheme.typography.labelSmall) },
                readOnly = collection,
                colors = fieldColors(),
                modifier = Modifier.weight(1f),
            )
            onScan?.let { OrnateButton("", it, Modifier.width(48.dp).padding(top = 8.dp), icon = Icons.Default.PhotoCamera, accent = Palette.Sage, frameColor = Palette.Sage, height = 56.dp) }
            OrnateButton("", onSave, Modifier.width(48.dp).padding(top = 8.dp), style = OrnateStyle.Primary, icon = Icons.Default.Save, enabled = canSave, height = 56.dp)
            OverflowMenu(
                modifier = Modifier.padding(top = 8.dp), height = 56.dp, items =
                listOfNotNull(
                    MenuItem("Import", Icons.Default.FileDownload, Palette.Slate, onClick = onImport),
                    MenuItem("Export", Icons.Default.IosShare, Palette.Slate, enabled = canExport, onClick = onExport),
                    if (!collection) MenuItem("Switch to cheapest printings", Icons.Default.Savings, Palette.Sage, enabled = canExport, onClick = onCheapest) else null,
                    if (clear != null) MenuItem("Delete collection", Icons.Default.DeleteForever, Palette.Terracotta) { clearing = 1 } else null,
                ),
            )
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OrnateButton("", onBack, Modifier.width(52.dp), icon = Icons.AutoMirrored.Filled.ArrowBack)
        OutlinedTextField(
            name, onName, singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge,
            label = { Text(if (LocalCollectionMode.current) "Collection" else "${format.label} deck", style = MaterialTheme.typography.labelSmall) },
            readOnly = LocalCollectionMode.current,
            colors = fieldColors(),
            modifier = Modifier.weight(1f),
        )
        onScan?.let { OrnateButton("Scan", it, icon = Icons.Default.PhotoCamera, accent = Palette.Sage, frameColor = Palette.Sage) }
        OrnateButton(if (compact) "" else "Import", onImport, icon = Icons.Default.FileDownload, accent = Palette.Slate)
        OrnateButton(if (compact) "" else "Export", onExport, icon = Icons.Default.IosShare, accent = Palette.Slate, enabled = canExport)
        if (!LocalCollectionMode.current) {
            OrnateButton(if (compact) "" else "Cheapest", onCheapest, icon = Icons.Default.Savings, accent = Palette.Sage, enabled = canExport)
        }
        OrnateButton(if (compact) "" else saveLabel, onSave, style = OrnateStyle.Primary, icon = Icons.Default.Save, enabled = canSave)
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
    onOpenResults: (List<PaperCard>, Int) -> Unit,
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
    val collectionMode = LocalCollectionMode.current
    val deckPrintings = if (filter.within != Within.All) (draft.commander.keys + draft.main.keys).toSet() else null
    LaunchedEffect(query, filter, prices, deckPrintings) {
        delay(250) // debounce typing
        searching = true
        val (found, count) = withContext(Dispatchers.Default) {
            runCatching {
                filter.search(
                    query, deck = deckNames, collection = if (collectionMode) deckNames else CardCollection.names(),
                    owned = if (collectionMode) deckPrintings.orEmpty() else CardCollection.cards().keys,
                )
            }.getOrElse { emptyList<PaperCard>() to 0 }
        }
        results = found
        total = count
        onCount(count)
        searching = false
    }
    // One card per name: show the printing (and finish) the deck / collection actually has, so
    // picking another printing in the viewer shows up here too, not the search's default one.
    val shown by remember(results, filter) {
        derivedStateOf {
            val perPrinting = collectionMode || filter.within == Within.Collection || !filter.hideDuplicates ||
                filter.sets.isNotEmpty() || filter.rarities.isNotEmpty() || filter.artist.isNotBlank()
            if (perPrinting) results else {
                val owned = (draft.commander.keys + draft.main.keys).groupBy { it.name }
                results.map { r ->
                    owned[r.name]?.firstOrNull { filter.finish == Finish.Any || it.isFoil == r.isFoil } ?: r
                }
            }
        }
    }

    val compactSearch = LocalCompactBuilder.current
    Column(modifier.paper(Palette.PaperLight).ornateFrame(inset = 4.dp).padding(if (compactSearch) 10.dp else 12.dp)) {
        if (compactSearch) {
            CompactSearchTop(draft, format, problem, listOpen, onOpenList, filter.activeCount, onOpenFilters, query, { query = it }, filter.exactName)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.landIn(3), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                ManaPip.WUBRG.forEach { pip ->
                    val color = pip.color
                    ManaChip(pip, color in filter.colors, size = 30.dp) {
                        onFilter(filter.copy(colors = if (color in filter.colors) filter.colors - color else filter.colors + color))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.landIn(3).horizontalDragScroll(),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                QUICK_TYPES.forEach { type ->
                    Chip(type.take(5), type in filter.types.values && filter.types.mode != GroupMode.Not) {
                        onFilter(filter.copy(types = filter.types.toggle(type)))
                    }
                }
            }
        } else {
        Box(Modifier.landIn(1)) { DeckStatus(draft, format, problem, listOpen, onOpenList) }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.landIn(2), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            val compact = LocalCompactBuilder.current
            OrnateButton(
                when {
                    compact -> if (active > 0) "$active" else ""
                    active > 0 -> "Filters · $active"
                    else -> "Filters"
                }, onOpenFilters,
                icon = Icons.Default.Tune, accent = Palette.Plum, frameColor = Palette.Plum, height = 56.dp,
                // Long press: put the results aside and look at the deck.
                onLongClick = { showDeck = true },
            )
        }
        Spacer(Modifier.height(8.dp))
        // Quick toggles for the most used filters; the panel has the rest.
        Row(
            Modifier.landIn(3).horizontalDragScroll(),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
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
        }
        Spacer(Modifier.height(6.dp))
        val idle = query.isBlank() && filter.activeCount == 0
        // Pinch to zoom the card grids: fewer, bigger cards (down to 5 columns on a wide screen).
        var zoomSteps by remember { mutableIntStateOf(0) }
        BoxWithConstraints(Modifier.fillMaxSize()) {
        // Phones fit a third column (narrower tiles) instead of two big ones.
        val base = ((maxWidth + 8.dp) / (if (LocalCompactBuilder.current) 108.dp else 126.dp)).toInt().coerceAtLeast(1)
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
            val cascade = remember { java.util.concurrent.atomic.AtomicInteger(4) }
            var flashed by remember { mutableStateOf<String?>(null) }
            LazyVerticalGrid(
                GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(shown, key = { _, it -> printingKey(it) }) { index, card ->
                    val order = remember { cascade.getAndIncrement() }
                    val ui = remember(card) { CardUi.fromPaper(card) }
                    var held by remember { mutableStateOf(false) }
                    val price = remember(card, prices) { CardPrices.of(card) }
                    Column(Modifier.landIn(order.coerceAtMost(30)), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.tapOrHold(card.name, onTap = { onOpenResults(shown, index) }, onHold = { h, _ -> held = h })) {
                            CardTile(ui, tile, showTapped = false)
                            OwnedBadge(card.name, Modifier.align(Alignment.BottomStart))
                            if (commanderFormat && canLead(card)) {
                                CommanderStar(draft.commander.containsKey(card), Modifier.align(Alignment.TopStart)) { draft.makeCommander(card) }
                            }
                            HoldZoom(ui, held, caption = price?.let(::zoomCaption))
                            // A short "Added" stamp on the card just added.
                            if (flashed == "${card.name}|${card.edition}") {
                                LaunchedEffect(Unit) { delay(900); flashed = null }
                                Text(
                                    "Added ✓",
                                    Modifier.align(Alignment.Center).background(Palette.Sage).padding(horizontal = 10.dp, vertical = 4.dp),
                                    fontFamily = Cinzel, fontWeight = FontWeight.Bold, color = Palette.OnAccent,
                                )
                            }
                        }
                        Row(Modifier.width(tile), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { PriceTag(price) }
                            Icon(
                                Icons.Default.Add, "Add to deck",
                                Modifier.size(32.dp).clickable { onAdd(card); flashed = "${card.name}|${card.edition}" }.padding(4.dp),
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

/**
 * Phones: legality with Filters and List on one line, then a one-line search with the card
 * count and value beside it (where Filters sits on wide screens).
 */
@Composable
private fun CompactSearchTop(
    draft: DeckDraft,
    format: Engine.Format,
    problem: String?,
    listOpen: Boolean,
    onList: () -> Unit,
    activeFilters: Int,
    onFilters: () -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    exactName: Boolean,
) {
    val commander = format == Engine.Format.Commander
    val target = if (commander) 100 else 60
    val prices by CardPrices.version.collectAsState()
    val value = remember(prices, draft.commander.toMap(), draft.main.toMap()) {
        CardPrices.deckValue(draft.commander.entries + draft.main.entries)
    }
    val collectionMode = LocalCollectionMode.current
    Row(Modifier.landIn(1), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (collectionMode) "${draft.main.size} different cards" else problem ?: "Legal ✓",
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (problem == null) Palette.Sage else Palette.Terracotta,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        OrnateButton(
            if (activeFilters > 0) "$activeFilters" else "", onFilters, if (activeFilters > 0) Modifier else Modifier.width(48.dp),
            icon = Icons.Default.Tune, accent = Palette.Plum, frameColor = Palette.Plum, height = 42.dp,
        )
        OrnateButton(
            "", onList, Modifier.width(48.dp), icon = Icons.AutoMirrored.Filled.List,
            style = if (listOpen) OrnateStyle.Primary else OrnateStyle.Secondary,
            accent = Palette.Slate, frameColor = Palette.Slate, height = 42.dp,
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.landIn(2), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            query, onQuery, singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Palette.InkSoft) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "Clear search", tint = Palette.InkSoft) }
                }
            },
            placeholder = { Text(if (exactName) "Exact name" else "Search cards", style = MaterialTheme.typography.bodyMedium, maxLines = 1) },
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = fieldColors(),
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (collectionMode) "${draft.total}" else "${draft.total}/$target", fontFamily = Cinzel, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                color = if (!collectionMode && (draft.total == target || (!commander && draft.total >= target))) Palette.Sage else Palette.Ink,
            )
            if (value > 0) Text(CardPrice.usd(value.toFloat()), fontFamily = Cinzel, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Slate)
        }
    }
}

/** True on narrow screens (phones in portrait): the builder packs its controls tighter. */
internal val LocalCompactBuilder = staticCompositionLocalOf { false }

/** True while the builder edits the collection rather than a deck. */
internal val LocalCollectionMode = staticCompositionLocalOf { false }

/** Collection mode only: empties the whole collection (after two confirmations). */
internal val LocalClearCollection = staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
private fun ClearCollectionButton() {
    val clear = LocalClearCollection.current ?: return
    var step by remember { mutableIntStateOf(0) }
    OrnateButton(
        "Delete all", { step = 1 },
        icon = Icons.Default.DeleteForever, accent = Palette.Terracotta, frameColor = Palette.Terracotta, height = 44.dp,
    )
    ClearCollectionDialog(step, { step = it }, clear)
}

/** The two confirmations before emptying the collection; [step] 0 = closed. */
@Composable
private fun ClearCollectionDialog(step: Int, onStep: (Int) -> Unit, clear: () -> Unit) {
    if (step > 0) {
        OrnateDialog(onDismiss = { onStep(0) }, maxWidth = 480.dp) {
            SectionLabel(if (step == 1) "Delete your collection?" else "Are you sure?")
            Spacer(Modifier.height(10.dp))
            Text(
                if (step == 1) "This removes every card from My collection. Your decks are not affected."
                else "This can't be undone. Make a backup first if you might want it back.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OrnateButton("Cancel", { onStep(0) }, Modifier.weight(1f), accent = Palette.Slate)
                OrnateButton(
                    if (step == 1) "Delete" else "Yes, delete everything",
                    { if (step == 1) onStep(2) else { onStep(0); clear() } },
                    Modifier.weight(1f), style = OrnateStyle.Primary,
                )
            }
        }
    }
}

/** One grid / list entry per printing and finish. */
internal fun printingKey(card: PaperCard) = "${card.name}|${card.edition}|${card.collectorNumber}|${card.artIndex}|${card.isFoil}"

private fun snapshotMain(draft: DeckDraft): Map<PaperCard, Int> = draft.main.toMap()

/** A small check on cards the player owns (from their collection). */
@Composable
private fun OwnedBadge(name: String, modifier: Modifier) {
    if (LocalCollectionMode.current) return
    val version by CardCollection.version.collectAsState()
    val owned = remember(name, version) { CardCollection.owns(name) }
    if (!owned) return
    Box(
        modifier.padding(4.dp).size(24.dp).background(Palette.Sage, CircleShape).border(1.5.dp, Palette.PaperLight, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Check, "In your collection", Modifier.size(16.dp), tint = Palette.OnAccent)
    }
}

/**
 * Two-finger pinch: steps the column count, leaving one-finger scrolling to the grid. With a mouse,
 * Ctrl + wheel does the same (wheel up: fewer, bigger cards); the plain wheel still scrolls.
 */
private fun Modifier.pinchColumns(onZoomIn: () -> Unit, onZoomOut: () -> Unit): Modifier = composed {
    // The gesture loop lives across recompositions; read the newest callbacks, not the first ones.
    val zoomIn by rememberUpdatedState(onZoomIn)
    val zoomOut by rememberUpdatedState(onZoomOut)
    pointerInput(Unit) { pinchLoop({ zoomIn() }, { zoomOut() }) }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    // Initial pass: take Ctrl + wheel before the grid scrolls with it.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type != PointerEventType.Scroll || !event.keyboardModifiers.isCtrlPressed) continue
                    val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (dy < 0f) zoomIn() else if (dy > 0f) zoomOut()
                    event.changes.forEach { it.consume() }
                }
            }
        }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.pinchLoop(onZoomIn: () -> Unit, onZoomOut: () -> Unit) {
    awaitEachGesture {
        // Initial pass: see the fingers before the grid's own scrolling takes them.
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var scale = 1f
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
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
        if (card.isFoil) append(" *F*")
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

/** Cards of the deck the collection doesn't cover (basic lands left out), with what they'd cost. */
private fun shoppingList(draft: DeckDraft): Pair<String, Double> {
    val need = LinkedHashMap<String, Pair<PaperCard, Int>>()
    draft.ordered().forEach { card ->
        if (card.rules.type.isBasicLand) return@forEach
        val n = (draft.commander[card] ?: 0) + (draft.main[card] ?: 0)
        val prev = need[card.name]
        need[card.name] = card to (prev?.second ?: 0) + n
    }
    var total = 0.0
    val text = buildString {
        need.forEach { (name, pair) ->
            val missing = pair.second - CardCollection.count(name).coerceAtMost(pair.second)
            if (missing <= 0) return@forEach
            val price = CardPrices.cheapest(name)?.retail
            if (price != null) total += price * missing
            append(missing).append(' ').append(name)
            price?.let { append("  (").append(CardPrice.usd(it)).append(')') }
            append('\n')
        }
    }
    return text.trim() to total
}

@Composable
private fun ExportDialog(name: String, draft: DeckDraft, onDismiss: () -> Unit) {
    val context = LocalAppContext.current
    val clipboard = LocalClipboardManager.current
    var done by remember { mutableStateOf<String?>(null) }
    var shopping by remember { mutableStateOf(false) }
    val deck = remember { deckText(draft) }
    val (missing, cost) = remember { shoppingList(draft) }
    val text = if (shopping) "$name — shopping list\n\n$missing" else deck
    val save = rememberCreateDocument("text/plain") { open ->
        if (open != null) {
            runCatching { open()?.use { it.write(text.toByteArray()) } }
                .onSuccess { done = "Saved." }.onFailure { done = "Couldn't save: ${it.message}" }
        }
    }
    OrnateDialog(onDismiss = onDismiss, maxWidth = 560.dp) {
        SectionLabel("Export deck")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Deck list", !shopping, fontSize = 12) { shopping = false; done = null }
            Chip("Shopping list", shopping, fontSize = 12) { shopping = true; done = null }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (!shopping) "${draft.total} cards as a plain list, ready for Moxfield, Archidekt, ManaBox or a friend."
            else if (missing.isBlank()) "You already own every card in this deck."
            else "Cards in this deck that your collection doesn't cover (basic lands left out)" +
                if (cost > 0) " · about ${CardPrice.usd(cost.toFloat())} at Card Kingdom." else ".",
            style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft,
        )
        if (shopping && missing.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                missing, Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall, color = Palette.Ink,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OrnateButton("Copy", { clipboard.setText(AnnotatedString(text)); done = "Copied to the clipboard." },
                Modifier.weight(1f), icon = Icons.Default.ContentCopy, accent = Palette.Slate)
            OrnateButton("Share", { context.shareText(text) }, Modifier.weight(1f), icon = Icons.Default.IosShare, accent = Palette.Sage)
            OrnateButton("Save .txt", { save("${name.ifBlank { "deck" }}${if (shopping) " - shopping list" else ""}.txt") },
                Modifier.weight(1f), icon = Icons.Default.Save, style = OrnateStyle.Primary)
        }
        done?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Sage)
        }
    }
}

/** For each card in the deck, the printing Card Kingdom sells cheapest (when it's a different one). */
private fun cheapestSwaps(draft: DeckDraft): Map<PaperCard, PaperCard> {
    val db = StaticData.instance().commonCards
    return (draft.commander.keys + draft.main.keys).mapNotNull { card ->
        val best = runCatching { db.getAllCards(card.name) }.getOrNull().orEmpty()
            .mapNotNull { p -> CardPrices.of(p)?.takeIf { it.exact }?.let { p to it.retail } }
            .minByOrNull { it.second } ?: return@mapNotNull null
        val now = CardPrices.of(card)?.takeIf { it.exact }?.retail ?: Float.MAX_VALUE
        if (best.first != card && best.second < now) card to best.first else null
    }.toMap()
}

@Composable
private fun CheapestDialog(draft: DeckDraft, onDismiss: () -> Unit) {
    val swaps by produceState<Map<PaperCard, PaperCard>?>(null) { value = withContext(Dispatchers.Default) { cheapestSwaps(draft) } }
    OrnateDialog(onDismiss = onDismiss, maxWidth = 480.dp) {
        SectionLabel("Cheapest printings")
        Spacer(Modifier.height(10.dp))
        val list = swaps
        if (list == null) {
            CircularProgressIndicator(color = Palette.Terracotta)
        } else {
            val saving = list.entries.sumOf { (old, new) ->
                val n = (draft.commander[old] ?: 0) + (draft.main[old] ?: 0)
                ((CardPrices.of(old)?.retail ?: 0f) - (CardPrices.of(new)?.retail ?: 0f)).toDouble().coerceAtLeast(0.0) * n
            }
            Text(
                if (list.isEmpty()) "Every card already uses its cheapest Card Kingdom printing."
                else "Switch ${list.size} card(s) to their cheapest Card Kingdom printing" +
                    if (saving > 0) ", saving about ${CardPrice.usd(saving.toFloat())}?" else "?",
                style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OrnateButton(if (list.isEmpty()) "Close" else "Cancel", onDismiss, Modifier.weight(1f), accent = Palette.Slate)
                if (list.isNotEmpty()) {
                    OrnateButton("Switch", { list.values.forEach { draft.usePrinting(it) }; onDismiss() }, Modifier.weight(1f), style = OrnateStyle.Primary)
                }
            }
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
    val collectionMode = LocalCollectionMode.current
    if (LocalCompactBuilder.current) {
        // Phones: count, value and icon-only buttons on one line, legality under it.
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (collectionMode) "${draft.total} cards" else "${draft.total} / $target", fontFamily = Cinzel, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = if (!collectionMode && (draft.total == target || (!commander && draft.total >= target))) Palette.Sage else Palette.Ink,
                )
                if (value > 0) Text(CardPrice.usd(value.toFloat()), fontFamily = Cinzel, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Slate)
                Spacer(Modifier.weight(1f))
                OrnateButton(
                    "", onList, Modifier.width(48.dp), icon = Icons.AutoMirrored.Filled.List,
                    style = if (listOpen) OrnateStyle.Primary else OrnateStyle.Secondary,
                    accent = Palette.Slate, frameColor = Palette.Slate, height = 42.dp,
                )
            }
            Text(
                if (collectionMode) "${draft.main.size} different cards" else problem ?: "Legal ✓",
                style = MaterialTheme.typography.bodySmall,
                color = if (problem == null) Palette.Sage else Palette.Terracotta,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (collectionMode) "${draft.total} cards" else "${draft.total} / $target", fontFamily = Cinzel, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            color = if (!collectionMode && (draft.total == target || (!commander && draft.total >= target))) Palette.Sage else Palette.Ink,
        )
        Text(
            if (collectionMode) "${draft.main.size} different cards" else problem ?: "Legal ✓",
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = if (problem == null) Palette.Sage else Palette.Terracotta,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (value > 0) {
            Text(CardPrice.usd(value.toFloat()), fontFamily = Cinzel, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Slate)
        }
        if (collectionMode) ClearCollectionButton()
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

    val cascade = remember { java.util.concurrent.atomic.AtomicInteger(4) }
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
            items(cards.sortedBy { it.first.rules.manaCost.cmc * 1000 + it.first.name.first().code }, key = { label + printingKey(it.first) }) { (card, n) ->
                val order = remember { cascade.getAndIncrement() }
                val commander = label == "Commander"
                val ui = remember(card) { CardUi.fromPaper(card) }
                var held by remember { mutableStateOf(false) }
                val prices by CardPrices.version.collectAsState()
                val price = remember(card, prices) { CardPrices.of(card) }
                Column(Modifier.landIn(order.coerceAtMost(30)), horizontalAlignment = Alignment.CenterHorizontally) {
                    val openHere = { val all = draft.ordered(); onOpen(all, all.indexOf(card).coerceAtLeast(0)) }
                    Box(Modifier.tapOrHold(card.name, onTap = { openHere() }, onHold = { h, _ -> held = h })) {
                        CardTile(ui, tile, showTapped = false)
                            OwnedBadge(card.name, Modifier.align(Alignment.BottomStart))
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
                items(draft.commander.entries.toList(), key = { "c" + printingKey(it.key) }) { (card, n) ->
                    DeckRow(card, n, isCommander = true, showStar = true, onOpen = { openCard(card) },
                        onAdd = {}, onRemove = { draft.remove(card, fromCommander = true) }, onStar = { draft.toggleCommander(card) })
                }
            }
            groups.forEach { (label, cards) ->
                if (cards.isEmpty()) return@forEach
                item { Spacer(Modifier.height(6.dp)); SectionLabel("$label · ${cards.sumOf { it.value }}") }
                items(cards.sortedBy { it.key.rules.manaCost.cmc * 1000 + it.key.name.first().code }, key = { "m" + printingKey(it.key) }) { (card, n) ->
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
            // Which printing: copies of one name in different printings are separate rows.
            Text(
                "${card.edition} #${card.collectorNumber}" + if (card.isFoil) " · ✦ Foil" else "",
                style = MaterialTheme.typography.labelSmall, color = Palette.InkSoft, maxLines = 1,
            )
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
