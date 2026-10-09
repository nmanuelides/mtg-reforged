package io.github.nmanuelides.mtgreforged.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nmanuelides.mtgreforged.ui.theme.Cinzel
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import kotlin.random.Random

// ---- Paper ---------------------------------------------------------------------------

/** Parchment: warm paper, a soft darker rim and a sprinkle of fibres. */
fun Modifier.parchment(base: Color = Palette.Paper): Modifier = drawBehind {
    drawRect(base)
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color(0x1A6B4A2A)),
            center = center,
            radius = size.maxDimension * 0.75f,
        ),
    )
    val rnd = Random(7)
    repeat((size.width * size.height / 9000f).toInt().coerceAtMost(900)) {
        drawCircle(
            Color(0x0F5A3E28),
            radius = rnd.nextFloat() * 1.6f + 0.4f,
            center = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height),
        )
    }
}

// ---- Frames --------------------------------------------------------------------------

private fun DrawScope.diamond(center: Offset, r: Float, color: Color, filled: Boolean = true) {
    val path = Path().apply {
        moveTo(center.x, center.y - r)
        lineTo(center.x + r, center.y)
        lineTo(center.x, center.y + r)
        lineTo(center.x - r, center.y)
        close()
    }
    if (filled) drawPath(path, color) else drawPath(path, color, style = Stroke(1.dp.toPx()))
}

/**
 * The storybook double rule: an outer and an inner hairline with small diamonds at the
 * corners, as on the reference sheets.
 */
fun Modifier.ornateFrame(
    color: Color = Palette.LineStrong,
    inset: Dp = 4.dp,
    corners: Boolean = true,
    topGem: Boolean = false,
): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    val gap = inset.toPx()
    val half = stroke / 2
    drawRect(color, topLeft = Offset(half, half), size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
    drawRect(
        color.copy(alpha = color.alpha * 0.7f),
        topLeft = Offset(gap, gap),
        size = Size(size.width - 2 * gap, size.height - 2 * gap),
        style = Stroke(stroke),
    )
    if (corners) {
        val r = 3.dp.toPx()
        listOf(Offset(0f, 0f), Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height))
            .forEach { diamond(it, r, color) }
    }
    if (topGem) {
        diamond(Offset(size.width / 2, 0f), 5.dp.toPx(), Palette.PaperLight)
        diamond(Offset(size.width / 2, 0f), 5.dp.toPx(), color, filled = false)
        diamond(Offset(size.width / 2, 0f), 2.dp.toPx(), color)
    }
}

/** A paper panel with the ornate double rule. */
@Composable
fun OrnatePanel(
    modifier: Modifier = Modifier,
    color: Color = Palette.PaperLight,
    frame: Color = Palette.LineStrong,
    padding: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.background(color).ornateFrame(frame).padding(padding), content = content)
}

// ---- Dividers & labels -------------------------------------------------------------

/** ——◆—— with dots, used between sections and under titles. */
@Composable
fun OrnateDivider(modifier: Modifier = Modifier, color: Color = Palette.LineStrong, width: Dp? = null) {
    Canvas(modifier.then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()).height(12.dp)) {
        val y = size.height / 2
        val cx = size.width / 2
        val stroke = 1.dp.toPx()
        val gem = 5.dp.toPx()
        drawLine(color, Offset(0f, y), Offset(cx - gem * 2.2f, y), stroke)
        drawLine(color, Offset(cx + gem * 2.2f, y), Offset(size.width, y), stroke)
        diamond(Offset(cx, y), gem, color, filled = false)
        diamond(Offset(cx, y), gem * 0.4f, color)
        drawCircle(color, 1.6.dp.toPx(), Offset(cx - gem * 3.4f, y))
        drawCircle(color, 1.6.dp.toPx(), Offset(cx + gem * 3.4f, y))
    }
}

/** Small-caps caption flanked by flourishes: "⟡ CHARACTER ⟡". */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Palette.Terracotta) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Flourish(color, mirrored = false)
        Text(
            text.uppercase(),
            Modifier.padding(horizontal = 10.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
        Flourish(color, mirrored = true)
    }
}

@Composable
private fun Flourish(color: Color, mirrored: Boolean) {
    Canvas(Modifier.size(34.dp, 10.dp)) {
        val y = size.height / 2
        val stroke = 1.dp.toPx()
        val (from, to) = if (mirrored) 0f to size.width - 8.dp.toPx() else 8.dp.toPx() to size.width
        drawLine(color, Offset(from, y), Offset(to, y), stroke)
        val tip = if (mirrored) size.width - 4.dp.toPx() else 4.dp.toPx()
        diamond(Offset(tip, y), 3.dp.toPx(), color)
    }
}

// ---- Buttons -----------------------------------------------------------------------

enum class OrnateStyle { Primary, Secondary, Quiet }

/**
 * Framed storybook button. Primary is a terracotta plaque with a cream inner rule,
 * Secondary is paper with the double rule.
 */
@Composable
fun OrnateButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: OrnateStyle = OrnateStyle.Secondary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    accent: Color = Palette.Terracotta,
    /** Frame colour for the Secondary style; defaults to the standard rule colour. */
    frameColor: Color = Palette.LineStrong,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    height: Dp = 48.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val (fill, frame, ink) = when (style) {
        OrnateStyle.Primary -> Triple(accent, Palette.PaperLight.copy(alpha = 0.75f), Palette.PaperLight)
        OrnateStyle.Secondary -> Triple(Palette.PaperLight, frameColor, accent)
        OrnateStyle.Quiet -> Triple(Palette.Paper, Palette.Line, Palette.Ink)
    }
    Box(
        modifier
            .height(height)
            .scale(if (pressed) 0.97f else 1f)
            .alpha(if (enabled) 1f else 0.45f)
            .background(fill)
            .ornateFrame(frame, inset = 3.dp, corners = style != OrnateStyle.Primary)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (style == OrnateStyle.Primary) Text("✦", color = ink.copy(alpha = 0.7f), fontSize = 10.sp)
            if (icon != null) {
                Spacer(Modifier.width(6.dp))
                Icon(icon, null, tint = if (style == OrnateStyle.Primary) ink else accent, modifier = Modifier.size(18.dp))
            }
            Text(
                text.uppercase(),
                Modifier.padding(horizontal = 8.dp),
                style = textStyle,
                color = ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            if (style == OrnateStyle.Primary) Text("✦", color = ink.copy(alpha = 0.7f), fontSize = 10.sp)
        }
    }
}

/** The big title-screen button with arrow flourishes reaching out on both sides. */
@Composable
fun PlayPlaque(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Palette.Terracotta,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(76.dp)) {
            val y = size.height / 2
            val c = color
            val stroke = 1.dp.toPx()
            drawLine(c, Offset(0f, y), Offset(28.dp.toPx(), y), stroke)
            drawLine(c, Offset(size.width - 28.dp.toPx(), y), Offset(size.width, y), stroke)
            diamond(Offset(4.dp.toPx(), y), 3.dp.toPx(), c)
            diamond(Offset(size.width - 4.dp.toPx(), y), 3.dp.toPx(), c)
        }
        OrnateButton(
            text, onClick,
            Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
            style = OrnateStyle.Secondary,
            enabled = enabled,
            accent = color,
            frameColor = color,
            textStyle = MaterialTheme.typography.headlineLarge.copy(letterSpacing = 6.sp),
            height = 76.dp,
        )
    }
}

// ---- Data display ------------------------------------------------------------------

/** Capsule tag like "LEADER • STRIKER". */
@Composable
fun Pill(text: String, color: Color = Palette.Sage, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier
            .background(color, RoundedCornerShape(50))
            .ornatePillRule()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelMedium,
        color = Palette.PaperLight,
    )
}

private fun Modifier.ornatePillRule(): Modifier = drawBehind {
    val inset = 3.dp.toPx()
    drawRoundRect(
        Palette.PaperLight.copy(alpha = 0.55f),
        topLeft = Offset(inset, inset),
        size = Size(size.width - 2 * inset, size.height - 2 * inset),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height),
        style = Stroke(1.dp.toPx()),
    )
}

/** A stat row: diamond badge, small-caps label, coloured bar and value. */
@Composable
fun StatBar(label: String, value: Float, display: String, color: Color, glyph: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(30.dp)) {
                diamond(center, size.minDimension / 2, color)
                diamond(center, size.minDimension / 2 - 3.dp.toPx(), Palette.PaperLight, filled = false)
            }
            Text(glyph, color = Palette.PaperLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .weight(1f)
                .height(40.dp)
                .background(Palette.PaperLight)
                .ornateFrame(Palette.Line, inset = 3.dp, corners = false)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(end = 40.dp)) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Ink)
                Spacer(Modifier.height(3.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).background(Palette.PaperDeep)) {
                    Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(4.dp).background(color))
                }
            }
            Text(
                display,
                Modifier.align(Alignment.CenterEnd),
                fontFamily = Cinzel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink,
            )
        }
    }
}
