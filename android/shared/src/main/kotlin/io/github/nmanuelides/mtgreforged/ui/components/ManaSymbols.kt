package io.github.nmanuelides.mtgreforged.ui.components

import io.github.nmanuelides.mtgreforged.ui.theme.Cinzel
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import forge.card.MagicColor
import io.github.nmanuelides.mtgreforged.R
import io.github.nmanuelides.mtgreforged.ui.theme.Palette
import io.github.nmanuelides.mtgreforged.platform.ResId
import io.github.nmanuelides.mtgreforged.platform.painterRes

/**
 * Mana symbols in the storybook palette: Forge's own glyphs (from its mana icon sprite)
 * on pastel discs instead of the saturated printed colours.
 */
enum class ManaPip(val glyph: ResId, val disc: Color, val label: String) {
    White(R.drawable.mana_w, Color(0xFFEFE6C8), "White"),
    Blue(R.drawable.mana_u, Color(0xFFB9D3E6), "Blue"),
    Black(R.drawable.mana_b, Color(0xFFC9BFC4), "Black"),
    Red(R.drawable.mana_r, Color(0xFFEDB79C), "Red"),
    Green(R.drawable.mana_g, Color(0xFFB8D2AE), "Green"),
    Colorless(R.drawable.mana_c, Color(0xFFD9D2C5), "Colorless");

    companion object {
        val WUBRG = listOf(White, Blue, Black, Red, Green)

        fun of(color: Byte): ManaPip = when (color) {
            MagicColor.WHITE -> White
            MagicColor.BLUE -> Blue
            MagicColor.BLACK -> Black
            MagicColor.RED -> Red
            MagicColor.GREEN -> Green
            else -> Colorless
        }
    }

    val color: Byte
        get() = when (this) {
            White -> MagicColor.WHITE
            Blue -> MagicColor.BLUE
            Black -> MagicColor.BLACK
            Red -> MagicColor.RED
            Green -> MagicColor.GREEN
            Colorless -> MagicColor.COLORLESS
        }
}

/** Ink for the glyphs: always dark, since the discs stay light in both themes. */
private val GlyphInk = Color(0xFF3B2A1E)

/** A mana symbol on its pastel disc. */
@Composable
fun ManaSymbol(pip: ManaPip, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).background(pip.disc, CircleShape).border(1.dp, GlyphInk.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterRes(pip.glyph), pip.label,
            Modifier.size(size * 0.82f),
            colorFilter = ColorFilter.tint(GlyphInk),
        )
    }
}

/** Toggle chip for a colour filter: faded when off, full colour and lifted when on. */
@Composable
fun ManaChip(pip: ManaPip, selected: Boolean, size: Dp = 32.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(2.dp)
            .shadow(if (selected) 4.dp else 0.dp, CircleShape)
            .alpha(if (selected) 1f else 0.5f)
            .clickable(onClick = onClick),
    ) {
        ManaSymbol(pip, size)
    }
}

// Android's ICU regex needs the closing brace escaped too.
private val SHARD = Regex("""\{([^}]+)\}""")

/** A mana cost like "{4}{W}{W}" drawn as symbols: generic amounts on a stone disc, colours as pips. */
@Composable
fun ManaCost(cost: String, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    val symbols = remember(cost) { SHARD.findAll(cost).map { it.groupValues[1].uppercase() }.toList() }
    if (symbols.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        symbols.forEach { s ->
            val pip = when (s) {
                "W" -> ManaPip.White
                "U" -> ManaPip.Blue
                "B" -> ManaPip.Black
                "R" -> ManaPip.Red
                "G" -> ManaPip.Green
                "C" -> ManaPip.Colorless
                else -> null
            }
            if (pip != null) ManaSymbol(pip, size) else GenericSymbol(s, size)
        }
    }
}

/** Generic, X, hybrid and other non-colour symbols: their text on a stone disc. */
@Composable
private fun GenericSymbol(text: String, size: Dp) {
    val label = text.replace("/", "")
    Box(
        Modifier.size(size).background(ManaPip.Colorless.disc, CircleShape).border(1.dp, GlyphInk.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = Cinzel, fontWeight = FontWeight.Bold, color = GlyphInk,
            fontSize = (size.value * if (label.length > 1) 0.42f else 0.6f).sp,
            lineHeight = (size.value * 0.6f).sp,
        )
    }
}
