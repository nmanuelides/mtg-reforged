package io.github.nmanuelides.mtgreforged.ui.prompts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import io.github.nmanuelides.mtgreforged.engine.PendingPrompt
import io.github.nmanuelides.mtgreforged.engine.Prompt
import io.github.nmanuelides.mtgreforged.engine.PromptItem
import io.github.nmanuelides.mtgreforged.ui.components.CardMark
import io.github.nmanuelides.mtgreforged.ui.components.CardTile
import io.github.nmanuelides.mtgreforged.ui.components.OrnateButton
import io.github.nmanuelides.mtgreforged.ui.components.OrnateDialog
import io.github.nmanuelides.mtgreforged.ui.components.OrnateStyle
import io.github.nmanuelides.mtgreforged.ui.components.SectionLabel
import io.github.nmanuelides.mtgreforged.ui.components.ornateFrame
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

/** Engine questions block the game, so they can't be dismissed by tapping outside. */
@Composable
private fun PromptFrame(title: String, wide: Boolean = false, content: @Composable () -> Unit) {
    OrnateDialog(onDismiss = {}, dismissible = false, maxWidth = if (wide) 860.dp else 560.dp) {
        if (title.isNotBlank()) {
            SectionLabel(title.take(60))
            Spacer(Modifier.height(14.dp))
        }
        content()
    }
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun MessageDialog(p: Prompt.Message, onDone: () -> Unit) {
    PromptFrame(if (p.isError) "Error" else p.title) {
        Body(p.message)
        Spacer(Modifier.height(18.dp))
        OrnateButton("OK", onDone, Modifier.fillMaxWidth(), style = OrnateStyle.Primary)
    }
}

@Composable
private fun OptionsDialog(p: Prompt.Options, onPick: (Int) -> Unit) {
    PromptFrame(p.title) {
        p.card?.let { CardTile(it, 180.dp, showTapped = false); Spacer(Modifier.height(12.dp)) }
        Body(p.message)
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            p.options.forEachIndexed { i, label ->
                OrnateButton(
                    label, { onPick(i) }, Modifier.fillMaxWidth(),
                    style = if (i == p.defaultOption) OrnateStyle.Primary else OrnateStyle.Secondary,
                )
            }
        }
    }
}

@Composable
private fun InputDialog(p: Prompt.Input, onDone: (String?) -> Unit) {
    var value by remember(p) { mutableStateOf(p.initial.orEmpty()) }
    PromptFrame(p.title.ifBlank { "Forge" }) {
        Body(p.message)
        Spacer(Modifier.height(12.dp))
        if (p.suggestions.isNullOrEmpty()) {
            OutlinedTextField(
                value, { value = it }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(keyboardType = if (p.numeric) KeyboardType.Number else KeyboardType.Text),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Palette.Terracotta, unfocusedBorderColor = Palette.Line,
                    focusedContainerColor = Palette.PaperLight, unfocusedContainerColor = Palette.PaperLight,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OrnateButton("Cancel", { onDone(null) }, Modifier.weight(1f), accent = Palette.Slate)
                OrnateButton("OK", { onDone(value) }, Modifier.weight(1f), style = OrnateStyle.Primary)
            }
        } else {
            LazyColumn(Modifier.heightIn(max = 340.dp)) {
                itemsIndexed(p.suggestions) { _, s -> TextChoice(s, false) { onDone(s) } }
            }
            Spacer(Modifier.height(12.dp))
            OrnateButton("Cancel", { onDone(null) }, Modifier.fillMaxWidth(), accent = Palette.Slate)
        }
    }
}

@Composable
private fun ChooseDialog(p: Prompt.Choose, onDone: (List<Int>?) -> Unit) {
    val selected = remember(p) { p.preselected.toMutableStateList() }
    // Forge passes min = max = -1 to just show a list (reveal).
    val displayOnly = p.max <= 0
    val single = p.max == 1
    val hasCards = p.items.any { it.card != null }
    val valid = selected.size in p.min..p.max

    fun toggle(i: Int) {
        if (displayOnly) return
        if (single) {
            onDone(listOf(i))
            return
        }
        if (i in selected) selected.remove(i) else if (selected.size < p.max) selected.add(i)
    }

    PromptFrame(p.title.ifBlank { "Choose" }, wide = hasCards) {
        if (p.max > 1) {
            Text(
                if (p.min == p.max) "Choose ${p.min}" else "Choose ${p.min} to ${p.max}",
                style = MaterialTheme.typography.labelMedium, color = Palette.InkSoft,
            )
            Spacer(Modifier.height(10.dp))
        }
        if (hasCards) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(p.items) { i, item -> CardChoice(item, i in selected) { toggle(i) } }
            }
        } else {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                itemsIndexed(p.items) { i, item -> TextChoice(item.label, i in selected) { toggle(i) } }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (p.min == 0 && !displayOnly) {
                OrnateButton("None", { onDone(if (single) emptyList() else null) }, Modifier.weight(1f), accent = Palette.Slate)
            }
            when {
                displayOnly -> OrnateButton("OK", { onDone(emptyList()) }, Modifier.weight(1f), style = OrnateStyle.Primary)
                !single -> OrnateButton("Confirm", { onDone(selected.toList()) }, Modifier.weight(1f), style = OrnateStyle.Primary, enabled = valid)
            }
        }
    }
}

@Composable
private fun CardChoice(item: PromptItem, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(150.dp)) {
        CardTile(item.card!!, 150.dp, mark = if (selected) CardMark.Highlighted else CardMark.None, showTapped = false, onClick = onClick)
        Spacer(Modifier.height(4.dp))
        Text(item.label, style = MaterialTheme.typography.bodySmall, maxLines = 2, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TextChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(if (selected) Palette.EmberSoft else Palette.PaperLight)
            .ornateFrame(if (selected) Palette.Terracotta else Palette.Line, inset = 3.dp, corners = false)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun OrderDialog(p: Prompt.Order, onDone: (List<Int>) -> Unit) {
    val order = remember(p) { mutableStateListOf(*p.items.indices.toList().toTypedArray()) }
    PromptFrame(p.title.ifBlank { "Order" }) {
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            itemsIndexed(order, key = { _, idx -> idx }) { pos, idx ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text("${pos + 1}.", Modifier.width(28.dp), style = MaterialTheme.typography.titleMedium, color = Palette.Terracotta)
                    p.items[idx].card?.let { CardTile(it, 48.dp, showTapped = false); Spacer(Modifier.width(10.dp)) }
                    Text(p.items[idx].label, Modifier.weight(1f), maxLines = 2, style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { if (pos > 0) order.add(pos - 1, order.removeAt(pos)) }, enabled = pos > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, "Up", tint = Palette.Terracotta)
                    }
                    IconButton(onClick = { if (pos < order.lastIndex) order.add(pos + 1, order.removeAt(pos)) }, enabled = pos < order.lastIndex) {
                        Icon(Icons.Default.KeyboardArrowDown, "Down", tint = Palette.Terracotta)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        OrnateButton("Confirm", { onDone(order.toList()) }, Modifier.fillMaxWidth(), style = OrnateStyle.Primary)
    }
}

@Composable
private fun DistributeDialog(p: Prompt.Distribute, onDone: (List<Int>?) -> Unit) {
    val amounts = remember(p) { p.initial.toMutableStateList() }
    val assigned = amounts.sum()
    val valid = assigned == p.total && (!p.atLeastOne || amounts.all { it > 0 })
    PromptFrame(p.title) {
        Text(
            "Assigned $assigned / ${p.total}",
            style = MaterialTheme.typography.labelLarge,
            color = if (valid) Palette.Sage else Palette.InkSoft,
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.heightIn(max = 380.dp)) {
            itemsIndexed(p.targets) { i, target ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    target.card?.let { CardTile(it, 48.dp, showTapped = false); Spacer(Modifier.width(10.dp)) }
                    Text(target.label, Modifier.weight(1f), maxLines = 2, style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { if (amounts[i] > 0) amounts[i]-- }) { Icon(Icons.Default.Remove, "Less", tint = Palette.Terracotta) }
                    Text("${amounts[i]}", Modifier.width(32.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { if (assigned < p.total) amounts[i]++ }) { Icon(Icons.Default.Add, "More", tint = Palette.Terracotta) }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (p.maySkip) OrnateButton("Auto", { onDone(null) }, Modifier.weight(1f), accent = Palette.Slate)
            OrnateButton("Confirm", { onDone(amounts.toList()) }, Modifier.weight(1f), style = OrnateStyle.Primary, enabled = valid)
        }
    }
}
