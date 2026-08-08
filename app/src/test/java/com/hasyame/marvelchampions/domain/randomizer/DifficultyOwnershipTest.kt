package com.hasyame.marvelchampions.domain.randomizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * A difficulty is a set of encounter cards that came in a box.
 *
 * The draw offered all five to everybody, so a player who owns only the Core
 * Set was told to play Standard III — which arrived with The Age of Apocalypse
 * and is sitting in a shop. Nothing in the card database says which box a
 * difficulty came in, so the mapping is stated on the enum and has to be pinned.
 */
class DifficultyOwnershipTest {

    private val scenario = SetRef("rhino", "core")

    private fun pools(vararg difficulties: Difficulty) = RandomizerPools(
        scenarios = listOf(scenario),
        heroes = listOf(HeroRef("01001", "core")),
        aspects = listOf("justice"),
        difficulties = difficulties.toList(),
    )

    @Test
    fun `each difficulty knows the pack it came in`() {
        assertEquals("core", Difficulty.STANDARD_I.packCode)
        assertEquals("core", Difficulty.EXPERT_I.packCode)
        assertEquals("hood", Difficulty.STANDARD_II.packCode)
        assertEquals("hood", Difficulty.EXPERT_II.packCode)
        assertEquals("aoa", Difficulty.STANDARD_III.packCode)
    }

    @Test
    fun `a core-only collection is never told to play Standard III`() {
        val core = pools(Difficulty.STANDARD_I, Difficulty.EXPERT_I)

        // Forty seeds: one draw proving nothing is the failure mode here.
        repeat(40) { seed ->
            val draw = ScenarioRandomizer.draw(
                pools = core,
                rules = emptyMap(),
                filters = RandomizerFilters(),
                random = Random(seed),
            )
            assertTrue(
                "seed $seed drew ${draw.difficulty}",
                draw.difficulty in listOf(Difficulty.STANDARD_I, Difficulty.EXPERT_I),
            )
        }
    }

    @Test
    fun `owning The Hood puts its two difficulties in play`() {
        val withHood = pools(
            Difficulty.STANDARD_I,
            Difficulty.EXPERT_I,
            Difficulty.STANDARD_II,
            Difficulty.EXPERT_II,
        )

        val drawn = (0 until 60).map { seed ->
            ScenarioRandomizer.draw(
                pools = withHood,
                rules = emptyMap(),
                filters = RandomizerFilters(),
                random = Random(seed),
            ).difficulty
        }.toSet()

        assertTrue("Standard II never came up", Difficulty.STANDARD_II in drawn)
    }

    @Test
    fun `the collection beats the filter when the two disagree`() {
        // Excluding everything ownable used to leave the difficulty null and the
        // draw unplayable. A difficulty you own is a better answer than none.
        val core = pools(Difficulty.STANDARD_I, Difficulty.EXPERT_I)

        val draw = ScenarioRandomizer.draw(
            pools = core,
            rules = emptyMap(),
            filters = RandomizerFilters(allowedDifficulties = setOf(Difficulty.STANDARD_III)),
            random = Random(1),
        )

        assertTrue(draw.difficulty in listOf(Difficulty.STANDARD_I, Difficulty.EXPERT_I))
    }
}
