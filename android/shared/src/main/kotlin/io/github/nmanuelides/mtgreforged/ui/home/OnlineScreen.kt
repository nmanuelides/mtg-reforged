package io.github.nmanuelides.mtgreforged.ui.home

import io.github.nmanuelides.mtgreforged.ui.components.ZoomableCard
import io.github.nmanuelides.mtgreforged.platform.screenHeightDp
import io.github.nmanuelides.mtgreforged.ui.decks.fromPaper
import androidx.compose.foundation.layout.width
import io.github.nmanuelides.mtgreforged.ui.components.ManaSymbol
import io.github.nmanuelides.mtgreforged.ui.components.ManaPip
import io.github.nmanuelides.mtgreforged.ui.components.CardTile
import io.github.nmanuelides.mtgreforged.match.CardUi
import io.github.nmanuelides.mtgreforged.engine.DeckSummary
import io.github.nmanuelides.mtgreforged.engine.CardPrices
import io.github.nmanuelides.mtgreforged.engine.CardPrice
import forge.StaticData
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
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
import io.github.nmanuelides.mtgreforged.engine.Relay
import io.github.nmanuelides.mtgreforged.platform.LocalAppContext
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

/** Play with friends, online by room code or on the same Wi-Fi: host or join, pick decks, ready up, start. */
@Composable
fun OnlineScreen(format: Engine.Format, decks: List<Engine.DeckEntry>, onBack: () -> Unit) {
    val context = LocalAppContext.current
    remember { Relay.init(context) }
    val activated by Relay.activated.collectAsState()
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
                    if (Relay.configured) {
                        "One player hosts and gets a room code; friends anywhere join by typing it. On the same Wi-Fi, the host's address works too."
                    } else "Everyone must be on the same Wi-Fi. One player hosts; the others join by typing the host's address.",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft,
                )
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Your name") }, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                if (Relay.configured && !activated) ActivateOnline(name)
                OrnateButton(
                    if (busy) "Opening…" else "Host a game", {
                        busy = true
                        scope.launch { withContext(Dispatchers.IO) { runCatching { Online.host(format, name) } }; busy = false }
                    },
                    Modifier.fillMaxWidth(), icon = Icons.Default.Wifi, style = OrnateStyle.Primary, enabled = !busy, height = 56.dp,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        address, { address = it }, singleLine = true,
                        label = { Text(if (Relay.configured) "Room code or host address" else "Host address (e.g. 192.168.1.20)") },
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
                if (current.role == Online.Role.Host && (current.room != null || current.addresses.isNotEmpty())) {
                    Column(Modifier.fillMaxWidth().paper(Palette.PaperLight).ornateFrame(Palette.Sage, inset = 3.dp, corners = false).padding(14.dp)) {
                        current.room?.let { room ->
                            Text("Room code · friends anywhere type this to join", style = MaterialTheme.typography.labelSmall, color = Palette.InkSoft)
                            Text(room, style = MaterialTheme.typography.headlineMedium, color = Palette.Terracotta)
                            if (current.addresses.isNotEmpty()) Spacer(Modifier.height(8.dp))
                        }
                        if (current.addresses.isNotEmpty()) {
                            Text(if (current.room != null) "Or on the same Wi-Fi" else "Friends join with", style = MaterialTheme.typography.labelSmall, color = Palette.InkSoft)
                            current.addresses.forEach { Text(it, style = MaterialTheme.typography.headlineSmall, color = Palette.Sage) }
                        }
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
                // Back to host / join: the host's server (and its port and room) closes for everyone.
                OrnateButton(
                    if (current.role == Online.Role.Host) "Close this game" else "Leave this game",
                    { scope.launch(Dispatchers.IO) { Online.leave() } },
                    Modifier.fillMaxWidth(), icon = Icons.Default.Close, accent = Palette.Terracotta, frameColor = Palette.Terracotta,
                )
            }
        }
    }

    picking?.let { onPick ->
        DeckPicker(decks, onPick = { onPick(it); picking = null }, onDismiss = { picking = null })
    }
}

/** Hosting online needs this device activated with an access code; joining doesn't. */
@Composable
private fun ActivateOnline(name: String) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxWidth().paper(Palette.PaperLight).ornateFrame(Palette.Line, inset = 3.dp, corners = false).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Hosting online games is by invitation for now", style = MaterialTheme.typography.titleSmall, color = Palette.Ink)
        Text(
            "Enter your access code to host games over the internet. You can still join a friend's game with their room code, or host on the same Wi-Fi.",
            style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(code, { code = it.uppercase() }, singleLine = true, label = { Text("Access code") }, colors = fieldColors(), modifier = Modifier.weight(1f))
            OrnateButton(
                if (busy) "Checking…" else "Activate", {
                    busy = true
                    problem = null
                    scope.launch { problem = withContext(Dispatchers.IO) { Relay.activate(code, name) }; busy = false }
                },
                enabled = !busy && code.isNotBlank(), accent = Palette.Sage, height = 56.dp,
            )
        }
        problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Terracotta) }
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
    OrnateDialog(onDismiss = onDismiss, maxWidth = 900.dp) {
        SectionLabel("Choose a deck")
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            query, { query = it }, singleLine = true, colors = fieldColors(),
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Palette.InkSoft) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search", tint = Palette.InkSoft) } },
            placeholder = { Text("Search ${decks.size} decks") }, modifier = Modifier.fillMaxWidth(),
        )
        // As tall as the window allows, leaving room for the title and search.
        val listHeight = (screenHeightDp() - 230f).coerceAtLeast(320f).dp
        LazyColumn(Modifier.fillMaxWidth().height(listHeight).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { it.file.path }) { deck -> DeckChoice(deck) { onPick(deck) } }
        }
    }
}

/** A deck to pick: its commander (or signature card) on the left; name, colours and value beside it. */
@Composable
private fun DeckChoice(deck: Engine.DeckEntry, onClick: () -> Unit) {
    val prices by CardPrices.version.collectAsState()
    val summary by produceState<DeckSummary?>(null, deck, prices) {
        value = withContext(Dispatchers.IO) { runCatching { DeckSummary.cached(deck) }.getOrNull() }
    }
    Row(
        Modifier.fillMaxWidth().paper(Palette.PaperLight).ornateFrame(Palette.Line, inset = 3.dp, corners = false)
            .clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val card = remember(summary?.artCard) { summary?.artCard?.let { runCatching { StaticData.instance().commonCards.getCard(it) }.getOrNull() } }
        // Press and hold to read it full size; a tap falls through to the row and picks the deck.
        if (card != null) ZoomableCard(remember(card) { CardUi.fromPaper(card) }, 140.dp)
        else Box(Modifier.width(140.dp).aspectRatio(63f / 88f).background(Palette.PaperDeep))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                summary?.title ?: deck.name, style = MaterialTheme.typography.headlineSmall, color = Palette.Ink,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(summary?.commander, summary?.tag?.ifBlank { null }, "yours".takeIf { deck.custom }).joinToString("  ·  ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyLarge, color = Palette.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            summary?.let { s ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (s.colors.isEmpty()) ManaSymbol(ManaPip.Colorless, 28.dp)
                    s.colors.forEach { ManaSymbol(ManaPip.of(it), 28.dp) }
                }
            }
        }
        summary?.value?.takeIf { it > 0 }?.let { value ->
            Text(CardPrice.usd(value), style = MaterialTheme.typography.headlineSmall, color = Palette.Slate)
        }
    }
}
