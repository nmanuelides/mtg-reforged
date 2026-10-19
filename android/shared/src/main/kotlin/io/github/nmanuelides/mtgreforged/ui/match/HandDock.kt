package io.github.nmanuelides.mtgreforged.ui.match

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.nmanuelides.mtgreforged.match.CardUi
import io.github.nmanuelides.mtgreforged.match.MatchUiState
import io.github.nmanuelides.mtgreforged.ui.components.CARD_ASPECT
import io.github.nmanuelides.mtgreforged.ui.components.CardMark
import io.github.nmanuelides.mtgreforged.ui.components.CardTile
import io.github.nmanuelides.mtgreforged.ui.components.tapOrHold
import kotlin.math.exp
import kotlin.math.floor

/**
 * The player's hand, fanned to fit. Tap a card to play it. Press and hold, then slide,
 * to magnify the cards under your finger like the classic macOS dock.
 */
@Composable
internal fun HandDock(
    cards: List<CardUi>,
    state: MatchUiState,
    actions: BoardActions,
    cardWidth: Dp,
    height: Dp,
    maxScale: Float = 2.4f,
) {
    var focusX by remember { mutableStateOf<Float?>(null) }
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxWidth().height(height).padding(horizontal = 6.dp)) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val cardW = with(density) { cardWidth.toPx() }
        val cardH = cardW / CARD_ASPECT
        val gap = with(density) { 8.dp.toPx() }
        val step = if (cards.size <= 1) 0f else minOf(cardW + gap, (widthPx - cardW) / (cards.size - 1))
        val total = if (cards.isEmpty()) 0f else cardW + step * (cards.size - 1)
        val start = (widthPx - total) / 2

        // With overlapping cards the topmost one under x is the last that starts left of it.
        fun cardAt(x: Float): CardUi? {
            if (cards.isEmpty() || x < start || x > start + total) return null
            val i = if (step == 0f) 0 else floor((x - start) / step).toInt().coerceIn(0, cards.lastIndex)
            return cards[i]
        }

        Box(
            Modifier
                .fillMaxSize()
                .tapOrHold(
                    key = cards.map { it.id },
                    onTap = { pos -> cardAt(pos.x)?.let { actions.onCard(it.id) } },
                    onHold = { holding, pos -> focusX = if (holding) pos.x else null },
                    onHoldMove = { pos -> focusX = pos.x },
                ),
        ) {
            cards.forEachIndexed { i, card ->
                val left = start + i * step
                val center = left + cardW / 2
                val target = focusX?.let { fx ->
                    val d = (center - fx) / cardW
                    1f + (maxScale - 1f) * exp(-d * d * 0.8f)
                } ?: 1f
                val scale by animateFloatAsState(target, spring(stiffness = Spring.StiffnessMediumLow), label = "dock")
                val mark = state.markFor(card)
                val lift = if (mark == CardMark.Selectable && focusX == null) with(density) { 10.dp.toPx() } else 0f

                CardTile(
                    card, cardWidth,
                    Modifier
                        .offset { IntOffset(left.toInt(), (heightPx - cardH - lift).toInt()) }
                        .zIndex(scale)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            // Grow upwards from the bottom edge, over the battlefield.
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        },
                    mark = mark,
                    showTapped = false,
                )
            }
        }
    }
}
