package io.github.nmanuelides.mtgreforged.ui.components

import androidx.compose.runtime.collectAsState
import io.github.nmanuelides.mtgreforged.engine.Connectivity
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.github.nmanuelides.mtgreforged.engine.ArtImages

/** A card's illustration, cropped to fill; shows [placeholder] colour until it arrives. */
@Composable
fun ArtImage(
    cardName: String?,
    modifier: Modifier = Modifier,
    placeholder: Color = Color.Transparent,
    alignment: Alignment = Alignment.TopCenter,
) {
    val online by Connectivity.online.collectAsState()
    val image by produceState<ImageBitmap?>(cardName?.let(ArtImages::cached)?.asImageBitmap(), cardName, online) {
        value = cardName?.let(ArtImages::cached)?.asImageBitmap()
        if (cardName != null && value == null) value = ArtImages.load(cardName)?.asImageBitmap()
    }
    Box(modifier.background(placeholder)) {
        Crossfade(image, label = "art") { bitmap ->
            if (bitmap != null) {
                Image(bitmap, cardName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = alignment)
            }
        }
    }
}
