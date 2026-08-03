package com.hasyame.marvelchampions.domain.model

/**
 * How a list of cards is ordered.
 *
 * [SET] is the default and is what the app has always done: pack, then printed
 * position. It matches the order the cards sit in the box, which is what you
 * want when you are looking for one to pull out.
 *
 * The others answer questions the set order cannot: what is cheap enough to
 * play this turn, and where is the card whose name I half remember.
 */
enum class CardSort(val code: String) {
    SET("set"),
    NAME("name"),
    COST_LOW_TO_HIGH("cost_asc"),
    COST_HIGH_TO_LOW("cost_desc"),
    ;

    /**
     * The SQL for this ordering.
     *
     * Built here rather than in the query builder so the two cannot drift, and
     * every branch is a constant — no user input reaches it, which is what
     * keeps an ORDER BY clause safe to interpolate.
     */
    val orderBy: String
        get() = when (this) {
            SET -> "cards.packCode, cards.position"
            NAME -> "cards.searchName, cards.packCode"
            // Cards with no printed cost sort last either way: a hero has no
            // cost, and letting NULL lead would bury the answer.
            COST_LOW_TO_HIGH -> "cards.cost IS NULL, cards.cost ASC, cards.searchName"
            COST_HIGH_TO_LOW -> "cards.cost IS NULL, cards.cost DESC, cards.searchName"
        }

    companion object {
        fun fromCode(code: String?): CardSort =
            entries.firstOrNull { it.code == code } ?: SET
    }
}
