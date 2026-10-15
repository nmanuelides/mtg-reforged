package io.github.nmanuelides.mtgreforged.ui.match

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.key
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import forge.game.phase.PhaseType
import io.github.nmanuelides.mtgreforged.match.CardUi
import io.github.nmanuelides.mtgreforged.match.MatchUiState
import io.github.nmanuelides.mtgreforged.ui.components.CardTile
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Where every card and player portrait currently sits on screen, so effects can fly between
 * them. Keys: "c<cardId>" for cards, "p<playerId>" for player portraits.
 */
object BoardPositions {
    val rects = mutableStateMapOf<String, Rect>()

    /** When each key was last hit (uptime ms); tracked elements shake right after. */
    val hits = mutableStateMapOf<String, Long>()

    fun hit(key: String) { hits[key] = android.os.SystemClock.uptimeMillis() }
}

/** Records this element's position under [key] and shakes it when it gets hit. */
fun Modifier.trackPosition(key: String): Modifier = composed {
    val shake = remember { Animatable(0f) }
    val hitAt = BoardPositions.hits[key]
    LaunchedEffect(hitAt) {
        if (hitAt == null) return@LaunchedEffect
        shake.snapTo(1f)
        shake.animateTo(0f, tween(420, easing = LinearEasing))
    }
    onGloballyPositioned { BoardPositions.rects[key] = it.boundsInRoot() }
        .graphicsLayer {
            val s = shake.value
            translationX = sin(s * 40f) * 9.dp.toPx() * s
            val scale = 1f + 0.06f * s
            scaleX = scale
            scaleY = scale
        }
}

private class Flight(val card: CardUi?, val from: Offset, val to: Rect, val targetKey: String, val returns: Boolean)

/**
 * Spells that target permanents or players fly to them and strike; in combat damage each
 * attacker flies at its blocker, or at the player / planeswalker it attacks if unblocked.
 */
@Composable
fun ImpactAnimations(state: MatchUiState) {
    val flights = remember { mutableStateListOf<Flight>() }
    val seenSpells = remember { HashSet<Int>() }

    // Spells / abilities with targets, as they go on the stack.
    val top = state.stack.firstOrNull()
    LaunchedEffect(top?.id, top?.targets) {
        if (top == null || top.targets.isEmpty() || !seenSpells.add(top.id)) return@LaunchedEffect
        delay(700) // let the cast spotlight show the card first
        val root = BoardPositions.rects.values.fold<Rect, Rect?>(null) { acc, r -> acc?.let { Rect(minOf(it.left, r.left), minOf(it.top, r.top), maxOf(it.right, r.right), maxOf(it.bottom, r.bottom)) } ?: r }
        val origin = root?.center ?: return@LaunchedEffect
        top.targets.forEach { key -> BoardPositions.rects[key]?.let { flights += Flight(top.source, origin, it, key, returns = false) } }
    }

    // Combat damage: each attacker strikes its blocker, or whatever it attacks.
    val combat = state.combat
    val damageStep = state.phase == PhaseType.COMBAT_DAMAGE || state.phase == PhaseType.COMBAT_FIRST_STRIKE_DAMAGE
    LaunchedEffect(state.turn, state.phase) {
        if (!damageStep || combat == null) return@LaunchedEffect
        val cards = state.players.flatMap { it.battlefield }.associateBy { it.id }
        combat.attackers.keys.forEach { attacker ->
            val from = BoardPositions.rects["c$attacker"] ?: return@forEach
            val blocker = combat.blockers[attacker].orEmpty().firstOrNull()
            val key = if (blocker != null) "c$blocker" else combat.defenderKeys[attacker] ?: return@forEach
            val to = BoardPositions.rects[key] ?: return@forEach
            flights += Flight(cards[attacker], from.center, to, key, returns = true)
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (combat != null && !damageStep) CombatArrows(combat)
        flights.toList().forEach { flight ->
            FlightView(flight) { flights.remove(flight) }
        }
    }
}

/**
 * While attackers and blockers are declared: an arrow from each attacker to whom it attacks
 * (terracotta) and from each blocker to the attacker it blocks (slate), drawn in as they
 * appear; they give way to the strike animations at combat damage.
 */
@Composable
private fun CombatArrows(combat: io.github.nmanuelides.mtgreforged.match.CombatUi) {
    val pairs = buildList {
        combat.attackers.keys.forEach { a -> combat.defenderKeys[a]?.let { add(Triple("c$a", it, true)) } }
        combat.blockers.forEach { (a, bs) -> bs.forEach { b -> add(Triple("c$b", "c$a", false)) } }
    }
    pairs.forEach { (from, to, attack) -> key(from, to) { Arrow(from, to, attack) } }
}

@Composable
private fun Arrow(fromKey: String, toKey: String, attack: Boolean) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
    val from = BoardPositions.rects[fromKey]?.center ?: return
    val to = BoardPositions.rects[toKey] ?: return
    val color = if (attack) Palette.Terracotta else Palette.Slate
    Canvas(Modifier.fillMaxSize()) {
        // Stop at the target's edge so the head stays visible.
        val dx = to.center.x - from.x
        val dy = to.center.y - from.y
        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val ux = dx / len
        val uy = dy / len
        val stopShort = minOf(to.width, to.height) * 0.45f
        val end = Offset(from.x + ux * (len - stopShort) * grow.value, from.y + uy * (len - stopShort) * grow.value)
        val w = 5.dp.toPx()
        drawLine(color.copy(alpha = 0.9f), from, end, strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val head = 16.dp.toPx()
        val px = -uy
        val py = ux
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(end.x + ux * head * 0.6f, end.y + uy * head * 0.6f)
            lineTo(end.x - ux * head * 0.6f + px * head * 0.55f, end.y - uy * head * 0.6f + py * head * 0.55f)
            lineTo(end.x - ux * head * 0.6f - px * head * 0.55f, end.y - uy * head * 0.6f - py * head * 0.55f)
            close()
        }
        drawPath(path, color)
        drawCircle(color, radius = w * 1.2f, center = from)
    }
}

@Composable
private fun FlightView(flight: Flight, onDone: () -> Unit) {
    val travel = remember { Animatable(0f) }
    val lift = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val density = LocalDensity.current
    LaunchedEffect(flight) {
        // Lift off and hang for a moment (slowing to a stop), then accelerate into the target.
        lift.animateTo(1f, tween(320, easing = LinearOutSlowInEasing))
        delay(140)
        travel.animateTo(1f, tween(380, easing = CubicBezierEasing(0.7f, 0f, 0.84f, 0f))) // ease-in (expo)
        BoardPositions.hit(flight.targetKey)
        launch { burst.animateTo(1f, tween(450)) }
        if (flight.returns) travel.animateTo(0f, tween(320)) else delay(450)
        onDone()
    }
    val t = travel.value
    val target = flight.to.center
    val raised = with(density) { 22.dp.toPx() } * lift.value * (1f - t)
    val x = flight.from.x + (target.x - flight.from.x) * t
    val y = flight.from.y + (target.y - flight.from.y) * t - raised
    val width = 64.dp
    val half = with(density) { Offset(width.toPx() / 2, width.toPx() / 0.716f / 2) }

    // Impact: a bright ring spreading from the target.
    if (burst.value > 0f && burst.value < 1f) {
        Canvas(Modifier.fillMaxSize()) {
            val b = burst.value
            drawCircle(
                Palette.Terracotta.copy(alpha = (1f - b) * 0.9f),
                radius = flight.to.width.coerceAtLeast(40f) * (0.4f + b),
                center = target,
                style = Stroke(width = 8.dp.toPx() * (1f - b) + 1f),
            )
        }
    }
    if (flight.card != null && (flight.returns || travel.value < 1f || burst.value == 0f)) {
        Box(
            Modifier
                .offset { IntOffset((x - half.x).toInt(), (y - half.y).toInt()) }
                .graphicsLayer {
                    // Spells shrink as they arrive; attackers lunge with a slight tilt.
                    val s = if (flight.returns) 1f + 0.18f * lift.value - 0.05f * t else 1.2f - 0.4f * t
                    scaleX = s; scaleY = s
                    rotationZ = if (flight.returns) 8f * t else 0f
                    shadowElevation = 12f
                },
        ) {
            CardTile(flight.card, width, showTapped = false)
        }
    }
}
