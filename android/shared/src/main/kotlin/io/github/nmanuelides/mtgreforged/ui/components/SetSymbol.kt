package io.github.nmanuelides.mtgreforged.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.nmanuelides.mtgreforged.engine.SetIcons

/** A set's expansion symbol in [color]; nothing (no room taken) until it's loaded, or if it can't be. */
@Composable
fun SetSymbol(code: String, color: Color, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    var icon by remember(code) { mutableStateOf(SetIcons.cached(code)) }
    LaunchedEffect(code) { if (icon == null) icon = SetIcons.load(code) }
    val shape = icon ?: return
    Canvas(modifier.size(size)) {
        val box = shape.viewBox
        val scale = minOf(this.size.width / box.width, this.size.height / box.height)
        // Centred in the square, whatever the symbol's proportions.
        val dx = (this.size.width - box.width * scale) / 2f
        val dy = (this.size.height - box.height * scale) / 2f
        translate(dx, dy) {
            withTransform({
                scale(scale, scale, pivot = androidx.compose.ui.geometry.Offset.Zero)
                translate(-box.left, -box.top)
            }) {
                drawPath(shape.path, color)
            }
        }
    }
}
