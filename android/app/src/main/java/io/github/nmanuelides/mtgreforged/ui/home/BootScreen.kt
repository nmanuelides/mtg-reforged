package io.github.nmanuelides.mtgreforged.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.nmanuelides.mtgreforged.engine.Engine
import io.github.nmanuelides.mtgreforged.ui.theme.Palette

@Composable
fun BootScreen(status: Engine.Status, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Palette.Background), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text("MTG Reforged", style = MaterialTheme.typography.headlineLarge)
            when (status) {
                is Engine.Status.InstallingData -> {
                    Text("Instalando datos de cartas…", color = Palette.TextSecondary)
                    LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.width(240.dp))
                }
                is Engine.Status.Loading -> {
                    Text(status.step, color = Palette.TextSecondary, textAlign = TextAlign.Center)
                    val p = status.progress
                    if (p != null) {
                        LinearProgressIndicator(progress = { p }, modifier = Modifier.width(240.dp))
                    } else {
                        LinearProgressIndicator(modifier = Modifier.width(240.dp))
                    }
                }
                is Engine.Status.Failed -> {
                    Text("No se pudo iniciar el motor", color = Palette.Life)
                    Text(status.message, color = Palette.TextSecondary, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onRetry) { Text("Reintentar") }
                }
                else -> LinearProgressIndicator(modifier = Modifier.width(240.dp))
            }
        }
    }
}
