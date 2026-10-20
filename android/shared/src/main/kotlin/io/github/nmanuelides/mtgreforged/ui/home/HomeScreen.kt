package io.github.nmanuelides.mtgreforged.ui.home

import androidx.compose.material.icons.filled.Favorite
import io.github.nmanuelides.mtgreforged.engine.Engine
import io.github.nmanuelides.mtgreforged.engine.News
import io.github.nmanuelides.mtgreforged.engine.Favorites
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import io.github.nmanuelides.mtgreforged.engine.Profiles
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.runtime.collectAsState
import io.github.nmanuelides.mtgreforged.engine.ImageDownloader
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.CloudDownload
import io.github.nmanuelides.mtgreforged.ui.components.paperShadow
import io.github.nmanuelides.mtgreforged.ui.components.paper
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
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.ui.Modifier
import io.github.nmanuelides.mtgreforged.ui.theme.ThemeMode
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nmanuelides.mtgreforged.BuildConfig
import io.github.nmanuelides.mtgreforged.engine.Engine.DeckEntry
import io.github.nmanuelides.mtgreforged.engine.SavedGame
import io.github.nmanuelides.mtgreforged.engine.Engine.Format
import io.github.nmanuelides.mtgreforged.ui.components.LocalArt
import io.github.nmanuelides.mtgreforged.ui.components.LocalArtImage
import io.github.nmanuelides.mtgreforged.ui.components.rememberArtAccent
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
import io.github.nmanuelides.mtgreforged.platform.LocalAppContext

sealed interface UpdateUi {
    data object None : UpdateUi
    data class Available(val name: String, val notes: String) : UpdateUi
    data class Downloading(val progress: Float) : UpdateUi
    data class Failed(val message: String) : UpdateUi
}

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
    art: LocalArt,
    saved: SavedGame? = null,
    onContinue: () -> Unit = {},
    /** False while the boot window is still opening onto this screen. */
    chromeVisible: Boolean = true,
    onArtBounds: (Rect) -> Unit = {},
    onCollection: () -> Unit = {},
    onFriends: () -> Unit = {},
) {
    var opponentCount by rememberSaveable { mutableStateOf(3) }
    var showRivals by remember { mutableStateOf(false) }
    val decks = decksFor(format)
    val favoriteVersion by Favorites.version.collectAsState()
    // Your favourite deck is the default pick until you choose another.
    val mine = selections[-1] ?: remember(format, decks, favoriteVersion) { Favorites.of(format, decks) }

    // Title and Play take their colour from the artwork on show.
    val accent = rememberArtAccent(art)
    // Title and bottom bar settle in once the boot window has finished opening.
    val chrome by animateFloatAsState(if (chromeVisible) 1f else 0f, tween(550, easing = FastOutSlowInEasing), label = "chrome")

    var lookup by remember { mutableStateOf(false) }
    var pillBounds by remember { mutableStateOf<Rect?>(null) }
    // While the card lookup is open, the home screen blurs behind it (Android 12+).
    val blur by androidx.compose.animation.core.animateDpAsState(if (lookup) 18.dp else 0.dp, label = "blur")
    Box(Modifier.fillMaxSize()) {
    BoxWithConstraints(Modifier.fillMaxSize().blur(blur).parchment()) {
        val wide = maxWidth > maxHeight
        Column(Modifier.fillMaxSize()) {
            // Top ~3/4: the artwork edge to edge, the title on a cream plaque.
            Box(Modifier.fillMaxWidth().weight(1f)) {
                LocalArtImage(art, Modifier.fillMaxSize().onGloballyPositioned { onArtBounds(it.boundsInRoot()) })
                // Lower-right corner of the art: the background card load (right after start-up) and
                // a way to support the developer.
                val allCards by Engine.allCardsLoaded.collectAsState()
                var support by remember { mutableStateOf(false) }
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !allCards,
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut(),
                    ) {
                        Row(
                            Modifier.paperShadow(offsetY = 2.dp, blur = 8.dp).paper(Palette.Paper).padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(Modifier.size(14.dp), color = Palette.Terracotta, strokeWidth = 2.dp)
                            Text("Loading all cards…", color = Palette.InkSoft, fontSize = 13.sp)
                        }
                    }
                    Row(
                        Modifier
                            .paperShadow(offsetY = 3.dp, blur = 10.dp)
                            .paper(Palette.Paper)
                            .clickable { support = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = Palette.Terracotta, modifier = Modifier.size(18.dp))
                        Text("Support the developer", color = Palette.Ink, fontSize = 14.sp, fontFamily = io.github.nmanuelides.mtgreforged.ui.theme.Cinzel, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    }
                }
                if (support) SupportDialog { support = false }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 16.dp, start = 24.dp, end = 24.dp)
                        .graphicsLayer { alpha = chrome; translationY = (1f - chrome) * -40.dp.toPx() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    UpdateBanner(update, onInstallUpdate)
                    // Small paper tabs in the corner of the art: offline images and light / dark.
                    val context = LocalAppContext.current
                    var offline by remember { mutableStateOf(false) }
                    var backup by remember { mutableStateOf(false) }
                    var profile by remember { mutableStateOf(false) }
                    val downloading by ImageDownloader.progress.collectAsState()
                    Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Profile: your avatar (or initial) opens name and avatars.
                        AvatarImage(0, Profiles.name, Modifier.size(44.dp).shadow(6.dp, CircleShape).border(2.dp, accent, CircleShape).clickable { profile = true })
                        Box(
                            Modifier
                                .paperShadow(offsetY = 3.dp, blur = 10.dp)
                                .paper(Palette.Paper)
                                .clickable { backup = true }
                                .padding(10.dp),
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = "Backup & restore", tint = accent)
                        }
                        Box(
                            Modifier
                                .paperShadow(offsetY = 3.dp, blur = 10.dp)
                                .paper(Palette.Paper)
                                .clickable { offline = true }
                                .padding(10.dp),
                        ) {
                            Icon(
                                if (downloading.running) Icons.Default.Downloading else Icons.Default.CloudDownload,
                                contentDescription = "Offline card images",
                                tint = accent,
                            )
                        }
                        Box(
                            Modifier
                                .paperShadow(offsetY = 3.dp, blur = 10.dp)
                                .paper(Palette.Paper)
                                .clickable { ThemeMode.toggle(context) }
                                .padding(10.dp),
                        ) {
                            Icon(
                                if (ThemeMode.dark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = if (ThemeMode.dark) "Light theme" else "Dark theme",
                                tint = accent,
                            )
                        }
                    }
                    if (offline) OfflineImagesDialog { offline = false }
                    if (backup) BackupDialog { backup = false }
                    if (profile) ProfileDialog { profile = false }
                    // News from the developer: one message at a time, each shown once.
                    val news by News.unseen.collectAsState()
                    if (chromeVisible) news.firstOrNull()?.let { item ->
                        val uri = androidx.compose.ui.platform.LocalUriHandler.current
                        OrnateDialog(onDismiss = { News.markSeen(context, item) }, maxWidth = 560.dp) {
                            SectionLabel("News")
                            Spacer(Modifier.height(8.dp))
                            if (item.title.isNotBlank()) Text(item.title, style = MaterialTheme.typography.headlineSmall, color = Palette.Ink, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(8.dp))
                            Text(item.body, style = MaterialTheme.typography.bodyLarge, color = Palette.InkSoft, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                item.link?.let { link ->
                                    OrnateButton("Open link", { runCatching { uri.openUri(link) } }, Modifier.weight(1f), accent = Palette.Slate)
                                }
                                OrnateButton("OK", { News.markSeen(context, item) }, Modifier.weight(1f), style = OrnateStyle.Primary)
                            }
                        }
                    }
                    Box(
                        Modifier
                            .paperShadow(offsetY = 6.dp, blur = 16.dp)
                            .paper(Palette.Paper)
                            .padding(horizontal = 36.dp, vertical = 10.dp),
                    ) { Title(accent) }
                }
                // Card lookup, on the wallpaper right above Play.
                if (!lookup) {
                    CardSearchPill(
                        { lookup = true },
                        Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 18.dp)
                            .onGloballyPositioned { pillBounds = it.boundsInRoot() }
                            .graphicsLayer { alpha = chrome },
                    )
                }
            }

            // Bottom bar: a hard rule along its top edge, casting a shadow onto the art.
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = (1f - chrome) * size.height }
                    .paperShadow(offsetY = (-6).dp)
                    .parchment()
                    .drawBehind { drawRect(Palette.LineStrong, size = Size(size.width, 2.dp.toPx())) }
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (saved != null) {
                    // A paused match waits here until it is continued or overwritten.
                    OrnateButton(
                        "Continue · ${saved.summary}", onContinue,
                        Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(bottom = 10.dp),
                        accent = accent, frameColor = accent, height = 46.dp,
                        textStyle = MaterialTheme.typography.labelLarge,
                    )
                }
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
                    color = accent,
                )
                Spacer(Modifier.height(12.dp))

                val buttons: List<@Composable (Modifier) -> Unit> = listOf(
                    { m -> OrnateButton(format.label, { onFormat(Format.entries[(format.ordinal + 1) % Format.entries.size]) }, m, icon = Icons.Default.Style) },
                    { m -> OrnateButton("${opponentCount} opponent${if (opponentCount > 1) "s" else ""}", { opponentCount = opponentCount % 3 + 1 }, m, icon = Icons.Default.Groups, accent = Palette.Slate) },
                    { m -> OrnateButton("Decks", { onBrowseDecks(format, -1, mine) }, m, icon = Icons.Default.AutoStories, accent = Palette.Sage) },
                    { m -> OrnateButton("Opponents", { showRivals = true }, m, icon = Icons.Default.Shield, accent = Palette.Ochre) },
                    { m -> OrnateButton("Collection", onCollection, m, icon = Icons.Default.CollectionsBookmark, accent = Palette.Plum) },
                    { m -> OrnateButton("Friends", onFriends, m, icon = Icons.Default.Wifi, accent = Palette.Sage) },
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
    }

    if (lookup) CardLookupOverlay(pillBounds) { lookup = false }
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
private fun Title(color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
        OrnateDivider(width = 260.dp, color = color)
        Text(
            "MTG Reforged",
            style = MaterialTheme.typography.displayLarge,
            color = color,
            textAlign = TextAlign.Center,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OrnateDivider(width = 80.dp, color = color)
            Text(
                "  v${BuildConfig.VERSION_NAME} · Forge engine ${BuildConfig.ENGINE_VERSION}  ",
                style = MaterialTheme.typography.labelSmall, color = Palette.InkSoft,
            )
            OrnateDivider(width = 80.dp, color = color)
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
                .paper(Palette.PaperLight)
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
