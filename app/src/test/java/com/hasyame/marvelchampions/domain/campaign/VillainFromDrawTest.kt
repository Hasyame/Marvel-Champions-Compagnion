package com.hasyame.marvelchampions.domain.campaign

import com.hasyame.marvelchampions.domain.campaign.template.BaseSetup
import com.hasyame.marvelchampions.domain.campaign.template.villainStages
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A villain the campaign draws rather than the scenario names.
 *
 * Fear No Evil pairs a scenario with whichever subordinate has not been fought
 * yet, and each brings its own stages — different ones on Expert. Every other
 * campaign writes the deck into the scenario, so the drawn case has to fall
 * back cleanly rather than replace that.
 */
class VillainFromDrawTest {

    private val drawn = BaseSetup(
        villainDeckFromDraw = "villain",
        villainDecks = mapOf(
            "bullseye" to mapOf(
                "standard" to listOf("b1", "b2"),
                "expert" to listOf("b2", "b3"),
            ),
            "electro" to mapOf(
                "standard" to listOf("e1", "e2"),
                "expert" to listOf("e2", "e3"),
            ),
        ),
    )

    @Test
    fun `the drawn villain brings its own stages`() {
        assertEquals(listOf("b1", "b2"), drawn.villainStages("standard", "bullseye"))
        assertEquals(listOf("e1", "e2"), drawn.villainStages("standard", "electro"))
    }

    @Test
    fun `expert gets that villain's expert stages`() {
        assertEquals(listOf("b2", "b3"), drawn.villainStages("expert", "bullseye"))
    }

    @Test
    fun `nothing drawn yet falls back rather than blanking the briefing`() {
        // The briefing can render before the draw lands. Showing an empty
        // villain deck would read as a broken scenario.
        val withFallback = drawn.copy(villainDeck = mapOf("standard" to listOf("placeholder")))

        assertEquals(listOf("placeholder"), withFallback.villainStages("standard", null))
    }

    @Test
    fun `a villain with no stages for this difficulty falls back too`() {
        val partial = drawn.copy(
            villainDecks = mapOf("bullseye" to mapOf("standard" to emptyList())),
            villainDeck = mapOf("standard" to listOf("written")),
        )

        assertEquals(listOf("written"), partial.villainStages("standard", "bullseye"))
    }

    @Test
    fun `a scenario that names its villain is untouched`() {
        // Every campaign shipped so far. The drawn path must not disturb them.
        val fixed = BaseSetup(
            villainDeck = mapOf("standard" to listOf("x1", "x2"), "expert" to listOf("x2", "x3")),
        )

        assertEquals(listOf("x1", "x2"), fixed.villainStages("standard", null))
        assertEquals(listOf("x2", "x3"), fixed.villainStages("expert", "ignored"))
    }
}
