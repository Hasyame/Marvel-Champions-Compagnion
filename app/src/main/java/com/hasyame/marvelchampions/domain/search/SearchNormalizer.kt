package com.hasyame.marvelchampions.domain.search

import java.text.Normalizer

/**
 * Folds text into the form stored in the FTS index and used for queries.
 *
 * Lowercases, strips diacritics, removes the HTML markup MarvelCDB embeds in
 * card text, and collapses punctuation to spaces. Applying it to both the
 * indexed text and the query is what makes `strategie` match `Stratégie` and
 * `spider man` match `Spider-Man`, without needing a custom SQLite tokeniser.
 */
object SearchNormalizer {

    private val htmlTag = Regex("<[^>]*>")
    private val nonSearchable = Regex("[^\\p{L}\\p{N}]+")
    private val combiningMarks = Regex("\\p{Mn}+")

    fun normalize(input: String?): String {
        if (input.isNullOrEmpty()) {
            return ""
        }
        val withoutMarkup = htmlTag.replace(input, " ")
        val decomposed = Normalizer.normalize(withoutMarkup, Normalizer.Form.NFD)
        val withoutAccents = combiningMarks.replace(decomposed, "")
        return nonSearchable
            .replace(withoutAccents, " ")
            .trim()
            .lowercase()
    }

    /**
     * Builds an FTS4 MATCH expression that prefix-matches every token of the
     * query, so `spid ma` finds Spider-Man while the user is still typing.
     *
     * Returns null when the query has no searchable content, which callers
     * should treat as "no filter" rather than "no results".
     */
    fun toPrefixMatchQuery(rawQuery: String): String? {
        val tokens = normalize(rawQuery).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            return null
        }
        return tokens.joinToString(separator = " ") { "$it*" }
    }
}
