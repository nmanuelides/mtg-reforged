package io.github.nmanuelides.mtgreforged.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.nmanuelides.mtgreforged.match.CardUi

/**
 * One gesture detector for cards: a quick tap calls [onTap]; pressing and holding calls
 * [onHold] with true, then false on release, and never also counts as a tap.
 * [onHoldMove] reports the finger position while held (for the hand's dock effect).
 * With [onSecondaryClick], a right-click calls it instead of holding.
 */
fun Modifier.tapOrHold(
    key: Any?,
    onTap: (Offset) -> Unit,
    onHold: (Boolean, Offset) -> Unit,
    onHoldMove: (Offset) -> Unit = {},
    onSecondaryClick: (() -> Unit)? = null,
): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (onSecondaryClick != null && currentEvent.buttons.isSecondaryPressed) {
            down.consume()
            onSecondaryClick()
            return@awaitEachGesture
        }
        // A right-click (Windows) holds straight away, until the button is released.
        val longPress = if (currentEvent.buttons.isSecondaryPressed) down else awaitLongPressOrCancellation(down.id)
        if (longPress == null) {
            // Released early (a tap) or moved past touch slop (a scroll): only taps count.
            val up = currentEvent.changes.firstOrNull { it.id == down.id }
            if (up != null && !up.pressed && (up.position - down.position).getDistance() < viewConfiguration.touchSlop) {
                onTap(up.position)
            }
            return@awaitEachGesture
        }
        onHold(true, longPress.position)
        var change: PointerInputChange? = longPress
        while (change != null && change.pressed) {
            val event = awaitPointerEvent()
            change = event.changes.firstOrNull { it.id == longPress.id }
            change?.let {
                onHoldMove(it.position)
                it.consume()
            }
        }
        onHold(false, change?.position ?: longPress.position)
    }
}

/** Centres the popup on its anchor, nudged back inside the window near the edges. */
private object CenterOnAnchor : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = anchorBounds.center.x - popupContentSize.width / 2
        val y = anchorBounds.center.y - popupContentSize.height / 2
        return IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}

/** While [visible], shows [card] enlarged right on top of the composable that hosts it. */
@Composable
fun HoldZoom(card: CardUi, visible: Boolean, width: Dp = 300.dp, caption: String? = null) {
    if (!visible) return
    val grow = remember(card.id) { Animatable(0.6f) }
    LaunchedEffect(card.id) { grow.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 500f)) }
    Popup(popupPositionProvider = CenterOnAnchor, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
        Column(
            Modifier.graphicsLayer { scaleX = grow.value; scaleY = grow.value; alpha = grow.value },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CardTile(card, width, showTapped = false)
            caption?.let {
                Spacer(Modifier.height(8.dp))
                Pill(it, Palette.Slate)
            }
        }
    }
}

/** A card that can be held to see it big: tap does [onTap]; press and hold (or right-click) zooms until released. */
@Composable
fun ZoomableCard(card: CardUi, width: Dp, modifier: Modifier = Modifier, onTap: (() -> Unit)? = null) {
    var held by remember { mutableStateOf(false) }
    Box(modifier.tapOrHold(card.id, onTap = { onTap?.invoke() }, onHold = { h, _ -> held = h })) {
        CardTile(card, width, showTapped = false)
        HoldZoom(card, held)
    }
}
