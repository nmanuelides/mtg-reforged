package io.github.nmanuelides.mtgreforged.ui.prompts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.nmanuelides.mtgreforged.engine.PendingPrompt
import io.github.nmanuelides.mtgreforged.engine.Prompt
import io.github.nmanuelides.mtgreforged.engine.PromptItem
import io.github.nmanuelides.mtgreforged.ui.components.CardMark
import io.github.nmanuelides.mtgreforged.ui.components.CardTile
import io.github.nmanuelides.mtgreforged.ui.theme.Palette

/** Renders whatever question the engine is currently waiting on. */
@Composable
fun PromptDialog(pending: PendingPrompt) {
    when (val p = pending.prompt) {
        is Prompt.Message -> MessageDialog(p) { pending.answer(Unit) }
        is Prompt.Options -> OptionsDialog(p) { pending.answer(it) }
        is Prompt.Input -> InputDialog(p) { pending.answer(it) }
        is Prompt.Choose -> ChooseDialog(p) { pending.answer(it) }
        is Prompt.Order -> OrderDialog(p) { pending.answer(it) }
        is Prompt.Distribute -> DistributeDialog(p) { pending.answer(it) }
    }
}

private val noDismiss = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)

@Composable
private fun MessageDialog(p: Prompt.Message, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = noDismiss,
        title = { Text(p.title, color = if (p.isError) Palette.Life else MaterialTheme.colorScheme.onSurface) },
        text = { Text(p.message, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { Button(onClick = onDone) { Text("OK") } },
    )
}

@Composable
private fun OptionsDialog(p: Prompt.Options, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = noDismiss,
        title = { if (p.title.isNotBlank()) Text(p.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                p.card?.let { CardTile(it, 160.dp); Spacer(Modifier.size(12.dp)) }
                Text(p.message)
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                p.options.forEachIndexed { i, label ->
                    if (i == p.defaultOption) {
                        Button(onClick = { onPick(i) }) { Text(label) }
                    } else {
                        FilledTonalButton(onClick = { onPick(i) }) { Text(label) }
                    }
                }
            }
        },
    )
}

@Composable
private fun InputDialog(p: Prompt.Input, onDone: (String?) -> Unit) {
    var value by remember(p) { mutableStateOf(p.initial.orEmpty()) }
    AlertDialog(
        onDismissRequest = {},
        properties = noDismiss,
        title = { Text(p.title.ifBlank { "Forge" }) },
        text = {
            Column {
                Text(p.message)
                Spacer(Modifier.size(8.dp))
                if (p.suggestions.isNullOrEmpty()) {
                    OutlinedTextField(
                        value, { value = it }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = if (p.numeric) KeyboardType.Number else KeyboardType.Text),
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        itemsIndexed(p.suggestions) { _, s ->
                            Text(s, Modifier.fillMaxWidth().clickable { onDone(s) }.padding(12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { if (p.suggestions.isNullOrEmpty()) Button(onClick = { onDone(value) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancelar") } },
    )
}

@Composable
private fun ChooseDialog(p: Prompt.Choose, onDone: (List<Int>?) -> Unit) {
    val selected = remember(p) { p.preselected.toMutableStateList() }
    val single = p.max == 1
    val hasCards = p.items.any { it.card != null }
    val valid = selected.size in p.min..p.max

    fun toggle(i: Int) {
        if (single) {
            onDone(listOf(i))
            return
        }
        if (i in selected) selected.remove(i) else if (selected.size < p.max) selected.add(i)
    }

    AlertDialog(
        onDismissRequest = {},
        properties = noDismiss,
        title = { Text(p.title.ifBlank { "Elegí" }) },
        text = {
            Column {
                if (p.max > 1) {
                    Text(
                        if (p.min == p.max) "Elegí ${p.min}" else "Elegí entre ${p.min} y ${p.max}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                if (hasCards) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(p.items) { i, item -> CardChoice(item, i in selected) { toggle(i) } }
                    }
                } else {
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        itemsIndexed(p.items) { i, item -> TextChoice(item.label, i in selected) { toggle(i) } }
                    }
                }
            }
        },
        confirmButton = {
            if (!single) Button(onClick = { onDone(selected.toList()) }, enabled = valid) { Text("Confirmar") }
        },
        dismissButton = {
            if (p.min == 0) TextButton(onClick = { onDone(if (single) emptyList() else null) }) { Text("Ninguno") }
        },
    )
}

@Composable
private fun CardChoice(item: PromptItem, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(120.dp)) {
        CardTile(item.card!!, 120.dp, mark = if (selected) CardMark.Highlighted else CardMark.None, showTapped = false, onClick = onClick)
        Text(item.label, style = MaterialTheme.typography.bodySmall, maxLines = 2, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TextChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(if (selected) Palette.EmberSoft else Palette.SurfaceHigh, RoundedCornerShape(10.dp))
            .border(1.dp, if (selected) Palette.Ember else Palette.Outline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) { Text(label) }
}

@Composable
private fun OrderDialog(p: Prompt.Order, onDone: (List<Int>) -> Unit) {
    val order = remember(p) { mutableStateListOf(*p.items.indices.toList().toTypedArray()) }
    AlertDialog(
        onDismissRequest = {},
        properties = noDismiss,
        title = { Text(p.title.ifBlank { "Ordená" }) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(order, key = { _, idx -> idx }) { pos, idx ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("${pos + 1}.", Modifier.width(28.dp), color = Palette.TextSecondary)
                        p.items[idx].card?.let { CardTile(it, 44.dp, showTapped = false); Spacer(Modifier.width(8.dp)) }
                        Text(p.items[idx].label, Modifier.weight(1f), maxLines = 2)
                        IconButton(onClick = { if (pos > 0) order.add(pos - 1, order.removeAt(pos)) }, enabled = pos > 0) {
                            Icon(Icons.Default.KeyboardArrowUp, "Subir")
                        }
                        IconButton(onClick = { if (pos < order.lastIndex) order.add(pos + 1, order.removeAt(pos)) }, enabled = pos < order.lastIndex) {
                            Icon(Icons.Default.KeyboardArrowDown, "Bajar")
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onDone(order.toList()) }) { Text("Confirmar") } },
    )
}

@Composable
private fun DistributeDialog(p: Prompt.Distribute, onDone: (List<Int>?) -> Unit) {
    val amounts = remember(p) { p.initial.toMutableStateList() }
    val assigned = amounts.sum()
    val valid = assigned == p.total && (!p.atLeastOne || amounts.all { it > 0 })
    AlertDialog(
        onDismissRequest = {},
        properties = noDismiss,
        title = { Text(p.title) },
        text = {
            Column {
                Text("Asignado: $assigned / ${p.total}", color = if (valid) Palette.Poison else Palette.TextSecondary)
                Spacer(Modifier.size(8.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    itemsIndexed(p.targets) { i, target ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            target.card?.let { CardTile(it, 44.dp, showTapped = false); Spacer(Modifier.width(8.dp)) }
                            Text(target.label, Modifier.weight(1f), maxLines = 2)
                            IconButton(onClick = { if (amounts[i] > 0) amounts[i]-- }) { Icon(Icons.Default.Remove, "Menos") }
                            Text("${amounts[i]}", Modifier.width(28.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { if (assigned < p.total) amounts[i]++ }) { Icon(Icons.Default.Add, "Más") }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onDone(amounts.toList()) }, enabled = valid) { Text("Confirmar") } },
        dismissButton = { if (p.maySkip) TextButton(onClick = { onDone(null) }) { Text("Automático") } },
    )
}
