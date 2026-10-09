package io.github.nmanuelides.mtgreforged.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A plaque that only fires when its handle is dragged to the right end. Used where a
 * stray tap would be costly, such as passing priority and skipping phases.
 */
@Composable
fun SwipeToConfirm(
    text: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 54.dp,
) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val density = LocalDensity.current
    val knobWidth = 64.dp

    BoxWithConstraints(
        modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.45f)
            .background(Palette.Terracotta)
            .ornateFrame(Palette.PaperLight.copy(alpha = 0.75f), inset = 3.dp, corners = false),
    ) {
        val max = with(density) { (maxWidth - knobWidth - 8.dp).toPx() }.coerceAtLeast(1f)
        val progress = offset.value / max

        Text(
            "${text.uppercase()}  ›››",
            Modifier.align(Alignment.Center).alpha(1f - progress).padding(start = knobWidth / 2),
            style = MaterialTheme.typography.labelLarge,
            color = Palette.PaperLight,
        )
        Box(
            Modifier
                .padding(4.dp)
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .width(knobWidth)
                .fillMaxHeight()
                .background(Palette.PaperLight)
                .ornateFrame(Palette.LineStrong, inset = 2.dp, corners = false)
                .draggable(
                    enabled = enabled,
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, max)) }
                    },
                    onDragStopped = {
                        if (offset.value >= max * 0.85f) {
                            offset.animateTo(max)
                            onConfirm()
                        }
                        offset.animateTo(0f, spring())
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("›", color = Palette.Terracotta, fontSize = 26.sp)
        }
    }
}
