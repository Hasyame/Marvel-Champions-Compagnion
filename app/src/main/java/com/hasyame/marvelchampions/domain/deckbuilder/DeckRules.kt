package com.hasyame.marvelchampions.domain.deckbuilder

/**
 * Deck building rules for one hero.
 *
 * Almost all of this comes from the card data. The exception is
 * [MINIMUM_DECK_SIZE], which MarvelCDB does not encode anywhere — see the note
 * on that constant.
 */
data class HeroDeckRules(
    val heroCode: String,
    val heroSetCode: String?,
    /** How many aspects this hero picks. One for nearly every hero. */
    val aspectCount: Int = 1,
    /**
     * Cap on cards taken from each chosen aspect, when the hero trades breadth
     * for depth. Adam Warlock picks four aspects but only one card from each.
     */
    val perAspectLimit: Int? = null,
    /** Extra allowances that widen what is legal, straight from `deck_options`. */
    val options: List<DeckOption> = emptyList(),
)

/**
 * An entry of a hero's `deck_options`, which *permits* cards that would
 * otherwise be off-aspect. Only five heroes have one, but without it the
 * builder would wrongly reject their legal decks.
 *
 * A card must match every criterion that is present.
 */
data class DeckOption(
    val traits: List<String> = emptyList(),
    val types: List<String> = emptyList(),
    val resources: List<String> = emptyList(),
    /** Maximum number of cards admitted through this allowance. */
    val limit: Int? = null,
)

/** The subset of a card the validator needs. Keeps the rules free of Room types. */
data class DeckCardInfo(
    val code: String,
    val name: String,
    val factionCode: String,
    val typeCode: String,
    val cardSetCode: String?,
    val traits: String?,
    val deckLimit: Int?,
    val isUnique: Boolean,
    val resourcePhysical: Int? = null,
    val resourceMental: Int? = null,
    val resourceEnergy: Int? = null,
    val resourceWild: Int? = null,
) {
    fun hasTrait(trait: String): Boolean =
        traits?.split('.')?.any { it.trim().equals(trait.trim(), ignoreCase = true) } == true

    fun hasResource(resource: String): Boolean = when (resource.lowercase()) {
        "physical" -> (resourcePhysical ?: 0) > 0
        "mental" -> (resourceMental ?: 0) > 0
        "energy" -> (resourceEnergy ?: 0) > 0
        "wild" -> (resourceWild ?: 0) > 0
        else -> false
    }
}

/** Something wrong with a deck. Every case names the card it is about. */
sealed interface DeckProblem {
    data class TooFewCards(val actual: Int, val required: Int) : DeckProblem

    data class WrongAspectCount(val actual: Int, val required: Int) : DeckProblem

    data class OffAspectCard(val cardCode: String, val cardName: String, val factionCode: String) :
        DeckProblem

    data class OverCopyLimit(
        val cardCode: String,
        val cardName: String,
        val quantity: Int,
        val limit: Int,
    ) : DeckProblem

    data class DuplicateUniqueCard(
        val cardCode: String,
        val cardName: String,
        val quantity: Int,
    ) : DeckProblem

    data class OverAspectLimit(val aspect: String, val actual: Int, val limit: Int) : DeckProblem
}

data class DeckValidation(
    val problems: List<DeckProblem> = emptyList(),
    val totalCards: Int = 0,
) {
    val isLegal: Boolean get() = problems.isEmpty()
}

/**
 * The one rule that is not in the card data.
 *
 * MarvelCDB encodes copy limits, uniqueness, factions and the handful of
 * per-hero exceptions, but nothing anywhere states the deck size. This is the
 * standard minimum and is kept as a named constant so it is obvious what is
 * assumed rather than derived.
 */
const val MINIMUM_DECK_SIZE: Int = 40
