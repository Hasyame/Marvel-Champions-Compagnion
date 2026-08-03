package com.hasyame.marvelchampions.ui.plays

import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaysSummaryTest {

    private fun play(
        id: String,
        won: Boolean,
        minutes: Long = 0,
        campaignRunId: String? = null,
    ) = PlayEntity(
        id = id,
        playedAt = 0,
        scenarioCode = "s",
        scenarioName = "Scenario",
        difficulty = "standard",
        heroCode = "h",
        heroName = "Hero",
        aspects = "Justice",
        won = won,
        elapsedMillis = minutes * 60_000L,
        campaignRunId = campaignRunId,
    )

    private fun summarise(plays: List<PlayEntity>) =
        summarise(
            plays,
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
        )

    @Test
    fun `an empty history counts to nothing rather than dividing by zero`() {
        val stats = summarise(emptyList())

        assertEquals(0, stats.totalPlayed)
        assertEquals(0L, stats.averageMillis)
        assertEquals(0, stats.bestStreak)
    }

    @Test
    fun `untimed games count towards the total but not the average`() {
        // A play logged without a duration is a game nobody timed, not a
        // nought-minute game. Averaging it in would drag the figure down.
        val stats = summarise(
            listOf(
                play("a", won = true, minutes = 60),
                play("b", won = true, minutes = 40),
                play("c", won = true, minutes = 0),
            ),
        )

        assertEquals(100 * 60_000L, stats.totalMillis)
        assertEquals(50 * 60_000L, stats.averageMillis)
        assertEquals(60 * 60_000L, stats.longestMillis)
    }

    @Test
    fun `the current streak counts back from the most recent game only`() {
        // Newest first, so a loss anywhere in the run ends the current streak
        // even though a longer one exists further back.
        val stats = summarise(
            listOf(
                play("newest", won = true),
                play("b", won = true),
                play("c", won = false),
                play("d", won = true),
                play("e", won = true),
                play("oldest", won = true),
            ),
        )

        assertEquals(2, stats.currentStreak)
        assertEquals(3, stats.bestStreak)
        assertEquals(5, stats.totalWon)
    }

    @Test
    fun `a history of only losses has no streak`() {
        val stats = summarise(listOf(play("a", won = false), play("b", won = false)))

        assertEquals(0, stats.currentStreak)
        assertEquals(0, stats.bestStreak)
    }

    @Test
    fun `campaign scenarios are counted separately from one-off games`() {
        val stats = summarise(
            listOf(
                play("a", won = true, campaignRunId = "run-1"),
                play("b", won = true, campaignRunId = "run-1"),
                play("c", won = false),
            ),
        )

        assertEquals(3, stats.totalPlayed)
        assertEquals(2, stats.campaignPlays)
    }
}
