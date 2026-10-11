package io.github.nmanuelides.mtgreforged.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nmanuelides.mtgreforged.engine.CardImages
import io.github.nmanuelides.mtgreforged.match.CardUi
import io.github.nmanuelides.mtgreforged.ui.theme.Cinzel
import io.github.nmanuelides.mtgreforged.ui.theme.Palette

const val CARD_ASPECT = 63f / 88f

enum class CardMark { None, Selectable, Highlighted, Attacking, Blocking }

@Composable
fun rememberCardImage(key: String?): ImageBitmap? {
    // produceState keeps its value across key changes, so reset it for every new card;
    // otherwise a reused tile keeps showing the previous card's image.
    val image by produceState(initialValue = key?.let(CardImages::cached)?.asImageBitmap(), key) {
        value = key?.let(CardImages::cached)?.asImageBitmap()
        if (key != null && value == null) value = CardImages.load(key)?.asImageBitmap()
    }
    return image
}

@Composable
fun CardTile(
    card: CardUi,
    width: Dp,
    modifier: Modifier = Modifier,
    mark: CardMark = CardMark.None,
    showTapped: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val rotation by animateFloatAsState(if (showTapped && card.tapped) 90f else 0f, label = "tap")
    val shape = RoundedCornerShape(width * 0.055f)
    val borderColor = when (mark) {
        CardMark.Selectable -> Palette.Selectable
        CardMark.Highlighted -> Palette.Ember
        CardMark.Attacking -> Palette.Attacking
        CardMark.Blocking -> Palette.Blocking
        CardMark.None -> Color.Transparent
    }
    Box(
        modifier
            .width(width)
            .aspectRatio(CARD_ASPECT)
            .rotate(rotation)
            .clip(shape)
            .border(if (mark == CardMark.None) 0.dp else 2.dp, borderColor, shape)
            .combinedClickable(
                enabled = onClick != null || onLongClick != null,
                onClick = { onClick?.invoke() },
                onLongClick = onLongClick,
            ),
    ) {
        val image = rememberCardImage(card.imageKey)
        if (image != null) {
            Image(image, card.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            CardFallback(card, width)
        }
        CardBadges(card, width)
    }
}

/** Text frame shown until (or instead of) the card image. */
@Composable
private fun CardFallback(card: CardUi, width: Dp) {
    // A dark, framed stand-in so the card reads clearly on the parchment table.
    val compact = width < 80.dp
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF4A3B31), Color(0xFF2B221C))))
            .ornateFrame(Palette.Ochre.copy(alpha = 0.8f), inset = 2.dp, corners = false)
            .padding(horizontal = 5.dp, vertical = 4.dp),
    ) {
        Text(
            card.name,
            fontFamily = Cinzel,
            fontSize = if (compact) 8.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = if (compact) 3 else 2,
            overflow = TextOverflow.Ellipsis,
            color = Palette.OnAccent,
            lineHeight = if (compact) 9.sp else 14.sp,
        )
        if (!compact) {
            ManaCost(card.manaCost, 13.dp, Modifier.padding(vertical = 2.dp))
            Text(card.typeLine, fontSize = 10.sp, color = Color(0xFFE6D5B3), maxLines = 2)
            Text(card.text, fontSize = 9.sp, color = Palette.OnAccent, maxLines = 8, overflow = TextOverflow.Ellipsis, lineHeight = 11.sp)
        } else {
            Text(card.typeLine.substringBefore(" -").substringBefore(" —"), fontSize = 7.sp, color = Color(0xFFE6D5B3), maxLines = 1)
        }
    }
}

@Composable
private fun CardBadges(card: CardUi, width: Dp) {
    val small = width < 80.dp
    Box(Modifier.fillMaxSize().padding(2.dp)) {
        if (card.power != null && card.toughness != null) {
            val damaged = card.damage > 0
            Badge(
                "${card.power}/${card.toughness - card.damage.coerceAtMost(card.toughness)}",
                Modifier.align(Alignment.BottomEnd),
                if (damaged) Palette.Life else Color(0xE6101218),
                small,
            )
        } else if (card.loyalty != null) {
            Badge(card.loyalty, Modifier.align(Alignment.BottomEnd), Color(0xE6101218), small)
        }
        if (card.counters.isNotEmpty()) {
            val (name, count) = card.counters.first()
            val label = when (name.uppercase()) {
                "P1P1" -> "+$count"
                "M1M1" -> "-$count"
                else -> "$count ${name.take(3).lowercase()}"
            }
            Badge(label, Modifier.align(Alignment.TopEnd), Palette.Arcane, small)
        }
        if (card.sick && card.isCreature) {
            Badge("zz", Modifier.align(Alignment.TopStart), Color(0xB3101218), small)
        }
    }
}

@Composable
private fun Badge(text: String, modifier: Modifier, color: Color, small: Boolean) {
    Text(
        text,
        modifier
            .background(color, RoundedCornerShape(4.dp))
            .padding(horizontal = if (small) 3.dp else 5.dp, vertical = 1.dp),
        fontSize = if (small) 8.sp else 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
    )
}
