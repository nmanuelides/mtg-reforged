package io.github.nmanuelides.mtgreforged.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Looks like the normal primary plaque, but only fires on a rightward swipe across it;
 * a plain tap just flashes a hint. Used where a stray tap would be costly, such as
 * passing priority and skipping phases.
 */
@Composable
fun SwipeToConfirm(
    text: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 54.dp,
    /** Plaque colour: terracotta for Next Step / Resolve, slate for End Turn. */
    fill: Color = Palette.Terracotta,
) {
    val confirm by rememberUpdatedState(onConfirm)
    val scope = rememberCoroutineScope()
    val nudge = remember { Animatable(0f) } // the label follows the finger a little
    var hint by remember { mutableStateOf(false) }
    LaunchedEffect(hint) {
        if (hint) {
            delay(1200)
            hint = false
        }
    }

    Box(
        modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.45f)
            .paperShadow(offsetY = 3.dp, blur = 7.dp, cover = fill, darkness = 120)
            .paper(fill)
            .ornateFrame(Palette.OnAccent.copy(alpha = 0.75f), inset = 3.dp, corners = false)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val threshold = maxOf(size.width * 0.3f, 80.dp.toPx())
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var dx = 0f
                    var dy = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val delta = change.positionChange()
                        dx += delta.x
                        dy += delta.y
                        if (abs(dx) > abs(dy)) change.consume()
                        scope.launch { nudge.snapTo((dx / threshold).coerceIn(0f, 1f)) }
                    }
                    when {
                        dx >= threshold && abs(dx) > abs(dy) -> confirm()
                        abs(dx) < 12.dp.toPx() && abs(dy) < 12.dp.toPx() -> hint = true
                    }
                    scope.launch { nudge.animateTo(0f, spring()) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (hint) "Swipe  →" else "${text.uppercase()}  ›››",
            Modifier.offset { IntOffset((nudge.value * 24.dp.toPx()).roundToInt(), 0) },
            style = MaterialTheme.typography.labelLarge,
            color = Palette.OnAccent.copy(alpha = 0.75f + 0.25f * nudge.value),
        )
    }
}
