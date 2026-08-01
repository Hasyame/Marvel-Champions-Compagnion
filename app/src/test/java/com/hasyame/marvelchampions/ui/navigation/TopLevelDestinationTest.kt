package com.hasyame.marvelchampions.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopLevelDestinationTest {

    @Test
    fun `destinations are in the order specified for the navigation bar`() {
        assertEquals(
            listOf(
                TopLevelDestination.CARDS,
                TopLevelDestination.DECKS,
                TopLevelDestination.CAMPAIGN,
                TopLevelDestination.RANDOMIZER,
                TopLevelDestination.SETTINGS,
            ),
            TopLevelDestination.entries,
        )
    }

    @Test
    fun `there are at most five destinations`() {
        // Material 3 navigation bars support five items; a sixth would silently
        // break the layout rather than fail to compile.
        assertTrue(
            "A navigation bar holds at most 5 items, found ${TopLevelDestination.entries.size}",
            TopLevelDestination.entries.size <= 5,
        )
    }

    @Test
    fun `each destination has a distinct graph and start route`() {
        val graphRoutes = TopLevelDestination.entries.map { it.graphRoute }
        val routes = TopLevelDestination.entries.map { it.route }

        assertEquals(graphRoutes.size, graphRoutes.distinct().size)
        assertEquals(routes.size, routes.distinct().size)
        assertTrue(
            "A tab's graph route must differ from its start route, otherwise the " +
                "nested graph collapses and the tab loses its own back stack",
            graphRoutes.none { it in routes },
        )
    }
}
