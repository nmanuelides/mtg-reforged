package io.github.nmanuelides.mtgreforged.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class LandFrom { Top, Bottom, Left, Right }

/**
 * Screen entrance: the element starts outside the screen and "lands" in place with a soft
 * overshoot, a little after the ones before it ([order]).
 */
fun Modifier.landIn(order: Int = 0, from: LandFrom = LandFrom.Bottom): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(order * 80L)
        progress.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 220f))
    }
    graphicsLayer {
        val p = progress.value
        val away = 1f - p
        val extra = 80.dp.toPx()
        when (from) {
            LandFrom.Top -> translationY = -away * (size.height + extra)
            LandFrom.Bottom -> translationY = away * (size.height + extra)
            LandFrom.Left -> translationX = -away * (size.width + extra)
            LandFrom.Right -> translationX = away * (size.width + extra)
        }
        alpha = p.coerceIn(0f, 1f)
        val scale = 0.94f + 0.06f * p
        scaleX = scale
        scaleY = scale
    }
}
