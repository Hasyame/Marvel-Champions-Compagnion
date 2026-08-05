package com.hasyame.marvelchampions.domain.randomizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Modular sets the player has not got.
 *
 * Owning a pack is not the same as owning every modular set in it, and the
 * draw cannot know which are missing. Excluding one keeps it out of the draw —
 * and keeps out any scenario that requires it, because a scenario you cannot
 * field is not a scenario you can be offered.
 */
class ExcludedModularSetsTest {

    private val pools = RandomizerPools(
        scenarios = listOf(SetRef("plain", "p"), SetRef("needsBomb", "p")),
        modularSets = listOf(SetRef("bomb", "p"), SetRef("goons", "p")),
        heroes = listOf(HeroRef("hero", "p")),
        aspects = listOf("Justice"),
    )

    private val rules = mapOf(
        "needsBomb" to ScenarioRule(
            code = "needsBomb",
            packCode = "p",
            modularCount = 1,
            mandatoryModulars = listOf("bomb"),
        ),
        "plain" to ScenarioRule(code = "plain", packCode = "p", modularCount = 1),
    )

    private fun draw(filters: RandomizerFilters, seed: Int) = ScenarioRandomizer.draw(
        pools = pools,
        rules = rules,
        filters = filters,
        random = Random(seed),
    )

    @Test
    fun `an excluded set is never drawn`() {
        val filters = RandomizerFilters(excludedModularSets = setOf("bomb"))

        repeat(40) { seed ->
            assertFalse("bomb" in draw(filters, seed).modularSetCodes)
        }
    }

    @Test
    fun `a scenario that requires an excluded set is not offered`() {
        // Benoit's rule: if you cannot field a set the scenario requires, the
        // scenario is not playable, so it is not drawn at all.
        val filters = RandomizerFilters(excludedModularSets = setOf("bomb"))

        repeat(40) { seed ->
            assertEquals("plain", draw(filters, seed).scenarioCode)
        }
    }

    @Test
    fun `excluding nothing leaves the draw alone`() {
        val drawn = (0 until 40).map { draw(RandomizerFilters(), it) }

        assertTrue("needsBomb" in drawn.mapNotNull { it.scenarioCode })
        assertTrue(drawn.any { "bomb" in it.modularSetCodes })
    }
}
