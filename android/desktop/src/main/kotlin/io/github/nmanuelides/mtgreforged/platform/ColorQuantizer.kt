package io.github.nmanuelides.mtgreforged.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import io.github.nmanuelides.mtgreforged.ui.components.ColorMath
import java.util.PriorityQueue
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * androidx.palette's colour extraction (Palette.Builder + ColorCutQuantizer, default filter),
 * ported so the Windows app finds the same accent colours in the bundled artwork as Android.
 */
internal object ColorQuantizer {
    private const val WORD = 5
    private const val MASK = (1 shl WORD) - 1
    private const val RESIZE_AREA = 112 * 112

    fun swatches(image: ImageBitmap, maxColors: Int): List<Swatch> {
        val pixels = scaledPixels(image)
        val hist = IntArray(1 shl (WORD * 3))
        for (i in pixels.indices) {
            val q = quantize(pixels[i])
            pixels[i] = q
            hist[q]++
        }
        val hsl = FloatArray(3)
        var distinct = 0
        for (color in hist.indices) {
            if (hist[color] > 0 && ignored(approximate(color), hsl)) hist[color] = 0
            if (hist[color] > 0) distinct++
        }
        val colors = IntArray(distinct)
        var n = 0
        for (color in hist.indices) if (hist[color] > 0) colors[n++] = color

        if (distinct <= maxColors) return colors.map { swatch(approximate(it), hist[it]) }

        val queue = PriorityQueue<Box>(maxColors) { a, b -> b.volume - a.volume }
        queue.offer(Box(colors, hist, 0, distinct - 1))
        while (queue.size < maxColors) {
            val box = queue.poll()
            if (box != null && box.canSplit) {
                queue.offer(box.split())
                queue.offer(box)
            } else break
        }
        return queue.map { it.average() }.filterNot { ignored(it.rgb, it.hsl) }
    }

    /** Palette scales the image down (nearest neighbour) to about 112 × 112 before counting. */
    private fun scaledPixels(image: ImageBitmap): IntArray {
        val map = image.toPixelMap()
        val area = image.width * image.height
        val ratio = if (area > RESIZE_AREA) sqrt(RESIZE_AREA / area.toDouble()) else 1.0
        val w = ceil(image.width * ratio).toInt().coerceAtLeast(1)
        val h = ceil(image.height * ratio).toInt().coerceAtLeast(1)
        return IntArray(w * h) { i ->
            val x = ((i % w) * image.width / w).coerceAtMost(image.width - 1)
            val y = ((i / w) * image.height / h).coerceAtMost(image.height - 1)
            val c = map[x, y]
            (0xFF shl 24) or ((c.red * 255).roundToInt() shl 16) or ((c.green * 255).roundToInt() shl 8) or (c.blue * 255).roundToInt()
        }
    }

    private fun swatch(rgb: Int, population: Int) = Swatch(rgb, population, FloatArray(3).also { ColorMath.colorToHsl(rgb, it) })

    /** Palette's default filter: no near-black, near-white, or the "red I line" of skin tones. */
    private fun ignored(rgb: Int, hsl: FloatArray): Boolean {
        ColorMath.colorToHsl(rgb, hsl)
        return hsl[2] <= 0.05f || hsl[2] >= 0.95f || (hsl[0] in 10f..37f && hsl[1] <= 0.82f)
    }

    private fun width(value: Int, from: Int, to: Int): Int =
        (if (to > from) value shl (to - from) else value shr (from - to)) and ((1 shl to) - 1)

    private fun quantize(argb: Int): Int =
        (width(argb shr 16 and 0xFF, 8, WORD) shl (WORD * 2)) or (width(argb shr 8 and 0xFF, 8, WORD) shl WORD) or width(argb and 0xFF, 8, WORD)

    private fun red(c: Int) = c shr (WORD * 2) and MASK
    private fun green(c: Int) = c shr WORD and MASK
    private fun blue(c: Int) = c and MASK

    private fun approximate(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (width(r, WORD, 8) shl 16) or (width(g, WORD, 8) shl 8) or width(b, WORD, 8)

    private fun approximate(c: Int) = approximate(red(c), green(c), blue(c))

    private class Box(val colors: IntArray, val hist: IntArray, val lower: Int, var upper: Int) {
        var minR = 0; var maxR = 0; var minG = 0; var maxG = 0; var minB = 0; var maxB = 0
        var population = 0

        init { fit() }

        val volume get() = (maxR - minR + 1) * (maxG - minG + 1) * (maxB - minB + 1)
        val canSplit get() = upper + 1 - lower > 1

        fun fit() {
            minR = Int.MAX_VALUE; minG = Int.MAX_VALUE; minB = Int.MAX_VALUE
            maxR = Int.MIN_VALUE; maxG = Int.MIN_VALUE; maxB = Int.MIN_VALUE
            population = 0
            for (i in lower..upper) {
                val c = colors[i]
                population += hist[c]
                minR = minOf(minR, red(c)); maxR = maxOf(maxR, red(c))
                minG = minOf(minG, green(c)); maxG = maxOf(maxG, green(c))
                minB = minOf(minB, blue(c)); maxB = maxOf(maxB, blue(c))
            }
        }

        fun split(): Box {
            val at = splitPoint()
            val other = Box(colors, hist, at + 1, upper)
            upper = at
            fit()
            return other
        }

        private fun longest(): Int {
            val r = maxR - minR; val g = maxG - minG; val b = maxB - minB
            return if (r >= g && r >= b) 0 else if (g >= r && g >= b) 1 else 2
        }

        /** Sorts by the longest dimension (made the most significant bits) and cuts at the median. */
        private fun splitPoint(): Int {
            val dim = longest()
            swapSignificant(dim)
            colors.sort(lower, upper + 1)
            swapSignificant(dim)
            val mid = population / 2
            var count = 0
            for (i in lower..upper) {
                count += hist[colors[i]]
                if (count >= mid) return minOf(upper - 1, i)
            }
            return lower
        }

        private fun swapSignificant(dim: Int) {
            for (i in lower..upper) {
                val c = colors[i]
                colors[i] = when (dim) {
                    1 -> (green(c) shl (WORD * 2)) or (red(c) shl WORD) or blue(c)
                    2 -> (blue(c) shl (WORD * 2)) or (green(c) shl WORD) or red(c)
                    else -> c
                }
            }
        }

        fun average(): Swatch {
            var r = 0L; var g = 0L; var b = 0L; var total = 0
            for (i in lower..upper) {
                val c = colors[i]
                val p = hist[c]
                total += p
                r += p.toLong() * red(c); g += p.toLong() * green(c); b += p.toLong() * blue(c)
            }
            return swatch(approximate((r / total.toFloat()).roundToInt(), (g / total.toFloat()).roundToInt(), (b / total.toFloat()).roundToInt()), total)
        }
    }
}
