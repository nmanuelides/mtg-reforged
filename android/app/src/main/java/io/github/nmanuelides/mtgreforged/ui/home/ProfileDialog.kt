package io.github.nmanuelides.mtgreforged.ui.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
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

/** A seat's round avatar, or its initial when there is none. */
@Composable
fun AvatarImage(seat: Int, fallback: String, modifier: Modifier) {
    val version by Profiles.version.collectAsState()
    val bitmap = remember(seat, version) { Profiles.avatar(seat)?.asImageBitmap() }
    Box(modifier.clip(CircleShape).background(Palette.PaperDeep), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(fallback.take(1).uppercase(), fontFamily = Cinzel, fontWeight = FontWeight.Bold, color = Palette.InkSoft)
    }
}

/** Your name, your avatar and the opponents' avatars. */
@Composable
fun ProfileDialog(onDismiss: () -> Unit) {
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
        OrnateButton("Done", { Profiles.name = name; onDismiss() }, Modifier.fillMaxWidth(), style = OrnateStyle.Primary)
    }
    editing?.let { seat -> AvatarEditor(seat) { editing = null } }
}

/** Pick a photo or any card's art, then drag / pinch it inside the circle and save. */
@Composable
private fun AvatarEditor(seat: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var searching by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            source = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } }.getOrNull()
            }
        }
    }

    OrnateDialog(onDismiss = onDismiss, maxWidth = 560.dp) {
        SectionLabel(if (seat == 0) "Your avatar" else "Opponent $seat avatar")
        Spacer(Modifier.height(12.dp))
        val image = source
        if (image == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OrnateButton("Photo", { pickFile.launch("image/*") }, Modifier.weight(1f), icon = Icons.Default.Image, accent = Palette.Slate)
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
    val names = remember { StaticData.instance().commonCards.uniqueCards.map { it.name }.filterNot { it.startsWith("A-") }.distinct().sorted() }
    val hits = remember(query) { if (query.length < 2) emptyList() else names.filter { query.lowercase() in it.lowercase() }.take(60) }
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
private fun Cropper(image: Bitmap, onCancel: () -> Unit, onSave: (Bitmap) -> Unit) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableFloatStateOf(1f) }
    val bitmap = remember(image) { image.asImageBitmap() }
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
            },
    ) {
        Image(
            bitmap, null,
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoom; scaleY = zoom
                translationX = offset.x; translationY = offset.y
            },
            contentScale = ContentScale.Crop,
        )
    }
    Spacer(Modifier.height(8.dp))
    Text("Drag to move · pinch to zoom", style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OrnateButton("Back", onCancel, Modifier.weight(1f), accent = Palette.Slate)
        OrnateButton("Save", { onSave(crop(image, viewport, zoom, offset)) }, Modifier.weight(1f), style = OrnateStyle.Primary)
    }
}

/** Renders what the circular viewport shows (cover-fit image, then zoom and pan) into 512px. */
private fun crop(image: Bitmap, viewport: Float, zoom: Float, offset: Offset): Bitmap {
    val out = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    val k = 512f / viewport
    val cover = maxOf(viewport / image.width, viewport / image.height)
    val m = android.graphics.Matrix().apply {
        // Cover-fit, centred in the viewport.
        postScale(cover, cover)
        postTranslate((viewport - image.width * cover) / 2f, (viewport - image.height * cover) / 2f)
        // graphicsLayer zooms around the centre, then translates.
        postScale(zoom, zoom, viewport / 2f, viewport / 2f)
        postTranslate(offset.x, offset.y)
        postScale(k, k)
    }
    canvas.drawBitmap(image, m, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
    return out
}
