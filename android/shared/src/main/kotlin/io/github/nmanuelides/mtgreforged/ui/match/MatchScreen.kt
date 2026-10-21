package io.github.nmanuelides.mtgreforged.ui.match

import io.github.nmanuelides.mtgreforged.ui.components.ZoomableCard
import io.github.nmanuelides.mtgreforged.match.ZoneChoiceUi
import androidx.compose.foundation.horizontalScroll
import io.github.nmanuelides.mtgreforged.match.BoardChoiceUi
import androidx.compose.runtime.collectAsState
import io.github.nmanuelides.mtgreforged.ui.home.AvatarImage
import io.github.nmanuelides.mtgreforged.engine.Profiles
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.material.icons.automirrored.filled.Undo
import io.github.nmanuelides.mtgreforged.ui.components.paperShadow
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.runtime.snapshotFlow
import io.github.nmanuelides.mtgreforged.ui.components.ManaCost
import io.github.nmanuelides.mtgreforged.ui.components.paper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import io.github.nmanuelides.mtgreforged.engine.Engine
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
import io.github.nmanuelides.mtgreforged.platform.BackHandler
import io.github.nmanuelides.mtgreforged.platform.KeepScreenOn
import io.github.nmanuelides.mtgreforged.platform.LocalAppContext
import io.github.nmanuelides.mtgreforged.platform.showToast
import io.github.nmanuelides.mtgreforged.ui.components.horizontalDragScroll
import io.github.nmanuelides.mtgreforged.ui.components.mouseDrag

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
    KeepScreenOn()

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
        // Bigger windows (Windows) get bigger cards: half of how much the window grew past
        // 1000x700 (average of width and height), so the board still gains room. At most 1.8x.
        val growth = kotlin.math.sqrt((maxWidth / 1000.dp) * (maxHeight / 700.dp))
        val cardScale = if (!io.github.nmanuelides.mtgreforged.platform.IS_DESKTOP) 1f else ((growth - 1f) / 2f + 1f).coerceIn(1f, 1.8f)
        androidx.compose.runtime.CompositionLocalProvider(LocalCardScale provides cardScale) {
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
        }

        YourTurnGlow(state)
        TurnBanner(state)
        ImpactAnimations(state)
        state.boardChoice?.let { BoardChoiceDialog(state, it, game) }
        state.zoneChoice?.let { ZoneChoiceDialog(state, it, game) }
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
    if (showLog) LogSheet(state.logEntries, onRewind = { game.rewindTo(it); showLog = false }) { showLog = false }
    if (confirmLeave) {
        OrnateDialog(onDismiss = { confirmLeave = false }) {
            SectionLabel("Leave the match")
            Spacer(Modifier.height(12.dp))
            val canSave = remember { Engine.canSave() }
            Text(
                if (canSave) "Save it to continue later, or just end it here."
                else "End the match here?",
                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OrnateButton(
                    "Save & leave", { confirmLeave = false; if (Engine.saveMatch()) onLeave() },
                    Modifier.fillMaxWidth(), style = OrnateStyle.Primary, enabled = canSave,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OrnateButton("Keep playing", { confirmLeave = false }, Modifier.weight(1f), accent = Palette.Slate)
                    // Just stops the game: no concession, no loss, nothing saved.
                    OrnateButton("End match", { confirmLeave = false; onLeave() }, Modifier.weight(1f))
                }
            }
        }
    }
}

// ---- Layouts -----------------------------------------------------------------------

/** How much bigger than normal the match's cards are drawn (grows with large windows). */
private val LocalCardScale = androidx.compose.runtime.compositionLocalOf { 1f }

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
                    me, state, actions, mirrored = false, maxCardWidth = 110.dp * LocalCardScale.current,
                    modifier = Modifier.fillMaxWidth().weight(1f - topWeight).padding(horizontal = 8.dp),
                )
                PlayerStrip(me, state, actions, Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                HandDock(me.hand, state, actions, cardWidth = 118.dp * LocalCardScale.current, height = 176.dp * LocalCardScale.current)
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
        TopBar(state, onLog, onConcede, game::undo)
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
        PhaseTrack(state.phase, Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp))
        PromptBar(state, onOk = game::pressOk, onCancel = game::pressCancel, onPlayer = game::tapPlayer)
    }
}

@Composable
private fun OpponentArea(player: PlayerUi, state: MatchUiState, actions: BoardActions, compact: Boolean, modifier: Modifier) {
    Column(modifier.alpha(if (player.hasLost) 0.4f else 1f).padding(horizontal = 6.dp, vertical = 4.dp)) {
        PlayerStrip(player, state, actions, compact = compact)
        Battlefield(
            player, state, actions, mirrored = true, maxCardWidth = (if (compact) 76.dp else 96.dp) * LocalCardScale.current,
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

        // Phases sit right above the pass controls: where we are and what passing skips to.
        PhaseTrack(state.phase, Modifier.fillMaxWidth())

        val p = state.prompt
        Column(
            Modifier
                .fillMaxWidth()
                .paper(Palette.PaperLight)
                .ornateFrame(Palette.Line, inset = 3.dp)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Who has priority, with Undo in the box's top-right corner.
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    p.message.ifBlank { "Waiting…" },
                    Modifier.weight(1f).heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.width(8.dp))
                UndoButton(state, game::undo)
            }
            PlayerChoices(state, game::tapPlayer)
            if (p.passesPriority) {
                // Passing priority can skip whole phases; make it deliberate.
                SwipeToConfirm(passLabel(state), { NextStep.press(); game.pressOk() }, Modifier.fillMaxWidth(), enabled = p.okEnabled)
                if (state.stack.size >= 2) {
                    OrnateButton(
                        "Resolve all (${state.stack.size})", { NextStep.press(); game.resolveAllNow() }, Modifier.fillMaxWidth(),
                        accent = Palette.Terracotta, frameColor = Palette.Terracotta, height = 40.dp, enabled = p.okEnabled,
                    )
                }
            } else {
                OrnateButton(
                    p.okLabel.ifBlank { "OK" }, game::pressOk, Modifier.fillMaxWidth(),
                    style = OrnateStyle.Primary, enabled = p.okEnabled, height = 54.dp,
                )
            }
            if (p.cancelLabel.isNotBlank()) {
                if (p.passesPriority) {
                    // "End Turn" skips even more than a step: same deliberate swipe.
                    SwipeToConfirm(p.cancelLabel, { NextStep.press(); game.pressCancel() }, Modifier.fillMaxWidth(), enabled = p.cancelEnabled, height = 46.dp, fill = Palette.Slate)
                } else {
                    OrnateButton(
                        p.cancelLabel, game::pressCancel, Modifier.fillMaxWidth(),
                        accent = Palette.Slate, enabled = p.cancelEnabled, height = 46.dp,
                    )
                }
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(card.typeLine, color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Spacer(Modifier.width(8.dp))
            ManaCost(card.manaCost, 16.dp)
        }
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
                        .paper(Palette.PaperLight)
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
private fun TopBar(state: MatchUiState, onLog: () -> Unit, onConcede: () -> Unit, onUndo: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Turn ${state.turn}", style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
        Spacer(Modifier.width(10.dp))
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onUndo, enabled = state.canUndo) {
            Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = if (state.canUndo) Palette.Ochre else Palette.Line)
        }
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
                    .weight(1f)
                    .background(if (active) Palette.Terracotta else Palette.PaperLight)
                    .ornateFrame(if (active) Palette.PaperLight.copy(alpha = 0.7f) else Palette.Line, inset = 2.dp, corners = false)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                textAlign = TextAlign.Center,
                fontFamily = Cinzel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) Palette.OnAccent else Palette.InkSoft,
            )
        }
    }
}

@Composable
private fun PlayerStrip(player: PlayerUi, state: MatchUiState, actions: BoardActions, modifier: Modifier = Modifier, compact: Boolean = false) {
    val targetable = player.id in state.highlightedIds || player.id in state.choosablePlayerIds
    val underAttack = player.id == state.attackTargetId
    Row(
        modifier
            .fillMaxWidth()
            .background(if (player.isActive) Palette.Terracotta.copy(alpha = 0.16f) else Palette.PaperLight.copy(alpha = 0.8f))
            .ornateFrame(
                when {
                    underAttack -> Palette.Attacking
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
            Modifier.size(if (compact) 34.dp else 42.dp).trackPosition("p${player.id}").background(ring, CircleShape).padding(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            PlayerPortrait(player.seat, player.name, portrait, ring, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // 1: who (always readable) and, beside it, the latest play.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (player.isActive) {
                    Text(
                        "TURN",
                        Modifier.background(Palette.Terracotta).padding(horizontal = 6.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall, color = Palette.OnAccent, maxLines = 1,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    player.name + if (player.hasLost) " (out)" else "",
                    Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                    color = if (player.isActive) Palette.Terracotta else Palette.Ink,
                )
                // Your bar: the latest play of the game, by anyone. Opponents: their own latest play.
                (if (player.isLocal) state.lastPlay else state.lastActions[player.name])?.let { action ->
                    Spacer(Modifier.width(10.dp))
                    Text(
                        action,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                        color = Palette.InkSoft, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // 2: zones as compact chips; they slide sideways rather than squeeze when space runs out.
            val labels = player.isLocal && !compact
            Row(Modifier.horizontalDragScroll(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ZoneButton(Icons.Default.PanTool, "Hand", player.handSize, !labels) {}
                ZoneButton(Icons.Default.Layers, "Library", player.librarySize, !labels) {}
                ZoneButton(Icons.Default.Delete, "Grave", player.graveyard.size, !labels) {
                    actions.onZone("${player.name}'s graveyard", player.graveyard)
                }
                if (player.exile.isNotEmpty()) {
                    ZoneButton(Icons.Default.Block, "Exile", player.exile.size, !labels) { actions.onZone("${player.name}'s exile", player.exile) }
                }
            }
            // 3: commander damage taken, when there is any.
            if (player.commanderDamage.isNotEmpty()) {
                Text(
                    player.commanderDamage.joinToString("  ") { (name, dmg) -> "⚔ ${name.substringBefore(',')} $dmg" },
                    style = MaterialTheme.typography.labelSmall, color = Palette.Life, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
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
        LifeCounter(player.life, compact)
    }
}

@Composable
private fun ZoneButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, count: Int, compact: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .paperShadow(offsetY = 1.dp, blur = 3.dp, cover = Palette.Paper, darkness = 80)
            .paper(Palette.Paper)
            .ornateFrame(Palette.Line, inset = 2.dp, corners = false)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 6.dp else 9.dp, vertical = if (compact) 2.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(if (compact) 13.dp else 16.dp), tint = Palette.InkSoft)
        Spacer(Modifier.width(4.dp))
        Text("$count", fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = if (compact) 12.sp else 14.sp, color = Palette.Ink, maxLines = 1, softWrap = false)
        if (!compact) {
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary, maxLines = 1, softWrap = false)
        }
    }
}

/** Floating mana: each colour's symbol on its disc, and how much of it underneath ("x3"). */
@Composable
private fun ManaPool(mana: Map<Byte, Int>) {
    if (mana.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 6.dp)) {
        mana.forEach { (color, amount) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(26.dp).background(manaColor(color), CircleShape).border(1.dp, Palette.Ink.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    // Dark glyph on the light discs (white, colourless), light glyph on the others.
                    val light = color == MagicColor.WHITE || color == MagicColor.COLORLESS
                    androidx.compose.foundation.Image(
                        io.github.nmanuelides.mtgreforged.platform.painterRes(io.github.nmanuelides.mtgreforged.ui.components.ManaPip.of(color).glyph),
                        contentDescription = io.github.nmanuelides.mtgreforged.ui.components.ManaPip.of(color).label,
                        modifier = Modifier.size(20.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (light) Color(0xFF3B2A1E) else Palette.OnAccent),
                    )
                }
                Text("x$amount", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
            val stackRow = androidx.compose.foundation.lazy.rememberLazyListState()
            LazyRow(
                Modifier.mouseDrag(stackRow).paper(Palette.PaperLight).ornateFrame(Palette.Slate, inset = 3.dp).padding(8.dp),
                state = stackRow,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item { Text("Stack", style = MaterialTheme.typography.labelSmall, color = Palette.Slate) }
                items(stack, key = { it.id }) { item ->
                    item.source?.let { src ->
                        ZoomableCard(src, 40.dp, onTap = { actions.onInspect(src) })
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
        Box(Modifier.fillMaxWidth().paper(Palette.PaperDeep).padding(horizontal = 14.dp, vertical = 6.dp)) {
            PlayerChoices(state, onPlayer)
        }
    }
    Row(
        Modifier.fillMaxWidth().paper(Palette.PaperDeep).ornateFrame(Palette.Line, inset = 3.dp, corners = false).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            p.message.ifBlank { "Waiting…" },
            Modifier.weight(1f).heightIn(max = 64.dp).verticalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.width(8.dp))
        if (p.cancelLabel.isNotBlank()) {
            if (p.passesPriority) {
                SwipeToConfirm(p.cancelLabel, { NextStep.press(); onCancel() }, Modifier.width(170.dp), enabled = p.cancelEnabled, height = 44.dp, fill = Palette.Slate)
            } else {
                OrnateButton(p.cancelLabel, onCancel, enabled = p.cancelEnabled, accent = Palette.Slate, height = 42.dp)
            }
            Spacer(Modifier.width(8.dp))
        }
        if (p.passesPriority) {
            SwipeToConfirm(passLabel(state), { NextStep.press(); onOk() }, Modifier.width(200.dp), enabled = p.okEnabled, height = 44.dp)
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
                    .paper(Palette.PaperLight)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.typeLine, color = Palette.TextSecondary, textAlign = TextAlign.Center)
                Spacer(Modifier.width(8.dp))
                ManaCost(card.manaCost, 18.dp)
            }
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
            val zoneRow = androidx.compose.foundation.lazy.rememberLazyListState()
            LazyRow(Modifier.mouseDrag(zoneRow), state = zoneRow, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cards.reversed(), key = { it.id }) { CardTile(it, 140.dp, showTapped = false, onClick = { onInspect(it) }) }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}


// ---- Turn & cast announcements ------------------------------------------------------

/** A plaque that announces each new turn for a moment, without blocking the board. */
@Composable
private fun TurnBanner(state: MatchUiState) {
    val active = state.players.firstOrNull { it.isActive }
    var shown by remember { mutableStateOf<PlayerUi?>(null) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.turn, active?.id) {
        if (active == null || state.turn == 0) return@LaunchedEffect
        shown = active
        if (active.isLocal) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        kotlinx.coroutines.delay(if (active.isLocal) 2400 else 1700)
        shown = null
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // The player's own turn arrives big, from in front of the glass; others' turns stay modest.
        AnimatedVisibility(
            shown != null,
            enter = fadeIn(tween(260)) + scaleIn(tween(420, easing = FastOutSlowInEasing), initialScale = if (shown?.isLocal == true) 1.6f else 0.85f),
            exit = fadeOut(tween(400)),
        ) {
            val p = shown ?: return@AnimatedVisibility
            Column(
                Modifier
                    .paper(Palette.PaperLight)
                    .ornateFrame(if (p.isLocal) Palette.Terracotta else Palette.Slate, inset = 5.dp, topGem = true)
                    .padding(horizontal = 48.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SectionLabel("Turn ${state.turn}", color = if (p.isLocal) Palette.Terracotta else Palette.Slate)
                Text(
                    if (p.isLocal) "Your turn" else "${p.name}'s turn",
                    style = if (p.isLocal) MaterialTheme.typography.displayLarge else MaterialTheme.typography.headlineLarge,
                    color = if (p.isLocal) Palette.Terracotta else Palette.Slate,
                )
            }
        }
    }
}

/**
 * While it's the local player's turn the screen edges glow in terracotta, breathing slowly;
 * the turn starts with a stronger flash, so it's always obvious whose turn it is.
 */
@Composable
private fun YourTurnGlow(state: MatchUiState) {
    val mine = state.players.firstOrNull { it.isActive }?.isLocal == true
    val flash = remember { Animatable(0f) }
    LaunchedEffect(state.turn, mine) {
        if (mine && state.turn > 0) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(1800, easing = FastOutSlowInEasing))
        }
    }
    val breathe by rememberInfiniteTransition(label = "turn").animateFloat(
        0.18f, 0.42f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "breathe",
    )
    val strength = maxOf(if (mine) breathe else 0f, flash.value)
    if (strength <= 0.01f) return
    Canvas(Modifier.fillMaxSize()) {
        val band = 26.dp.toPx() + 70.dp.toPx() * flash.value
        val glow = Palette.Terracotta.copy(alpha = strength.coerceAtMost(0.85f))
        val clear = Color.Transparent
        drawRect(Brush.verticalGradient(listOf(glow, clear), 0f, band), size = Size(size.width, band))
        drawRect(Brush.verticalGradient(listOf(clear, glow), size.height - band, size.height), topLeft = Offset(0f, size.height - band), size = Size(size.width, band))
        drawRect(Brush.horizontalGradient(listOf(glow, clear), 0f, band), size = Size(band, size.height))
        drawRect(Brush.horizontalGradient(listOf(clear, glow), size.width - band, size.width), topLeft = Offset(size.width - band, 0f), size = Size(band, size.height))
    }
}

/** Picking permanents (whose they are, big enough to see), with what was picked before. */
@Composable
private fun BoardChoiceDialog(state: MatchUiState, choice: BoardChoiceUi, game: ComposeGuiGame) {
    val p = state.prompt
    OrnateDialog(onDismiss = {}, dismissible = false, maxWidth = 760.dp) {
        Text(p.message, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, textAlign = TextAlign.Center)
        if (choice.history.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Chosen so far: " + choice.history.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = Palette.Sage, textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            choice.groups.forEach { (owner, cards) ->
                SectionLabel(owner + " cards", color = Palette.InkSoft)
                Row(Modifier.horizontalDragScroll(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cards.forEach { card ->
                        CardTile(
                            card, 110.dp, showTapped = false,
                            mark = if (card.id in choice.selectedIds) CardMark.Highlighted else CardMark.Selectable,
                            onClick = { game.tapCard(card.id) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (p.cancelLabel.isNotBlank()) {
                OrnateButton(p.cancelLabel, game::pressCancel, Modifier.weight(1f), accent = Palette.Slate, enabled = p.cancelEnabled)
            }
            OrnateButton(p.okLabel.ifBlank { "OK" }, game::pressOk, Modifier.weight(1f), style = OrnateStyle.Primary, enabled = p.okEnabled)
        }
    }
}

/** Picking cards from a graveyard, exile or library: the cards big, swiping between them. */
@Composable
private fun ZoneChoiceDialog(state: MatchUiState, choice: ZoneChoiceUi, game: ComposeGuiGame) {
    val p = state.prompt
    // "Choose on the battlefield" hides the carousel until this prompt changes.
    var hiddenFor by remember { mutableStateOf<String?>(null) }
    if (hiddenFor == p.message) return
    val pager = androidx.compose.foundation.pager.rememberPagerState { choice.cards.size }
    OrnateDialog(onDismiss = {}, dismissible = false, maxWidth = 760.dp) {
        Text(p.message, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            "Swipe to look through them, tap one to choose it." + if (choice.cards.size > 1) "  ·  ${pager.currentPage + 1} / ${choice.cards.size}" else "",
            style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = minOf(maxWidth * 0.62f, 240.dp)
            androidx.compose.foundation.pager.HorizontalPager(
                pager,
                Modifier.mouseDrag(pager),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = (maxWidth - cardWidth) / 2),
                pageSpacing = 16.dp,
            ) { page ->
                val (card, where) = choice.cards[page]
                val picked = card.id in choice.selectedIds
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CardTile(
                        card, cardWidth, showTapped = false,
                        mark = if (picked) CardMark.Highlighted else CardMark.Selectable,
                        onClick = { game.tapCard(card.id) },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (picked) "$where  ·  chosen ✓" else where,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (picked) Palette.Sage else Palette.InkSoft,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (p.cancelLabel.isNotBlank()) {
                OrnateButton(p.cancelLabel, game::pressCancel, Modifier.weight(1f), accent = Palette.Slate, enabled = p.cancelEnabled)
            }
            if (choice.alsoOnBoard) {
                OrnateButton("On the battlefield", { hiddenFor = p.message }, Modifier.weight(1f), accent = Palette.Slate)
            }
            if (p.okEnabled) {
                OrnateButton(p.okLabel.ifBlank { "OK" }, game::pressOk, Modifier.weight(1f), style = OrnateStyle.Primary)
            }
        }
    }
}

/** The classic undo arrow, alone: a round paper button. */
@Composable
private fun UndoButton(state: MatchUiState, onUndo: () -> Unit) {
    val context = LocalAppContext.current
    val explain = !state.canUndo && !state.undoPending && state.undoError != null
    Box(
        Modifier
            .size(42.dp)
            .alpha(if (state.canUndo || state.undoPending) 1f else 0.4f)
            .paperShadow(offsetY = 2.dp, blur = 5.dp, cover = Palette.PaperLight, darkness = 100)
            .paper(Palette.PaperLight)
            .ornateFrame(Palette.Ochre, inset = 2.dp, corners = false)
            // While an undo waits for the opponent, a tap calls it off.
            .clickable(enabled = state.canUndo || state.undoPending || explain) {
                // Disabled because the game couldn't be copied: say why instead of doing nothing.
                if (explain) context.showToast("Undo isn't available: ${state.undoError}")
                else onUndo()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (state.undoPending) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), color = Palette.Ochre, strokeWidth = 2.dp)
        } else {
            Icon(Icons.AutoMirrored.Filled.Undo, "Undo last play", tint = Palette.Ochre)
        }
    }
}

/** Passing with something on the stack resolves it; with an empty stack it moves to the next step. */
private fun passLabel(state: MatchUiState) = if (state.stack.isNotEmpty()) "Resolve" else "Next Step"

/** Counts "Next Step" presses so the cast spotlight can get out of the way. */
private object NextStep {
    val presses = androidx.compose.runtime.mutableIntStateOf(0)
    fun press() { presses.intValue++ }
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
    }
    // The timer belongs to the spotlight itself, so a stack change mid-way can't strand it.
    LaunchedEffect(spotlight?.id) {
        if (spotlight == null) return@LaunchedEffect
        kotlinx.coroutines.delay(3500)
        spotlight = null
    }
    // Moving to the next step dismisses it straight away.
    LaunchedEffect(Unit) {
        var last = NextStep.presses.intValue
        snapshotFlow { NextStep.presses.intValue }.collect { if (it != last) { last = it; spotlight = null } }
    }
    // Purely informative: it never blocks the game. Only the card itself takes touches,
    // so a swipe up on it dismisses early; everything around it stays playable.
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(spotlight) { drag.snapTo(0f) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            spotlight != null,
            enter = fadeIn() + scaleIn(initialScale = 0.7f),
            exit = fadeOut() + slideOutVertically { -it / 2 },
        ) {
            val item = spotlight ?: return@AnimatedVisibility
            Column(
                Modifier
                    .graphicsLayer { translationY = drag.value; alpha = 1f + drag.value / 600f }
                    .pointerInput(item.id) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dy ->
                                change.consume()
                                scope.launch { drag.snapTo((drag.value + dy).coerceAtMost(0f)) }
                            },
                            onDragEnd = {
                                if (drag.value < -80.dp.toPx()) spotlight = null
                                else scope.launch { drag.animateTo(0f) }
                            },
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "${item.controllerName} plays",
                    Modifier.paper(Palette.PaperLight).ornateFrame(Palette.Slate, inset = 3.dp).padding(horizontal = 16.dp, vertical = 6.dp),
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
    val attacking = state.attackTargetId != null
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (attacking) {
            // Declaring attackers: make the current target unmistakable.
            Text(
                "Tap creatures to attack the marked player. Tap another player to switch.",
                style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft,
            )
        }
        choices.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { player ->
                    val isTarget = player.id == state.attackTargetId
                    OrnateButton(
                        (if (isTarget) "⚔ " else "") + (if (player.isLocal) "You" else player.name),
                        { onPlayer(player.id) },
                        Modifier.weight(1f),
                        style = if (isTarget) OrnateStyle.Primary else OrnateStyle.Secondary,
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

/**
 * Life total with feedback: on a change the heart shakes and flashes and the difference
 * floats up from the number ("-3" in red, "+2" in green).
 */
@Composable
private fun LifeCounter(life: Int, compact: Boolean) {
    var previous by remember { mutableStateOf(life) }
    var delta by remember { mutableStateOf(0) }
    val rise = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(life) {
        if (life == previous) return@LaunchedEffect
        delta = life - previous
        previous = life
        launch {
            if (delta < 0) {
                repeat(3) {
                    shake.animateTo(6f, tween(45))
                    shake.animateTo(-6f, tween(45))
                }
                shake.animateTo(0f, tween(45))
            }
        }
        rise.snapTo(0f)
        rise.animateTo(1f, tween(1600, easing = LinearOutSlowInEasing))
    }
    val hurt = delta < 0 && rise.value < 1f
    Box(contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .graphicsLayer { translationX = shake.value.dp.toPx() }
                .background(if (hurt) Palette.Life.copy(alpha = 0.25f * (1f - rise.value)) else Color.Transparent)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("♥", Modifier.padding(start = 4.dp), color = Palette.Terracotta, fontSize = if (compact) 16.sp else 20.sp)
            Text(
                "$life",
                Modifier.padding(start = 4.dp),
                fontFamily = Cinzel,
                fontSize = if (compact) 22.sp else 28.sp,
                color = if (life <= 5) Palette.Terracotta else Palette.Ink,
                fontWeight = FontWeight.Bold,
            )
        }
        if (rise.value < 1f && delta != 0) {
            Text(
                if (delta > 0) "+$delta" else "$delta",
                Modifier.graphicsLayer {
                    translationY = -rise.value * 46.dp.toPx()
                    alpha = 1f - rise.value
                    scaleX = 1.4f - 0.4f * rise.value
                    scaleY = 1.4f - 0.4f * rise.value
                },
                fontFamily = Cinzel,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 24.sp else 30.sp,
                color = if (delta < 0) Palette.Life else Palette.Sage,
            )
        }
    }
}

/** A player's portrait: their avatar picture, otherwise their commander's art, otherwise their initial. */
@Composable
internal fun PlayerPortrait(seat: Int, name: String, commander: String?, ring: Color, modifier: Modifier = Modifier) {
    val avatarVersion by Profiles.version.collectAsState()
    val avatar = remember(seat, avatarVersion) { Profiles.avatar(seat) }
    Box(modifier.clip(CircleShape), contentAlignment = Alignment.Center) {
        if (avatar != null) {
            AvatarImage(seat, name, Modifier.fillMaxSize())
        } else if (commander != null) {
            ArtImage(commander, Modifier.fillMaxSize().clip(CircleShape), placeholder = ring, alignment = Alignment.Center)
        } else {
            Box(Modifier.fillMaxSize().background(ring, CircleShape), contentAlignment = Alignment.Center) {
                Text(name.take(1).uppercase(), fontFamily = Cinzel, fontWeight = FontWeight.Bold, color = Palette.OnAccent)
            }
        }
    }
}
