package com.hasyame.marvelchampions.domain.deeplink

/**
 * A MarvelCDB deck reference.
 *
 * MarvelCDB keeps two separate id spaces, so the kind matters: decklist 30000
 * and deck 30000 are different decks.
 */
data class DeckReference(
    val id: Long,
    val kind: Kind,
) {
    enum class Kind {
        /** A published decklist. Always readable. */
        DECKLIST,

        /** A personal deck. Only readable if the owner enabled sharing. */
        DECK,
    }

    /** The API endpoint for this reference, on the canonical host. */
    val apiUrl: String
        get() = when (kind) {
            Kind.DECKLIST -> "https://marvelcdb.com/api/public/decklist/$id"
            Kind.DECK -> "https://marvelcdb.com/api/public/deck/$id"
        }
}

/**
 * Extracts a deck reference from anything the user can paste or share.
 *
 * Share sheets rarely hand over a bare URL — they send "Look at this
 * https://marvelcdb.com/decklist/view/123/x #marvelchampions" — so the parser
 * scans for a URL inside free text rather than requiring the whole string to be
 * one.
 */
object MarvelCdbDeckUrl {

    private val urlPattern = Regex(
        // Optional scheme, optional subdomain (www, or a locale such as fr),
        // then /decklist/view/<id> or /deck/view/<id>, then anything.
        //
        // The leading lookbehind is what stops a lookalike domain being
        // accepted: without it "notmarvelcdb.com/deck/view/1" matches, because
        // the pattern is searched for anywhere in the input rather than
        // anchored.
        """(?<![A-Za-z0-9.-])(?:https?://)?(?:[A-Za-z0-9-]+\.)*marvelcdb\.com/(decklist|deck)/view/(\d+)""",
        RegexOption.IGNORE_CASE,
    )

    /** Matches a bare id typed on its own, which is the fastest thing to type. */
    private val bareIdPattern = Regex("""^\s*(\d+)\s*$""")

    fun parse(input: String?): DeckReference? {
        if (input.isNullOrBlank()) {
            return null
        }

        urlPattern.find(input)?.let { match ->
            val kind = when (match.groupValues[1].lowercase()) {
                "decklist" -> DeckReference.Kind.DECKLIST
                else -> DeckReference.Kind.DECK
            }
            val id = match.groupValues[2].toLongOrNull() ?: return null
            return DeckReference(id = id, kind = kind)
        }

        // A bare number is ambiguous between the two id spaces. Published
        // decklists are what a user is overwhelmingly likely to have in hand,
        // and they are the ones that always resolve.
        bareIdPattern.find(input)?.let { match ->
            val id = match.groupValues[1].toLongOrNull() ?: return null
            return DeckReference(id = id, kind = DeckReference.Kind.DECKLIST)
        }

        return null
    }

    fun isMarvelCdbLink(input: String?): Boolean = parse(input) != null
}
