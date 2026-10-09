package io.github.nmanuelides.mtgreforged.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.nmanuelides.mtgreforged.BuildConfig
import io.github.nmanuelides.mtgreforged.engine.Engine.DeckEntry
import io.github.nmanuelides.mtgreforged.ui.theme.Palette

sealed interface UpdateUi {
    data object None : UpdateUi
    data class Available(val name: String, val notes: String) : UpdateUi
    data class Downloading(val progress: Float) : UpdateUi
    data class Failed(val message: String) : UpdateUi
}

@Composable
fun HomeScreen(
    decks: List<DeckEntry>,
    update: UpdateUi,
    onInstallUpdate: () -> Unit,
    onStart: (DeckEntry, DeckEntry) -> Unit,
) {
    var mine by rememberSaveable { mutableStateOf<String?>(null) }
    var theirs by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = mine, false = opponent's
    val myDeck = decks.firstOrNull { it.name == mine }
    val theirDeck = decks.firstOrNull { it.name == theirs }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF231A33), Palette.Background, Palette.Background))),
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("MTG Reforged", style = MaterialTheme.typography.headlineLarge, color = Palette.TextPrimary)
            Text(
                "Motor Forge ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextSecondary,
            )

            UpdateBanner(update, onInstallUpdate)

            Spacer(Modifier.height(8.dp))
            Text("Partida rápida contra la IA", style = MaterialTheme.typography.titleMedium)
            DeckSlot("Tu mazo", myDeck?.name, onPick = { picking = true }, onRandom = { mine = decks.randomOrNull()?.name })
            DeckSlot("Mazo rival", theirDeck?.name, onPick = { picking = false }, onRandom = { theirs = decks.randomOrNull()?.name })

            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    val a = myDeck ?: decks.random()
                    val b = theirDeck ?: decks.random()
                    onStart(a, b)
                },
                enabled = decks.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Jugar", style = MaterialTheme.typography.titleMedium) }
        }
    }

    picking?.let { forMe ->
        DeckPicker(decks, onPick = {
            if (forMe) mine = it.name else theirs = it.name
            picking = null
        }, onDismiss = { picking = null })
    }
}

@Composable
private fun UpdateBanner(update: UpdateUi, onInstall: () -> Unit) {
    when (update) {
        UpdateUi.None -> Unit
        is UpdateUi.Available -> Row(
            Modifier
                .fillMaxWidth()
                .background(Palette.EmberSoft, RoundedCornerShape(14.dp))
                .border(1.dp, Palette.Ember, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.SystemUpdate, null, tint = Palette.Ember)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Nueva versión del motor", style = MaterialTheme.typography.titleMedium)
                Text(update.name, style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
            }
            TextButton(onClick = onInstall) { Text("Actualizar") }
        }
        is UpdateUi.Downloading -> Column(Modifier.fillMaxWidth()) {
            Text("Descargando actualización…", style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { update.progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
        is UpdateUi.Failed -> Text("No se pudo actualizar: ${update.message}", color = Palette.Life, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DeckSlot(label: String, deck: String?, onPick: () -> Unit, onRandom: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Surface, RoundedCornerShape(16.dp))
            .border(1.dp, Palette.Outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onPick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary)
            Text(deck ?: "Aleatorio", style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = onRandom) { Icon(Icons.Default.Casino, "Aleatorio", tint = Palette.Ember) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeckPicker(decks: List<DeckEntry>, onPick: (DeckEntry) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, decks) { decks.filter { it.name.contains(query, ignoreCase = true) } }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Surface) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                query, { query = it },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Buscar entre ${decks.size} mazos") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(Modifier.fillMaxWidth().height(480.dp).padding(top = 8.dp)) {
                items(filtered, key = { it.name }) { deck ->
                    Text(
                        deck.name,
                        Modifier.fillMaxWidth().clickable { onPick(deck) }.padding(vertical = 14.dp, horizontal = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
