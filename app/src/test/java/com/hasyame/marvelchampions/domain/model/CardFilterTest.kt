package com.hasyame.marvelchampions.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardFilterTest {

    @Test
    fun `a default filter is empty`() {
        assertTrue(CardFilter().isEmpty)
        assertEquals(0, CardFilter().activeCount)
    }

    @Test
    fun `a query alone does not count as an active filter`() {
        // The search box is always visible, so counting it would leave the
        // filter badge permanently lit.
        val filter = CardFilter(query = "spider")

        assertFalse(filter.isEmpty)
        assertEquals(0, filter.activeCount)
    }

    @Test
    fun `a cost range counts once, not twice`() {
        assertEquals(1, CardFilter(minCost = 1, maxCost = 3).activeCount)
        assertEquals(1, CardFilter(minCost = 1).activeCount)
    }

    @Test
    fun `each dimension counts once regardless of how many values it holds`() {
        val filter = CardFilter(
            packCodes = setOf("core", "gmw", "mts"),
            typeCodes = setOf("ally"),
        )

        assertEquals(2, filter.activeCount)
    }

    @Test
    fun `owned only counts as an active filter`() {
        assertEquals(1, CardFilter(ownedOnly = true).activeCount)
        assertFalse(CardFilter(ownedOnly = true).isEmpty)
    }
}
