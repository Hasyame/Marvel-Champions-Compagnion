package com.hasyame.marvelchampions.domain.model

/**
 * The language card data is stored and displayed in, independent of the UI
 * language.
 *
 * MarvelCDB serves translations from a **locale subdomain**; the `_locale` query
 * parameter and the `Accept-Language` header are both ignored. See
 * `docs/DATA_SOURCES.md`.
 */
enum class CardLocale(val code: String, val host: String) {
    ENGLISH("en", "marvelcdb.com"),
    FRENCH("fr", "fr.marvelcdb.com"),
    ;

    companion object {
        fun fromCode(code: String): CardLocale? = entries.firstOrNull { it.code == code }
    }
}
