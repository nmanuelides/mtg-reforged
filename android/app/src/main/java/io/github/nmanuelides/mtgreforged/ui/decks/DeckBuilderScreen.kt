package io.github.nmanuelides.mtgreforged.ui.decks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
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

private val COLOR_FILTERS = listOf(
    "W" to MagicColor.WHITE, "U" to MagicColor.BLUE, "B" to MagicColor.BLACK,
    "R" to MagicColor.RED, "G" to MagicColor.GREEN,
)
private val TYPE_FILTERS = listOf("Creature", "Instant", "Sorcery", "Artifact", "Enchantment", "Planeswalker", "Land")

/** Editable deck: copies per card, split into the command zone and the main deck. */
private class DeckDraft(val commander: SnapshotStateMap<PaperCard, Int>, val main: SnapshotStateMap<PaperCard, Int>) {
    val total get() = commander.values.sum() + main.values.sum()

    fun toDeck(name: String) = Deck(name).apply {
        commander.forEach { (c, n) -> getOrCreate(DeckSection.Commander).add(c, n) }
        main.forEach { (c, n) -> getOrCreate(DeckSection.Main).add(c, n) }
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

private fun CardUi.Companion.fromPaper(card: PaperCard): CardUi {
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
                    CardSearch(Modifier.weight(0.58f).fillMaxHeight()) { draft.add(it) }
                    DeckList(draft, format, problem, Modifier.weight(0.42f).fillMaxHeight())
                }
            } else {
                CardSearch(Modifier.fillMaxWidth().weight(0.55f)) { draft.add(it) }
                Spacer(Modifier.height(8.dp))
                DeckList(draft, format, problem, Modifier.fillMaxWidth().weight(0.45f))
            }
        }
    }

    if (importing) {
        ImportDialog(
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
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.Terracotta, unfocusedBorderColor = Palette.Line,
    focusedContainerColor = Palette.PaperLight, unfocusedContainerColor = Palette.PaperLight,
    focusedTextColor = Palette.Ink, unfocusedTextColor = Palette.Ink,
)

// ---- Search ------------------------------------------------------------------------

@Composable
private fun CardSearch(modifier: Modifier, onAdd: (PaperCard) -> Unit) {
    var query by remember { mutableStateOf("") }
    val colors = remember { mutableStateListOf<Byte>() }
    val types = remember { mutableStateListOf<String>() }
    var results by remember { mutableStateOf<List<PaperCard>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(query, colors.toList(), types.toList()) {
        delay(250) // debounce typing
        searching = true
        results = withContext(Dispatchers.Default) { search(query, colors.toSet(), types.toSet()) }
        searching = false
    }

    Column(modifier.background(Palette.PaperLight).ornateFrame(inset = 4.dp).padding(12.dp)) {
        OutlinedTextField(
            query, { query = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Palette.InkSoft) },
            placeholder = { Text("Search cards by name or rules text", style = MaterialTheme.typography.bodyMedium) },
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            COLOR_FILTERS.forEach { (label, color) ->
                Chip(label, color in colors) { if (color in colors) colors.remove(color) else colors.add(color) }
            }
            Spacer(Modifier.width(6.dp))
            TYPE_FILTERS.forEach { type ->
                Chip(type.take(5), type in types) { if (type in types) types.remove(type) else types.add(type) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                GridCells.Adaptive(118.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(results, key = { it.name + it.edition }) { card ->
                    val ui = remember(card) { CardUi.fromPaper(card) }
                    var held by remember { mutableStateOf(false) }
                    Box(Modifier.tapOrHold(card.name, onTap = { onAdd(card) }, onHold = { h, _ -> held = h })) {
                        CardTile(ui, 118.dp, showTapped = false)
                        HoldZoom(ui, held)
                    }
                }
            }
            if (searching) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Palette.Terracotta)
            if (!searching && results.isEmpty()) {
                Text("No cards match.", Modifier.align(Alignment.Center), color = Palette.InkSoft)
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        Modifier
            .background(if (selected) Palette.Terracotta else Palette.Paper)
            .ornateFrame(if (selected) Palette.OnAccent.copy(alpha = 0.6f) else Palette.Line, inset = 2.dp, corners = false)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        fontFamily = Cinzel, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        color = if (selected) Palette.OnAccent else Palette.InkSoft,
    )
}

private fun search(query: String, colors: Set<Byte>, types: Set<String>): List<PaperCard> {
    val q = query.trim().lowercase()
    if (q.length < 2 && colors.isEmpty() && types.isEmpty()) return emptyList()
    return StaticData.instance().commonCards.uniqueCards.asSequence()
        .filter { card ->
            val rules = card.rules
            (q.isEmpty() || q in card.name.lowercase() || q in rules.oracleText.orEmpty().lowercase()) &&
                (colors.isEmpty() || colors.all { rules.color.hasAnyColor(it.toInt()) }) &&
                (types.isEmpty() || types.any { rules.type.toString().contains(it) })
        }
        .sortedWith(compareBy({ !it.name.lowercase().startsWith(q) }, { it.name }))
        .take(240)
        .toList()
}

// ---- Deck list ---------------------------------------------------------------------

@Composable
private fun DeckList(draft: DeckDraft, format: Engine.Format, problem: String?, modifier: Modifier) {
    val commander = format == Engine.Format.Commander
    val target = if (commander) 100 else 60
    val main = draft.main.entries.toList()
    val groups = listOf(
        "Creatures" to main.filter { it.key.rules.type.isCreature },
        "Spells" to main.filter { !it.key.rules.type.isCreature && !it.key.rules.type.isLand },
        "Lands" to main.filter { it.key.rules.type.isLand },
    )

    Column(modifier.background(Palette.PaperLight).ornateFrame(inset = 4.dp).padding(12.dp)) {
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
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            if (commander) {
                item { SectionLabel("Commander") }
                if (draft.commander.isEmpty()) {
                    item {
                        Text("Tap ☆ on a legendary creature to make it your commander.",
                            Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
                    }
                }
                items(draft.commander.entries.toList(), key = { "c" + it.key.name }) { (card, n) ->
                    DeckRow(card, n, isCommander = true, showStar = true,
                        onAdd = {}, onRemove = { draft.remove(card, fromCommander = true) }, onStar = { draft.toggleCommander(card) })
                }
            }
            groups.forEach { (label, cards) ->
                if (cards.isEmpty()) return@forEach
                item { Spacer(Modifier.height(6.dp)); SectionLabel("$label · ${cards.sumOf { it.value }}") }
                items(cards.sortedBy { it.key.rules.manaCost.cmc * 1000 + it.key.name.first().code }, key = { "m" + it.key.name + it.key.edition }) { (card, n) ->
                    DeckRow(card, n, isCommander = false, showStar = commander && card.rules.type.isLegendary && card.rules.type.isCreature,
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
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onStar: () -> Unit,
) {
    val ui = remember(card) { CardUi.fromPaper(card) }
    var held by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).tapOrHold(card.name, onTap = {}, onHold = { h, _ -> held = h }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).background(Palette.Paper, CircleShape), contentAlignment = Alignment.Center) {
            Text("$count", fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Palette.Ink)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(card.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(ui.manaCost, style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
        }
        if (showStar) {
            IconButton(onClick = onStar) {
                Icon(if (isCommander) Icons.Default.Star else Icons.Default.StarBorder, "Commander", tint = Palette.Ochre)
            }
        }
        IconButton(onClick = onRemove) { Icon(Icons.Default.Remove, "Remove one", tint = Palette.Terracotta) }
        if (!isCommander) IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add one", tint = Palette.Sage) }
        Box { HoldZoom(ui, held) }
    }
}

// ---- Import ------------------------------------------------------------------------

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onImported: (DeckImport.Result) -> Unit) {
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
                            val result = withContext(Dispatchers.IO) { runCatching { DeckImport.import(text, "Imported deck") } }
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
