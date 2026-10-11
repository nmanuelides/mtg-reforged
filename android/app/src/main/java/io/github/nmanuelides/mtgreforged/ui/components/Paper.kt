package io.github.nmanuelides.mtgreforged.ui.components

import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.ImageShader
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
import androidx.compose.ui.unit.dp
import io.github.nmanuelides.mtgreforged.R
import io.github.nmanuelides.mtgreforged.ui.theme.Palette

/**
 * The grain of a scanned sheet of old paper (high-passed, so no vignette or stains): toasted
 * brown where the fibres are darker, warm cream where lighter, the difference as alpha.
 * Tiled on top of each surface it keeps that surface's colour (light or dark theme) and adds
 * the same grain everywhere, so every panel and button reads as a real piece of paper.
 */
object PaperTexture {
    /** How strongly the paper shows through. */
    const val STRENGTH = 0.35f

    @Volatile private var image: ImageBitmap? = null

    fun load(context: Context) {
        if (image != null) return
        val options = BitmapFactory.Options().apply { inScaled = false }
        image = BitmapFactory.decodeResource(context.resources, R.drawable.paper_texture, options)?.asImageBitmap()
        brush = image?.let { ShaderBrush(ImageShader(it, TileMode.Mirror, TileMode.Mirror)) }
    }

    /**
     * Draws the texture over what's already drawn. Small surfaces get a centred cut at the
     * standard grain size; surfaces bigger than the scan stretch it to cover, stains included.
     */
    /** The grain tiled at its own size, mirrored so the tiles join without seams. */
    @Volatile private var brush: ShaderBrush? = null

    /** Draws the paper grain over what's already drawn, at the same scale on every surface. */
    fun DrawScope.drawPaper(strength: Float = STRENGTH) {
        val grain = brush ?: return
        drawRect(grain, alpha = strength)
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
