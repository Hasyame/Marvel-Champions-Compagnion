package com.hasyame.marvelchampions.data.marvelcdb

import com.hasyame.marvelchampions.domain.model.CardLocale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarvelCdbUrlsTest {

    @Test
    fun `french card data comes from the locale subdomain`() {
        // Verified against the live API on 2026-08-01: the _locale query
        // parameter and the Accept-Language header are both ignored, only the
        // subdomain works.
        assertEquals("https://fr.marvelcdb.com/", MarvelCdbUrls.baseUrl(CardLocale.FRENCH))
        assertEquals("https://marvelcdb.com/", MarvelCdbUrls.baseUrl(CardLocale.ENGLISH))
    }

    @Test
    fun `the bulk card url always requests encounter cards`() {
        // Without encounter=1 the endpoint silently drops every villain,
        // scheme, minion, treachery and modular set card.
        CardLocale.entries.forEach { locale ->
            val url = MarvelCdbUrls.allCards(locale)
            assertEquals(
                "https://${locale.host}/api/public/cards/?encounter=1",
                url,
            )
        }
    }

    @Test
    fun `pack urls are per locale`() {
        assertEquals("https://fr.marvelcdb.com/api/public/packs/", MarvelCdbUrls.packs(CardLocale.FRENCH))
    }

    @Test
    fun `card images resolve against the canonical host`() {
        assertEquals(
            "https://marvelcdb.com/bundles/cards/01001a.png",
            MarvelCdbUrls.cardImage("/bundles/cards/01001a.png"),
        )
    }

    @Test
    fun `an absolute image url is left alone`() {
        assertEquals(
            "https://example.com/a.png",
            MarvelCdbUrls.cardImage("https://example.com/a.png"),
        )
    }

    @Test
    fun `a missing image is null rather than a broken url`() {
        assertNull(MarvelCdbUrls.cardImage(null))
        assertNull(MarvelCdbUrls.cardImage(""))
        assertNull(MarvelCdbUrls.cardImage("   "))
    }
}
