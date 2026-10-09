package io.github.nmanuelides.mtgreforged.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.Context
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

/** Light (parchment) or dark (deep brown and toasted sand); read by every [Palette] colour. */
object ThemeMode {
    private const val PREFS = "theme"
    private const val KEY = "dark"

    var dark by mutableStateOf(false)
        private set

    /** Uses the saved choice, or the system setting if the player never picked one. */
    fun init(context: Context, systemDark: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        dark = if (prefs.contains(KEY)) prefs.getBoolean(KEY, false) else systemDark
    }

    fun toggle(context: Context) {
        dark = !dark
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, dark).apply()
    }
}

private fun pick(light: Long, dark: Long) = if (ThemeMode.dark) Color(dark) else Color(light)

/**
 * Storybook palette: paper, ink and a few earthy pigments. Every colour is a getter that
 * follows [ThemeMode], so switching themes recomposes the whole app with no other wiring.
 */
object Palette {
    val Paper get() = pick(0xFFF1E6CE, 0xFF2B221C)
    val PaperLight get() = pick(0xFFF7EEDC, 0xFF3A2E25)
    val PaperDeep get() = pick(0xFFE6D5B3, 0xFF221A15)
    val Ink get() = pick(0xFF3A2E26, 0xFFEADBC0)
    val InkSoft get() = pick(0xFF7A6656, 0xFFB9A587)
    val Line get() = pick(0xFFC09A82, 0xFF6E5745)
    val LineStrong get() = pick(0xFFB0705A, 0xFFB4876A)

    val Terracotta get() = pick(0xFFB4472C, 0xFFD2694A)
    val Sage get() = pick(0xFF6F8A63, 0xFF93AE84)
    val Slate get() = pick(0xFF55779C, 0xFF84A3C6)
    val Ochre get() = pick(0xFFC6963A, 0xFFDDB15C)
    val Plum get() = pick(0xFF6E4E63, 0xFFA77F99)

    // Semantic names used across the app.
    val Background get() = Paper
    val Surface get() = PaperLight
    val SurfaceHigh get() = PaperDeep
    val Outline get() = Line
    val Ember get() = Terracotta
    val EmberSoft get() = Terracotta.copy(alpha = 0.15f)
    val Arcane get() = Slate
    val Life get() = Terracotta
    val Poison get() = Sage
    val TextPrimary get() = Ink
    val TextSecondary get() = InkSoft
    val Selectable get() = pick(0xFF4F8A55, 0xFF78C27D)
    val Attacking get() = Terracotta
    val Blocking get() = Slate

    /** Text on a coloured (primary) plaque: always light so it reads in both themes. */
    val OnAccent get() = Color(0xFFF7EEDC)

    // Mana colours, WUBRG + colourless, in the same muted pigments.
    val White get() = Color(0xFFF6EBC8)
    val Blue get() = Slate
    val Black get() = Plum
    val Red get() = Terracotta
    val Green get() = Sage
    val Colorless get() = Color(0xFFB9AE9C)
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

private fun scheme() = (if (ThemeMode.dark) darkColorScheme() else lightColorScheme()).copy(
    primary = Palette.Terracotta,
    onPrimary = Palette.OnAccent,
    secondary = Palette.Sage,
    onSecondary = Palette.OnAccent,
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
    MaterialTheme(colorScheme = scheme(), typography = typography) {
        CompositionLocalProvider(LocalContentColor provides Palette.Ink, content = content)
    }
}
