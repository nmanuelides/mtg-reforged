package io.github.nmanuelides.mtgreforged.ui.match

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import forge.card.MagicColor
import forge.game.phase.PhaseType
import io.github.nmanuelides.mtgreforged.match.CardUi
import io.github.nmanuelides.mtgreforged.match.ComposeGuiGame
import io.github.nmanuelides.mtgreforged.match.MatchUiState
import io.github.nmanuelides.mtgreforged.match.PlayerUi
import io.github.nmanuelides.mtgreforged.match.StackItemUi
import io.github.nmanuelides.mtgreforged.ui.components.CardMark
import io.github.nmanuelides.mtgreforged.ui.components.CardTile
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import io.github.nmanuelides.mtgreforged.ui.components.ArtImage
import io.github.nmanuelides.mtgreforged.ui.components.LocalArt
import io.github.nmanuelides.mtgreforged.ui.components.LocalArtImage
import io.github.nmanuelides.mtgreforged.ui.components.OrnateButton
import io.github.nmanuelides.mtgreforged.ui.components.OrnateDialog
import io.github.nmanuelides.mtgreforged.ui.components.OrnateDivider
import io.github.nmanuelides.mtgreforged.ui.components.OrnateStyle
import io.github.nmanuelides.mtgreforged.ui.components.SectionLabel
import io.github.nmanuelides.mtgreforged.ui.components.SwipeToConfirm
import io.github.nmanuelides.mtgreforged.ui.components.ornateFrame
import io.github.nmanuelides.mtgreforged.ui.components.parchment
import io.github.nmanuelides.mtgreforged.ui.theme.Cinzel

/** Wide screens (tablets, landscape) get a side panel; phones keep a stacked layout. */
private val TABLET_MIN_WIDTH = 840.dp

@Composable
fun MatchScreen(game: ComposeGuiGame, onLeave: () -> Unit) {
    val state by game.state.collectAsStateWithLifecycle()
    var detail by remember { mutableStateOf<CardUi?>(null) }
    var zoneSheet by remember { mutableStateOf<Pair<String, List<CardUi>>?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(game) {
        game.feedback.collect { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
    BackHandler { confirmLeave = true }

    // AI turns in multiplayer can take a while; don't let the screen sleep mid-match.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val actions = remember(game) {
        BoardActions(
            onCard = game::tapCard,
            onPlayer = game::tapPlayer,
            onInspect = { detail = it },
            onZone = { title, cards -> zoneSheet = title to cards },
        )
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .parchment(),
    ) {
        val tablet = maxWidth >= TABLET_MIN_WIDTH
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            if (tablet) {
                TabletLayout(
                    state, actions, game, detail,
                    onCloseDetail = { detail = null },
                    onLog = { showLog = true },
                    onConcede = { confirmLeave = true },
                )
            } else {
                PhoneLayout(state, actions, game, onLog = { showLog = true }, onConcede = { confirmLeave = true })
            }
        }

        TurnBanner(state)
        CastSpotlight(state)

        AnimatedVisibility(state.gameOver != null, enter = fadeIn(), exit = fadeOut()) {
            state.gameOver?.let { over ->
                GameOverOverlay(over.title, over.youWon, over.matchOver, game::continueAfterGame, onLeave)
            }
        }

        // On tablets the inspected card lives in the side panel instead of a sheet.
        if (!tablet) detail?.let { CardDetailSheet(it) { detail = null } }
    }

    zoneSheet?.let { (title, cards) ->
        ZoneSheet(title, cards, onInspect = { detail = it }, onDismiss = { zoneSheet = null })
    }
    if (showLog) LogSheet(state.log) { showLog = false }
    if (confirmLeave) {
        OrnateDialog(onDismiss = { confirmLeave = false }) {
            SectionLabel("Concede")
            Spacer(Modifier.height(12.dp))
            Text("Leave the match? It will count as a loss.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OrnateButton("Keep playing", { confirmLeave = false }, Modifier.weight(1f), accent = Palette.Slate)
                OrnateButton("Concede", { confirmLeave = false; game.concedeGame(); onLeave() }, Modifier.weight(1f), style = OrnateStyle.Primary)
            }
        }
    }
}

// ---- Layouts -----------------------------------------------------------------------

@Composable
private fun TabletLayout(
    state: MatchUiState,
    actions: BoardActions,
    game: ComposeGuiGame,
    detail: CardUi?,
    onCloseDetail: () -> Unit,
    onLog: () -> Unit,
    onConcede: () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            val opponents = state.opponents
            // More opponents share the top band; give it more height so each still breathes.
            val topWeight = if (opponents.size > 1) 0.46f else 0.42f
            Row(Modifier.fillMaxWidth().weight(topWeight), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                opponents.forEach { opp ->
                    OpponentArea(opp, state, actions, compact = opponents.size > 1, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
            CenterDivider()
            state.me?.let { me ->
                Battlefield(
                    me, state, actions, mirrored = false, maxCardWidth = 110.dp,
                    modifier = Modifier.fillMaxWidth().weight(1f - topWeight).padding(horizontal = 8.dp),
                )
                PlayerStrip(me, state, actions, Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                HandDock(me.hand, state, actions, cardWidth = 118.dp, height = 176.dp)
            }
        }
        SidePanel(state, game, actions, detail, onCloseDetail, onLog, onConcede, Modifier.width(320.dp).fillMaxHeight())
    }
}

@Composable
private fun PhoneLayout(
    state: MatchUiState,
    actions: BoardActions,
    game: ComposeGuiGame,
    onLog: () -> Unit,
    onConcede: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopBar(state, onLog, onConcede)
        val opponents = state.opponents
        if (opponents.size == 1) {
            PlayerStrip(opponents[0], state, actions, Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            Battlefield(
                opponents[0], state, actions, mirrored = true, maxCardWidth = 72.dp,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 6.dp),
            )
        } else {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                opponents.forEach { OpponentArea(it, state, actions, compact = true, modifier = Modifier.weight(1f).fillMaxHeight()) }
            }
        }
        StackStrip(state.stack, actions)
        state.me?.let { me ->
            Battlefield(
                me, state, actions, mirrored = false, maxCardWidth = 72.dp,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 6.dp),
            )
            PlayerStrip(me, state, actions, Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            HandDock(me.hand, state, actions, cardWidth = 80.dp, height = 118.dp, maxScale = 2.0f)
        }
        PromptBar(state, onOk = game::pressOk, onCancel = game::pressCancel, onPlayer = game::tapPlayer)
    }
}

@Composable
private fun OpponentArea(player: PlayerUi, state: MatchUiState, actions: BoardActions, compact: Boolean, modifier: Modifier) {
    Column(modifier.alpha(if (player.hasLost) 0.4f else 1f).padding(horizontal = 6.dp, vertical = 4.dp)) {
        PlayerStrip(player, state, actions, compact = compact)
        Battlefield(
            player, state, actions, mirrored = true, maxCardWidth = if (compact) 76.dp else 96.dp,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp),
        )
    }
}

@Composable
private fun CenterDivider() {
    OrnateDivider(Modifier.padding(horizontal = 16.dp))
}

// ---- Side panel (tablet) -----------------------------------------------------------

@Composable
private fun SidePanel(
    state: MatchUiState,
    game: ComposeGuiGame,
    actions: BoardActions,
    detail: CardUi?,
    onCloseDetail: () -> Unit,
    onLog: () -> Unit,
    onConcede: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier
            .parchment(Palette.PaperDeep)
            .ornateFrame(inset = 5.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Turn ${state.turn}", style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
                Text(
                    state.players.firstOrNull { it.isActive }?.let { if (it.isLocal) "Your turn" else "${it.name}'s turn" }.orEmpty(),
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OrnateButton("Log", onLog, Modifier.weight(1f), style = OrnateStyle.Quiet, icon = Icons.AutoMirrored.Filled.List,
                height = 38.dp, textStyle = MaterialTheme.typography.labelSmall)
            OrnateButton("Leave", onConcede, Modifier.weight(1f), icon = Icons.AutoMirrored.Filled.ExitToApp,
                height = 38.dp, textStyle = MaterialTheme.typography.labelSmall)
        }
        PhaseTrack(state.phase, Modifier.fillMaxWidth())

        // Whatever needs attention most: the card being inspected, otherwise the stack.
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                detail != null -> CardDetailPanel(detail, onCloseDetail)
                state.stack.isNotEmpty() -> StackList(state.stack, actions)
                else -> Text(
                    "Long-press any card to see it here.",
                    Modifier.align(Alignment.Center).padding(16.dp),
                    color = Palette.TextSecondary, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        val p = state.prompt
        Column(
            Modifier
                .fillMaxWidth()
                .background(Palette.PaperLight)
                .ornateFrame(Palette.Line, inset = 3.dp)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                p.message.ifBlank { "Waiting…" },
                Modifier.fillMaxWidth().heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyLarge,
            )
            PlayerChoices(state, game::tapPlayer)
            if (p.passesPriority) {
                // Passing priority can skip whole phases; make it deliberate.
                SwipeToConfirm("Pass", game::pressOk, Modifier.fillMaxWidth(), enabled = p.okEnabled)
            } else {
                OrnateButton(
                    p.okLabel.ifBlank { "OK" }, game::pressOk, Modifier.fillMaxWidth(),
                    style = OrnateStyle.Primary, enabled = p.okEnabled, height = 54.dp,
                )
            }
            if (p.cancelLabel.isNotBlank()) {
                OrnateButton(
                    p.cancelLabel, game::pressCancel, Modifier.fillMaxWidth(),
                    accent = Palette.Slate, enabled = p.cancelEnabled, height = 46.dp,
                )
            }
        }
    }
}

@Composable
private fun CardDetailPanel(card: CardUi, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth()) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Default.Close, "Close", tint = Palette.TextSecondary)
            }
        }
        CardTile(card, 240.dp, showTapped = false, onClick = onClose)
        Spacer(Modifier.height(8.dp))
        Text(card.name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text("${card.typeLine}  ${card.manaCost}", color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(card.text, style = MaterialTheme.typography.bodySmall)
        if (card.counters.isNotEmpty()) {
            Text(card.counters.joinToString { (n, c) -> "$c × $n" }, color = Palette.Arcane, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StackList(stack: List<StackItemUi>, actions: BoardActions) {
    Column {
        SectionLabel("The stack", color = Palette.Slate)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(stack, key = { it.id }) { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Palette.PaperLight)
                        .ornateFrame(Palette.Slate.copy(alpha = 0.6f), inset = 3.dp, corners = false)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item.source?.let { src -> CardTile(src, 48.dp, showTapped = false, onClick = { actions.onInspect(src) }) }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(item.controllerName, style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
                        Text(item.text, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// ---- Shared pieces -----------------------------------------------------------------

@Composable
private fun TopBar(state: MatchUiState, onLog: () -> Unit, onConcede: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Turn ${state.turn}", style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
        Spacer(Modifier.width(10.dp))
        PhaseTrack(state.phase, Modifier.weight(1f))
        IconButton(onClick = onLog) { Icon(Icons.AutoMirrored.Filled.List, "Log", tint = Palette.TextSecondary) }
        OrnateButton("Leave", onConcede, icon = Icons.AutoMirrored.Filled.ExitToApp, height = 36.dp,
            textStyle = MaterialTheme.typography.labelSmall)
    }
}

private val PHASES = listOf(
    "UP" to setOf(PhaseType.UNTAP, PhaseType.UPKEEP, PhaseType.DRAW),
    "M1" to setOf(PhaseType.MAIN1),
    "CMB" to setOf(
        PhaseType.COMBAT_BEGIN, PhaseType.COMBAT_DECLARE_ATTACKERS, PhaseType.COMBAT_DECLARE_BLOCKERS,
        PhaseType.COMBAT_FIRST_STRIKE_DAMAGE, PhaseType.COMBAT_DAMAGE, PhaseType.COMBAT_END,
    ),
    "M2" to setOf(PhaseType.MAIN2),
    "END" to setOf(PhaseType.END_OF_TURN, PhaseType.CLEANUP),
)

@Composable
private fun PhaseTrack(phase: PhaseType?, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PHASES.forEach { (label, phases) ->
            val active = phase in phases
            Text(
                label,
                Modifier
                    .background(if (active) Palette.Terracotta else Palette.PaperLight)
                    .ornateFrame(if (active) Palette.PaperLight.copy(alpha = 0.7f) else Palette.Line, inset = 2.dp, corners = false)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                fontFamily = Cinzel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) Palette.PaperLight else Palette.InkSoft,
            )
        }
    }
}

@Composable
private fun PlayerStrip(player: PlayerUi, state: MatchUiState, actions: BoardActions, modifier: Modifier = Modifier, compact: Boolean = false) {
    val targetable = player.id in state.highlightedIds || player.id in state.choosablePlayerIds
    Row(
        modifier
            .fillMaxWidth()
            .background(if (player.isActive) Palette.Terracotta.copy(alpha = 0.16f) else Palette.PaperLight.copy(alpha = 0.8f))
            .ornateFrame(
                when {
                    targetable -> Palette.Selectable
                    player.isActive -> Palette.Terracotta
                    else -> Palette.Line
                },
                inset = 3.dp,
            )
            .clickable { actions.onPlayer(player.id) }
            .padding(horizontal = 10.dp, vertical = if (compact) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Portrait: the commander's art when there is one, otherwise an initial medallion.
        val portrait = player.command.firstOrNull()?.name
        val ring = if (player.isAi) Palette.Slate else Palette.Terracotta
        Box(
            Modifier.size(if (compact) 34.dp else 42.dp).background(ring, CircleShape).padding(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (portrait != null) {
                ArtImage(portrait, Modifier.fillMaxSize().clip(CircleShape), placeholder = ring, alignment = Alignment.Center)
            } else {
                Text(player.name.take(1).uppercase(), fontFamily = Cinzel, fontWeight = FontWeight.Bold, color = Palette.PaperLight)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (player.isActive) {
                    Text(
                        "TURN",
                        Modifier.background(Palette.Terracotta).padding(horizontal = 6.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall, color = Palette.PaperLight,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    player.name + if (player.hasLost) " (out)" else "",
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (player.isActive) Palette.Terracotta else Palette.Ink,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ZoneCount("Hand", player.handSize) {}
                ZoneCount("Library", player.librarySize) {}
                ZoneCount("Grave", player.graveyard.size) { actions.onZone("${player.name}'s graveyard", player.graveyard) }
                if (player.exile.isNotEmpty()) ZoneCount("Exile", player.exile.size) { actions.onZone("${player.name}'s exile", player.exile) }
            }
            if (player.commanderDamage.isNotEmpty()) {
                Text(
                    player.commanderDamage.joinToString("  ") { (name, dmg) -> "⚔ ${name.substringBefore(',')} $dmg" },
                    style = MaterialTheme.typography.labelSmall, color = Palette.Life, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        player.command.forEach { cmd ->
            CardTile(
                cmd, if (compact) 30.dp else 36.dp, Modifier.padding(horizontal = 2.dp),
                mark = state.markFor(cmd), showTapped = false,
                onClick = { actions.onCard(cmd.id) }, onLongClick = { actions.onInspect(cmd) },
            )
        }
        ManaPool(player.mana)
        if (player.poison > 0) {
            Text("☠ ${player.poison}", color = Palette.Poison, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
        }
        Text("♥", Modifier.padding(start = 8.dp), color = Palette.Terracotta, fontSize = if (compact) 16.sp else 20.sp)
        Text(
            "${player.life}",
            Modifier.padding(start = 4.dp),
            fontFamily = Cinzel,
            fontSize = if (compact) 22.sp else 28.sp,
            color = if (player.life <= 5) Palette.Terracotta else Palette.Ink,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ZoneCount(label: String, count: Int, onClick: () -> Unit) {
    Text(
        "$label $count",
        Modifier.clickable(onClick = onClick),
        style = MaterialTheme.typography.labelSmall,
        color = Palette.TextSecondary,
    )
}

@Composable
private fun ManaPool(mana: Map<Byte, Int>) {
    if (mana.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(horizontal = 6.dp)) {
        mana.forEach { (color, amount) ->
            Box(Modifier.size(20.dp).background(manaColor(color), CircleShape), contentAlignment = Alignment.Center) {
                Text("$amount", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF101218))
            }
        }
    }
}

private fun manaColor(color: Byte): Color = when (color) {
    MagicColor.WHITE -> Palette.White
    MagicColor.BLUE -> Palette.Blue
    MagicColor.BLACK -> Palette.Black
    MagicColor.RED -> Palette.Red
    MagicColor.GREEN -> Palette.Green
    else -> Palette.Colorless
}

@Composable
private fun StackStrip(stack: List<StackItemUi>, actions: BoardActions) {
    Box(Modifier.fillMaxWidth().heightIn(min = 6.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Outline))
        if (stack.isNotEmpty()) {
            LazyRow(
                Modifier.background(Palette.PaperLight).ornateFrame(Palette.Slate, inset = 3.dp).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item { Text("Stack", style = MaterialTheme.typography.labelSmall, color = Palette.Slate) }
                items(stack, key = { it.id }) { item ->
                    item.source?.let { src ->
                        CardTile(src, 40.dp, showTapped = false, onClick = { actions.onInspect(src) })
                    } ?: Text(item.text, fontSize = 11.sp, maxLines = 2, modifier = Modifier.width(120.dp))
                }
            }
        }
    }
}

@Composable
private fun PromptBar(state: MatchUiState, onOk: () -> Unit, onCancel: () -> Unit, onPlayer: (Int) -> Unit) {
    val p = state.prompt
    if (state.choosablePlayerIds.isNotEmpty()) {
        Box(Modifier.fillMaxWidth().background(Palette.PaperDeep).padding(horizontal = 14.dp, vertical = 6.dp)) {
            PlayerChoices(state, onPlayer)
        }
    }
    Row(
        Modifier.fillMaxWidth().background(Palette.PaperDeep).ornateFrame(Palette.Line, inset = 3.dp, corners = false).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            p.message.ifBlank { "Waiting…" },
            Modifier.weight(1f).heightIn(max = 64.dp).verticalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.width(8.dp))
        if (p.cancelLabel.isNotBlank()) {
            OrnateButton(p.cancelLabel, onCancel, enabled = p.cancelEnabled, accent = Palette.Slate, height = 42.dp)
            Spacer(Modifier.width(8.dp))
        }
        if (p.passesPriority) {
            SwipeToConfirm("Pass", onOk, Modifier.width(200.dp), enabled = p.okEnabled, height = 44.dp)
        } else {
            OrnateButton(p.okLabel.ifBlank { "OK" }, onOk, enabled = p.okEnabled, style = OrnateStyle.Primary, height = 42.dp)
        }
    }
}

// ---- Overlays & sheets -------------------------------------------------------------

@Composable
private fun GameOverOverlay(title: String, won: Boolean, matchOver: Boolean, onContinue: () -> Unit, onLeave: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x993A2E26)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(520.dp).parchment().ornateFrame(inset = 6.dp, topGem = true).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val art = remember(won) { if (won) LocalArt.white.random() else (LocalArt.black + LocalArt.blue).random() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Palette.PaperLight)
                    .ornateFrame(inset = 4.dp)
                    .padding(8.dp),
            ) { LocalArtImage(art, Modifier.fillMaxSize()) }
            SectionLabel(if (matchOver) "Match over" else "Game over")
            Text(
                if (won) "Victory" else "Defeat",
                style = MaterialTheme.typography.displayLarge,
                color = if (won) Palette.Terracotta else Palette.Slate,
            )
            OrnateDivider(width = 200.dp)
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Palette.InkSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OrnateButton("Leave", onLeave, Modifier.weight(1f), accent = Palette.Slate)
                if (!matchOver) OrnateButton("Next game", onContinue, Modifier.weight(1f), style = OrnateStyle.Primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardDetailSheet(card: CardUi, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Paper) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CardTile(card, 260.dp, showTapped = false)
            Spacer(Modifier.height(12.dp))
            Text(card.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text("${card.typeLine}  ${card.manaCost}", color = Palette.TextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(card.text, style = MaterialTheme.typography.bodyMedium)
            if (card.counters.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(card.counters.joinToString { (n, c) -> "$c × $n" }, color = Palette.Arcane)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZoneSheet(title: String, cards: List<CardUi>, onInspect: (CardUi) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Paper) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(8.dp))
        if (cards.isEmpty()) {
            Text("Empty", color = Palette.TextSecondary, modifier = Modifier.padding(20.dp))
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cards.reversed(), key = { it.id }) { CardTile(it, 140.dp, showTapped = false, onClick = { onInspect(it) }) }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogSheet(log: List<String>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Paper) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Text("Log", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            log.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ---- Turn & cast announcements ------------------------------------------------------

/** A plaque that announces each new turn for a moment, without blocking the board. */
@Composable
private fun TurnBanner(state: MatchUiState) {
    val active = state.players.firstOrNull { it.isActive }
    var shown by remember { mutableStateOf<PlayerUi?>(null) }
    LaunchedEffect(state.turn, active?.id) {
        if (active == null || state.turn == 0) return@LaunchedEffect
        shown = active
        kotlinx.coroutines.delay(1700)
        shown = null
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(shown != null, enter = fadeIn() + scaleIn(initialScale = 0.85f), exit = fadeOut()) {
            val p = shown ?: return@AnimatedVisibility
            Column(
                Modifier
                    .background(Palette.PaperLight)
                    .ornateFrame(if (p.isLocal) Palette.Terracotta else Palette.Slate, inset = 5.dp, topGem = true)
                    .padding(horizontal = 48.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SectionLabel("Turn ${state.turn}", color = if (p.isLocal) Palette.Terracotta else Palette.Slate)
                Text(
                    if (p.isLocal) "Your turn" else "${p.name}'s turn",
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (p.isLocal) Palette.Terracotta else Palette.Slate,
                )
            }
        }
    }
}

/** Shows each new spell or ability on the stack front and centre, with who played it. */
@Composable
private fun CastSpotlight(state: MatchUiState) {
    var spotlight by remember { mutableStateOf<StackItemUi?>(null) }
    val seen = remember { HashSet<Int>() }
    val top = state.stack.firstOrNull()
    // The source card can arrive a snapshot after the stack item itself, so key on both.
    LaunchedEffect(top?.id, top?.source != null) {
        if (top == null || top.source == null || !seen.add(top.id)) return@LaunchedEffect
        spotlight = top
        kotlinx.coroutines.delay(1800)
        spotlight = null
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(spotlight != null, enter = fadeIn() + scaleIn(initialScale = 0.7f), exit = fadeOut()) {
            val item = spotlight ?: return@AnimatedVisibility
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${item.controllerName} plays",
                    Modifier.background(Palette.PaperLight).ornateFrame(Palette.Slate, inset = 3.dp).padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleMedium, color = Palette.Slate,
                )
                Spacer(Modifier.height(8.dp))
                item.source?.let { CardTile(it, 230.dp, showTapped = false) }
            }
        }
    }
}

/** One plaque per player the engine is asking us to choose; friendlier than tapping portraits. */
@Composable
private fun PlayerChoices(state: MatchUiState, onPlayer: (Int) -> Unit) {
    val choices = state.players.filter { it.id in state.choosablePlayerIds }
    if (choices.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { player ->
                    OrnateButton(
                        if (player.isLocal) "You" else player.name,
                        { onPlayer(player.id) },
                        Modifier.weight(1f),
                        accent = if (player.isLocal) Palette.Terracotta else Palette.Slate,
                        height = 44.dp,
                        textStyle = MaterialTheme.typography.labelMedium,
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
