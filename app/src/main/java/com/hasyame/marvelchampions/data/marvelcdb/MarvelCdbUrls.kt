package com.hasyame.marvelchampions.data.marvelcdb

import com.hasyame.marvelchampions.domain.model.CardLocale

/**
 * Builds MarvelCDB URLs for a given locale.
 *
 * Localisation is by **host**: `fr.marvelcdb.com` serves French card text.
 * The `_locale` query parameter and the `Accept-Language` header are both
 * ignored by the server, so neither is used here.
 */
object MarvelCdbUrls {

    const val DEFAULT_BASE_URL: String = "https://marvelcdb.com/"

    fun baseUrl(locale: CardLocale): String = "https://${locale.host}/"

    /** Every card in [locale], encounter cards included. */
    fun allCards(locale: CardLocale): String =
        "${baseUrl(locale)}api/public/cards/?encounter=1"

    fun packs(locale: CardLocale): String =
        "${baseUrl(locale)}api/public/packs/"

    /**
     * Absolute URL for a card image. The API returns [imageSrc] as a host
     * relative path such as `/bundles/cards/01001a.png`.
     *
     * Images are always taken from the canonical host: they are language
     * neutral artwork and the locale subdomains serve the same files.
     */
    fun cardImage(imageSrc: String?): String? {
        if (imageSrc.isNullOrBlank()) {
            return null
        }
        return if (imageSrc.startsWith("http")) {
            imageSrc
        } else {
            DEFAULT_BASE_URL.trimEnd('/') + "/" + imageSrc.trimStart('/')
        }
    }
}
