package com.hasyame.marvelchampions.domain.deckbuilder

/**
 * Checks a deck against the rules that are actually knowable.
 *
 * Pure, so every rule is testable without a database.
 *
 * Deliberately *not* checked: anything MarvelCDB does not encode. The validator
 * would rather stay silent than invent a rule and reject a legal deck.
 */
object DeckValidator {

    private const val BASIC_FACTION = "basic"
    private const val HERO_FACTION = "hero"

    fun validate(
        rules: HeroDeckRules,
        chosenAspects: List<String>,
        /** Card code to quantity, hero card excluded. */
        slots: Map<String, Int>,
        cards: Map<String, DeckCardInfo>,
    ): DeckValidation {
        val problems = mutableListOf<DeckProblem>()
        val totalCards = slots.values.sum()

        if (chosenAspects.size != rules.aspectCount) {
            problems += DeckProblem.WrongAspectCount(chosenAspects.size, rules.aspectCount)
        }

        if (totalCards < MINIMUM_DECK_SIZE) {
            problems += DeckProblem.TooFewCards(totalCards, MINIMUM_DECK_SIZE)
        }

        // How many cards each deck_options allowance has admitted so far, so a
        // limited allowance stops admitting once it is full.
        val optionUsage = IntArray(rules.options.size)
        val aspectUsage = mutableMapOf<String, Int>()

        for ((code, quantity) in slots) {
            val card = cards[code] ?: continue

            if (quantity < 1) {
                continue
            }

            val limit = card.deckLimit
            if (limit != null && quantity > limit) {
                problems += DeckProblem.OverCopyLimit(code, card.name, quantity, limit)
            }
            if (card.isUnique && quantity > 1) {
                problems += DeckProblem.DuplicateUniqueCard(code, card.name, quantity)
            }

            when {
                // The hero's own signature cards. A hero-faction card from a
                // different hero is not legal here, which is why the set code
                // is compared rather than just the faction.
                card.factionCode == HERO_FACTION ->
                    if (rules.heroSetCode != null && card.cardSetCode != rules.heroSetCode) {
                        problems += DeckProblem.OffAspectCard(code, card.name, card.factionCode)
                    }

                card.factionCode == BASIC_FACTION -> Unit

                card.factionCode in chosenAspects ->
                    aspectUsage[card.factionCode] =
                        (aspectUsage[card.factionCode] ?: 0) + quantity

                else -> {
                    val admitted = admitByOption(rules, card, quantity, optionUsage)
                    if (!admitted) {
                        problems += DeckProblem.OffAspectCard(code, card.name, card.factionCode)
                    }
                }
            }
        }

        rules.perAspectLimit?.let { limit ->
            aspectUsage.forEach { (aspect, used) ->
                if (used > limit) {
                    problems += DeckProblem.OverAspectLimit(aspect, used, limit)
                }
            }
        }

        return DeckValidation(problems = problems, totalCards = totalCards)
    }

    /**
     * Tries to admit an off-aspect card through one of the hero's
     * `deck_options` allowances, consuming capacity from the first that fits.
     */
    private fun admitByOption(
        rules: HeroDeckRules,
        card: DeckCardInfo,
        quantity: Int,
        usage: IntArray,
    ): Boolean {
        rules.options.forEachIndexed { index, option ->
            if (!option.matches(card)) {
                return@forEachIndexed
            }
            val limit = option.limit
            if (limit == null) {
                return true
            }
            if (usage[index] + quantity <= limit) {
                usage[index] += quantity
                return true
            }
        }
        return false
    }

    private fun DeckOption.matches(card: DeckCardInfo): Boolean {
        if (types.isNotEmpty() && card.typeCode !in types) {
            return false
        }
        if (traits.isNotEmpty() && traits.none { card.hasTrait(it) }) {
            return false
        }
        if (resources.isNotEmpty() && resources.none { card.hasResource(it) }) {
            return false
        }
        // An allowance with no criteria at all would admit everything, which is
        // never what the data means.
        return types.isNotEmpty() || traits.isNotEmpty() || resources.isNotEmpty()
    }
}
