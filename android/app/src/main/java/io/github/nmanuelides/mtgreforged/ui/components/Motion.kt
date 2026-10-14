package io.github.nmanuelides.mtgreforged.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/** Decelerates into place and never passes it: no overshoot, no bounce. */
private val Settle = CubicBezierEasing(0.2f, 0.8f, 0.25f, 1f)

private const val DURATION_MS = 460
private const val STAGGER_MS = 55L

/** Elements showing up this long after their screen opened (e.g. scrolled into view) don't animate. */
private const val ENTRANCE_WINDOW_MS = 1400L

/** When the current screen opened; see [EntranceScope]. */
private val LocalEntranceStart = compositionLocalOf { 0L }

/** Marks a screen: its [landIn] elements animate only while it's opening, not on later scrolls. */
@Composable
fun EntranceScope(content: @Composable () -> Unit) {
    val start = remember { SystemClock.uptimeMillis() }
    CompositionLocalProvider(LocalEntranceStart provides start, content = content)
}

/**
 * Screen entrance like icons returning to the iOS home screen: each element comes from
 * "in front of the glass", towards the viewer — starting larger and transparent — and
 * settles into its place. [order] staggers them so they arrive one after another,
 * overlapping rather than all at once.
 */
fun Modifier.landIn(order: Int = 0): Modifier = composed {
    val start = LocalEntranceStart.current
    val late = start != 0L && SystemClock.uptimeMillis() - start > ENTRANCE_WINDOW_MS
    val progress = remember { Animatable(if (late) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (late) return@LaunchedEffect
        delay(order * STAGGER_MS)
        progress.animateTo(1f, tween(durationMillis = DURATION_MS, easing = Settle))
    }
    graphicsLayer {
        val p = progress.value
        val scale = 1.35f - 0.35f * p
        scaleX = scale
        scaleY = scale
        alpha = p
    }
}
