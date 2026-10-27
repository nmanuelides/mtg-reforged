package io.github.nmanuelides.mtgreforged.engine

import forge.StaticData
import forge.item.PaperCard
import forge.localinstance.properties.ForgeConstants
import java.io.File
import java.text.Normalizer
import kotlin.math.abs

/**
 * One line of text the camera read: how far down the picture it sits (0 top, 1 bottom) and
 * how tall its letters are, both as a share of the picture's height.
 */
data class ScannedLine(val text: String, val y: Float, val height: Float = 0f, val x: Float = 0.5f)

/**
 * A card recognised in a frame. [nameRead]: its name was read (not only the set code and
 * number); [numbered]: the printing comes from the set code and number, not a guess.
 */
class ScanHit(val card: PaperCard, val nameRead: Boolean, val numbered: Boolean)

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
        /** Alternative names printed on some cards ("The Dead Marshes" is an Urborg) to that printing. */
        val flavor: Map<String, PaperCard>,
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
        // Spanish cards: their printed names (Forge's translation list) find the English card.
        val spanish = spanishNames()
        spanish.forEach { (english, translated) ->
            val card = byName[norm(english)] ?: return@forEach
            translated.split(" // ").forEach { byName.putIfAbsent(norm(it), card) }
        }
        val byNumber = HashMap<String, PaperCard>()
        val bySet = HashMap<String, HashMap<String, String>>()
        val flavor = HashMap<String, PaperCard>()
        cards.forEach { c ->
            if (c.hasFlavorName()) {
                val key = norm(c.displayName)
                flavor.putIfAbsent(key, c)
                byName.putIfAbsent(key, c.name)
                bySet.getOrPut(c.edition.uppercase()) { HashMap() }.putIfAbsent(key, c.name)
            }
            val edition = db.getCardEdition(c.edition)
            val codes = listOfNotNull(c.edition, edition?.code2, edition?.scryfallCode).map { it.uppercase() }.distinct()
            c.collectorNumber?.takeIf { it.isNotBlank() }?.let { n -> codes.forEach { byNumber.putIfAbsent("$it#${numKey(n)}", c) } }
            val names = bySet.getOrPut(c.edition.uppercase()) { HashMap() }
            names[norm(c.name)] = c.name
            if (" // " in c.name) c.name.split(" // ").forEach { names.putIfAbsent(norm(it), c.name) }
            c.name.split(" // ").forEach { face -> spanish[face]?.split(" // ")?.forEach { names.putIfAbsent(norm(it), c.name) } }
        }
        return Index(
            byName,
            byName.entries.groupBy({ it.key.length }, { it.key to it.value }),
            byNumber,
            bySet.mapValues { (_, m) -> m.entries.map { it.key to it.value } },
            flavor,
        )
    }

    /** English name (each face of split cards) to its Spanish name, from Forge's translations. */
    private fun spanishNames(): Map<String, String> {
        val file = File(ForgeConstants.LANG_DIR, "cardnames-es-ES.txt")
        if (!file.exists()) return emptyMap()
        val out = HashMap<String, String>()
        runCatching {
            file.forEachLine { line ->
                val parts = line.split('|')
                if (parts.size >= 2 && parts[1].isNotBlank()) out[parts[0]] = parts[1]
            }
        }
        return out
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
        val title = name(titleLines(lines), idx, set?.uppercase())
        // Other cards around it (a binder page) show their bottom lines too: take the one under
        // this card's title, or without a title the one where the frame's bottom edge is.
        val bottoms = bottoms(lines)
        val bottom = if (title != null) {
            bottoms.filter { it.y > title.line.y }.minByOrNull { (it.y - title.line.y) + abs(it.x - title.line.x) }
        } else {
            bottoms.minByOrNull { abs(it.y - 0.7f) + abs(it.x - 0.5f) }
        }
        val code = set?.uppercase() ?: bottom?.code
        val byNumber = if (bottom != null && code != null) idx.byNumber["$code#${bottom.number}"] else null
        val name = title?.name
        return when {
            byNumber != null && (name == null || name == byNumber.name) -> ScanHit(byNumber, name != null, true)
            title != null -> (title.printing?.takeIf { set == null || it.edition.equals(set, ignoreCase = true) } ?: defaultPrinting(title.name, set))
                ?.let { ScanHit(it, true, false) }
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

    /**
     * The lines that can be the card's title: set in the card's largest type (rules text, which
     * may name other cards, is smaller), shaped like a name (no sentence, not a type line) and
     * at the top of the card. Topmost first; only the top two are tried.
     */
    private fun titleLines(lines: List<ScannedLine>): List<ScannedLine> {
        val named = lines.filter { looksLikeName(it.text) }
        val tallest = named.maxOfOrNull { it.height } ?: return emptyList()
        return named.filter { it.height >= tallest * 0.75f }.sortedBy { it.y }.take(2)
    }

    private fun looksLikeName(text: String): Boolean {
        val t = text.trim()
        if (t.length > 40 || t.count { it.isLetter() } < 3) return false
        if (!t.first().isLetter() || t.first().isLowerCase()) return false
        if (t.any { it in ".:;()\"•—" } || " - " in t) return false
        return TYPE_WORDS.none { t.startsWith(it, ignoreCase = true) }
    }

    /** The first word of a type line ("Legendary Creature — Elf"), never of a name. */
    private val TYPE_WORDS = listOf(
        "Creature", "Instant", "Sorcery", "Artifact", "Enchantment", "Land ", "Planeswalker", "Battle",
        "Legendary", "Basic Land", "Tribal", "Kindred", "Snow", "Token", "World Enchantment", "Conspiracy",
        // Spanish cards.
        "Criatura", "Instantáneo", "Conjuro", "Artefacto", "Encantamiento", "Tierra ", "Batalla", "Ficha", "Tribal",
    )

    /** A name read on [line]; [printing]: the one printing that carries it, for an alternative name. */
    private class NameMatch(val name: String, val line: ScannedLine, val printing: PaperCard?)

    /** The first of [lines] that is (or nearly is) a card name. */
    private fun name(lines: List<ScannedLine>, idx: Index, set: String?): NameMatch? {
        for (line in lines) {
            val n = norm(line.text)
            if (n.length < 3) continue
            if (set == null) idx.byName[n]?.let { return NameMatch(it, line, idx.flavor[n]) }
            val pool = if (set != null) idx.bySet[set].orEmpty()
            else (n.length - 2..n.length + 2).flatMap { idx.byLength[it].orEmpty() }
            // Within one set a looser match is still unambiguous.
            val allowed = if (set != null) maxOf(2, n.length / 4) else maxOf(1, n.length / 7)
            var best: Pair<String, String>? = null
            var bestDistance = allowed + 1
            for (entry in pool) {
                if (abs(entry.first.length - n.length) > allowed) continue
                val d = distance(n, entry.first, bestDistance)
                if (d < bestDistance) { bestDistance = d; best = entry; if (d == 0) break }
            }
            if (best != null) return NameMatch(best.second, line, idx.flavor[best.first])
        }
        return null
    }

    /** A collector number read, with the set code printed beside or under it (if read). */
    private class Bottom(val number: String, val code: String?, val y: Float, val x: Float)

    /**
     * Every collector number read ("R 0090", "0090 R" or the older "123/249"), each paired with
     * the set code ("FRA • EN") on its own line or the one right below it, so the number and
     * code of one card don't get mixed with a neighbour's.
     */
    private fun bottoms(lines: List<ScannedLine>): List<Bottom> {
        val codes = lines.mapNotNull { l -> SET_LANGUAGE.find(l.text.trim())?.let { l to it.groupValues[1].uppercase() } }
        return lines.mapNotNull { l ->
            val t = l.text.trim()
            val number = (RARITY_NUMBER.find(t) ?: NUMBER_RARITY.find(t) ?: OF_TOTAL.find(t))?.groupValues?.get(1) ?: return@mapNotNull null
            val code = codes
                .filter { (c, _) -> c.y - l.y in -0.005f..0.06f && abs(c.x - l.x) < 0.3f }
                .minByOrNull { (c, _) -> abs(c.y - l.y) + abs(c.x - l.x) }?.second
            Bottom(numKey(number), code, l.y, l.x)
        }
    }

    private val RARITY_NUMBER = Regex("""^[CURMLSTP]\s+0*(\d{1,4}[a-z]?)\b""")
    private val NUMBER_RARITY = Regex("""^0*(\d{1,4}[a-z]?)\s+[CURMLSTP]\b""")
    private val OF_TOTAL = Regex("""\b0*(\d{1,4})\s*/\s*\d{2,4}\b""")
    private val SET_LANGUAGE = Regex("""\b([A-Z0-9]{3,5})\s*[•·★*.\-]?\s*(EN|ES|SP|FR|DE|IT|PT|JA|JP|KO|RU|ZH|CS|CT|PH)\b""")
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
