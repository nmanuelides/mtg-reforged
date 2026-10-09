package io.github.nmanuelides.mtgreforged.engine

import forge.StaticData
import forge.deck.Deck
import forge.deck.DeckSection
import forge.item.PaperCard
import io.github.nmanuelides.mtgreforged.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Builds Forge decks from a pasted card list or a Moxfield / ManaBox share link. */
object DeckImport {

    data class Line(val quantity: Int, val name: String, val set: String?, val collectorNumber: String?, val section: DeckSection)

    data class Result(val deck: Deck, val unknown: List<String>)

    /** Anything that looks like a link is fetched; everything else is read as a list. */
    fun import(input: String, fallbackName: String): Result {
        val text = input.trim()
        return when {
            "moxfield.com/decks/" in text -> fromMoxfield(text)
            "manabox.app/decks/" in text -> fromManaBox(text)
            else -> build(fallbackName, parseList(text))
        }
    }

    // ---- Plain lists -------------------------------------------------------------------

    private val sectionHeaders = mapOf(
        "commander" to DeckSection.Commander, "commanders" to DeckSection.Commander,
        "deck" to DeckSection.Main, "main" to DeckSection.Main, "mainboard" to DeckSection.Main,
        "sideboard" to DeckSection.Sideboard, "maybeboard" to DeckSection.Sideboard,
    )

    // "1 Sol Ring", "1x Sol Ring (C21) 263", "4 Lightning Bolt *F*"
    private val cardLine = Regex("""^(\d+)\s*x?\s+(.+?)(?:\s+\(([A-Za-z0-9]{2,6})\)(?:\s+([A-Za-z0-9★-]+))?)?(?:\s+\*[A-Z]+\*)?\s*$""")

    fun parseList(text: String): List<Line> {
        var section = DeckSection.Main
        val lines = ArrayList<Line>()
        for (raw in text.lines()) {
            val line = raw.trim().removeSuffix(":")
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("#")) continue
            sectionHeaders[line.lowercase()]?.let { section = it; continue }
            val match = cardLine.matchEntire(line)
            if (match == null) {
                // A bare card name counts as one copy.
                if (line.any(Char::isLetter)) lines += Line(1, line, null, null, section)
                continue
            }
            val (qty, name, set, number) = match.destructured
            lines += Line(qty.toInt(), name.trim(), set.ifBlank { null }, number.ifBlank { null }, section)
        }
        return lines
    }

    // ---- Moxfield ----------------------------------------------------------------------

    private fun fromMoxfield(url: String): Result {
        val id = url.substringAfter("/decks/").substringBefore('/').substringBefore('?').trim()
        val json = JSONObject(get("https://api2.moxfield.com/v3/decks/all/$id"))
        val boards = json.getJSONObject("boards")
        val lines = ArrayList<Line>()
        fun board(name: String, section: DeckSection) {
            val cards = boards.optJSONObject(name)?.optJSONObject("cards") ?: return
            cards.keys().forEach { key ->
                val entry = cards.getJSONObject(key)
                val card = entry.getJSONObject("card")
                lines += Line(entry.optInt("quantity", 1), card.getString("name"), card.optString("set").ifBlank { null }, card.optString("cn").ifBlank { null }, section)
            }
        }
        board("commanders", DeckSection.Commander)
        board("mainboard", DeckSection.Main)
        board("sideboard", DeckSection.Sideboard)
        return build(json.optString("name", "Moxfield deck"), lines)
    }

    // ---- ManaBox -----------------------------------------------------------------------

    // The share page embeds each card as serialised props: name, quantity, board, set.
    private val manaBoxCard = Regex(
        """"name":\[0,"((?:[^"\\]|\\.)+)"],"quantity":\[0,(\d+)],"boardCategory":\[0,(\d+)].{0,600}?"setId":\[0,"([^"]*)"]""",
        RegexOption.DOT_MATCHES_ALL,
    )

    private fun fromManaBox(url: String): Result {
        val html = get(url.trim())
        val page = html.replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
        val lines = manaBoxCard.findAll(page).map { m ->
            val (name, qty, board, set) = m.destructured
            val section = when (board) {
                "0" -> DeckSection.Commander
                "3" -> DeckSection.Main
                else -> DeckSection.Sideboard
            }
            Line(qty.toInt(), name.replace("\\\"", "\""), set.ifBlank { null }, null, section)
        }.toList()
        require(lines.isNotEmpty()) { "No cards found on that ManaBox page" }
        val title = Regex("<title>([^<|]+)").find(html)?.groupValues?.get(1)?.trim() ?: "ManaBox deck"
        return build(title, lines)
    }

    // ---- Shared ------------------------------------------------------------------------

    private fun build(name: String, lines: List<Line>): Result {
        val deck = Deck(name)
        val unknown = ArrayList<String>()
        for (line in lines) {
            val card = find(line)
            if (card == null) {
                unknown += line.name
                continue
            }
            deck.getOrCreate(line.section).add(card, line.quantity)
        }
        return Result(deck, unknown)
    }

    /** Exact printing when the list names one, otherwise Forge's preferred printing. */
    private fun find(line: Line): PaperCard? {
        val db = StaticData.instance().commonCards
        val name = line.name.substringBefore(" // ").trim()
        if (line.set != null) {
            runCatching { db.getCard(name, line.set.uppercase()) }.getOrNull()?.let { return it }
        }
        return runCatching { db.getCard(name) }.getOrNull()
            ?: runCatching { db.getCard(line.name) }.getOrNull()
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "MTGReforged/${BuildConfig.VERSION_NAME} (Android)")
        connection.setRequestProperty("Accept", "*/*")
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        try {
            val code = connection.responseCode
            require(code == 200) { if (code == 404) "Deck not found (is it public?)" else "Server answered $code" }
            return connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }
}
