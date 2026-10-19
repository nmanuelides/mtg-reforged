package io.github.nmanuelides.mtgreforged.ui.components

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Lets a mouse drag sideways lists and pagers the way a finger does (Compose scrolls them only
 * by touch and the wheel), for the Windows app. Pagers settle on a page when released; a drag
 * that started on a card doesn't also click it. Touch input is left alone.
 */
fun Modifier.mouseDrag(state: ScrollableState): Modifier = pointerInput(state) {
    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.type != PointerType.Mouse) return@awaitEachGesture
            val pager = state as? PagerState
            val startPage = pager?.currentPage ?: 0
            var total = 0f
            var dragging = false
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val dx = change.positionChange().x
                total += dx
                if (!dragging && abs(total) > viewConfiguration.touchSlop) dragging = true
                if (dragging) {
                    state.dispatchRawDelta(-dx)
                    change.consume()
                }
            }
            if (dragging && pager != null) {
                var target = pager.currentPage
                // A short flick still turns one page, like a swipe.
                if (target == startPage && abs(total) > 40.dp.toPx()) target += if (total < 0) 1 else -1
                launch { pager.animateScrollToPage(target.coerceIn(0, (pager.pageCount - 1).coerceAtLeast(0))) }
            }
        }
    }
}

/** horizontalScroll that a mouse can also drag. */
fun Modifier.horizontalDragScroll(): Modifier = composed {
    val state = rememberScrollState()
    mouseDrag(state).horizontalScroll(state)
}

/** A right-click does what a long press does (for the Windows app; touch never sends one). */
fun Modifier.secondaryClick(onClick: (() -> Unit)?): Modifier = if (onClick == null) this else pointerInput(onClick) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        if (currentEvent.buttons.isSecondaryPressed) {
            currentEvent.changes.forEach { it.consume() }
            onClick()
        }
    }
}
