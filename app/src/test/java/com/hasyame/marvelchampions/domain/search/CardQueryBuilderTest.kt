package com.hasyame.marvelchampions.domain.search

import com.hasyame.marvelchampions.domain.model.CardFilter
import com.hasyame.marvelchampions.domain.model.CardLocale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardQueryBuilderTest {

    @Test
    fun `an empty filter still scopes to the locale`() {
        val query = CardQueryBuilder.build(CardFilter(), CardLocale.FRENCH)

        assertTrue(query.sql.contains("cards.locale = ?"))
        // locale, limit, offset
        assertEquals(listOf<Any>("fr", 200, 0), query.args)
    }

    @Test
    fun `no search text means no fts join`() {
        val query = CardQueryBuilder.build(CardFilter(), CardLocale.FRENCH)

        assertFalse(
            "an empty query must not pay for the FTS join",
            query.sql.contains("cards_fts"),
        )
    }

    @Test
    fun `search text joins fts with a normalised prefix match`() {
        val query = CardQueryBuilder.build(
            CardFilter(query = "Stratégie"),
            CardLocale.FRENCH,
        )

        assertTrue(query.sql.contains("cards_fts MATCH ?"))
        assertEquals("strategie*", query.args.first())
    }

    @Test
    fun `multiple packs become a single IN clause`() {
        val query = CardQueryBuilder.build(
            CardFilter(packCodes = setOf("core", "gmw")),
            CardLocale.ENGLISH,
        )

        assertTrue(query.sql.contains("cards.packCode IN (?,?)"))
        assertTrue(query.args.containsAll(listOf("core", "gmw")))
    }

    @Test
    fun `owned only restricts to the owned pack codes`() {
        val query = CardQueryBuilder.build(
            filter = CardFilter(ownedOnly = true),
            locale = CardLocale.FRENCH,
            ownedPackCodes = setOf("core", "magneto"),
        )

        assertTrue(query.sql.contains("cards.packCode IN (?,?)"))
        assertTrue(query.args.containsAll(listOf("core", "magneto")))
    }

    @Test
    fun `owned only with an empty collection yields no results rather than bad sql`() {
        val query = CardQueryBuilder.build(
            filter = CardFilter(ownedOnly = true),
            locale = CardLocale.FRENCH,
            ownedPackCodes = emptySet(),
        )

        // "IN ()" is a syntax error in SQLite, so this must be a false literal.
        assertFalse(query.sql.contains("IN ()"))
        assertTrue(query.sql.contains(" AND 0"))
    }

    @Test
    fun `cost bounds are inclusive on both ends`() {
        val query = CardQueryBuilder.build(
            CardFilter(minCost = 1, maxCost = 3),
            CardLocale.ENGLISH,
        )

        assertTrue(query.sql.contains("cards.cost >= ?"))
        assertTrue(query.sql.contains("cards.cost <= ?"))
        assertTrue(query.args.containsAll(listOf(1, 3)))
    }

    @Test
    fun `traits are matched against the normalised column`() {
        val query = CardQueryBuilder.build(
            CardFilter(traits = setOf("Héros")),
            CardLocale.FRENCH,
        )

        assertTrue(query.sql.contains("cards.searchTraits LIKE ?"))
        assertTrue(query.args.contains("%heros%"))
    }

    @Test
    fun `every placeholder has exactly one argument`() {
        val query = CardQueryBuilder.build(
            filter = CardFilter(
                query = "spider",
                packCodes = setOf("core", "gmw"),
                typeCodes = setOf("ally"),
                factionCodes = setOf("justice", "leadership"),
                traits = setOf("Avenger"),
                minCost = 1,
                maxCost = 4,
                ownedOnly = true,
            ),
            locale = CardLocale.FRENCH,
            ownedPackCodes = setOf("core"),
        )

        // A mismatch here is the classic cause of a runtime bind error, and it
        // is invisible until the query actually runs.
        assertEquals(query.sql.count { it == '?' }, query.args.size)
    }

    @Test
    fun `results are ordered deterministically`() {
        val query = CardQueryBuilder.build(CardFilter(), CardLocale.FRENCH)

        assertTrue(query.sql.contains("ORDER BY cards.packCode, cards.position"))
    }
}
