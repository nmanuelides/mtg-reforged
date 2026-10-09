package io.github.nmanuelides.mtgreforged.ui.match

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.github.nmanuelides.mtgreforged.match.ComposeGuiGame
import io.github.nmanuelides.mtgreforged.match.MatchUiState
import io.github.nmanuelides.mtgreforged.match.PlayerUi
import io.github.nmanuelides.mtgreforged.ui.components.CARD_ASPECT
import io.github.nmanuelides.mtgreforged.ui.components.CardMark
import io.github.nmanuelides.mtgreforged.ui.components.CardTile
import io.github.nmanuelides.mtgreforged.ui.theme.Palette

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

    val actions = remember(game) {
        BoardActions(
            onCard = game::tapCard,
            onPlayer = game::tapPlayer,
            onInspect = { detail = it },
            onZone = { title, cards -> zoneSheet = title to cards },
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF141623), Palette.Background, Color(0xFF14121C)))),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TopBar(state, onLog = { showLog = true }, onConcede = { confirmLeave = true })
            state.opponents.firstOrNull()?.let { opp ->
                PlayerStrip(opp, state, actions)
                Battlefield(opp, state, actions, mirrored = true, modifier = Modifier.weight(1f))
            }
            CenterLine(state, actions)
            state.me?.let { me ->
                Battlefield(me, state, actions, mirrored = false, modifier = Modifier.weight(1f))
                PlayerStrip(me, state, actions)
                Hand(me.hand, state, actions)
            }
            PromptBar(state, onOk = game::pressOk, onCancel = game::pressCancel)
        }

        AnimatedVisibility(state.gameOver != null, enter = fadeIn(), exit = fadeOut()) {
            state.gameOver?.let { over ->
                GameOverOverlay(
                    title = over.title,
                    won = over.youWon,
                    matchOver = over.matchOver,
                    onContinue = game::continueAfterGame,
                    onLeave = onLeave,
                )
            }
        }
    }

    detail?.let { CardDetailSheet(it) { detail = null } }
    zoneSheet?.let { (title, cards) ->
        ZoneSheet(title, cards, onInspect = { detail = it }, onDismiss = { zoneSheet = null })
    }
    if (showLog) LogSheet(state.log) { showLog = false }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("¿Abandonar la partida?") },
            text = { Text("La partida se va a dar por perdida.") },
            confirmButton = {
                Button(onClick = { confirmLeave = false; game.concedeGame(); onLeave() }) { Text("Abandonar") }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Seguir jugando") } },
        )
    }
}

private class BoardActions(
    val onCard: (Int) -> Unit,
    val onPlayer: (Int) -> Unit,
    val onInspect: (CardUi) -> Unit,
    val onZone: (String, List<CardUi>) -> Unit,
)

private fun MatchUiState.markFor(card: CardUi): CardMark = when {
    card.id in selectableIds -> CardMark.Selectable
    card.id in highlightedIds -> CardMark.Highlighted
    combat?.attackers?.containsKey(card.id) == true -> CardMark.Attacking
    combat?.blockers?.values?.any { card.id in it } == true -> CardMark.Blocking
    else -> CardMark.None
}

// ---- Top bar & phases --------------------------------------------------------------

@Composable
private fun TopBar(state: MatchUiState, onLog: () -> Unit, onConcede: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Turno ${state.turn}", style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
        Spacer(Modifier.width(10.dp))
        PhaseTrack(state.phase, Modifier.weight(1f))
        IconButton(onClick = onLog) { Icon(Icons.AutoMirrored.Filled.List, "Registro", tint = Palette.TextSecondary) }
        IconButton(onClick = onConcede) { Icon(Icons.Default.Flag, "Abandonar", tint = Palette.TextSecondary) }
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
                    .background(if (active) Palette.Ember else Palette.SurfaceHigh, RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) Color(0xFF1A0E04) else Palette.TextSecondary,
            )
        }
    }
}

// ---- Players -----------------------------------------------------------------------

@Composable
private fun PlayerStrip(player: PlayerUi, state: MatchUiState, actions: BoardActions) {
    val targetable = player.id in state.highlightedIds
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .background(Palette.Surface.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
            .border(
                width = if (targetable || player.isActive) 1.5.dp else 1.dp,
                color = when {
                    targetable -> Palette.Selectable
                    player.isActive -> Palette.Ember
                    else -> Palette.Outline
                },
                shape = RoundedCornerShape(14.dp),
            )
            .clickable { actions.onPlayer(player.id) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).background(if (player.isAi) Palette.Arcane else Palette.Ember, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(player.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Color(0xFF101218))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(player.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ZoneCount("Mano", player.handSize) {}
                ZoneCount("Mazo", player.librarySize) {}
                ZoneCount("Cem.", player.graveyard.size) { actions.onZone("Cementerio de ${player.name}", player.graveyard) }
                if (player.exile.isNotEmpty()) ZoneCount("Exilio", player.exile.size) { actions.onZone("Exilio de ${player.name}", player.exile) }
            }
        }
        ManaPool(player.mana)
        if (player.poison > 0) {
            Text("☠ ${player.poison}", color = Palette.Poison, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
        }
        Text(
            "${player.life}",
            style = MaterialTheme.typography.headlineSmall,
            color = if (player.life <= 5) Palette.Life else Palette.TextPrimary,
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
            Box(
                Modifier.size(20.dp).background(manaColor(color), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("$amount", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF101218)) }
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

// ---- Battlefield -------------------------------------------------------------------

@Composable
private fun Battlefield(player: PlayerUi, state: MatchUiState, actions: BoardActions, mirrored: Boolean, modifier: Modifier) {
    // Attachments (auras, equipment) are drawn tucked under their host.
    val attachments = player.battlefield.filter { it.attachedToId != null }.groupBy { it.attachedToId }
    val roots = player.battlefield.filter { it.attachedToId == null || player.battlefield.none { b -> b.id == it.attachedToId } }
    val lands = roots.filter { it.isLand && !it.isCreature }
    val others = roots - lands.toSet()
    val rows = if (mirrored) listOf(lands, others) else listOf(others, lands)

    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        val rowHeight = (maxHeight - 8.dp) / 2
        val cardWidth = (rowHeight * CARD_WIDTH_FACTOR).coerceIn(34.dp, 92.dp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rows.forEach { row ->
                LazyRow(
                    Modifier.fillMaxWidth().height(rowHeight),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(horizontal = cardWidth * 0.2f),
                ) {
                    items(row, key = { it.id }) { card ->
                        PermanentStack(card, attachments[card.id].orEmpty(), cardWidth, state, actions)
                    }
                }
            }
        }
    }
}

private const val CARD_WIDTH_FACTOR = 0.62f

@Composable
private fun PermanentStack(card: CardUi, attached: List<CardUi>, width: Dp, state: MatchUiState, actions: BoardActions) {
    // Reserve room for a tapped (rotated) card so neighbours don't overlap it.
    val slot = if (card.tapped) width / CARD_ASPECT else width
    Box(Modifier.width(slot), contentAlignment = Alignment.Center) {
        attached.forEachIndexed { i, a ->
            CardTile(
                a, width, Modifier.offset(y = -(10.dp * (attached.size - i))),
                mark = state.markFor(a),
                onClick = { actions.onCard(a.id) },
                onLongClick = { actions.onInspect(a) },
            )
        }
        CardTile(
            card, width,
            mark = state.markFor(card),
            onClick = { actions.onCard(card.id) },
            onLongClick = { actions.onInspect(card) },
        )
    }
}


// ---- Center line: stack ------------------------------------------------------------

@Composable
private fun CenterLine(state: MatchUiState, actions: BoardActions) {
    Box(Modifier.fillMaxWidth().heightIn(min = 6.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Outline))
        if (state.stack.isNotEmpty()) {
            LazyRow(
                Modifier.background(Palette.Surface, RoundedCornerShape(12.dp)).border(1.dp, Palette.Arcane, RoundedCornerShape(12.dp)).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item { Text("Pila", style = MaterialTheme.typography.labelSmall, color = Palette.Arcane) }
                items(state.stack, key = { it.id }) { item ->
                    item.source?.let { src ->
                        CardTile(src, 40.dp, showTapped = false, onLongClick = { actions.onInspect(src) }, onClick = { actions.onInspect(src) })
                    } ?: Text(item.text, fontSize = 11.sp, maxLines = 2, modifier = Modifier.width(120.dp))
                }
            }
        }
    }
}

// ---- Hand --------------------------------------------------------------------------

@Composable
private fun Hand(cards: List<CardUi>, state: MatchUiState, actions: BoardActions) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(118.dp).padding(horizontal = 6.dp)) {
        val cardWidth = 80.dp
        // Overlap cards when they don't fit, like a fanned hand.
        val spacing = if (cards.size <= 1) 0.dp else
            ((maxWidth - cardWidth) / (cards.size - 1)).coerceAtMost(cardWidth + 6.dp) - cardWidth
        LazyRow(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            items(cards, key = { it.id }) { card ->
                val mark = state.markFor(card)
                CardTile(
                    card, cardWidth,
                    Modifier.offset(y = if (mark == CardMark.Selectable) (-8).dp else 0.dp),
                    mark = mark,
                    showTapped = false,
                    onClick = { actions.onCard(card.id) },
                    onLongClick = { actions.onInspect(card) },
                )
            }
        }
    }
}

// ---- Prompt ------------------------------------------------------------------------

@Composable
private fun PromptBar(state: MatchUiState, onOk: () -> Unit, onCancel: () -> Unit) {
    val p = state.prompt
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            p.message.ifBlank { "Esperando…" },
            Modifier.weight(1f).heightIn(max = 64.dp).verticalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.width(8.dp))
        if (p.cancelLabel.isNotBlank()) {
            FilledTonalButton(onClick = onCancel, enabled = p.cancelEnabled) { Text(p.cancelLabel, maxLines = 1) }
            Spacer(Modifier.width(6.dp))
        }
        Button(onClick = onOk, enabled = p.okEnabled) { Text(p.okLabel.ifBlank { "OK" }, maxLines = 1) }
    }
}

// ---- Overlays & sheets -------------------------------------------------------------

@Composable
private fun GameOverOverlay(title: String, won: Boolean, matchOver: Boolean, onContinue: () -> Unit, onLeave: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                if (won) "¡Victoria!" else "Derrota",
                style = MaterialTheme.typography.headlineLarge,
                color = if (won) Palette.Ember else Palette.TextSecondary,
            )
            Text(title, color = Palette.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!matchOver) Button(onClick = onContinue) { Text("Siguiente partida") }
                FilledTonalButton(onClick = onLeave) { Text("Salir") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardDetailSheet(card: CardUi, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(8.dp))
        if (cards.isEmpty()) {
            Text("Vacío", color = Palette.TextSecondary, modifier = Modifier.padding(20.dp))
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cards.reversed(), key = { it.id }) { CardTile(it, 120.dp, showTapped = false, onClick = { onInspect(it) }) }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogSheet(log: List<String>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Text("Registro", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            log.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
            Spacer(Modifier.height(32.dp))
        }
    }
}
