package io.github.nmanuelides.mtgreforged.ui.decks

import io.github.nmanuelides.mtgreforged.engine.Engine
import forge.StaticData
import forge.card.CardRarity
import forge.card.CardRules
import forge.card.CardType
import forge.card.MagicColor
import forge.game.GameFormat
import forge.item.PaperCard
import forge.model.FModel
import io.github.nmanuelides.mtgreforged.engine.CardPrices

/** A comparison against a number printed on the card: mana value, power or toughness. */
data class NumberFilter(val op: Op, val value: Double) {
    enum class Op(val symbol: String, val test: (Double, Double) -> Boolean) {
        EQ("=", { a, b -> a == b }),
        LT("<", { a, b -> a < b }),
        LE("≤", { a, b -> a <= b }),
        GT(">", { a, b -> a > b }),
        GE("≥", { a, b -> a >= b }),
        NE("≠", { a, b -> a != b }),
    }

    fun matches(n: Number?) = n != null && op.test(n.toDouble(), value)
    override fun toString() = "${op.symbol} ${if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()}"
}

/** How a group of picked values (types, keywords…) has to match a card. */
enum class GroupMode(val label: String) { Any("Any of"), All("All of"), Not("None of") }

/** Where to search: the whole card pool, or only cards already in the deck / collection. */
enum class Within(val label: String) { All("All cards"), Deck("This deck"), Collection("My collection") }

/** Regular or foil printings; "Foil" turns results into foil printings Card Kingdom sells. */
enum class Finish(val label: String) { Any("Any"), Foil("Foil"), Regular("Non-foil") }

enum class ColorMode(val label: String) { Including("Including"), Exactly("Exactly"), AtMost("At most") }

enum class SortBy(val label: String) {
    Name("Name"), ManaValue("Mana value"), Price("Price"), Color("Color"), Type("Type"), Rarity("Rarity"),
    Power("Power"), Toughness("Toughness"), Newest("Newest"), Set("Set"),
}

/** Card types in deck-list order, for sorting by type. */
private val TYPE_ORDER = listOf("Creature", "Planeswalker", "Battle", "Instant", "Sorcery", "Artifact", "Enchantment", "Land")

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
    /** Arena-only rebalanced cards ("A-Name"): not legal in paper and without images. */
    val includeAlchemy: Boolean = false,
    val within: Within = Within.All,
    val finish: Finish = Finish.Any,
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
    /** Rules-text terms; by default a card must contain all of them. */
    val text: Group = Group(mode = GroupMode.All),
    val types: Group = Group(),
    val supertypes: Group = Group(),
    val subtypes: Group = Group(),
    val power: NumberFilter? = null,
    val toughness: NumberFilter? = null,
    val rarities: Set<CardRarity> = emptySet(),
    val artist: String = "",
    /** Card Kingdom retail price in USD, see [CardPrices]. */
    val price: NumberFilter? = null,
) {
    /** Number of filters in use, for the badge on the Filters button. */
    val activeCount: Int
        get() = listOf(
            colors.isNotEmpty() || colorless, identity.isNotEmpty(), sets.isNotEmpty(), keywords.active,
            legality != null, manaCost.isNotBlank(), manaValue != null, text.active, types.active,
            supertypes.active, subtypes.active, power != null, toughness != null, rarities.isNotEmpty(), artist.isNotBlank(),
            price != null, within != Within.All, finish != Finish.Any,
        ).count { it }

    /** Filters that depend on the printing rather than the card itself. */
    private val perPrinting get() = sets.isNotEmpty() || rarities.isNotEmpty() || artist.isNotBlank()

    /** [deck] / [collection]: card names for the "This deck" / "My collection" scopes. */
    /** [owned]: the owned printings; with the "My collection" scope those are listed, each one on its own. */
    fun search(
        query: String,
        deck: Set<String> = emptySet(),
        collection: Set<String> = emptySet(),
        owned: Collection<PaperCard>? = null,
        limit: Int = 240,
    ): Pair<List<PaperCard>, Int> {
        val q = query.trim().lowercase()
        if (q.length < 2 && activeCount == 0) return emptyList<PaperCard>() to 0
        val db = StaticData.instance().commonCards
        val ownedPool = within == Within.Collection && owned != null
        if (!ownedPool) Engine.awaitAllCards() // right after start-up the rest of the cards may still be loading
        val pool: Collection<PaperCard> = when {
            ownedPool -> owned!!
            perPrinting || !hideDuplicates -> db.allCards
            else -> db.uniqueCards
        }
        val format = legality?.let(::formatNamed)
        val cost = normalizeCost(manaCost)
        val terms = text.values.map { it.lowercase() }
        val artistQ = artist.trim().lowercase()
        val colorMask = colors.fold(0) { m, c -> m or c.toInt() }
        val identityMask = identity.fold(0) { m, c -> m or c.toInt() }

        var matches = pool.asSequence().filter { card ->
            val rules = card.rules
            val name = card.name.lowercase()
            (includeAlchemy || !card.name.startsWith("A-")) &&
                (within != Within.Deck || card.name in deck) &&
                (within != Within.Collection || card.name in collection) &&
                (q.isEmpty() || if (exactName) name == q else q in name || q in rules.oracleText.orEmpty().lowercase()) &&
                matchesColors(rules, colorMask) &&
                (identity.isEmpty() || rules.colorIdentity.hasNoColorsExcept(identityMask)) &&
                (sets.isEmpty() || card.edition in sets) &&
                (rarities.isEmpty() || card.rarity in rarities) &&
                (artistQ.isEmpty() || artistQ in card.artist.orEmpty().lowercase()) &&
                (terms.isEmpty() || matchesText(rules.oracleText.orEmpty().lowercase(), terms)) &&
                (cost.isEmpty() || normalizeCost(rules.manaCost.simpleString) == cost) &&
                (manaValue == null || manaValue.matches(rules.manaCost.cmc)) &&
                (power == null || power.matches(rules.power?.toIntOrNull())) &&
                (toughness == null || toughness.matches(rules.toughness?.toIntOrNull())) &&
                types.matches(rules.type.coreTypes.map { it.name }) &&
                supertypes.matches(rules.type.supertypes.map { it.name }) &&
                subtypes.matches(rules.type.subtypes.toList()) &&
                (!keywords.active || keywords.matches(keywordsOf(rules))) &&
                (format == null || format.filterRules.test(card)) &&
                (price == null || price.matches(priceOf(card)))
        }
        // One printing per card: the first one that matched, so a set or artist filter shows that art.
        if (hideDuplicates && !ownedPool && pool !== db.uniqueCards) matches = matches.distinctBy { it.name }

        matches = when {
            ownedPool && finish == Finish.Foil -> matches.filter { it.isFoil }
            ownedPool && finish == Finish.Regular -> matches.filter { !it.isFoil }
            finish == Finish.Foil -> matches.filter { CardPrices.hasFoil(it) }.map { it.foiled }
            else -> matches
        }
        val all = matches.sortedWith(comparator()).toList()
        return all.take(limit) to all.size
    }

    private fun matchesText(oracle: String, terms: List<String>) = when (text.mode) {
        GroupMode.Any -> terms.any { it in oracle }
        GroupMode.All -> terms.all { it in oracle }
        GroupMode.Not -> terms.none { it in oracle }
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

    /** With one printing per card, a budget filter cares about the cheapest printing. */
    private fun priceOf(card: PaperCard): Float? =
        (if (hideDuplicates && sets.isEmpty() && artist.isBlank()) CardPrices.cheapest(card.name) else CardPrices.of(card))?.retail

    private fun comparator(): Comparator<PaperCard> {
        val byName = compareBy<PaperCard> { it.name }
        val primary: Comparator<PaperCard> = when (sort) {
            SortBy.Name -> byName
            SortBy.ManaValue -> compareBy { it.rules.manaCost.cmc }
            SortBy.Color -> compareBy { it.rules.color.orderWeight }
            SortBy.Rarity -> compareBy { it.rarity.ordinal }
            SortBy.Power -> compareBy(nullsFirst()) { it.rules.power?.toIntOrNull() }
            SortBy.Toughness -> compareBy(nullsFirst()) { it.rules.toughness?.toIntOrNull() }
            SortBy.Price -> compareBy(nullsLast()) { priceOf(it) }
            SortBy.Newest -> compareBy { -(StaticData.instance().getCardEdition(it.edition)?.date?.time ?: 0L) }
            SortBy.Type -> compareBy { card ->
                TYPE_ORDER.indexOfFirst { t -> card.rules.type.coreTypes.any { it.name == t } }.let { if (it < 0) TYPE_ORDER.size else it }
            }
            SortBy.Set -> compareBy { StaticData.instance().getCardEdition(it.edition)?.name ?: it.edition }
        }
        // Same name: newest printing first, regular before foil, so owned printings keep their places.
        val byPrinting = compareBy<PaperCard>(
            { -(StaticData.instance().getCardEdition(it.edition)?.date?.time ?: 0L) }, { it.edition }, { it.collectorNumber }, { it.isFoil },
        )
        return (if (descending) primary.reversed() else primary).then(byName).then(byPrinting)
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
        Engine.awaitAllCards()
        StaticData.instance().commonCards.uniqueCards.flatMapTo(sortedSetOf()) { it.rules.type.subtypes }.toList()
    }

    /** Keywords printed on at least a few cards; drops Forge's internal script keywords. */
    val keywords: List<String> by lazy {
        Engine.awaitAllCards()
        StaticData.instance().commonCards.uniqueCards
            .flatMap { CardFilter.keywordsOf(it.rules) }
            .filter { it.firstOrNull()?.isUpperCase() == true && it.length < 24 && ' ' !in it.drop(12) }
            .groupingBy { it }.eachCount()
            .filterValues { it >= 3 }.keys.sorted()
    }

    val artists: List<String> by lazy {
        Engine.awaitAllCards()
        StaticData.instance().commonCards.allCards.mapNotNullTo(sortedSetOf()) { it.artist?.takeIf(String::isNotBlank) }.toList()
    }
}
