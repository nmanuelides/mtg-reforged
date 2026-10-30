package io.github.nmanuelides.mtgreforged.ui.decks

import io.github.nmanuelides.mtgreforged.ui.components.SetSymbol
import io.github.nmanuelides.mtgreforged.ui.components.ManaSymbol
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import io.github.nmanuelides.mtgreforged.ui.components.ManaPip
import io.github.nmanuelides.mtgreforged.ui.components.ManaChip
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import forge.card.CardRarity
import io.github.nmanuelides.mtgreforged.engine.CardPrices
import io.github.nmanuelides.mtgreforged.ui.components.OrnateButton
import io.github.nmanuelides.mtgreforged.ui.components.OrnateStyle
import io.github.nmanuelides.mtgreforged.ui.components.SectionLabel
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val RARITY_LABELS = mapOf(
    CardRarity.Common to "Common", CardRarity.Uncommon to "Uncommon", CardRarity.Rare to "Rare",
    CardRarity.MythicRare to "Mythic", CardRarity.Special to "Special",
)

/**
 * Every search option in one scrolling panel: general switches and sort on top, then the
 * linked filters (all must match), each collapsed to a one-line summary until opened.
 */
@Composable
fun FilterPanel(
    filter: CardFilter,
    onChange: (CardFilter) -> Unit,
    matches: Int,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    collectionAvailable: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(setOf<String>()) }
    fun section(key: String) = key in open
    fun toggle(key: String) { open = if (key in open) open - key else open + key }

    Column(modifier) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionLabel("General", Modifier.fillMaxWidth())
            ToggleRow("Exact-name match", "Match the card name exactly, from beginning to end.", filter.exactName) {
                onChange(filter.copy(exactName = it))
            }
            ToggleRow("Hide duplicates", "Show one printing of each card, even if there are several versions.", filter.hideDuplicates) {
                onChange(filter.copy(hideDuplicates = it))
            }
            FilterRow("Search in", "Every card, or only the ones you already have", filter.within.label, section("within"), { toggle("within") }) {
                ChipFlow {
                    Within.entries.filter { it != Within.Collection || collectionAvailable }.forEach { w ->
                        Chip(w.label, filter.within == w) { onChange(filter.copy(within = w)) }
                    }
                }
            }
            FilterRow("Finish", "Foil shows foil printings with foil prices", filter.finish.label.takeIf { filter.finish != Finish.Any },
                section("finish"), { toggle("finish") }, onClear = { onChange(filter.copy(finish = Finish.Any)) }) {
                ChipFlow { Finish.entries.forEach { f -> Chip(f.label, filter.finish == f) { onChange(filter.copy(finish = f)) } } }
            }
            ToggleRow("Include Alchemy cards", "Arena-only rebalanced versions (A-…). Not legal in paper.", filter.includeAlchemy) {
                onChange(filter.copy(includeAlchemy = it))
            }
            FilterRow("Sort by", "How results are ordered", "${filter.sort.label} ${if (filter.descending) "↓" else "↑"}", section("sort"), { toggle("sort") }) {
                ChipFlow {
                    SortBy.entries.forEach { s -> Chip(s.label, filter.sort == s) { onChange(filter.copy(sort = s)) } }
                }
                Spacer(Modifier.height(6.dp))
                ChipFlow {
                    Chip("Ascending ↑", !filter.descending) { onChange(filter.copy(descending = false)) }
                    Chip("Descending ↓", filter.descending) { onChange(filter.copy(descending = true)) }
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel("Filters · linked", Modifier.fillMaxWidth())

            val colorPips = ManaPip.WUBRG.filter { it.color in filter.colors } + listOfNotNull(ManaPip.Colorless.takeIf { filter.colorless })
            FilterRow(
                "Colors", "Colors of a spell or creature",
                when {
                    colorPips.isEmpty() -> if (filter.multicolor) "Multicolor" else null
                    filter.multicolor -> "Multicolor · ${filter.colorMode.label}"
                    else -> filter.colorMode.label
                },
                section("colors"), { toggle("colors") }, onClear = { onChange(filter.copy(colors = emptySet(), colorless = false, multicolor = false)) },
                symbols = { colorPips.forEach { ManaSymbol(it, 18.dp) } },
            ) {
                ChipFlow {
                    ManaPip.WUBRG.forEach { pip ->
                        ManaChip(pip, pip.color in filter.colors) { onChange(filter.copy(colors = filter.colors.toggle(pip.color))) }
                    }
                    ManaChip(ManaPip.Colorless, filter.colorless) { onChange(filter.copy(colorless = !filter.colorless)) }
                }
                Spacer(Modifier.height(6.dp))
                ChipFlow {
                    ColorMode.entries.forEach { m -> Chip(m.label, filter.colorMode == m) { onChange(filter.copy(colorMode = m)) } }
                    // Two or more colours, on top of the colours picked (none: any multicolour card).
                    Chip("Multicolor", filter.multicolor) { onChange(filter.copy(multicolor = !filter.multicolor)) }
                }
            }
            FilterRow(
                "Color identity", "Commander colors: cards that fit within them",
                filter.identity.takeIf { it.isNotEmpty() }?.let { "" },
                section("identity"), { toggle("identity") }, onClear = { onChange(filter.copy(identity = emptySet())) },
                symbols = { ManaPip.WUBRG.filter { it.color in filter.identity }.forEach { ManaSymbol(it, 18.dp) } },
            ) {
                ChipFlow {
                    ManaPip.WUBRG.forEach { pip ->
                        ManaChip(pip, pip.color in filter.identity) { onChange(filter.copy(identity = filter.identity.toggle(pip.color))) }
                    }
                }
            }
            FilterRow(
                "Sets / Editions", null, filter.sets.takeIf { it.isNotEmpty() }?.let { "" },
                section("sets"), { toggle("sets") }, onClear = { onChange(filter.copy(sets = emptySet())) },
                symbols = {
                    filter.sets.take(4).forEach { code ->
                        SetSymbol(code, Palette.Terracotta, 16.dp)
                        Text(code, style = MaterialTheme.typography.bodyMedium, color = Palette.Terracotta, maxLines = 1)
                    }
                    if (filter.sets.size > 4) Text("+${filter.sets.size - 4}", style = MaterialTheme.typography.bodyMedium, color = Palette.Terracotta)
                },
            ) {
                Picker({ FilterOptions.sets }, filter.sets, "Search sets by name or code") {
                    onChange(filter.copy(sets = filter.sets.toggle(it)))
                }
            }
            GroupRow("Keywords", "'Flying', 'Haste'…", filter.keywords, section("keywords"), { toggle("keywords") },
                { onChange(filter.copy(keywords = it)) }) { group, set ->
                Picker({ FilterOptions.keywords.map { it to it } }, group.values, "Search keywords") { set(group.toggle(it)) }
            }
            FilterRow(
                "Legality", "Legal in a format", filter.legality,
                section("legality"), { toggle("legality") }, onClear = { onChange(filter.copy(legality = null)) },
            ) {
                val formats by produceState(emptyList<String>()) { value = withContext(Dispatchers.Default) { FilterOptions.formats } }
                ChipFlow {
                    formats.forEach { f -> Chip(f, filter.legality == f) { onChange(filter.copy(legality = f.takeIf { filter.legality != f })) } }
                }
            }
            FilterRow(
                "Mana cost", "Exact casting cost & symbols", filter.manaCost.ifBlank { null },
                section("cost"), { toggle("cost") }, onClear = { onChange(filter.copy(manaCost = "")) },
            ) {
                TextFilter(filter.manaCost, "e.g. {2}{G}{G} or 2GG") { onChange(filter.copy(manaCost = it)) }
            }
            NumberRow("Mana value", "Total cost to cast a spell", filter.manaValue, section("mv"), { toggle("mv") }) {
                onChange(filter.copy(manaValue = it))
            }
            GroupRow("Text", "'destroy', 'flying'… add several", filter.text, section("text"), { toggle("text") },
                { onChange(filter.copy(text = it)) }) { group, set ->
                TermInput(group.values, "Rules text contains…", onAdd = { set(group.copy(values = group.values + it)) }) {
                    set(group.toggle(it))
                }
            }
            GroupRow("Type", null, filter.types, section("type"), { toggle("type") }, { onChange(filter.copy(types = it)) }) { group, set ->
                ChipFlow { FilterOptions.types.forEach { t -> Chip(t, t in group.values) { set(group.toggle(t)) } } }
            }
            GroupRow("Supertype", "Legendary, Basic, Snow…", filter.supertypes, section("super"), { toggle("super") },
                { onChange(filter.copy(supertypes = it)) }) { group, set ->
                ChipFlow { FilterOptions.supertypes.forEach { t -> Chip(t, t in group.values) { set(group.toggle(t)) } } }
            }
            GroupRow("Subtype", "Elf, Equipment, Saga…", filter.subtypes, section("sub"), { toggle("sub") },
                { onChange(filter.copy(subtypes = it)) }) { group, set ->
                Picker({ FilterOptions.subtypes.map { it to it } }, group.values, "Search subtypes") { set(group.toggle(it)) }
            }
            NumberRow("Power", null, filter.power, section("power"), { toggle("power") }) { onChange(filter.copy(power = it)) }
            NumberRow("Toughness", null, filter.toughness, section("tough"), { toggle("tough") }) { onChange(filter.copy(toughness = it)) }
            NumberRow(
                "Price", if (CardPrices.available) "Card Kingdom, USD" else "Card Kingdom prices download on Wi-Fi",
                filter.price, section("price"), { toggle("price") }, decimals = true,
            ) { onChange(filter.copy(price = it)) }
            FilterRow(
                "Rarity", null, filter.rarities.takeIf { it.isNotEmpty() }?.joinToString(", ") { RARITY_LABELS[it].orEmpty() },
                section("rarity"), { toggle("rarity") }, onClear = { onChange(filter.copy(rarities = emptySet())) },
            ) {
                ChipFlow {
                    FilterOptions.rarities.forEach { r ->
                        Chip(RARITY_LABELS[r] ?: r.name, r in filter.rarities) { onChange(filter.copy(rarities = filter.rarities.toggle(r))) }
                    }
                }
            }
            FilterRow(
                "Artist", null, filter.artist.ifBlank { null },
                section("artist"), { toggle("artist") }, onClear = { onChange(filter.copy(artist = "")) },
            ) {
                TextFilter(filter.artist, "Artist name") { onChange(filter.copy(artist = it)) }
                val artists by produceState(emptyList<String>()) { value = withContext(Dispatchers.Default) { FilterOptions.artists } }
                val q = filter.artist.trim()
                if (q.length >= 2) {
                    val hits = remember(q, artists) { artists.filter { q.lowercase() in it.lowercase() && !it.equals(q, true) }.take(6) }
                    hits.forEach { a -> SuggestionRow(a, false) { onChange(filter.copy(artist = a)) } }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OrnateButton(
                "Clear all", { onChange(CardFilter(hideDuplicates = filter.hideDuplicates, includeAlchemy = filter.includeAlchemy, sort = filter.sort, descending = filter.descending)) },
                Modifier.weight(1f), accent = Palette.Slate, enabled = filter.activeCount > 0,
            )
            OrnateButton("Show $matches", onDone, Modifier.weight(1.3f), style = OrnateStyle.Primary)
        }
    }
}

private fun <T> Set<T>.toggle(value: T) = if (value in this) this - value else this + value

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
        }
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.PaperLight, checkedTrackColor = Palette.Terracotta,
                uncheckedThumbColor = Palette.InkSoft, uncheckedTrackColor = Palette.Paper, uncheckedBorderColor = Palette.Line,
            ),
        )
    }
}

/** One filter: title and summary on a line, its controls underneath while open. */
@Composable
private fun FilterRow(
    title: String,
    subtitle: String?,
    summary: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClear: (() -> Unit)? = null,
    /** Drawn after the summary text, e.g. mana symbols. */
    symbols: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // Opening a filter scrolls the panel so its controls show, once they've unfolded.
    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(380)
            bring.bringIntoView()
        }
    }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(bring)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft) }
            }
            Text(
                summary ?: "Select",
                Modifier.weight(1f, fill = false).padding(start = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (summary != null) Palette.Terracotta else Palette.InkSoft,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (summary != null && symbols != null) {
                Row(Modifier.padding(start = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    symbols()
                }
            }
            if (summary != null && onClear != null) {
                Icon(Icons.Default.Close, "Clear", Modifier.padding(start = 6.dp).clickable(onClick = onClear), tint = Palette.Terracotta)
            }
            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = Palette.InkSoft)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) { content() }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line.copy(alpha = 0.5f)))
    }
}

/** A filter over a group of values with an Any / All / None-of switch. */
@Composable
private fun GroupRow(
    title: String,
    subtitle: String?,
    group: Group,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: (Group) -> Unit,
    content: @Composable (Group, (Group) -> Unit) -> Unit,
) {
    val summary = group.values.takeIf { it.isNotEmpty() }?.let {
        (if (group.mode == GroupMode.Not) "NOT " else "") + it.joinToString(if (group.mode == GroupMode.Any) " or " else ", ")
    }
    FilterRow(title, subtitle, summary, expanded, onToggle, onClear = { onChange(group.copy(values = emptySet())) }) {
        ChipFlow { GroupMode.entries.forEach { m -> Chip(m.label, group.mode == m) { onChange(group.copy(mode = m)) } } }
        Spacer(Modifier.height(8.dp))
        content(group, onChange)
    }
}

@Composable
private fun NumberRow(
    title: String,
    subtitle: String?,
    value: NumberFilter?,
    expanded: Boolean,
    onToggle: () -> Unit,
    decimals: Boolean = false,
    onChange: (NumberFilter?) -> Unit,
) {
    FilterRow(title, subtitle, value?.toString(), expanded, onToggle, onClear = { onChange(null) }) {
        var op by remember { mutableStateOf(value?.op ?: NumberFilter.Op.EQ) }
        var text by remember { mutableStateOf(value?.toString()?.substringAfter(' ').orEmpty()) }
        fun emit() = onChange(text.toDoubleOrNull()?.let { NumberFilter(op, it) })
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChipFlow(Modifier.weight(1f)) {
                NumberFilter.Op.entries.forEach { o -> Chip(" ${o.symbol} ", op == o, fontSize = 18) { op = o; emit() } }
            }
            Spacer(Modifier.width(10.dp))
            OutlinedTextField(
                text, { t -> text = t.filter { it.isDigit() || (decimals && it == '.') }.take(if (decimals) 7 else 2); emit() },
                singleLine = true, placeholder = { Text("0") },
                keyboardOptions = KeyboardOptions(keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyLarge, colors = fieldColors(),
                modifier = Modifier.width(90.dp),
            )
        }
    }
}

@Composable
private fun TextFilter(value: String, placeholder: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, singleLine = true,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
        textStyle = MaterialTheme.typography.bodyLarge, colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Free-text terms: type one, add it with Enter or +, remove it by tapping its chip. */
@Composable
private fun TermInput(terms: Set<String>, placeholder: String, onAdd: (String) -> Unit, onRemove: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    fun commit() {
        val term = draft.trim()
        if (term.isNotEmpty()) onAdd(term)
        draft = ""
    }
    if (terms.isNotEmpty()) {
        ChipFlow { terms.forEach { t -> Chip("\"$t\" ×", true) { onRemove(t) } } }
        Spacer(Modifier.height(6.dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            draft, { draft = it }, singleLine = true,
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
            textStyle = MaterialTheme.typography.bodyLarge, colors = fieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            // Hardware keyboards (emulators, tablets with covers) send Enter as a key event.
            modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
                    if (event.type == KeyEventType.KeyUp) commit()
                    true
                } else false
            },
        )
        Spacer(Modifier.width(8.dp))
        OrnateButton("", { commit() }, Modifier.width(56.dp), icon = Icons.Default.Add, enabled = draft.isNotBlank(), height = 56.dp)
    }
}

/** Searchable multi-select: picked values as chips, matches listed as you type. */
@Composable
private fun Picker(options: () -> List<Pair<String, String>>, selected: Set<String>, placeholder: String, onToggle: (String) -> Unit) {
    val all by produceState(emptyList<Pair<String, String>>()) { value = withContext(Dispatchers.Default) { options() } }
    var query by remember { mutableStateOf("") }
    if (selected.isNotEmpty()) {
        ChipFlow { selected.forEach { v -> Chip("$v ×", true) { onToggle(v) } } }
        Spacer(Modifier.height(6.dp))
    }
    TextFilter(query, placeholder) { query = it }
    val q = query.trim().lowercase()
    val hits = remember(q, all) {
        if (q.isEmpty()) all.take(8) else all.filter { (k, label) -> q in label.lowercase() || k.equals(q, true) }.take(8)
    }
    hits.forEach { (key, label) -> SuggestionRow(label, key in selected) { onToggle(key) } }
}

@Composable
private fun SuggestionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        (if (selected) "✓  " else "") + label,
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp, horizontal = 6.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = if (selected) Palette.Terracotta else Palette.Ink,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}
