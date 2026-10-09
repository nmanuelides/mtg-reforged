package io.github.nmanuelides.mtgreforged.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import io.github.nmanuelides.mtgreforged.R
import io.github.nmanuelides.mtgreforged.ui.theme.Palette

/**
 * Bundled Magic wallpapers used as decorative art. [focusX]/[focusY] (0..1) mark the
 * subject, so narrow crops (poster panels, banners) keep it in frame.
 */
enum class LocalArt(@DrawableRes val res: Int, val color: Color, val focusX: Float, val focusY: Float) {
    Moraug(R.drawable.art_moraug, Palette.Terracotta, 0.52f, 0.30f),
    Rakdos(R.drawable.art_rakdos, Palette.Terracotta, 0.55f, 0.35f),
    Dragoncaller(R.drawable.art_dragoncaller, Palette.Terracotta, 0.42f, 0.50f),
    Tiamat(R.drawable.art_tiamat, Palette.Terracotta, 0.50f, 0.45f),
    DeadeyeNavigator(R.drawable.art_deadeye_navigator, Palette.Slate, 0.50f, 0.35f),
    Kozilek(R.drawable.art_kozilek, Palette.Slate, 0.50f, 0.30f),
    CalamityOfTheTitans(R.drawable.art_calamity_titans, Palette.Slate, 0.55f, 0.40f),
    Ashiok(R.drawable.art_ashiok, Palette.Plum, 0.45f, 0.35f),
    Nazgul(R.drawable.art_nazgul, Palette.Plum, 0.42f, 0.40f),
    Dracula(R.drawable.art_dracula, Palette.Plum, 0.55f, 0.30f),
    Serra(R.drawable.art_serra, Palette.Ochre, 0.50f, 0.25f),
    EleshNorn(R.drawable.art_elesh_norn, Palette.Ochre, 0.48f, 0.30f),
    HulkingRaptor(R.drawable.art_hulking_raptor, Palette.Sage, 0.35f, 0.40f),
    SolRing(R.drawable.art_sol_ring, Palette.Line, 0.50f, 0.50f);

    val alignment get() = BiasAlignment(focusX * 2 - 1, focusY * 2 - 1)

    companion object {
        val red = listOf(Moraug, Rakdos, Dragoncaller, Tiamat)
        val blue = listOf(DeadeyeNavigator, Kozilek, CalamityOfTheTitans)
        val black = listOf(Ashiok, Nazgul, Dracula)
        val white = listOf(Serra, EleshNorn)
        val green = listOf(HulkingRaptor)
    }
}

@Composable
fun LocalArtImage(art: LocalArt, modifier: Modifier = Modifier) {
    Image(
        painterResource(art.res),
        contentDescription = art.name,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        alignment = art.alignment,
    )
}
