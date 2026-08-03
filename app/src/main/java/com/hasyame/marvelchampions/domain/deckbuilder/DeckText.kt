package com.hasyame.marvelchampions.domain.deckbuilder

/** One line of a shared decklist: how many, and of what. */
data class DeckTextCard(val quantity: Int, val name: String)

/**
 * Turns a deck into text somebody can read in a chat window.
 *
 * Plain text rather than a link or a file, because that is what actually gets
 * shared — pasted into a message, a forum post or a note. A link would only
 * work for decks that came from MarvelCDB, and a deck built in the app has no
 * URL at all.
 *
 * Grouped by type and counted, the way a decklist is written everywhere else,
 * so it reads as familiar rather than as this app's own invention.
 */
object DeckText {

    fun format(
        deckName: String,
        heroName: String,
        aspects: List<String>,
        cardsByType: Map<String, List<DeckTextCard>>,
        marvelCdbUrl: String? = null,
    ): String = buildString {
        appendLine(deckName)

        val aspectSuffix = aspects
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ", prefix = " (", postfix = ")")
            .orEmpty()
        appendLine(heroName + aspectSuffix)
        appendLine()

        // Sorted rather than left in map order, so the same deck shared twice
        // reads the same both times.
        cardsByType.entries.sortedBy { it.key }.forEach { (type, cards) ->
            appendLine("$type (${cards.sumOf { it.quantity }})")
            cards.sortedBy { it.name }.forEach { entry ->
                appendLine("  ${entry.quantity}x ${entry.name}")
            }
            appendLine()
        }

        appendLine("Total: ${cardsByType.values.flatten().sumOf { it.quantity }} cards")

        marvelCdbUrl?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            append(it)
        }
    }.trim()
}
