package io.github.nmanuelides.mtgreforged.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nmanuelides.mtgreforged.BuildConfig
import io.github.nmanuelides.mtgreforged.engine.Engine.DeckEntry
import io.github.nmanuelides.mtgreforged.engine.Engine.Format
import io.github.nmanuelides.mtgreforged.ui.components.LocalArt
import io.github.nmanuelides.mtgreforged.ui.components.LocalArtImage
import io.github.nmanuelides.mtgreforged.ui.components.OrnateButton
import io.github.nmanuelides.mtgreforged.ui.components.OrnateDialog
import io.github.nmanuelides.mtgreforged.ui.components.OrnateDivider
import io.github.nmanuelides.mtgreforged.ui.components.OrnateStyle
import io.github.nmanuelides.mtgreforged.ui.components.PlayPlaque
import io.github.nmanuelides.mtgreforged.ui.components.SectionLabel
import io.github.nmanuelides.mtgreforged.ui.components.ornateFrame
import io.github.nmanuelides.mtgreforged.ui.components.parchment
import io.github.nmanuelides.mtgreforged.ui.theme.Cinzel
import io.github.nmanuelides.mtgreforged.ui.theme.Palette

sealed interface UpdateUi {
    data object None : UpdateUi
    data class Available(val name: String, val notes: String) : UpdateUi
    data class Downloading(val progress: Float) : UpdateUi
    data class Failed(val message: String) : UpdateUi
}

/** One of the poster's character panels: a piece of art in a coloured geometric frame. */
private data class Hero(val art: LocalArt, val color: Color, val symbol: String, val shape: Shape, val height: Float)

private val Arch = RoundedCornerShape(topStartPercent = 50, topEndPercent = 50)
private val Spire = GenericShape { size, _ ->
    moveTo(size.width / 2, 0f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}
private val Gable = GenericShape { size, _ ->
    moveTo(size.width / 2, 0f)
    lineTo(size.width, size.width * 0.5f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    lineTo(0f, size.width * 0.5f)
    close()
}

/** A fresh cast every launch: one wallpaper per colour, in the poster's five frames. */
private fun castHeroes() = listOf(
    Hero(LocalArt.red.random(), Palette.Terracotta, "R", Arch, 0.80f),
    Hero(LocalArt.blue.random(), Palette.Slate, "U", RoundedCornerShape(0.dp), 0.90f),
    Hero(LocalArt.green.random(), Palette.Sage, "G", Spire, 1.00f),
    Hero(LocalArt.white.random(), Palette.Ochre, "W", Gable, 0.90f),
    Hero(LocalArt.black.random(), Palette.Plum, "B", Arch, 0.80f),
)

/** Which deck slot the deck browser is filling: -1 for the player, 0..2 for opponents. */
typealias DeckSlot = Int

@Composable
fun HomeScreen(
    decksFor: (Format) -> List<DeckEntry>,
    update: UpdateUi,
    onInstallUpdate: () -> Unit,
    onBrowseDecks: (Format, DeckSlot, DeckEntry?) -> Unit,
    selections: Map<DeckSlot, DeckEntry>,
    onStart: (Format, DeckEntry, List<DeckEntry>) -> Unit,
    format: Format,
    onFormat: (Format) -> Unit,
) {
    var opponentCount by rememberSaveable { mutableStateOf(3) }
    var showRivals by remember { mutableStateOf(false) }
    val decks = decksFor(format)
    val mine = selections[-1]

    BoxWithConstraints(Modifier.fillMaxSize().parchment()) {
        val wide = maxWidth > maxHeight
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            UpdateBanner(update, onInstallUpdate)
            Title()
            val heroes = remember { castHeroes() }
            HeroBanner(if (wide) heroes else heroes.subList(1, 4), Modifier.fillMaxWidth().weight(1f))
            Spacer(Modifier.height(10.dp))
            PlayPlaque(
                "Play",
                onClick = {
                    if (decks.isEmpty()) return@PlayPlaque
                    onStart(
                        format,
                        mine ?: decks.random(),
                        (0 until opponentCount).map { selections[it] ?: decks.random() },
                    )
                },
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                enabled = decks.isNotEmpty(),
            )
            Spacer(Modifier.height(12.dp))

            val buttons: List<@Composable (Modifier) -> Unit> = listOf(
                { m -> OrnateButton(format.label, { onFormat(Format.entries[(format.ordinal + 1) % Format.entries.size]) }, m, icon = Icons.Default.Style) },
                { m -> OrnateButton("${opponentCount} opponent${if (opponentCount > 1) "s" else ""}", { opponentCount = opponentCount % 3 + 1 }, m, icon = Icons.Default.Groups, accent = Palette.Slate) },
                { m -> OrnateButton("Your deck", { onBrowseDecks(format, -1, mine) }, m, icon = Icons.Default.AutoStories, accent = Palette.Sage) },
                { m -> OrnateButton("Opponents", { showRivals = true }, m, icon = Icons.Default.Shield, accent = Palette.Ochre) },
            )
            if (wide) {
                Row(Modifier.widthIn(max = 1000.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    buttons.forEach { it(Modifier.weight(1f)) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    buttons.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { it(Modifier.weight(1f)) } }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Playing " + (mine?.name ?: "a random deck"),
                style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (showRivals) {
        RivalsDialog(
            count = opponentCount,
            selections = selections,
            onPick = { slot -> showRivals = false; onBrowseDecks(format, slot, selections[slot]) },
            onDismiss = { showRivals = false },
        )
    }
}

@Composable
private fun Title() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
        OrnateDivider(width = 260.dp)
        Text(
            "MTG Reforged",
            style = MaterialTheme.typography.displayLarge,
            color = Palette.Terracotta,
            textAlign = TextAlign.Center,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OrnateDivider(width = 80.dp)
            Text(
                "  Forge engine ${BuildConfig.VERSION_NAME.substringBefore('-')}  ",
                style = MaterialTheme.typography.labelSmall, color = Palette.InkSoft,
            )
            OrnateDivider(width = 80.dp)
        }
    }
}

@Composable
private fun HeroBanner(heroes: List<Hero>, modifier: Modifier) {
    Box(modifier.padding(vertical = 8.dp), contentAlignment = Alignment.BottomCenter) {
        // Pale sky arches behind the cast, like the poster's background.
        Canvas(Modifier.fillMaxSize()) {
            val sky = Color(0xFFBBCCDC)
            val w = size.width
            val h = size.height
            drawRoundRect(sky, Offset(w * 0.05f, h * 0.08f), Size(w * 0.32f, h), CornerRadius(w * 0.16f))
            drawRoundRect(sky, Offset(w * 0.63f, h * 0.02f), Size(w * 0.32f, h), CornerRadius(w * 0.16f))
        }
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            heroes.forEach { hero -> HeroPanel(hero, Modifier.weight(1f).fillMaxHeight(hero.height)) }
        }
    }
}

@Composable
private fun HeroPanel(hero: Hero, modifier: Modifier) {
    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(hero.shape)
                .background(hero.color)
                .border(2.dp, Palette.PaperLight, hero.shape)
                .padding(5.dp)
                .clip(hero.shape),
        ) {
            LocalArtImage(hero.art, Modifier.fillMaxSize())
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (hero.shape == Spire) 64.dp else 18.dp)
                .size(34.dp)
                .background(hero.color, CircleShape)
                .border(1.5.dp, Palette.PaperLight, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(hero.symbol, fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Palette.PaperLight)
        }
    }
}

@Composable
private fun UpdateBanner(update: UpdateUi, onInstall: () -> Unit) {
    when (update) {
        UpdateUi.None -> Unit
        is UpdateUi.Available -> Row(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .background(Palette.PaperLight)
                .ornateFrame(Palette.Terracotta)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(Icons.Default.SystemUpdate, null, tint = Palette.Terracotta)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("New engine version", style = MaterialTheme.typography.titleSmall, color = Palette.Terracotta)
                Text(update.name, style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
            }
            OrnateButton("Update", onInstall, style = OrnateStyle.Primary, height = 40.dp)
        }
        is UpdateUi.Downloading -> Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            Text("Downloading update…", style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(
                progress = { update.progress },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                color = Palette.Terracotta,
                trackColor = Palette.PaperDeep,
            )
        }
        is UpdateUi.Failed -> Text("Update failed: ${update.message}", color = Palette.Terracotta, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RivalsDialog(count: Int, selections: Map<DeckSlot, DeckEntry>, onPick: (DeckSlot) -> Unit, onDismiss: () -> Unit) {
    OrnateDialog(onDismiss = onDismiss) {
        SectionLabel("Opponents")
        Spacer(Modifier.height(12.dp))
        repeat(count) { i ->
            OrnateButton(
                "Opponent ${i + 1}: " + (selections[i]?.name ?: "random"),
                { onPick(i) },
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                accent = Palette.Slate,
                textStyle = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.height(8.dp))
        OrnateButton("Done", onDismiss, Modifier.fillMaxWidth(), style = OrnateStyle.Primary)
    }
}
