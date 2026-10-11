package io.github.nmanuelides.mtgreforged.ui.decks

import forge.StaticData
import forge.card.CardRarity
import forge.card.CardRules
import forge.card.CardType
import forge.card.MagicColor
import forge.game.GameFormat
import forge.item.PaperCard
import forge.model.FModel

/** A comparison against a number printed on the card: mana value, power or toughness. */
data class NumberFilter(val op: Op, val value: Int) {
    enum class Op(val symbol: String, val test: (Int, Int) -> Boolean) {
        EQ("=", { a, b -> a == b }),
        LT("<", { a, b -> a < b }),
        LE("≤", { a, b -> a <= b }),
        GT(">", { a, b -> a > b }),
        GE("≥", { a, b -> a >= b }),
        NE("≠", { a, b -> a != b }),
    }

    fun matches(n: Int?) = n != null && op.test(n, value)
    override fun toString() = "${op.symbol} $value"
}

/** How a group of picked values (types, keywords…) has to match a card. */
enum class GroupMode(val label: String) { Any("Any of"), All("All of"), Not("None of") }

enum class ColorMode(val label: String) { Including("Including"), Exactly("Exactly"), AtMost("At most") }

enum class SortBy(val label: String) {
    Name("Name"), ManaValue("Mana value"), Color("Color"), Rarity("Rarity"),
    Power("Power"), Toughness("Toughness"), Newest("Newest"),
}

/** A set of picked values plus how they combine. */
data class Group(val values: Set<String> = emptySet(), val mode: GroupMode = GroupMode.Any) {
    val active get() = values.isNotEmpty()

    fun matches(cardValues: Collection<String>): Boolean = when {
        values.isEmpty() -> true
        mode == GroupMode.Any -> values.any { it in cardValues }
        mode == GroupMode.All -> values.all { it in cardValues }
        else -> values.none { it in cardValues }
    }

    fun toggle(value: String) = copy(values = if (value in values) values - value else values + value)
}

/** Everything the card search can filter and sort by. All filters must match (they're linked). */
data class CardFilter(
    val exactName: Boolean = false,
    val hideDuplicates: Boolean = true,
    val sort: SortBy = SortBy.Name,
    val descending: Boolean = false,
    val colors: Set<Byte> = emptySet(),
    val colorless: Boolean = false,
    val colorMode: ColorMode = ColorMode.Including,
    val identity: Set<Byte> = emptySet(),
    val sets: Set<String> = emptySet(),
    val keywords: Group = Group(mode = GroupMode.All),
    val legality: String? = null,
    val manaCost: String = "",
    val manaValue: NumberFilter? = null,
    val text: String = "",
    val types: Group = Group(),
    val supertypes: Group = Group(),
    val subtypes: Group = Group(),
    val power: NumberFilter? = null,
    val toughness: NumberFilter? = null,
    val rarities: Set<CardRarity> = emptySet(),
    val artist: String = "",
) {
    /** Number of filters in use, for the badge on the Filters button. */
    val activeCount: Int
        get() = listOf(
            colors.isNotEmpty() || colorless, identity.isNotEmpty(), sets.isNotEmpty(), keywords.active,
            legality != null, manaCost.isNotBlank(), manaValue != null, text.isNotBlank(), types.active,
            supertypes.active, subtypes.active, power != null, toughness != null, rarities.isNotEmpty(), artist.isNotBlank(),
        ).count { it }

    /** Filters that depend on the printing rather than the card itself. */
    private val perPrinting get() = sets.isNotEmpty() || rarities.isNotEmpty() || artist.isNotBlank()

    fun search(query: String, limit: Int = 240): Pair<List<PaperCard>, Int> {
        val q = query.trim().lowercase()
        if (q.length < 2 && activeCount == 0) return emptyList<PaperCard>() to 0
        val db = StaticData.instance().commonCards
        val pool = if (perPrinting || !hideDuplicates) db.allCards else db.uniqueCards
        val format = legality?.let(::formatNamed)
        val cost = normalizeCost(manaCost)
        val textQ = text.trim().lowercase()
        val artistQ = artist.trim().lowercase()
        val colorMask = colors.fold(0) { m, c -> m or c.toInt() }
        val identityMask = identity.fold(0) { m, c -> m or c.toInt() }

        var matches = pool.asSequence().filter { card ->
            val rules = card.rules
            val name = card.name.lowercase()
            (q.isEmpty() || if (exactName) name == q else q in name || q in rules.oracleText.orEmpty().lowercase()) &&
                matchesColors(rules, colorMask) &&
                (identity.isEmpty() || rules.colorIdentity.hasNoColorsExcept(identityMask)) &&
                (sets.isEmpty() || card.edition in sets) &&
                (rarities.isEmpty() || card.rarity in rarities) &&
                (artistQ.isEmpty() || artistQ in card.artist.orEmpty().lowercase()) &&
                (textQ.isEmpty() || textQ in rules.oracleText.orEmpty().lowercase()) &&
                (cost.isEmpty() || normalizeCost(rules.manaCost.simpleString) == cost) &&
                (manaValue == null || manaValue.matches(rules.manaCost.cmc)) &&
                (power == null || power.matches(rules.power?.toIntOrNull())) &&
                (toughness == null || toughness.matches(rules.toughness?.toIntOrNull())) &&
                types.matches(rules.type.coreTypes.map { it.name }) &&
                supertypes.matches(rules.type.supertypes.map { it.name }) &&
                subtypes.matches(rules.type.subtypes.toList()) &&
                (!keywords.active || keywords.matches(keywordsOf(rules))) &&
                (format == null || format.filterRules.test(card))
        }
        // One printing per card: the first one that matched, so a set or artist filter shows that art.
        if (hideDuplicates && pool !== db.uniqueCards) matches = matches.distinctBy { it.name }

        val all = matches.sortedWith(comparator()).toList()
        return all.take(limit) to all.size
    }

    private fun matchesColors(rules: CardRules, mask: Int): Boolean {
        if (colors.isEmpty() && !colorless) return true
        val color = rules.color
        return when (colorMode) {
            ColorMode.Including -> if (colors.isEmpty()) color.isColorless else color.hasAllColors(mask)
            ColorMode.Exactly -> if (colors.isEmpty()) color.isColorless else color.hasExactlyColor(mask)
            ColorMode.AtMost -> color.hasNoColorsExcept(mask) && (colorless || !color.isColorless)
        }
    }

    private fun comparator(): Comparator<PaperCard> {
        val byName = compareBy<PaperCard> { it.name }
        val primary: Comparator<PaperCard> = when (sort) {
            SortBy.Name -> byName
            SortBy.ManaValue -> compareBy { it.rules.manaCost.cmc }
            SortBy.Color -> compareBy { it.rules.color.orderWeight }
            SortBy.Rarity -> compareBy { it.rarity.ordinal }
            SortBy.Power -> compareBy(nullsFirst()) { it.rules.power?.toIntOrNull() }
            SortBy.Toughness -> compareBy(nullsFirst()) { it.rules.toughness?.toIntOrNull() }
            SortBy.Newest -> compareBy { -(StaticData.instance().getCardEdition(it.edition)?.date?.time ?: 0L) }
        }
        return (if (descending) primary.reversed() else primary).then(byName)
    }

    companion object {
        fun normalizeCost(cost: String) = cost.uppercase().filter { it.isLetterOrDigit() || it == '/' }

        fun keywordsOf(rules: CardRules): Set<String> =
            rules.allFaces.filterNotNull().flatMap { face -> face.keywords.map { it.substringBefore(':').trim() } }.toSet()

        fun formatNamed(name: String): GameFormat? = FModel.getFormats().getFormat(name)
    }
}

/** Values the filter pickers offer, read once from the card database. */
object FilterOptions {
    val types = listOf("Creature", "Instant", "Sorcery", "Artifact", "Enchantment", "Planeswalker", "Land", "Battle", "Kindred")
    val supertypes = CardType.Supertype.entries.map { it.name }
    val rarities = CardRarity.FILTER_OPTIONS.toList()
    val colors = listOf(
        "W" to MagicColor.WHITE, "U" to MagicColor.BLUE, "B" to MagicColor.BLACK,
        "R" to MagicColor.RED, "G" to MagicColor.GREEN,
    )

    /** (code, "Name (CODE)") newest first. */
    val sets: List<Pair<String, String>> by lazy {
        StaticData.instance().editions.sortedByDescending { it.date }.map { it.code to "${it.name} (${it.code})" }
    }

    val formats: List<String> by lazy {
        val formats = FModel.getFormats()
        (formats.sanctionedList + formats.casualList).map { it.name }.distinct()
    }

    val subtypes: List<String> by lazy {
        StaticData.instance().commonCards.uniqueCards.flatMapTo(sortedSetOf()) { it.rules.type.subtypes }.toList()
    }

    /** Keywords printed on at least a few cards; drops Forge's internal script keywords. */
    val keywords: List<String> by lazy {
        StaticData.instance().commonCards.uniqueCards
            .flatMap { CardFilter.keywordsOf(it.rules) }
            .filter { it.firstOrNull()?.isUpperCase() == true && it.length < 24 && ' ' !in it.drop(12) }
            .groupingBy { it }.eachCount()
            .filterValues { it >= 3 }.keys.sorted()
    }

    val artists: List<String> by lazy {
        StaticData.instance().commonCards.allCards.mapNotNullTo(sortedSetOf()) { it.artist?.takeIf(String::isNotBlank) }.toList()
    }
}
