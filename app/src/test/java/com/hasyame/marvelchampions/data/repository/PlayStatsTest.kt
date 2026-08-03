package com.hasyame.marvelchampions.data.repository

import com.hasyame.marvelchampions.data.db.dao.PlayStatsRow
import com.hasyame.marvelchampions.data.db.entity.PlayHero
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayStatsTest {

    private fun row(
        vararg seats: Pair<String, String>,
        won: Boolean = true,
        millis: Long = 0,
    ) = PlayStatsRow(
        heroCode = seats.firstOrNull()?.first.orEmpty(),
        heroName = seats.firstOrNull()?.first.orEmpty(),
        aspects = seats.map { it.second }.distinct().joinToString(", "),
        otherHeroes = seats.drop(1).joinToString(", ") { it.first },
        roster = seats.map { PlayHero(code = it.first, name = it.first, aspect = it.second) },
        won = won,
        elapsedMillis = millis,
    )

    /** An old row: the roster column is empty, as it is for every play before v10. */
    private fun legacyRow(
        first: String,
        others: List<String> = emptyList(),
        aspects: String = "",
        won: Boolean = true,
    ) = PlayStatsRow(
        heroCode = first,
        heroName = first,
        aspects = aspects,
        otherHeroes = others.joinToString(", "),
        roster = emptyList(),
        won = won,
        elapsedMillis = 0,
    )

    @Test
    fun `every hero at the table is counted, not only the first`() {
        // The bug: a four-player win credited one hero and dropped three.
        val stats = PlayStats.heroes(
            listOf(
                row(
                    "Spider-Man" to "Justice",
                    "Thor" to "Aggression",
                    "Ms Marvel" to "Protection",
                    "Captain Marvel" to "Leadership",
                ),
            ),
        )

        assertEquals(4, stats.size)
        assertEquals(setOf("Spider-Man", "Thor", "Ms Marvel", "Captain Marvel"), stats.map { it.key }.toSet())
        stats.forEach { assertEquals(1, it.played) }
        stats.forEach { assertEquals(1, it.won) }
    }

    @Test
    fun `a hero-aspect pairing uses that hero's own aspect, not the whole table's`() {
        // The second bug: pairing the first hero against every aspect anyone
        // brought invented combinations nobody played.
        val stats = PlayStats.heroAspects(
            List(2) { row("Spider-Man" to "Justice", "Thor" to "Aggression") },
        )

        assertEquals(
            listOf("Spider-Man · Justice", "Thor · Aggression"),
            stats.map { it.key }.sorted(),
        )
        assertNull(stats.firstOrNull { it.key == "Spider-Man · Aggression" })
    }

    @Test
    fun `one game counts once per hero even when two of them share an aspect`() {
        val stats = PlayStats.aspects(
            listOf(row("Spider-Man" to "Justice", "Ms Marvel" to "Justice")),
        )

        assertEquals(1, stats.size)
        assertEquals("Justice", stats.first().key)
        // Two seats, but one game: the aspect did not win twice.
        assertEquals(1, stats.first().played)
    }

    @Test
    fun `a loss counts against every hero who was there`() {
        val stats = PlayStats.heroes(
            listOf(row("Spider-Man" to "Justice", "Thor" to "Aggression", won = false)),
        )

        assertEquals(2, stats.size)
        stats.forEach { assertEquals(0, it.won) }
        stats.forEach { assertEquals(1, it.played) }
    }

    @Test
    fun `an old solo play still pairs its hero with its aspects`() {
        // Solo is the one case where an old row can be paired safely: there is
        // only one hero, so the aspects listed are necessarily that hero's.
        val stats = PlayStats.heroAspects(
            List(2) { legacyRow("Spider-Man", aspects = "Justice") },
        )

        assertEquals(listOf("Spider-Man · Justice"), stats.map { it.key })
        assertEquals(2, stats.first().played)
    }

    @Test
    fun `an old group play counts its heroes but invents no pairings`() {
        val old = List(2) {
            legacyRow("Spider-Man", others = listOf("Thor"), aspects = "Justice, Aggression")
        }

        // The names are known, so both heroes are counted.
        assertEquals(setOf("Spider-Man", "Thor"), PlayStats.heroes(old).map { it.key }.toSet())
        // The aspects are known, so both are counted.
        assertEquals(setOf("Justice", "Aggression"), PlayStats.aspects(old).map { it.key }.toSet())
        // Who played which is not known, so nothing is guessed.
        assertEquals(emptyList<String>(), PlayStats.heroAspects(old).map { it.key })
    }

    @Test
    fun `a pairing played only once is left out`() {
        val stats = PlayStats.heroAspects(listOf(row("Spider-Man" to "Justice")))

        assertEquals(emptyList<String>(), stats.map { it.key })
    }

    @Test
    fun `time is credited to each hero who played it`() {
        val stats = PlayStats.heroes(
            listOf(row("Spider-Man" to "Justice", "Thor" to "Aggression", millis = 3_600_000)),
        )

        stats.forEach { assertEquals(3_600_000L, it.totalMillis) }
    }
}
