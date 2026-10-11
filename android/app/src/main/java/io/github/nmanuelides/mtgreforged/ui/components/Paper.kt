package io.github.nmanuelides.mtgreforged.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.nmanuelides.mtgreforged.R
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import kotlin.math.roundToInt

/**
 * A scanned sheet of old paper, stored as grain only: dark-brown pixels where the scan is
 * darker than average, white where it's lighter, with the difference as alpha. Drawn on
 * top, it keeps each surface's own colour (light or dark theme) and only adds the grain
 * and stains, so every panel and button reads as a real piece of paper.
 */
object PaperTexture {
    /** How strongly the paper shows through. */
    const val STRENGTH = 0.35f

    /** Screen pixels per texture pixel, so the grain is the same size on every surface. */
    private const val GRAIN_SCALE = 1.0f

    @Volatile private var image: ImageBitmap? = null

    fun load(context: Context) {
        if (image != null) return
        val options = BitmapFactory.Options().apply { inScaled = false }
        image = BitmapFactory.decodeResource(context.resources, R.drawable.paper_texture, options)?.asImageBitmap()
    }

    /**
     * Draws the texture over what's already drawn. Small surfaces get a centred cut at the
     * standard grain size; surfaces bigger than the scan stretch it to cover, stains included.
     */
    fun DrawScope.drawPaper(strength: Float = STRENGTH) {
        val tex = image ?: return
        if (size.width < 1f || size.height < 1f) return
        val scale = maxOf(GRAIN_SCALE, size.width / tex.width, size.height / tex.height)
        val srcW = (size.width / scale).roundToInt().coerceIn(1, tex.width)
        val srcH = (size.height / scale).roundToInt().coerceIn(1, tex.height)
        drawImage(
            tex,
            srcOffset = IntOffset((tex.width - srcW) / 2, (tex.height - srcH) / 2),
            srcSize = IntSize(srcW, srcH),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            alpha = strength,
        )
    }
}

/** A flat paper surface in [color] with the paper texture on it (use instead of background). */
fun Modifier.paper(color: Color, strength: Float = PaperTexture.STRENGTH): Modifier = drawBehind {
    drawRect(color)
    with(PaperTexture) { drawPaper(strength) }
}

/**
 * The soft shadow of a sheet of paper lying on whatever is below. [offsetY] pushes it down
 * (positive) or up (negative). Apply before the sheet's own background; [cover] should be
 * the sheet's colour, since the shadow-casting rectangle is drawn underneath it.
 */
fun Modifier.paperShadow(offsetY: Dp, blur: Dp = 18.dp, cover: Color = Palette.Paper, darkness: Int = 190): Modifier = drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint().asFrameworkPaint().apply {
            color = cover.toArgb()
            setShadowLayer(blur.toPx(), 0f, offsetY.toPx(), android.graphics.Color.argb(darkness, 20, 12, 6))
        }
        canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
    }
}
