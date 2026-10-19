package io.github.nmanuelides.mtgreforged.ui.components

import kotlin.random.Random
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.nmanuelides.mtgreforged.R
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import io.github.nmanuelides.mtgreforged.platform.AppContext
import io.github.nmanuelides.mtgreforged.platform.drawShadowedRect
import io.github.nmanuelides.mtgreforged.platform.loadImageRes

/**
 * The grain of a scanned sheet of old paper (high-passed, so no vignette or stains): toasted
 * brown where the fibres are darker, warm cream where lighter, the difference as alpha.
 * Tiled on top of each surface it keeps that surface's colour (light or dark theme) and adds
 * the same grain everywhere, so every panel and button reads as a real piece of paper.
 */
object PaperTexture {
    /** How strongly the paper shows through on backgrounds. */
    const val STRENGTH = 0.35f

    /** Buttons, bars and panels: much fainter, so they read as their own pieces of paper. */
    const val ELEMENT_STRENGTH = 0.14f

    @Volatile private var image: ImageBitmap? = null

    fun load(context: AppContext) {
        if (image != null) return
        image = context.loadImageRes(R.drawable.paper_texture)
        brush = image?.let { ShaderBrush(ImageShader(it, TileMode.Mirror, TileMode.Mirror)) }
    }

    /**
     * Draws the texture over what's already drawn. Small surfaces get a centred cut at the
     * standard grain size; surfaces bigger than the scan stretch it to cover, stains included.
     */
    /** The grain tiled at its own size, mirrored so the tiles join without seams. */
    @Volatile private var brush: ShaderBrush? = null

    /** Draws the paper grain over what's already drawn, at the same scale on every surface. */
    fun DrawScope.drawPaper(strength: Float = STRENGTH, offset: Offset = Offset.Zero) {
        val grain = brush ?: return
        // Shifting the shader shows a different part of the sheet.
        translate(-offset.x, -offset.y) { drawRect(grain, topLeft = offset, size = size, alpha = strength) }
    }
}

/**
 * A separate piece of paper in [color] (use instead of background): faint grain from its
 * own random part of the sheet, so it never looks continuous with what's behind it.
 */
fun Modifier.paper(color: Color, strength: Float = PaperTexture.ELEMENT_STRENGTH): Modifier = composed {
    val offset = remember { Offset(Random.nextFloat() * 1200f, Random.nextFloat() * 1200f) }
    drawBehind {
        drawRect(color)
        with(PaperTexture) { drawPaper(strength, offset) }
    }
}

/**
 * The soft shadow of a sheet of paper lying on whatever is below. [offsetY] pushes it down
 * (positive) or up (negative). Apply before the sheet's own background; [cover] should be
 * the sheet's colour, since the shadow-casting rectangle is drawn underneath it.
 */
fun Modifier.paperShadow(offsetY: Dp, blur: Dp = 18.dp, cover: Color = Palette.Paper, darkness: Int = 190): Modifier = drawBehind {
    drawShadowedRect(cover, blur.toPx(), offsetY.toPx(), Color(20, 12, 6, darkness))
}
