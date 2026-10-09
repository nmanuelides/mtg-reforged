package io.github.nmanuelides.mtgreforged.ui.components

import android.content.Context
import androidx.annotation.DrawableRes
import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette as AndroidPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
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

/** Hands out wallpapers in turn for the landing, remembering where it left off between launches. */
object LandingArt {
    private const val PREFS = "landing"
    private const val KEY = "next"

    fun next(context: Context): LocalArt {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val index = prefs.getInt(KEY, 0) % LocalArt.entries.size
        prefs.edit().putInt(KEY, index + 1).apply()
        return LocalArt.entries[index]
    }
}

/**
 * The most vivid of the artwork's common colours, deepened just enough to read on the
 * cream paper, for text and ornaments that sit next to the art.
 */
@Composable
fun rememberArtAccent(art: LocalArt): Color {
    val resources = LocalContext.current.resources
    val accent by produceState(accentCache[art] ?: Palette.Terracotta, art) {
        value = accentCache[art] ?: withContext(Dispatchers.Default) {
            val bitmap = BitmapFactory.decodeResource(resources, art.res, BitmapFactory.Options().apply { inSampleSize = 8 })
            val swatches = AndroidPalette.from(bitmap).maximumColorCount(24).generate().swatches
            readableOnPaper(mostVividCommon(swatches) ?: Palette.Terracotta.toArgb())
        }.also { accentCache[art] = it }
    }
    return animateColorAsState(accent, tween(600), label = "accent").value
}

private val accentCache = HashMap<LocalArt, Color>()

/**
 * Among colours covering a fair share of the image, the most saturated one at a usable
 * lightness. The single most common colour is usually a dark or grey background.
 */
private fun mostVividCommon(swatches: List<AndroidPalette.Swatch>): Int? {
    val maxPopulation = swatches.maxOfOrNull { it.population } ?: return null
    return swatches
        .filter { it.population >= maxPopulation * 0.03f }
        .maxByOrNull { swatch ->
            val (_, saturation, lightness) = swatch.hsl
            // Favour saturation; penalise colours that are nearly black or nearly white.
            saturation * (1f - abs(lightness - 0.5f) * 1.6f).coerceAtLeast(0f) +
                0.05f * swatch.population / maxPopulation
        }
        ?.rgb
}

private fun readableOnPaper(rgb: Int): Color {
    val paper = Palette.Paper.toArgb()
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(rgb, it) }
    // Keep the hue, give it some body, and darken until it has text contrast on paper.
    hsl[1] = hsl[1].coerceAtLeast(0.45f)
    var color = ColorUtils.HSLToColor(hsl)
    // Title and Play are large type, so 3:1 contrast is enough and keeps the colour lively.
    while (ColorUtils.calculateContrast(color, paper) < 3.0 && hsl[2] > 0.05f) {
        hsl[2] -= 0.03f
        color = ColorUtils.HSLToColor(hsl)
    }
    return Color(color)
}
