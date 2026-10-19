package io.github.nmanuelides.mtgreforged.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.roundToInt

/** androidx.core's ColorUtils maths (HSL and WCAG contrast), so every platform gets the same colours. */
internal object ColorMath {
    fun colorToHsl(argb: Int, out: FloatArray) {
        val rf = (argb shr 16 and 0xFF) / 255f
        val gf = (argb shr 8 and 0xFF) / 255f
        val bf = (argb and 0xFF) / 255f
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val delta = max - min
        val l = (max + min) / 2f
        var h: Float
        val s: Float
        if (max == min) {
            h = 0f; s = 0f
        } else {
            h = when (max) {
                rf -> ((gf - bf) / delta) % 6f
                gf -> (bf - rf) / delta + 2f
                else -> (rf - gf) / delta + 4f
            }
            s = delta / (1f - abs(2f * l - 1f))
        }
        h = (h * 60f) % 360f
        if (h < 0) h += 360f
        out[0] = h.coerceIn(0f, 360f)
        out[1] = s.coerceIn(0f, 1f)
        out[2] = l.coerceIn(0f, 1f)
    }

    fun hslToColor(hsl: FloatArray): Int {
        val (h, s, l) = hsl
        val c = (1f - abs(2 * l - 1f)) * s
        val m = l - 0.5f * c
        val x = c * (1f - abs((h / 60f % 2f) - 1f))
        fun v(f: Float) = (255 * f).roundToInt().coerceIn(0, 255)
        val (r, g, b) = when (h.toInt() / 60) {
            0 -> Triple(v(c + m), v(x + m), v(m))
            1 -> Triple(v(x + m), v(c + m), v(m))
            2 -> Triple(v(m), v(c + m), v(x + m))
            3 -> Triple(v(m), v(x + m), v(c + m))
            4 -> Triple(v(x + m), v(m), v(c + m))
            5, 6 -> Triple(v(c + m), v(m), v(x + m))
            else -> Triple(0, 0, 0)
        }
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** WCAG contrast ratio of two opaque colours. */
    fun contrast(a: Int, b: Int): Double {
        val la = Color(a).luminance() + 0.05
        val lb = Color(b).luminance() + 0.05
        return maxOf(la, lb) / minOf(la, lb)
    }
}
