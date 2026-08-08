package com.hasyame.marvelchampions.domain.randomizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Civil War and She-Hulk share a modular pool that belongs to them.
 *
 * Those sets are legal only in a Civil War or She-Hulk game, and every other
 * scenario draws from everything except them. Both halves matter: without the
 * second, Hell's Kitchen turns up in Rhino, which it did, because MarvelCDB has
 * entered all fifteen Civil War sets and none of its scenarios.
 *
 * A Civil War game is also a pair — a side plus the hero you face — and takes
 * three or four sets rather than one, decided at the table.
 */
class RestrictedModularPoolTest {

    private val rhino = SetRef("rhino", "core")
    private val civilWar = SetRef("cw_resistance_iron_man", "cw")

    private val pools = RandomizerPools(
        scenarios = listOf(rhino, civilWar),
        modularSets = listOf(
            SetRef("bomb_scare", "core"),
            SetRef("masters_of_evil", "core"),
            SetRef("under_attack", "core"),
            SetRef("hells_kitchen", "cw"),
            SetRef("martial_law", "cw"),
            SetRef("new_avengers", "cw"),
            SetRef("secret_avengers", "cw"),
            SetRef("royal_guard", "shulk"),
        ),
        heroes = listOf(HeroRef("01001", "core")),
        aspects = listOf("justice"),
        difficulties = listOf(Difficulty.STANDARD_I),
    )

    private val rules = mapOf(
        "rhino" to ScenarioRule(code = "rhino", packCode = "core", modularCount = 1),
        "cw_resistance_iron_man" to ScenarioRule(
            code = "cw_resistance_iron_man",
            packCode = "cw",
            modularCount = 3,
            modularCountMax = 4,
            modularPacks = listOf("cw", "shulk"),
        ),
    )

    private fun draw(scenario: SetRef, seed: Int) = ScenarioRandomizer.draw(
        pools = pools,
        rules = rules,
        previous = RandomizerDraw(scenarioCode = scenario.code),
        locked = setOf(DrawField.SCENARIO),
        random = Random(seed),
    )

    @Test
    fun `an ordinary scenario never draws a Civil War set`() {
        val restricted = setOf(
            "hells_kitchen", "martial_law", "new_avengers", "secret_avengers", "royal_guard",
        )

        repeat(40) { seed ->
            val drawn = draw(rhino, seed).modularSetCodes
            assertTrue(
                "seed $seed put a restricted set into Rhino: $drawn",
                drawn.none { it in restricted },
            )
        }
    }

    @Test
    fun `a Civil War game draws only from Civil War and She-Hulk`() {
        val theirs = setOf(
            "hells_kitchen", "martial_law", "new_avengers", "secret_avengers", "royal_guard",
        )

        repeat(40) { seed ->
            val drawn = draw(civilWar, seed).modularSetCodes
            assertTrue(
                "seed $seed drew something from outside the pool: $drawn",
                drawn.all { it in theirs },
            )
        }
    }

    @Test
    fun `a Civil War game takes three or four sets`() {
        val counts = (0 until 60).map { draw(civilWar, it).modularSetCodes.size }.toSet()

        assertEquals(
            "a variable count should produce both, and nothing else",
            setOf(3, 4),
            counts,
        )
    }

    @Test
    fun `an ordinary scenario still takes exactly what its rule says`() {
        repeat(20) { seed ->
            assertEquals(1, draw(rhino, seed).modularSetCodes.size)
        }
    }
}
