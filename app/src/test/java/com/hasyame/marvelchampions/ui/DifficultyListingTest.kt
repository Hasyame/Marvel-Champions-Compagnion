package com.hasyame.marvelchampions.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * No screen may offer every difficulty.
 *
 * A difficulty is a set of encounter cards that came in a box: Standard I and
 * Expert I in the Core Set, Standard II and Expert II with The Hood, Standard
 * III with The Age of Apocalypse. Offering all five to everybody tells somebody
 * to play a difficulty sitting in a shop.
 *
 * This was fixed three separate times — the draw, then the scenario picker,
 * then the filter chips and the manual setup screen — because each place had
 * its own copy of `Difficulty.entries`. A source-level guard is crude, but the
 * alternative demonstrably was finding it a fourth time from a bug report.
 *
 * `pools.difficulties` is the list to render. It is already filtered by what
 * the collection owns.
 */
class DifficultyListingTest {

    @Test
    fun `no ui file renders the whole difficulty enum`() {
        val ui = File("src/main/java/com/hasyame/marvelchampions/ui")
        val offenders = ui.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                file.readLines().any { line ->
                    // Rendering, not parsing: firstOrNull { it.name == stored }
                    // turns a saved string back into a value and is fine.
                    "Difficulty.entries" in line &&
                        ("forEach" in line || "map" in line || "toSet" in line)
                }
            }
            .map { it.name }
            .toList()

        assertEquals(
            "these render every difficulty instead of pools.difficulties",
            emptyList<String>(),
            offenders,
        )
    }
}
