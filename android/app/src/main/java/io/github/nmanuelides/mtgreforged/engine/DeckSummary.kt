package io.github.nmanuelides.mtgreforged.engine

import forge.card.MagicColor
import forge.deck.DeckSection
import forge.item.PaperCard

/** What the deck sheet shows about a deck: identity, art and a few "stats". */
data class DeckSummary(
    val entry: Engine.DeckEntry,
    val title: String,
    val tag: String,
    val colors: List<Byte>,
    val artCard: String?,
    val commander: String?,
    val cardCount: Int,
    val creatures: Int,
    val lands: Int,
    val spells: Int,
    val averageCmc: Float,
    val featured: List<String>,
    /** Card Kingdom value of the whole deck in USD, or null before prices are downloaded. */
    val value: Float?,
) {
    companion object {
        private val bracket = Regex("""\s*\[([^\]]+)]""")

        fun of(entry: Engine.DeckEntry): DeckSummary {
            val deck = Engine.loadDeck(entry)
            val commanders = deck.commanders.orEmpty()
            val main = deck.get(DeckSection.Main)?.toFlatList().orEmpty()
            val all = commanders + main

            fun PaperCard.isLand() = rules.type.isLand
            fun PaperCard.isCreature() = rules.type.isCreature
            val nonLands = all.filterNot { it.isLand() }
            val identity = all.fold(0) { acc, c -> acc or c.rules.colorIdentity.color.toInt() }
            val signature = nonLands.filter { it.isCreature() }.maxByOrNull { it.rules.manaCost.cmc }
                ?: nonLands.maxByOrNull { it.rules.manaCost.cmc }

            // "Abzan Armor [TDC] [2025]" -> title "Abzan Armor", tag "TDC · 2025"
            val tags = bracket.findAll(entry.name).map { it.groupValues[1] }.toList()
            return DeckSummary(
                entry = entry,
                title = entry.name.replace(bracket, "").trim(),
                tag = tags.joinToString(" · "),
                colors = MagicColor.WUBRG.filter { identity and it.toInt() != 0 },
                artCard = (commanders.firstOrNull() ?: signature)?.name,
                commander = commanders.firstOrNull()?.name,
                cardCount = all.size,
                creatures = all.count { it.isCreature() },
                lands = all.count { it.isLand() },
                spells = nonLands.count { !it.isCreature() },
                averageCmc = nonLands.map { it.rules.manaCost.cmc }.average().toFloat().takeIf { !it.isNaN() } ?: 0f,
                featured = nonLands
                    .filter { it.name != commanders.firstOrNull()?.name }
                    .distinctBy { it.name }
                    .sortedByDescending { it.rules.manaCost.cmc * 10 + it.rarity.ordinal }
                    .take(3)
                    .map { it.name },
                value = if (CardPrices.available) all.sumOf { (CardPrices.of(it)?.retail ?: 0f).toDouble() }.toFloat() else null,
            )
        }
    }
}
