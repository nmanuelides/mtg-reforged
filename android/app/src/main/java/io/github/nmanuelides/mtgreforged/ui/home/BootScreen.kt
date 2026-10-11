package io.github.nmanuelides.mtgreforged.ui.home

import io.github.nmanuelides.mtgreforged.ui.components.paper
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.nmanuelides.mtgreforged.engine.Engine
import io.github.nmanuelides.mtgreforged.ui.components.LocalArt
import io.github.nmanuelides.mtgreforged.ui.components.LocalArtImage
import io.github.nmanuelides.mtgreforged.ui.components.rememberArtAccent
import io.github.nmanuelides.mtgreforged.ui.components.OrnateButton
import io.github.nmanuelides.mtgreforged.ui.components.OrnateDivider
import io.github.nmanuelides.mtgreforged.ui.components.OrnateStyle
import io.github.nmanuelides.mtgreforged.ui.components.ornateFrame
import io.github.nmanuelides.mtgreforged.ui.components.parchment
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import kotlin.math.roundToInt

/**
 * The loading screen: a framed window onto [art]. Once loading ends, [reveal] runs from
 * 0 to 1 and the window grows into [target] (where the landing shows the same art), so
 * finishing the load feels like stepping through the window onto the landing.
 */
@Composable
fun BootScreen(
    status: Engine.Status,
    onRetry: () -> Unit,
    art: LocalArt,
    reveal: Float = 0f,
    window: Rect? = null,
    target: Rect? = null,
    onWindowBounds: (Rect) -> Unit = {},
) {
    // Everything but the window fades out during the first half of the reveal.
    val fade = (1f - reveal * 2f).coerceIn(0f, 1f)
    val accent = rememberArtAccent(art)

    Box(Modifier.fillMaxSize().parchment()) {
        Box(Modifier.fillMaxSize().alpha(fade).ornateFrame(inset = 6.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.align(Alignment.Center).padding(32.dp),
        ) {
            Box(
                Modifier
                    .width(560.dp)
                    .height(240.dp)
                    .alpha(if (reveal > 0f) fade else 1f)
                    .paper(Palette.PaperLight)
                    .ornateFrame(inset = 5.dp, topGem = true)
                    .padding(10.dp),
            ) {
                LocalArtImage(
                    art,
                    Modifier
                        .fillMaxSize()
                        .alpha(if (reveal > 0f) 0f else 1f)
                        .onGloballyPositioned { onWindowBounds(it.boundsInRoot()) },
                )
            }
            Column(
                Modifier.alpha(fade),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OrnateDivider(width = 260.dp, color = accent)
                Text("MTG Reforged", style = MaterialTheme.typography.displayLarge, color = accent, textAlign = TextAlign.Center)
                OrnateDivider(width = 260.dp, color = accent)
                when (status) {
                    is Engine.Status.InstallingData -> {
                        Caption("Installing card data…")
                        ProgressRule(status.progress, accent)
                    }
                    is Engine.Status.Loading -> {
                        Caption(status.step)
                        ProgressRule(status.progress, accent)
                    }
                    is Engine.Status.Failed -> {
                        Caption("The engine could not start")
                        Text(status.message, color = Palette.InkSoft, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                        OrnateButton("Retry", onRetry, style = OrnateStyle.Primary)
                    }
                    Engine.Status.Ready -> {
                        Caption("Ready")
                        ProgressRule(1f, accent)
                    }
                    else -> ProgressRule(null, accent)
                }
            }
        }

        // The growing window, drawn freely on top so it can leave the column's layout.
        val start = window
        if (reveal > 0f && start != null && target != null) {
            val r = lerp(start, target, reveal)
            val density = LocalDensity.current
            with(density) {
                LocalArtImage(
                    art,
                    Modifier
                        .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                        .size(r.width.toDp(), r.height.toDp()),
                )
            }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = Palette.InkSoft, textAlign = TextAlign.Center)
}

/** Framed bar like the reference's health bar; eases between progress values, never jumps. */
@Composable
private fun ProgressRule(target: Float?, color: Color) {
    val progress = target?.let { animateFloatAsState(it, tween(250), label = "progress").value }
    val sweep by rememberInfiniteTransition(label = "sweep").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "sweep",
    )
    Box(
        Modifier
            .width(320.dp)
            .height(18.dp)
            .paper(Palette.PaperLight)
            .ornateFrame(color, inset = 3.dp, corners = false)
            .padding(5.dp),
    ) {
        if (progress != null) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(color))
        } else {
            Box(Modifier.fillMaxWidth(0.25f + 0.75f * sweep).fillMaxHeight().background(color.copy(alpha = 0.35f + 0.5f * sweep)))
        }
    }
}
