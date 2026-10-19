package io.github.nmanuelides.mtgreforged.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import forge.gamemodes.match.LobbySlotType
import io.github.nmanuelides.mtgreforged.engine.Engine
import io.github.nmanuelides.mtgreforged.engine.Online
import io.github.nmanuelides.mtgreforged.ui.components.OrnateButton
import io.github.nmanuelides.mtgreforged.ui.components.OrnateDialog
import io.github.nmanuelides.mtgreforged.ui.components.OrnateStyle
import io.github.nmanuelides.mtgreforged.ui.components.SectionLabel
import io.github.nmanuelides.mtgreforged.ui.components.landIn
import io.github.nmanuelides.mtgreforged.ui.components.ornateFrame
import io.github.nmanuelides.mtgreforged.ui.components.paper
import io.github.nmanuelides.mtgreforged.ui.components.parchment
import io.github.nmanuelides.mtgreforged.ui.decks.fieldColors
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.nmanuelides.mtgreforged.platform.BackHandler

/** Play with friends on the same Wi-Fi: host a lobby or join one, pick decks, ready up, start. */
@Composable
fun OnlineScreen(format: Engine.Format, decks: List<Engine.DeckEntry>, onBack: () -> Unit) {
    val lobby by Online.lobby.collectAsState()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(runCatching { forge.model.FModel.getPreferences().getPref(forge.localinstance.properties.ForgePreferences.FPref.PLAYER_NAME) }.getOrNull().orEmpty().ifBlank { "Player" }) }
    var address by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<((Engine.DeckEntry) -> Unit)?>(null) }
    val leave = { scope.launch(Dispatchers.IO) { Online.leave() }; onBack() }
    BackHandler { leave() }

    Column(
        Modifier.fillMaxSize().parchment().statusBarsPadding().navigationBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().landIn(0), verticalAlignment = Alignment.CenterVertically) {
            OrnateButton("", { leave() }, icon = Icons.AutoMirrored.Filled.ArrowBack)
            Spacer(Modifier.padding(start = 12.dp))
            SectionLabel("Play with friends · ${format.label}")
        }
        Spacer(Modifier.height(16.dp))
        val current = lobby
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().landIn(1), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (current == null || current.seats.isEmpty()) {
                // ---- Not connected yet: host or join ----
                Text(
                    "Everyone must be on the same Wi-Fi. One player hosts; the others join by typing the host's address.",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft,
                )
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Your name") }, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                OrnateButton(
                    if (busy) "Opening…" else "Host a game", {
                        busy = true
                        scope.launch { withContext(Dispatchers.IO) { runCatching { Online.host(format, name) } }; busy = false }
                    },
                    Modifier.fillMaxWidth(), icon = Icons.Default.Wifi, style = OrnateStyle.Primary, enabled = !busy, height = 56.dp,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        address, { address = it }, singleLine = true, label = { Text("Host address (e.g. 192.168.1.20)") },
                        colors = fieldColors(), modifier = Modifier.weight(1f),
                    )
                    OrnateButton(
                        if (busy) "Joining…" else "Join", {
                            busy = true
                            scope.launch { withContext(Dispatchers.IO) { runCatching { Online.join(address, format, name) } }; busy = false }
                        },
                        enabled = !busy && address.isNotBlank(), accent = Palette.Slate, height = 56.dp,
                    )
                }
                current?.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Terracotta) }
            } else {
                // ---- In a lobby ----
                if (current.role == Online.Role.Host && current.addresses.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().paper(Palette.PaperLight).ornateFrame(Palette.Sage, inset = 3.dp, corners = false).padding(14.dp)) {
                        Text("Friends join with", style = MaterialTheme.typography.labelSmall, color = Palette.InkSoft)
                        current.addresses.forEach { Text(it, style = MaterialTheme.typography.headlineSmall, color = Palette.Sage) }
                    }
                }
                current.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Terracotta) }
                current.seats.forEach { seat ->
                    SeatRow(
                        seat, host = current.role == Online.Role.Host,
                        onAi = { picking = { entry -> scope.launch(Dispatchers.IO) { Online.setAi(seat.index, Engine.loadDeck(entry)) } } },
                        onOpen = { scope.launch(Dispatchers.IO) { Online.setAi(seat.index, null) } },
                        onRemove = { scope.launch(Dispatchers.IO) { Online.removeSeat(seat.index) } },
                    )
                }
                if (current.role == Online.Role.Host && current.seats.size < 4) {
                    OrnateButton("Add seat", { scope.launch(Dispatchers.IO) { Online.addSeat() } }, Modifier.fillMaxWidth(), icon = Icons.Default.Add, accent = Palette.Slate)
                }
                val me = current.seats.firstOrNull { it.mine }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OrnateButton(
                        me?.deck?.let { "Deck: $it" } ?: "Choose your deck",
                        { picking = { entry -> scope.launch(Dispatchers.IO) { Online.chooseDeck(Engine.loadDeck(entry)) } } },
                        Modifier.weight(1f), accent = Palette.Sage, frameColor = Palette.Sage,
                    )
                    OrnateButton(
                        if (me?.ready == true) "Ready ✓" else "Ready", { scope.launch(Dispatchers.IO) { Online.setReady(me?.ready != true) } },
                        Modifier.weight(0.6f), style = if (me?.ready == true) OrnateStyle.Primary else OrnateStyle.Secondary, enabled = me?.deck != null,
                    )
                }
                if (current.role == Online.Role.Host) {
                    OrnateButton(
                        "Start", { Online.start() }, Modifier.fillMaxWidth(), style = OrnateStyle.Primary, height = 56.dp,
                        enabled = current.seats.count { it.kind != LobbySlotType.OPEN } >= 2,
                    )
                } else {
                    Text("Waiting for the host to start…", style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
                }
                current.messages.takeLast(6).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft) }
            }
        }
    }

    picking?.let { onPick ->
        DeckPicker(decks, onPick = { onPick(it); picking = null }, onDismiss = { picking = null })
    }
}

@Composable
private fun SeatRow(seat: Online.Seat, host: Boolean, onAi: () -> Unit, onOpen: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().paper(Palette.PaperLight).ornateFrame(if (seat.mine) Palette.Terracotta else Palette.Line, inset = 3.dp, corners = false).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (seat.kind == LobbySlotType.AI) Icon(Icons.Default.SmartToy, null, tint = Palette.Slate)
        Column(Modifier.weight(1f)) {
            Text(seat.name + if (seat.mine) " (you)" else "", style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
            Text(
                when {
                    seat.kind == LobbySlotType.OPEN -> "Waiting for a friend to join"
                    seat.deck == null -> "Choosing a deck…"
                    else -> seat.deck
                },
                style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft,
            )
        }
        if (seat.kind != LobbySlotType.OPEN) {
            Text(if (seat.ready) "Ready ✓" else "Not ready", style = MaterialTheme.typography.labelMedium, color = if (seat.ready) Palette.Sage else Palette.InkSoft)
        }
        if (host && !seat.mine) {
            when (seat.kind) {
                LobbySlotType.OPEN -> OrnateButton("AI", onAi, icon = Icons.Default.SmartToy, accent = Palette.Slate, height = 40.dp)
                LobbySlotType.AI -> OrnateButton("Open", onOpen, accent = Palette.Slate, height = 40.dp)
                else -> Unit
            }
            if (seat.index > 1) IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "Remove seat", tint = Palette.InkSoft) }
        }
    }
}

@Composable
private fun DeckPicker(decks: List<Engine.DeckEntry>, onPick: (Engine.DeckEntry) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, decks) { decks.filter { it.name.contains(query, ignoreCase = true) } }
    OrnateDialog(onDismiss = onDismiss, maxWidth = 640.dp) {
        SectionLabel("Choose a deck")
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            query, { query = it }, singleLine = true, colors = fieldColors(),
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Palette.InkSoft) },
            placeholder = { Text("Search ${decks.size} decks") }, modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(Modifier.fillMaxWidth().height(460.dp).padding(top = 8.dp)) {
            items(filtered, key = { it.file.path }) { deck ->
                Text(
                    deck.name + if (deck.custom) "  ·  yours" else "",
                    Modifier.fillMaxWidth().clickable { onPick(deck) }.padding(vertical = 12.dp, horizontal = 4.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
