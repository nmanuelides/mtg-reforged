package io.github.nmanuelides.mtgreforged.ui.home

import io.github.nmanuelides.mtgreforged.BuildConfig
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Refresh
import io.github.nmanuelides.mtgreforged.engine.Engine
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import forge.StaticData
import io.github.nmanuelides.mtgreforged.engine.ArtImages
import io.github.nmanuelides.mtgreforged.engine.Profiles
import io.github.nmanuelides.mtgreforged.ui.components.OrnateButton
import io.github.nmanuelides.mtgreforged.ui.components.OrnateDialog
import io.github.nmanuelides.mtgreforged.ui.components.OrnateStyle
import io.github.nmanuelides.mtgreforged.ui.components.SectionLabel
import io.github.nmanuelides.mtgreforged.ui.decks.fieldColors
import io.github.nmanuelides.mtgreforged.ui.theme.Cinzel
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.nmanuelides.mtgreforged.platform.IS_DESKTOP
import io.github.nmanuelides.mtgreforged.platform.decodeImageStream
import io.github.nmanuelides.mtgreforged.platform.rememberOpenDocument

/** A seat's round avatar, or its initial when there is none. */
@Composable
fun AvatarImage(seat: Int, fallback: String, modifier: Modifier) {
    val version by Profiles.version.collectAsState()
    val bitmap = remember(seat, version) { Profiles.avatar(seat) }
    Box(modifier.clip(CircleShape).background(Palette.PaperDeep), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(fallback.take(1).uppercase(), fontFamily = Cinzel, fontWeight = FontWeight.Bold, color = Palette.InkSoft)
    }
}

/** Your name, your avatar and the opponents' avatars. */
@Composable
fun ProfileDialog(onCheckUpdate: suspend () -> UpdateUi, onInstallUpdate: () -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(Profiles.name) }
    var editing by remember { mutableStateOf<Int?>(null) }
    OrnateDialog(onDismiss = { Profiles.name = name; onDismiss() }, maxWidth = 620.dp) {
        SectionLabel("Profile")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AvatarImage(0, name, Modifier.size(84.dp).border(2.dp, Palette.Terracotta, CircleShape).clickable { editing = 0 })
            OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Your name") }, colors = fieldColors(), modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Text("Opponents' avatars (in turn order)", style = MaterialTheme.typography.labelMedium, color = Palette.InkSoft)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            (1 until Profiles.SEATS).forEach { seat ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AvatarImage(seat, "$seat", Modifier.size(64.dp).border(2.dp, Palette.Slate, CircleShape).clickable { editing = seat })
                    Text("Opponent $seat", style = MaterialTheme.typography.labelSmall, color = Palette.InkSoft)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        UpdateCheck(onCheckUpdate, onInstallUpdate)
        Spacer(Modifier.height(16.dp))
        OrnateButton("Done", { Profiles.name = name; onDismiss() }, Modifier.fillMaxWidth(), style = OrnateStyle.Primary)
    }
    editing?.let { seat -> AvatarEditor(seat) { editing = null } }
}

/** Pick a photo or any card's art, then drag / pinch it inside the circle and save. */
@Composable
private fun AvatarEditor(seat: Int, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<ImageBitmap?>(null) }
    var searching by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val pickFile = rememberOpenDocument { open ->
        if (open != null) scope.launch {
            source = withContext(Dispatchers.IO) { runCatching { open()?.use { decodeImageStream(it) } }.getOrNull() }
        }
    }

    OrnateDialog(onDismiss = onDismiss, maxWidth = 560.dp) {
        SectionLabel(if (seat == 0) "Your avatar" else "Opponent $seat avatar")
        Spacer(Modifier.height(12.dp))
        val image = source
        if (image == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OrnateButton("Photo", { pickFile(arrayOf("image/*")) }, Modifier.weight(1f), icon = Icons.Default.Image, accent = Palette.Slate)
                OrnateButton("A card", { searching = true }, Modifier.weight(1f), icon = Icons.Default.Search, accent = Palette.Sage)
            }
            if (loading) { Spacer(Modifier.height(12.dp)); CircularProgressIndicator(color = Palette.Terracotta) }
            if (Profiles.avatar(seat) != null) {
                Spacer(Modifier.height(12.dp))
                OrnateButton("Remove avatar", { Profiles.setAvatar(seat, null); onDismiss() }, Modifier.fillMaxWidth(), accent = Palette.Terracotta)
            }
        } else {
            Cropper(image, onCancel = { source = null }) { cropped -> Profiles.setAvatar(seat, cropped); onDismiss() }
        }
    }
    if (searching) {
        CardArtPicker(onDismiss = { searching = false }) { cardName ->
            searching = false
            loading = true
            scope.launch {
                source = withContext(Dispatchers.IO) { runCatching { ArtImages.load(cardName) }.getOrNull() }
                loading = false
            }
        }
    }
}

@Composable
private fun CardArtPicker(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    // Every card name, once the background card load is done (right after start-up it may still be running).
    val names by produceState(emptyList<String>()) {
        value = withContext(Dispatchers.Default) {
            Engine.awaitAllCards()
            StaticData.instance().commonCards.uniqueCards.map { it.name }.filterNot { it.startsWith("A-") }.distinct().sorted()
        }
    }
    val hits = remember(query, names) { if (query.length < 2) emptyList() else names.filter { query.lowercase() in it.lowercase() }.take(60) }
    OrnateDialog(onDismiss = onDismiss, maxWidth = 560.dp) {
        SectionLabel("Choose a card")
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            query, { query = it }, singleLine = true, colors = fieldColors(),
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Palette.InkSoft) },
            placeholder = { Text("Card name") }, modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(Modifier.fillMaxWidth().height(380.dp).padding(top = 8.dp)) {
            items(hits) { n ->
                Text(n, Modifier.fillMaxWidth().clickable { onPick(n) }.padding(vertical = 11.dp, horizontal = 4.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Drag to move, pinch to zoom; the circle shows exactly what the avatar will be. */
@Composable
private fun Cropper(image: ImageBitmap, onCancel: () -> Unit, onSave: (ImageBitmap) -> Unit) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableFloatStateOf(1f) }
    Box(
        Modifier
            .size(280.dp)
            .clip(CircleShape)
            .border(3.dp, Palette.Terracotta, CircleShape)
            .background(Palette.PaperDeep)
            .onSizeChanged { viewport = it.width.toFloat() }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    zoom = (zoom * gestureZoom).coerceIn(1f, 8f)
                    offset += pan
                }
            }
            // Mouse wheel zooms too.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Scroll) continue
                        val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                        if (dy != 0f) zoom = (zoom * if (dy < 0) 1.1f else 1f / 1.1f).coerceIn(1f, 8f)
                        event.changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        Image(
            image, null,
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoom; scaleY = zoom
                translationX = offset.x; translationY = offset.y
            },
            contentScale = ContentScale.Crop,
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(if (IS_DESKTOP) "Drag to move · scroll to zoom" else "Drag to move · pinch to zoom", style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OrnateButton("Back", onCancel, Modifier.weight(1f), accent = Palette.Slate)
        OrnateButton("Save", { onSave(crop(image, viewport, zoom, offset)) }, Modifier.weight(1f), style = OrnateStyle.Primary)
    }
}

/** Renders what the circular viewport shows (cover-fit image, then zoom and pan) into 512px. */
private fun crop(image: ImageBitmap, viewport: Float, zoom: Float, offset: Offset): ImageBitmap {
    val out = ImageBitmap(512, 512)
    val k = 512f / viewport
    // Cover-fit, centred in the viewport; graphicsLayer then zooms around the centre and
    // translates; finally the viewport is scaled to 512px. All of it is one scale + move.
    val cover = maxOf(viewport / image.width, viewport / image.height)
    val centre = viewport / 2f
    val scale = k * zoom * cover
    val left = k * (zoom * ((viewport - image.width * cover) / 2f - centre) + centre + offset.x)
    val top = k * (zoom * ((viewport - image.height * cover) / 2f - centre) + centre + offset.y)
    Canvas(out).drawImageRect(
        image,
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize((image.width * scale).roundToInt(), (image.height * scale).roundToInt()),
        paint = Paint().apply { filterQuality = FilterQuality.High },
    )
    return out
}

/** The app's version, and a button that looks for a newer one right now. */
@Composable
private fun UpdateCheck(onCheckUpdate: suspend () -> UpdateUi, onInstallUpdate: () -> Unit) {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<UpdateUi?>(null) }
    Text("App version", style = MaterialTheme.typography.labelMedium, color = Palette.InkSoft)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium, color = Palette.Ink, modifier = Modifier.weight(1f))
        if (checking) CircularProgressIndicator(Modifier.size(22.dp), color = Palette.Terracotta, strokeWidth = 2.dp)
        OrnateButton(
            "Check for updates",
            { checking = true; scope.launch { result = onCheckUpdate(); checking = false } },
            icon = Icons.Default.Refresh, enabled = !checking, height = 42.dp,
        )
    }
    when (val r = result) {
        is UpdateUi.Available -> {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${r.name} is available.", style = MaterialTheme.typography.bodyMedium, color = Palette.Sage, modifier = Modifier.weight(1f))
                OrnateButton("Install", onInstallUpdate, icon = Icons.Default.SystemUpdate, style = OrnateStyle.Primary, height = 42.dp)
            }
        }
        UpdateUi.None -> {
            Spacer(Modifier.height(8.dp))
            Text("You have the latest version.", style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
        }
        is UpdateUi.Downloading -> {
            Spacer(Modifier.height(8.dp))
            Text("Downloading the update… ${(r.progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
        }
        is UpdateUi.Failed -> {
            Spacer(Modifier.height(8.dp))
            Text("Couldn't update: ${r.message}", style = MaterialTheme.typography.bodyMedium, color = Palette.Terracotta)
        }
        null -> {}
    }
}
