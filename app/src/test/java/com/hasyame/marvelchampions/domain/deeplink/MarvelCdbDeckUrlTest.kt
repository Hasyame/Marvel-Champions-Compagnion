package com.hasyame.marvelchampions.domain.deeplink

import com.hasyame.marvelchampions.domain.deeplink.DeckReference.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarvelCdbDeckUrlTest {

    @Test
    fun `parses a published decklist url with a slug`() {
        assertEquals(
            DeckReference(12345, Kind.DECKLIST),
            MarvelCdbDeckUrl.parse("https://marvelcdb.com/decklist/view/12345/some-slug"),
        )
    }

    @Test
    fun `parses a personal deck url`() {
        assertEquals(
            DeckReference(12345, Kind.DECK),
            MarvelCdbDeckUrl.parse("https://marvelcdb.com/deck/view/12345"),
        )
    }

    @Test
    fun `deck and decklist are different id spaces`() {
        // MarvelCDB really does serve two different decks at these urls.
        val decklist = MarvelCdbDeckUrl.parse("https://marvelcdb.com/decklist/view/30000")
        val deck = MarvelCdbDeckUrl.parse("https://marvelcdb.com/deck/view/30000")

        assertEquals(Kind.DECKLIST, decklist?.kind)
        assertEquals(Kind.DECK, deck?.kind)
        assertEquals(decklist?.id, deck?.id)
    }

    @Test
    fun `handles www`() {
        assertEquals(
            DeckReference(7, Kind.DECKLIST),
            MarvelCdbDeckUrl.parse("https://www.marvelcdb.com/decklist/view/7/x"),
        )
    }

    @Test
    fun `handles a locale subdomain`() {
        assertEquals(
            DeckReference(7, Kind.DECKLIST),
            MarvelCdbDeckUrl.parse("https://fr.marvelcdb.com/decklist/view/7/x"),
        )
    }

    @Test
    fun `handles http and a missing scheme`() {
        assertEquals(
            DeckReference(9, Kind.DECK),
            MarvelCdbDeckUrl.parse("http://marvelcdb.com/deck/view/9"),
        )
        assertEquals(
            DeckReference(9, Kind.DECK),
            MarvelCdbDeckUrl.parse("marvelcdb.com/deck/view/9"),
        )
    }

    @Test
    fun `ignores a trailing query string and fragment`() {
        assertEquals(
            DeckReference(42, Kind.DECKLIST),
            MarvelCdbDeckUrl.parse("https://marvelcdb.com/decklist/view/42/slug?utm_source=x&a=1#top"),
        )
    }

    @Test
    fun `finds a url inside shared text`() {
        // This is what a share sheet actually delivers.
        assertEquals(
            DeckReference(555, Kind.DECKLIST),
            MarvelCdbDeckUrl.parse(
                "Check this out https://marvelcdb.com/decklist/view/555/my-deck #marvelchampions",
            ),
        )
    }

    @Test
    fun `accepts a bare id as a published decklist`() {
        assertEquals(DeckReference(12345, Kind.DECKLIST), MarvelCdbDeckUrl.parse("12345"))
        assertEquals(DeckReference(12345, Kind.DECKLIST), MarvelCdbDeckUrl.parse("  12345  "))
    }

    @Test
    fun `rejects other sites`() {
        assertNull(MarvelCdbDeckUrl.parse("https://arkhamdb.com/decklist/view/12345"))
        assertNull(MarvelCdbDeckUrl.parse("https://marvelcdb.com.evil.example/decklist/view/1"))
        assertNull(MarvelCdbDeckUrl.parse("https://notmarvelcdb.com/decklist/view/1"))
    }

    @Test
    fun `rejects marvelcdb urls that are not decks`() {
        assertNull(MarvelCdbDeckUrl.parse("https://marvelcdb.com/card/01001a"))
        assertNull(MarvelCdbDeckUrl.parse("https://marvelcdb.com/decklist/view/notanumber"))
    }

    @Test
    fun `rejects empty input`() {
        assertNull(MarvelCdbDeckUrl.parse(null))
        assertNull(MarvelCdbDeckUrl.parse(""))
        assertNull(MarvelCdbDeckUrl.parse("   "))
    }

    @Test
    fun `builds the right api url for each kind`() {
        assertEquals(
            "https://marvelcdb.com/api/public/decklist/12345",
            DeckReference(12345, Kind.DECKLIST).apiUrl,
        )
        assertEquals(
            "https://marvelcdb.com/api/public/deck/12345",
            DeckReference(12345, Kind.DECK).apiUrl,
        )
    }

    @Test
    fun `isMarvelCdbLink agrees with parse`() {
        assertEquals(true, MarvelCdbDeckUrl.isMarvelCdbLink("marvelcdb.com/deck/view/1"))
        assertEquals(false, MarvelCdbDeckUrl.isMarvelCdbLink("https://example.com"))
    }
}
