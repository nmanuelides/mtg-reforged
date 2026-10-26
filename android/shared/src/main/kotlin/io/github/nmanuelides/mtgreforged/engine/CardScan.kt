package io.github.nmanuelides.mtgreforged.engine

import forge.StaticData
import forge.item.PaperCard
import java.text.Normalizer

/** One line of text the camera read, and how far down the picture it sits (0 top, 1 bottom). */
data class ScannedLine(val text: String, val y: Float)

/** A card recognised in a frame; [nameRead] is false when only the set code and number were read. */
class ScanHit(val card: PaperCard, val nameRead: Boolean)

/** A set to scan from: code and name, newest first in [CardScan.sets]. */
class ScanSet(val code: String, val name: String)

/**
 * Turns the text the camera reads into a card printing. The name (top line) says which card;
 * the set code and collector number printed at the bottom (cards from 2014 on) say which
 * printing. Names are matched loosely, so a misread letter or two still finds the card.
 */
object CardScan {
    private class Index(
        /** Normalised name (and each half of split cards) to the card's name. */
        val byName: Map<String, String>,
        val byLength: Map<Int, List<Pair<String, String>>>,
        /** "SET#number" (Forge's code and the printed one) to that printing. */
        val byNumber: Map<String, PaperCard>,
        /** Each set's card names, normalised, for scanning a single set. */
        val bySet: Map<String, List<Pair<String, String>>>,
    )

    @Volatile private var index: Index? = null

    /** Builds the lookup tables (once; needs every card loaded). */
    fun prepare() {
        if (index == null) synchronized(this) { if (index == null) index = build() }
    }

    private fun build(): Index {
        val db = StaticData.instance()
        val cards = db.commonCards.allCards.filterNot { it.name.startsWith("A-") }
        val byName = HashMap<String, String>()
        cards.mapTo(HashSet()) { it.name }.forEach { name ->
            byName[norm(name)] = name
            if (" // " in name) name.split(" // ").forEach { byName.putIfAbsent(norm(it), name) }
        }
        val byNumber = HashMap<String, PaperCard>()
        val bySet = HashMap<String, HashMap<String, String>>()
        cards.forEach { c ->
            val edition = db.getCardEdition(c.edition)
            val codes = listOfNotNull(c.edition, edition?.code2, edition?.scryfallCode).map { it.uppercase() }.distinct()
            c.collectorNumber?.takeIf { it.isNotBlank() }?.let { n -> codes.forEach { byNumber.putIfAbsent("$it#${numKey(n)}", c) } }
            val names = bySet.getOrPut(c.edition.uppercase()) { HashMap() }
            names[norm(c.name)] = c.name
            if (" // " in c.name) c.name.split(" // ").forEach { names.putIfAbsent(norm(it), c.name) }
        }
        return Index(
            byName,
            byName.entries.groupBy({ it.key.length }, { it.key to it.value }),
            byNumber,
            bySet.mapValues { (_, m) -> m.entries.map { it.key to it.value } },
        )
    }

    /** Sets worth scanning from (no online-only or joke sets), newest first. */
    fun sets(): List<ScanSet> {
        val db = StaticData.instance()
        val withCards = index?.bySet?.keys ?: emptySet()
        return db.editions
            .filter { it.code.uppercase() in withCards && it.type.name !in SKIPPED_TYPES }
            .sortedByDescending { it.date?.time ?: 0L }
            .map { ScanSet(it.code, it.name) }
    }

    /** What the lines of one frame show, if anything; [set] limits it to that set's cards. */
    fun read(lines: List<ScannedLine>, set: String?): ScanHit? {
        val idx = index ?: return null
        val code = set?.uppercase() ?: setCode(lines)
        val byNumber = code?.let { c -> number(lines)?.let { idx.byNumber["$c#$it"] } }
        // The name is printed along the top; lower lines are rules text that may mention other cards.
        val name = name(lines.filter { it.y < 0.6f }.sortedBy { it.y }, idx, set?.uppercase())
        return when {
            byNumber != null && (name == null || name == byNumber.name) -> ScanHit(byNumber, name != null)
            name != null -> defaultPrinting(name, set)?.let { ScanHit(it, true) }
            else -> null
        }
    }

    /** The printing to start from when only the name was read: from [set] if given, else the newest regular one. */
    private fun defaultPrinting(name: String, set: String?): PaperCard? {
        val db = StaticData.instance()
        val all = runCatching { db.commonCards.getAllCards(name) }.getOrNull().orEmpty()
        if (set != null) all.firstOrNull { it.edition.equals(set, ignoreCase = true) }?.let { return it }
        val dated = all.sortedByDescending { db.getCardEdition(it.edition)?.date?.time ?: 0L }
        return dated.firstOrNull { db.getCardEdition(it.edition)?.type?.name !in SKIPPED_TYPES } ?: dated.firstOrNull()
    }

    /** The topmost line that is (or nearly is) a card name. */
    private fun name(lines: List<ScannedLine>, idx: Index, set: String?): String? {
        for (line in lines) {
            val n = norm(line.text)
            if (n.length < 3) continue
            if (set == null) idx.byName[n]?.let { return it }
            val pool = if (set != null) idx.bySet[set].orEmpty()
            else (n.length - 2..n.length + 2).flatMap { idx.byLength[it].orEmpty() }
            // Within one set a looser match is still unambiguous.
            val allowed = if (set != null) maxOf(2, n.length / 4) else maxOf(1, n.length / 7)
            var best: String? = null
            var bestDistance = allowed + 1
            for ((key, real) in pool) {
                if (kotlin.math.abs(key.length - n.length) > allowed) continue
                val d = distance(n, key, bestDistance)
                if (d < bestDistance) { bestDistance = d; best = real; if (d == 0) break }
            }
            if (best != null) return best
        }
        return null
    }

    /** The collector number from the bottom line ("R 0090", "0090 R" or the older "123/249"). */
    private fun number(lines: List<ScannedLine>): String? {
        for (line in lines.sortedByDescending { it.y }) {
            val t = line.text.trim()
            RARITY_NUMBER.find(t)?.let { return numKey(it.groupValues[1]) }
            NUMBER_RARITY.find(t)?.let { return numKey(it.groupValues[1]) }
            OF_TOTAL.find(t)?.let { return numKey(it.groupValues[1]) }
        }
        return null
    }

    /** The set code from the bottom line ("FRA • EN"). */
    private fun setCode(lines: List<ScannedLine>): String? {
        for (line in lines.sortedByDescending { it.y }) {
            SET_LANGUAGE.find(line.text.trim())?.let { return it.groupValues[1].uppercase() }
        }
        return null
    }

    private val RARITY_NUMBER = Regex("""^[CURMLSTP]\s+0*(\d{1,4}[a-z]?)\b""")
    private val NUMBER_RARITY = Regex("""^0*(\d{1,4}[a-z]?)\s+[CURMLSTP]\b""")
    private val OF_TOTAL = Regex("""\b0*(\d{1,4})\s*/\s*\d{1,4}\b""")
    private val SET_LANGUAGE = Regex("""\b([A-Z0-9]{3,5})\s*[•·★*.\-]?\s*(EN|ES|FR|DE|IT|PT|JA|JP|KO|RU|ZH|CS|CT|PH)\b""")
    private val SKIPPED_TYPES = setOf("ONLINE", "FUNNY", "THIRDPARTY", "CUSTOM_SET")

    private fun numKey(n: String) = n.trim().lowercase().trimStart('0').ifEmpty { "0" }

    /** Lowercase letters and digits only, accents dropped: what survives a camera read. */
    private fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

    /** Edit distance, giving up (returning [limit]) once it can't come in under it. */
    private fun distance(a: String, b: String, limit: Int): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                rowMin = minOf(rowMin, cur[j])
            }
            if (rowMin >= limit) return limit
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
