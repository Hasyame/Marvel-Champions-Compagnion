package com.hasyame.marvelchampions.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchNormalizerTest {

    @Test
    fun `strips accents so an unaccented query matches accented text`() {
        assertEquals("strategie", SearchNormalizer.normalize("Stratégie"))
        assertEquals("equipe de surveillance", SearchNormalizer.normalize("Équipe de Surveillance"))
        assertEquals("degats", SearchNormalizer.normalize("dégâts"))
    }

    @Test
    fun `lowercases`() {
        assertEquals("gamma slam", SearchNormalizer.normalize("Gamma Slam"))
    }

    @Test
    fun `splits on punctuation so hyphenated names are found by their parts`() {
        assertEquals("spider man", SearchNormalizer.normalize("Spider-Man"))
        assertEquals("sp dr", SearchNormalizer.normalize("SP//dr"))
        assertEquals("s h i e l d", SearchNormalizer.normalize("S.H.I.E.L.D."))
    }

    @Test
    fun `removes the HTML markup MarvelCDB embeds in card text`() {
        assertEquals(
            "hero action attaque infligez x degats a un ennemi",
            SearchNormalizer.normalize(
                "<b>Hero Action</b> <i>(attaque)</i> : infligez X dégâts à un ennemi",
            ),
        )
    }

    @Test
    fun `handles null and blank input`() {
        assertEquals("", SearchNormalizer.normalize(null))
        assertEquals("", SearchNormalizer.normalize(""))
        assertEquals("", SearchNormalizer.normalize("   "))
    }

    @Test
    fun `builds a prefix match query so results appear while typing`() {
        assertEquals("spid* ma*", SearchNormalizer.toPrefixMatchQuery("Spid Ma"))
        assertEquals("strategie*", SearchNormalizer.toPrefixMatchQuery("Stratégie"))
    }

    @Test
    fun `returns null for a query with no searchable content`() {
        // Callers must treat this as "no filter", not "no results".
        assertNull(SearchNormalizer.toPrefixMatchQuery(""))
        assertNull(SearchNormalizer.toPrefixMatchQuery("   "))
        assertNull(SearchNormalizer.toPrefixMatchQuery("--- ..."))
    }

    @Test
    fun `normalizing is idempotent`() {
        val once = SearchNormalizer.normalize("Équipe de Surveillance — <b>Action</b>")
        assertEquals(once, SearchNormalizer.normalize(once))
    }
}
