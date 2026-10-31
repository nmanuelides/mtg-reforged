package io.github.nmanuelides.mtgreforged.ui.decks

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import forge.item.PaperCard
import io.github.nmanuelides.mtgreforged.engine.CardImages
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * The cards still to buy as pictures to share: a grid of card images (with how many of each),
 * [PER_PAGE] cards a picture so the cards stay readable without the picture getting huge.
 */
internal object ShoppingImage {
    const val PER_PAGE = 15
    private const val COLUMNS = 5
    private const val CARD_W = 360
    private const val CARD_H = CARD_W * 88 / 63
    private const val GAP = 16
    private const val HEADER = 76
    private val background = Color(0xFF1E1B17)
    private val ink = Color(0xFFF2EBDD)
    private val badge = Color(0xFFB5523B)

    /** One picture per [PER_PAGE] cards, titled with [title]. Downloads missing card images. */
    suspend fun build(cards: List<Pair<PaperCard, Int>>, title: String, measurer: TextMeasurer): List<ImageBitmap> {
        val images = coroutineScope {
            cards.chunked(6).flatMap { batch -> batch.map { (card, _) -> async { CardImages.load(card.getImageKey(false)) } }.awaitAll() }
        }
        val pages = cards.indices.chunked(PER_PAGE)
        return pages.mapIndexed { page, indices ->
            val heading = if (pages.size > 1) "$title  (${page + 1}/${pages.size})" else title
            draw(indices.map { Triple(cards[it].first, cards[it].second, images[it]) }, heading, measurer)
        }
    }

    private fun draw(cards: List<Triple<PaperCard, Int, ImageBitmap?>>, title: String, measurer: TextMeasurer): ImageBitmap {
        val cols = cards.size.coerceAtMost(COLUMNS)
        val rows = (cards.size + cols - 1) / cols
        val width = cols * CARD_W + (cols + 1) * GAP
        val height = HEADER + rows * CARD_H + (rows + 1) * GAP
        val bitmap = ImageBitmap(width, height)
        // Density 1: sizes below are in pixels.
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawRect(background)
            val heading = measurer.measure(
                title, TextStyle(color = ink, fontSize = 34.sp, fontWeight = FontWeight.Bold), overflow = TextOverflow.Ellipsis,
                maxLines = 1, constraints = Constraints(maxWidth = width - 2 * GAP), density = Density(1f),
            )
            drawText(heading, topLeft = Offset(GAP.toFloat(), (HEADER + GAP - heading.size.height) / 2f))
            cards.forEachIndexed { i, (card, count, image) ->
                val x = GAP + (i % cols) * (CARD_W + GAP)
                val y = HEADER + GAP + (i / cols) * (CARD_H + GAP)
                val frame = Path().apply {
                    addRoundRect(RoundRect(x.toFloat(), y.toFloat(), (x + CARD_W).toFloat(), (y + CARD_H).toFloat(), CornerRadius(CARD_W * 0.045f)))
                }
                clipPath(frame) {
                    if (image != null) {
                        drawImage(
                            image, srcSize = IntSize(image.width, image.height), dstOffset = IntOffset(x, y),
                            dstSize = IntSize(CARD_W, CARD_H), filterQuality = FilterQuality.High,
                        )
                    } else {
                        // No image (offline): the name on a blank card.
                        drawRect(Color(0xFF3A352E), Offset(x.toFloat(), y.toFloat()), Size(CARD_W.toFloat(), CARD_H.toFloat()))
                        val name = measurer.measure(
                            card.name, TextStyle(color = ink, fontSize = 30.sp, fontWeight = FontWeight.Bold),
                            constraints = Constraints(maxWidth = CARD_W - 40), density = Density(1f),
                        )
                        drawText(name, topLeft = Offset(x + 20f, y + 24f))
                    }
                }
                if (count > 1) {
                    val label = measurer.measure(
                        "×$count", TextStyle(color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold), density = Density(1f),
                    )
                    val w = label.size.width + 28f
                    val h = label.size.height + 8f
                    val left = x + CARD_W - w - 12f
                    val top = y + CARD_H - h - 16f
                    drawRoundRect(badge, Offset(left, top), Size(w, h), CornerRadius(h / 2))
                    drawText(label, topLeft = Offset(left + 14f, top + 4f))
                }
            }
        }
        return bitmap
    }
}
