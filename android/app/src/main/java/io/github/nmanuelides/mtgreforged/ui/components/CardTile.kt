package io.github.nmanuelides.mtgreforged.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.collectAsState
import io.github.nmanuelides.mtgreforged.engine.Connectivity
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
    // Re-runs when the network comes back, so cards that failed offline load then.
    val online by Connectivity.online.collectAsState()
    val image by produceState(initialValue = key?.let(CardImages::cached)?.asImageBitmap(), key, online) {
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
        if (card.foil) FoilSheen()
        CardBadges(card, width)
    }
}

/**
 * Holographic foil, after keldon-market's "holo rare ultra": a rainbow of thin horizontal
 * bands and dark-and-teal metallic stripes at 133°, both color-dodged onto the art (plus an
 * exclusion pass), with a soft shade around the light. There is no mouse on a phone, so the
 * light drifts on its own in a slow loop and the bands slide with it.
 */
@Composable
private fun FoilSheen() {
    val t = rememberInfiniteTransition(label = "foil")
    val px by t.animateFloat(0.15f, 0.85f, infiniteRepeatable(tween(5200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "px")
    val py by t.animateFloat(0.2f, 0.8f, infiniteRepeatable(tween(3700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "py")
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = px - 0.5f
        val cy = py - 0.5f
        // Brighter toward the edges, like --hyp; never below the idle baseline.
        val hyp = maxOf(0.28f, minOf(1f, kotlin.math.sqrt(cx * cx + cy * cy) * 2f))

        // 1. Rainbow bands (repeating every ~6 x 5% of the height), sliding with the light.
        val band = h * 0.35f
        val rainbowY = py * h * 2f
        drawRect(
            Brush.linearGradient(
                listOf(
                    Color(0xFFFF7773), Color(0xFFFFED5F), Color(0xFFA8FF5F), Color(0xFF83FFF7),
                    Color(0xFF7894FF), Color(0xFFD875FF), Color(0xFFFF7773),
                ),
                start = Offset(0f, rainbowY), end = Offset(0f, rainbowY + band), tileMode = TileMode.Repeated,
            ),
            alpha = 0.22f + 0.18f * hyp,
            blendMode = BlendMode.ColorDodge,
        )

        // 2. Metallic stripes at 133°: dark navy with a thin teal sheen, repeating.
        val rad = Math.toRadians(133.0)
        val dx = kotlin.math.cos(rad).toFloat()
        val dy = kotlin.math.sin(rad).toFloat()
        val period = (w + h) * 0.12f
        val shift = px * w * 3f
        val origin = Offset(shift, py * h)
        val navy = Color(0xFF0E152E)
        drawRect(
            Brush.linearGradient(
                0f to navy, 0.38f to Color(0xFF8C9999), 0.45f to Color(0xFF93BDBD), 0.52f to Color(0xFF8C9999), 1f to navy,
                start = origin, end = Offset(origin.x + dx * period, origin.y + dy * period), tileMode = TileMode.Repeated,
            ),
            alpha = 0.18f + 0.12f * hyp,
            blendMode = BlendMode.ColorDodge,
        )
        // Second pass in exclusion (the CSS ::after), offset the other way.
        drawRect(
            Brush.linearGradient(
                0f to navy, 0.45f to Color(0xFF93BDBD), 1f to navy,
                start = Offset(-shift, h - py * h), end = Offset(-shift + dx * period * 1.3f, h - py * h + dy * period * 1.3f),
                tileMode = TileMode.Repeated,
            ),
            alpha = 0.10f + 0.08f * hyp,
            blendMode = BlendMode.Exclusion,
        )

        // 3. Soft shade around where the light falls.
        drawRect(
            Brush.radialGradient(
                0.12f to Color(0x1A000000), 0.2f to Color(0x26000000), 1f to Color(0x40000000),
                center = Offset(px * w, py * h), radius = maxOf(w, h),
            ),
            alpha = 0.6f,
        )
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
