package io.github.nmanuelides.mtgreforged.ui.match

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nmanuelides.mtgreforged.match.CardUi
import io.github.nmanuelides.mtgreforged.match.MatchUiState
import io.github.nmanuelides.mtgreforged.match.PlayerUi
import io.github.nmanuelides.mtgreforged.ui.components.CARD_ASPECT
import io.github.nmanuelides.mtgreforged.ui.components.CardMark
import io.github.nmanuelides.mtgreforged.ui.components.CardTile
import io.github.nmanuelides.mtgreforged.ui.components.HoldZoom
import io.github.nmanuelides.mtgreforged.ui.components.tapOrHold
import io.github.nmanuelides.mtgreforged.ui.theme.Palette

/**
 * One slot on the battlefield: a permanent with whatever is attached to it, or several
 * identical permanents (basic lands, token swarms) collapsed into a single pile.
 */
private class FieldItem(val cards: List<CardUi>, val attachments: List<CardUi>, val section: Int) {
    val top get() = cards.first()
    val tapped get() = top.tapped
}

private const val ATTACH_PEEK = 0.16f
private const val GAP_DP = 6f

private fun groupKey(c: CardUi) =
    listOf(c.name, c.tapped, c.sick, c.power, c.toughness, c.damage, c.counters, c.token, c.faceDown)

private fun buildItems(cards: List<CardUi>, mirrored: Boolean): List<FieldItem> {
    val ids = cards.mapTo(HashSet()) { it.id }
    val attached = cards.filter { it.attachedToId != null && it.attachedToId in ids }.groupBy { it.attachedToId }
    val roots = cards.filter { it.attachedToId == null || it.attachedToId !in ids }

    fun section(c: CardUi) = when {
        c.isCreature -> 0
        !c.isLand -> 1
        else -> 2
    }.let { if (mirrored) 2 - it else it }

    val items = ArrayList<FieldItem>()
    roots.groupBy { section(it) }.toSortedMap().forEach { (sec, inSection) ->
        // Only bare permanents are stacked; anything with attachments stays on its own.
        val (plain, decorated) = inSection.partition { attached[it.id].isNullOrEmpty() }
        plain.groupBy(::groupKey).values.forEach { items += FieldItem(it, emptyList(), sec) }
        decorated.forEach { items += FieldItem(listOf(it), attached[it.id].orEmpty(), sec) }
    }
    return items
}

private class Placement(val item: FieldItem, val x: Float, val y: Float, val w: Float, val h: Float)
private class Plan(val cardWidth: Float, val placements: List<Placement>)

/** Finds the largest card width at which every item fits, packing rows section by section. */
private fun plan(items: List<FieldItem>, maxW: Float, maxH: Float, minCard: Float, maxCard: Float): Plan {
    fun layout(cardW: Float): Pair<Float, List<Placement>> {
        val cardH = cardW / CARD_ASPECT
        val rows = ArrayList<MutableList<Pair<FieldItem, Pair<Float, Float>>>>()
        var rowWidth = 0f
        var lastSection = -1
        for (item in items) {
            val w = (if (item.tapped) cardH else cardW) + fanStep(item.cards.size, cardW) * (item.cards.size - 1)
            val h = cardH * (1 + ATTACH_PEEK * item.attachments.size)
            val newRow = rows.isEmpty() || item.section != lastSection || rowWidth + GAP_DP + w > maxW
            if (newRow) {
                rows += mutableListOf<Pair<FieldItem, Pair<Float, Float>>>()
                rowWidth = w
            } else {
                rowWidth += GAP_DP + w
            }
            rows.last() += item to (w to h)
            lastSection = item.section
        }
        val placements = ArrayList<Placement>()
        var y = 0f
        rows.forEach { row ->
            val rowH = row.maxOf { it.second.second }
            val total = row.sumOf { it.second.first.toDouble() }.toFloat() + GAP_DP * (row.size - 1)
            var x = (maxW - total) / 2
            row.forEach { (item, size) ->
                placements += Placement(item, x, y + (rowH - size.second) / 2, size.first, size.second)
                x += size.first + GAP_DP
            }
            y += rowH + GAP_DP
        }
        return (y - GAP_DP) to placements
    }

    var width = maxCard
    while (width > minCard) {
        val (height, placements) = layout(width)
        if (height <= maxH) return Plan(width, placements)
        width -= 2f
    }
    return Plan(minCard, layout(minCard).second)
}

@Composable
internal fun Battlefield(
    player: PlayerUi,
    state: MatchUiState,
    actions: BoardActions,
    mirrored: Boolean,
    maxCardWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val items = remember(player.battlefield, mirrored) { buildItems(player.battlefield, mirrored) }
    BoxWithConstraints(modifier) {
        val plan = remember(items, maxWidth, maxHeight, maxCardWidth) {
            plan(items, maxWidth.value, maxHeight.value, 28f, maxCardWidth.value)
        }
        // Opponents' fields hug the top edge of their area, ours the bottom one.
        val usedHeight = plan.placements.maxOfOrNull { it.y + it.h } ?: 0f
        val shiftY = if (mirrored) 0f else (maxHeight.value - usedHeight).coerceAtLeast(0f)
        plan.placements.forEach { p ->
            Box(
                Modifier.offset(p.x.dp, (p.y + shiftY).dp).size(p.w.dp, p.h.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                FieldItemView(p.item, plan.cardWidth.dp, state, actions)
            }
        }
    }
}

/** Horizontal offset between copies in a pile: generous for a few, squeezed for many. */
private fun fanStep(count: Int, cardWidth: Float): Float =
    if (count <= 1) 0f else minOf(cardWidth * 0.3f, cardWidth * 1.6f / (count - 1))

@Composable
private fun FieldItemView(item: FieldItem, cardWidth: Dp, state: MatchUiState, actions: BoardActions) {
    val cardHeight = cardWidth / CARD_ASPECT
    val slotWidth = if (item.tapped) cardHeight else cardWidth
    val step = fanStep(item.cards.size, cardWidth.value).dp

    Box(contentAlignment = Alignment.BottomStart) {
        item.attachments.forEachIndexed { i, a ->
            var held by remember { mutableStateOf(false) }
            Box(
                Modifier
                    .offset(y = -(cardHeight * ATTACH_PEEK * (item.attachments.size - i)))
                    .tapOrHold(a.id, onTap = { actions.onCard(a.id) }, onHold = { h, _ -> held = h }),
            ) {
                CardTile(a, cardWidth, mark = state.markFor(a))
                HoldZoom(a, held)
            }
        }
        // Identical permanents fan out so each copy can be tapped (or picked) on its own.
        // Holding any copy zooms it in place until the finger lifts.
        // Holding any copy zooms it; sliding the finger sideways zooms the neighbouring copies.
        var focus by remember(item.cards.size) { mutableStateOf<Int?>(null) }
        val stepPx = with(LocalDensity.current) { step.toPx() }
        item.cards.forEachIndexed { i, card ->
            Box(
                Modifier
                    .offset(x = step * i)
                    .size(slotWidth, cardHeight)
                    .trackPosition("c${card.id}")
                    .tapOrHold(
                        card.id,
                        onTap = { actions.onCard(card.id) },
                        onHold = { h, _ -> focus = if (h) i else null },
                        onHoldMove = { pos ->
                            if (stepPx > 0f) focus = ((stepPx * i + pos.x) / stepPx).toInt().coerceIn(0, item.cards.lastIndex)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                CardTile(card, cardWidth, mark = state.markFor(card))
                state.attackTargetName(card.id)?.let { target ->
                    Text(
                        "→ $target",
                        Modifier
                            .align(Alignment.TopCenter)
                            .background(Palette.Terracotta)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Palette.OnAccent,
                        maxLines = 1,
                    )
                }
                HoldZoom(card, focus == i, caption = zoomInfo(card))
            }
        }
    }
}

/** Who (or which planeswalker) a declared attacker is going after. */
internal fun MatchUiState.attackTargetName(cardId: Int): String? {
    val combat = combat ?: return null
    if (!combat.attackers.containsKey(cardId)) return null
    val defender = combat.attackers[cardId] ?: return "?"
    players.firstOrNull { it.id == defender }?.let { return if (it.isLocal) "You" else it.name }
    return players.firstNotNullOfOrNull { p -> p.battlefield.firstOrNull { it.id == defender }?.name } ?: "?"
}

/** Counters and current power / toughness, shown under a held card. */
private fun zoomInfo(card: CardUi): String? {
    val parts = ArrayList<String>()
    if (card.power != null && card.toughness != null) parts += "${card.power}/${card.toughness}"
    card.counters.forEach { (n, c) -> parts += "$c × $n" }
    if (card.damage > 0) parts += "${card.damage} damage"
    return parts.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
}

internal fun MatchUiState.markFor(card: CardUi): CardMark = when {
    card.id == blockFocusId -> CardMark.Highlighted
    card.id in selectableIds -> CardMark.Selectable
    card.id in highlightedIds -> CardMark.Highlighted
    combat?.attackers?.containsKey(card.id) == true -> CardMark.Attacking
    combat?.blockers?.values?.any { card.id in it } == true -> CardMark.Blocking
    else -> CardMark.None
}

internal class BoardActions(
    val onCard: (Int) -> Unit,
    val onPlayer: (Int) -> Unit,
    val onInspect: (CardUi) -> Unit,
    val onZone: (String, List<CardUi>) -> Unit,
)
