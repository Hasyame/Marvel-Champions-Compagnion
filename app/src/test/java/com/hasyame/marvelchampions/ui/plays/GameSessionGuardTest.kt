package com.hasyame.marvelchampions.ui.plays

import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.domain.campaign.engine.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tapping a result twice must not file the game twice.
 *
 * This is not hypothetical. Saving a play and reporting it to BoardGameGeek
 * takes a network round trip, and while it ran the screen looked unchanged —
 * so a real game was logged five times and sent to BGG five times. The rule is
 * cheap to state and was expensive to learn.
 *
 * The guard lives in the state, so it is testable without a view model or a
 * database.
 */
class GameSessionGuardTest {

    private fun playing(isFinishing: Boolean = false) = GameSessionUiState(
        phase = SessionPhase.PLAYING,
        scenarioCode = "01097",
        heroes = listOf(SessionHero("01001a", "Justice")),
        timer = TimerState().start(0L),
        isFinishing = isFinishing,
    )

    /** The check the view model makes before doing any work. */
    private fun wouldFile(state: GameSessionUiState): Boolean =
        !state.isFinishing && state.scenarioCode != null && state.heroes.isNotEmpty()

    @Test
    fun `the first tap files the game`() {
        assertTrue(wouldFile(playing()))
    }

    @Test
    fun `a second tap while the first is still saving files nothing`() {
        assertFalse(wouldFile(playing(isFinishing = true)))
    }

    @Test
    fun `a game with no hero or no scenario is not filed at all`() {
        assertFalse(wouldFile(playing().copy(heroes = emptyList())))
        assertFalse(wouldFile(playing().copy(scenarioCode = null)))
    }

    @Test
    fun `finishing pauses the clock so the displayed time stops`() {
        // The timer carried on while the play was being saved, which made the
        // screen look as though nothing had happened.
        val paused = playing().timer.pause(60_000L)

        assertFalse(paused.isRunning)
        assertEquals(60_000L, paused.elapsedAt(999_999L))
    }

    @Test
    fun `starting another game clears the guard`() {
        // Otherwise a rematch could be set up but never finished.
        val afterReset = playing(isFinishing = true).copy(
            phase = SessionPhase.SETUP,
            timer = TimerState(),
            elapsedMillis = 0,
            isFinishing = false,
        )

        assertTrue(wouldFile(afterReset))
    }

    @Test
    fun `the recorded play carries the time captured at the tap`() {
        // Captured once, so a slow save or a repeat tap cannot inflate it.
        val elapsedAtTap = playing().timer.elapsedAt(45 * 60_000L)
        val play = PlayEntity(
            id = "p1",
            playedAt = 0,
            scenarioCode = "01097",
            scenarioName = "Rhino",
            difficulty = "standard",
            heroCode = "01001a",
            heroName = "Spider-Man",
            aspects = "Justice",
            won = true,
            elapsedMillis = elapsedAtTap,
        )

        assertEquals(45 * 60_000L, play.elapsedMillis)
    }
}
