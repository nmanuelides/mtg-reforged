package io.github.nmanuelides.mtgreforged.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Fast start, long gentle stop; never passes its target (no overshoot, no bounce). */
private val Settle = CubicBezierEasing(0.16f, 0.84f, 0.3f, 1f)

/**
 * Screen entrance, like icons returning to the iOS home screen: the element starts above
 * the top of the screen and falls straight down into place, easing to a stop. [order]
 * staggers elements so they arrive one after another.
 */
fun Modifier.landIn(order: Int = 0): Modifier = composed {
    val progress = remember { Animatable(0f) }
    val top = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        delay(order * 70L)
        progress.animateTo(1f, tween(durationMillis = 560, easing = Settle))
    }
    onGloballyPositioned { top.floatValue = it.positionInRoot().y }
        .graphicsLayer {
            val p = progress.value
            // Far enough to start fully above the screen, wherever the element sits.
            translationY = -(1f - p) * (top.floatValue + size.height + 24.dp.toPx())
            alpha = (p * 1.6f).coerceIn(0f, 1f)
        }
}
