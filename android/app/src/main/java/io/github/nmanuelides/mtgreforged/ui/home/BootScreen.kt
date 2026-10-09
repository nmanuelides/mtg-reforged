package io.github.nmanuelides.mtgreforged.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.nmanuelides.mtgreforged.engine.Engine
import io.github.nmanuelides.mtgreforged.ui.components.OrnateButton
import io.github.nmanuelides.mtgreforged.ui.components.OrnateDivider
import io.github.nmanuelides.mtgreforged.ui.components.OrnateStyle
import io.github.nmanuelides.mtgreforged.ui.components.ornateFrame
import io.github.nmanuelides.mtgreforged.ui.components.parchment
import io.github.nmanuelides.mtgreforged.ui.theme.Palette

@Composable
fun BootScreen(status: Engine.Status, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().parchment().ornateFrame(inset = 6.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            OrnateDivider(width = 260.dp)
            Text("MTG Reforged", style = MaterialTheme.typography.displayLarge, color = Palette.Terracotta, textAlign = TextAlign.Center)
            OrnateDivider(width = 260.dp)
            when (status) {
                is Engine.Status.InstallingData -> {
                    Caption("Installing card data…")
                    ProgressRule(status.progress)
                }
                is Engine.Status.Loading -> {
                    Caption(status.step)
                    ProgressRule(status.progress)
                }
                is Engine.Status.Failed -> {
                    Caption("The engine could not start")
                    Text(status.message, color = Palette.InkSoft, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                    OrnateButton("Retry", onRetry, style = OrnateStyle.Primary)
                }
                else -> ProgressRule(null)
            }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = Palette.InkSoft, textAlign = TextAlign.Center)
}

/** Framed bar like the reference's health bar; slides back and forth when indeterminate. */
@Composable
private fun ProgressRule(progress: Float?) {
    val sweep by rememberInfiniteTransition(label = "sweep").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "sweep",
    )
    Box(
        Modifier
            .width(320.dp)
            .height(18.dp)
            .background(Palette.PaperLight)
            .ornateFrame(Palette.LineStrong, inset = 3.dp, corners = false)
            .padding(5.dp),
    ) {
        if (progress != null) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Palette.Terracotta))
        } else {
            Box(Modifier.fillMaxWidth(0.25f + 0.75f * sweep).fillMaxHeight().background(Palette.Terracotta.copy(alpha = 0.35f + 0.5f * sweep)))
        }
    }
}
