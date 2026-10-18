package io.github.nmanuelides.mtgreforged.ui.components

import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.asAndroidBitmap
import io.github.nmanuelides.mtgreforged.R
import androidx.compose.ui.res.imageResource
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
 * Foil: a port of keldon-market's "holo-rare-ultra" shine. Two stacks of the same four layers
 * (the illusion texture, a vertical rainbow, fine 133° metallic stripes and a soft radial shade),
 * blended into each other like the CSS background-blend-mode, filtered (brightness / contrast /
 * saturate) and laid on the art: the first with colour-dodge, the second inside it with exclusion.
 * keldon follows the pointer; here the light point drifts slowly around keldon's resting spot.
 */
@Composable
private fun FoilSheen() {
    val texture = ImageBitmap.imageResource(R.drawable.foil_illusion)
    val drift = rememberInfiniteTransition(label = "foil")
    val a by drift.animateFloat(0f, 2f * PI.toFloat(), infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "a")
    Canvas(Modifier.fillMaxSize()) {
        // Pointer position as fractions of the card (keldon's --mx/--my = --posx/--posy).
        val px = 0.52f + 0.14f * sin(a)
        val py = 0.48f + 0.10f * sin(2f * a + 0.6f)
        val dx = px - 0.5f
        val dy = py - 0.5f
        val hyp = maxOf(0.28f, minOf(1f, sqrt(dx * dx + dy * dy) * 2f))
        val bounds = Rect(Offset.Zero, size)
        val bitmap = texture.asAndroidBitmap()
        drawIntoCanvas {
            it.saveLayer(bounds, Paint().apply {
                blendMode = BlendMode.ColorDodge
                colorFilter = cssFilter(brightness = hyp * 0.3f + 0.5f, contrast = 2f, saturate = 1f)
            })
        }
        holoStack(bitmap, px, py, rainbowHeight = 7f, stripesWidth = 3f, stripesX = px)
        drawIntoCanvas {
            it.saveLayer(bounds, Paint().apply {
                blendMode = BlendMode.Exclusion
                colorFilter = cssFilter(brightness = hyp * 0.5f + 0.8f, contrast = 1.6f, saturate = 0.9f)
            })
        }
        holoStack(bitmap, px, py, rainbowHeight = 4f, stripesWidth = 1.95f, stripesX = -px)
        drawIntoCanvas { it.restore(); it.restore() }
    }
}

private val RAINBOW = listOf(
    Color(0xFFFF7773), Color(0xFFFFED5F), Color(0xFFA8FF5F), Color(0xFF83FFF7), Color(0xFF7894FF), Color(0xFFD875FF), Color(0xFFFF7773),
)
private val NAVY = Color(0xFF0E152E)
private val STEEL = Color(0xFF8FA3A3) // hsl(180, 10%, 60%)
private val TEAL = Color(0xFF8FC1C1) // hsl(180, 29%, 66%)

/**
 * One CSS background stack, bottom layer first. Sizes are multiples of the card
 * ([rainbowHeight] for the 200% × N00% rainbow, [stripesWidth] for the N% × 100% stripes) and
 * placed the way CSS background-position percentages place an oversized image.
 */
private fun DrawScope.holoStack(texture: android.graphics.Bitmap, px: Float, py: Float, rainbowHeight: Float, stripesWidth: Float, stripesX: Float) {
    val w = size.width
    val h = size.height

    // 4. radial-gradient(farthest-corner circle at mx my), 200% × 200% at posx posy.
    val imgX = (w - 2f * w) * px
    val imgY = (h - 2f * h) * py
    val center = Offset(imgX + 2f * w * px, imgY + 2f * h * py)
    val far = listOf(Offset(imgX, imgY), Offset(imgX + 2 * w, imgY), Offset(imgX, imgY + 2 * h), Offset(imgX + 2 * w, imgY + 2 * h))
        .maxOf { (it - center).getDistance() }
    drawRect(
        Brush.radialGradient(
            0.1f to Color.Black.copy(alpha = 0.1f), 0.1667f to Color.Black.copy(alpha = 0.15f), 1f to Color.Black.copy(alpha = 0.25f),
            center = center, radius = far * 1.2f,
        ),
    )

    // 3. repeating-linear-gradient(133deg, navy / steel / teal stripes), hard-light, tiled sideways.
    val sw = stripesWidth * w
    val theta = Math.toRadians(133.0)
    val dir = Offset(sin(theta).toFloat(), -cos(theta).toFloat())
    val length = (sw * abs(sin(theta)) + h * abs(cos(theta))).toFloat()
    val period = 0.12f * length
    var left = (w - sw) * stripesX
    while (left > 0f) left -= sw
    while (left < w) {
        val start = Offset(left + sw / 2f, h / 2f) - dir * (length / 2f)
        clipRect(left, 0f, left + sw, h) {
            drawRect(
                Brush.linearGradient(
                    0f to NAVY, (0.038f / 0.12f) to STEEL, (0.045f / 0.12f) to TEAL, (0.052f / 0.12f) to STEEL,
                    (0.10f / 0.12f) to NAVY, 1f to NAVY,
                    start = start, end = start + dir * period, tileMode = TileMode.Repeated,
                ),
                blendMode = BlendMode.Hardlight,
            )
        }
        left += sw
    }

    // 2. repeating-linear-gradient(0deg, rainbow every 5%), 200% × N00% at 0% posy, hue.
    val rh = rainbowHeight * h
    val bottom = (h - rh) * py + rh
    drawRect(
        Brush.linearGradient(
            *RAINBOW.mapIndexed { i, c -> i / 6f to c }.toTypedArray(),
            start = Offset(0f, bottom - 0.05f * rh), end = Offset(0f, bottom - 0.35f * rh), tileMode = TileMode.Repeated,
        ),
        blendMode = BlendMode.Hue,
    )

    // 1. The illusion texture at 50% × 50%, centred and repeated, soft-light.
    val shader = android.graphics.BitmapShader(texture, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
    shader.setLocalMatrix(android.graphics.Matrix().apply {
        setScale(0.5f * w / texture.width, 0.5f * h / texture.height)
        postTranslate(0.25f * w, 0.25f * h)
    })
    drawRect(ShaderBrush(shader), blendMode = BlendMode.Softlight)
}

/** CSS brightness() contrast() saturate(), in that order, as one colour matrix. */
private fun cssFilter(brightness: Float, contrast: Float, saturate: Float): ColorFilter {
    val k = brightness * contrast
    val offset = 255f * 0.5f * (1f - contrast)
    val s = saturate
    val m = floatArrayOf(
        (0.213f + 0.787f * s) * k, (0.715f - 0.715f * s) * k, (0.072f - 0.072f * s) * k, 0f, offset,
        (0.213f - 0.213f * s) * k, (0.715f + 0.285f * s) * k, (0.072f - 0.072f * s) * k, 0f, offset,
        (0.213f - 0.213f * s) * k, (0.715f - 0.715f * s) * k, (0.072f + 0.928f * s) * k, 0f, offset,
        0f, 0f, 0f, 1f, 0f,
    )
    return ColorFilter.colorMatrix(ColorMatrix(m))
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
