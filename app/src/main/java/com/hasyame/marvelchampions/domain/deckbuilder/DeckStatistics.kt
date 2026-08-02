package com.hasyame.marvelchampions.domain.deckbuilder

import com.hasyame.marvelchampions.data.db.entity.CardEntity

/**
 * What a deck is made of, counted rather than listed.
 *
 * Everything here counts **copies**, not distinct cards: a deck with three
 * copies of a one-cost ally has three one-cost cards in it, and the question a
 * player is asking — "how often will I draw something cheap?" — is about
 * copies. Counting rows instead would flatter every deck equally and answer
 * nothing.
 */
data class DeckStatistics(
    /** Cost to number of copies at that cost. Cards with no printed cost are excluded. */
    val costCurve: Map<Int, Int> = emptyMap(),
    /** Resource icons printed on the cards, totalled across copies. */
    val resources: ResourceCounts = ResourceCounts(),
    /** Card type name to copies, largest first. */
    val byType: List<Pair<String, Int>> = emptyList(),
    /** Aspect name to copies, largest first. */
    val byAspect: List<Pair<String, Int>> = emptyList(),
    /** Copies with a printed cost, which is what [costCurve] and [averageCost] cover. */
    val costedCards: Int = 0,
    val averageCost: Double = 0.0,
) {
    val hasCostData: Boolean get() = costedCards > 0

    /** The largest column, so a bar chart can scale to it. */
    val tallestCostColumn: Int get() = costCurve.values.maxOrNull() ?: 0
}

/**
 * Resource icons in the deck.
 *
 * These are what pays for cards, so the totals answer a real deckbuilding
 * question: a deck full of energy costs and no energy icons plays badly however
 * good the cards are.
 */
data class ResourceCounts(
    val physical: Int = 0,
    val mental: Int = 0,
    val energy: Int = 0,
    val wild: Int = 0,
) {
    val total: Int get() = physical + mental + energy + wild
}

object DeckStatisticsCalculator {

    /**
     * Counts a deck.
     *
     * The hero card is not passed in and must not be: it is not part of the
     * deck, has no cost, and would distort both the curve and the aspect split.
     */
    fun calculate(cards: List<Pair<CardEntity, Int>>): DeckStatistics {
        if (cards.isEmpty()) {
            return DeckStatistics()
        }

        val costCurve = sortedMapOf<Int, Int>()
        var physical = 0
        var mental = 0
        var energy = 0
        var wild = 0
        var costedCards = 0
        var costTotal = 0

        val typeCounts = mutableMapOf<String, Int>()
        val aspectCounts = mutableMapOf<String, Int>()

        for ((card, quantity) in cards) {
            // A card printed with a variable cost — "X" or "per hero" — has no
            // single number, so it is left out of the curve rather than
            // counted as whatever placeholder the database happens to hold.
            val cost = card.cost?.takeIf { !card.costStar && !card.costPerHero }
            if (cost != null) {
                costCurve[cost] = (costCurve[cost] ?: 0) + quantity
                costedCards += quantity
                costTotal += cost * quantity
            }

            physical += (card.resourcePhysical ?: 0) * quantity
            mental += (card.resourceMental ?: 0) * quantity
            energy += (card.resourceEnergy ?: 0) * quantity
            wild += (card.resourceWild ?: 0) * quantity

            typeCounts[card.typeName] = (typeCounts[card.typeName] ?: 0) + quantity
            aspectCounts[card.factionName] = (aspectCounts[card.factionName] ?: 0) + quantity
        }

        return DeckStatistics(
            costCurve = costCurve,
            resources = ResourceCounts(physical, mental, energy, wild),
            byType = typeCounts.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { it.key })
                .map { it.key to it.value },
            byAspect = aspectCounts.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { it.key })
                .map { it.key to it.value },
            costedCards = costedCards,
            averageCost = if (costedCards > 0) costTotal.toDouble() / costedCards else 0.0,
        )
    }
}
