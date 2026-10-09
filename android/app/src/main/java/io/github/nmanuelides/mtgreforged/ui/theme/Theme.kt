package io.github.nmanuelides.mtgreforged.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    val Background = Color(0xFF0E0F14)
    val Surface = Color(0xFF171922)
    val SurfaceHigh = Color(0xFF20232F)
    val Outline = Color(0xFF2E3242)
    val Ember = Color(0xFFF0812A)
    val EmberSoft = Color(0x33F0812A)
    val Arcane = Color(0xFF7C9CFF)
    val Life = Color(0xFFE5484D)
    val Poison = Color(0xFF5BB974)
    val TextPrimary = Color(0xFFECEDF2)
    val TextSecondary = Color(0xFF9EA3B5)
    val Selectable = Color(0xFF3DD6C6)
    val Attacking = Color(0xFFE5484D)
    val Blocking = Color(0xFF7C9CFF)

    // Mana colours, WUBRG + colourless.
    val White = Color(0xFFF8F1D8)
    val Blue = Color(0xFF4FA3E0)
    val Black = Color(0xFF8B7F99)
    val Red = Color(0xFFE5604D)
    val Green = Color(0xFF4FB477)
    val Colorless = Color(0xFFB8B8C0)
}

private val scheme = darkColorScheme(
    primary = Palette.Ember,
    onPrimary = Color(0xFF1A0E04),
    secondary = Palette.Arcane,
    background = Palette.Background,
    onBackground = Palette.TextPrimary,
    surface = Palette.Surface,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextSecondary,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    surfaceContainerHighest = Palette.SurfaceHigh,
    outline = Palette.Outline,
    error = Palette.Life,
)

private val typography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = 0.5.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.4.sp),
)

@Composable
fun MtgTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography) {
        // Text drawn straight on our gradients (outside a Surface) must still be light.
        CompositionLocalProvider(LocalContentColor provides Palette.TextPrimary, content = content)
    }
}
