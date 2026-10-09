package io.github.nmanuelides.mtgreforged.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.nmanuelides.mtgreforged.R

/** Storybook parchment palette: paper, ink and a few earthy pigments. */
object Palette {
    val Paper = Color(0xFFF1E6CE)
    val PaperLight = Color(0xFFF7EEDC)
    val PaperDeep = Color(0xFFE6D5B3)
    val Ink = Color(0xFF3A2E26)
    val InkSoft = Color(0xFF7A6656)
    val Line = Color(0xFFC09A82)
    val LineStrong = Color(0xFFB0705A)

    val Terracotta = Color(0xFFB4472C)
    val Sage = Color(0xFF6F8A63)
    val Slate = Color(0xFF55779C)
    val Ochre = Color(0xFFC6963A)
    val Plum = Color(0xFF6E4E63)

    // Semantic names used across the app.
    val Background = Paper
    val Surface = PaperLight
    val SurfaceHigh = PaperDeep
    val Outline = Line
    val Ember = Terracotta
    val EmberSoft = Color(0x26B4472C)
    val Arcane = Slate
    val Life = Terracotta
    val Poison = Sage
    val TextPrimary = Ink
    val TextSecondary = InkSoft
    val Selectable = Color(0xFF4F8A55)
    val Attacking = Terracotta
    val Blocking = Slate

    // Mana colours, WUBRG + colourless, in the same muted pigments.
    val White = Color(0xFFF6EBC8)
    val Blue = Slate
    val Black = Plum
    val Red = Terracotta
    val Green = Sage
    val Colorless = Color(0xFFB9AE9C)
}

@OptIn(ExperimentalTextApi::class)
val Cinzel = FontFamily(
    Font(R.font.cinzel, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.cinzel, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.cinzel, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

@OptIn(ExperimentalTextApi::class)
val Garamond = FontFamily(
    Font(R.font.eb_garamond, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.eb_garamond, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.eb_garamond, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.eb_garamond, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private val scheme = lightColorScheme(
    primary = Palette.Terracotta,
    onPrimary = Palette.PaperLight,
    secondary = Palette.Sage,
    onSecondary = Palette.PaperLight,
    tertiary = Palette.Slate,
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.PaperLight,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.PaperDeep,
    onSurfaceVariant = Palette.InkSoft,
    surfaceContainer = Palette.PaperLight,
    surfaceContainerLow = Palette.PaperLight,
    surfaceContainerHigh = Palette.Paper,
    surfaceContainerHighest = Palette.PaperDeep,
    secondaryContainer = Palette.PaperDeep,
    onSecondaryContainer = Palette.Ink,
    outline = Palette.Line,
    outlineVariant = Palette.Line,
    error = Palette.Terracotta,
)

private val typography = Typography(
    displayLarge = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 56.sp, letterSpacing = 2.sp),
    headlineLarge = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, letterSpacing = 1.5.sp),
    headlineSmall = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = 1.sp),
    titleLarge = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = 1.sp),
    titleMedium = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.8.sp),
    titleSmall = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.8.sp),
    bodyLarge = TextStyle(fontFamily = Garamond, fontSize = 18.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Garamond, fontSize = 16.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Garamond, fontSize = 14.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 1.sp),
    labelMedium = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.8.sp),
    labelSmall = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 0.8.sp),
)

@Composable
fun MtgTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(LocalContentColor provides Palette.Ink, content = content)
    }
}
