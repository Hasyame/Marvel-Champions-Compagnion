package com.hasyame.marvelchampions.data.repository

import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.data.db.entity.PlayHero
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * What actually reaches BoardGameGeek.
 *
 * A three-handed game was arriving as a solo play, because on BGG the number
 * of players *is* the number of player rows and the app was only ever sending
 * one. That is not visible from inside the app — the play looks right in the
 * history and wrong only on the website — so it is pinned here.
 */
class BggPayloadTest {

    /** 6 August 2026, 21:30 local, after a 75-minute game. */
    private val finishedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        .parse("2026-08-06 21:30")!!
        .time

    private fun play(
        roster: List<PlayHero> = emptyList(),
        elapsedMinutes: Long = 75,
    ) = PlayEntity(
        id = "p1",
        playedAt = finishedAt,
        scenarioCode = "zola",
        scenarioName = "Zola",
        difficulty = "Standard",
        heroCode = "01001",
        heroName = "Spider-Man",
        aspects = "Justice",
        roster = roster,
        won = true,
        elapsedMillis = elapsedMinutes * 60_000,
        victoryPoints = 4,
    )

    private fun bgg(entity: PlayEntity) = entity.toBggPlay("benoit")

    @Test
    fun `a solo game sends one seat`() {
        val payload = bgg(play(roster = listOf(PlayHero("01001", "Spider-Man", "Justice"))))

        assertEquals(1, payload.players.size)
        assertEquals("benoit", payload.players.single().username)
    }

    @Test
    fun `a three handed game sends three seats`() {
        // The bug: this used to send one, so BGG recorded a solo play.
        val payload = bgg(
            play(
                roster = listOf(
                    PlayHero("01001", "Spider-Man", "Justice"),
                    PlayHero("01029", "She-Hulk", "Aggression"),
                    PlayHero("01004", "Iron Man", "Leadership"),
                ),
            ),
        )

        assertEquals(3, payload.players.size)
        assertEquals(
            listOf("benoit", "She-Hulk", "Iron Man"),
            payload.players.map { it.name },
        )
    }

    @Test
    fun `only the account holder carries a username`() {
        // The other seats are real people the app has no account for. Naming
        // them after the hero is honest; inventing a BGG username would not be.
        val payload = bgg(
            play(
                roster = listOf(
                    PlayHero("01001", "Spider-Man", "Justice"),
                    PlayHero("01029", "She-Hulk", "Aggression"),
                ),
            ),
        )

        assertEquals("benoit", payload.players.first().username)
        assertEquals("", payload.players.last().username)
    }

    @Test
    fun `the play is filed on the day it finished`() {
        assertEquals("2026-08-06", bgg(play()).playedOn)
    }

    @Test
    fun `the comment carries the start and finish times`() {
        // BGG has no field for either, so the comment is the only place they
        // can go. 75 minutes before 21:30 is 20:15.
        val comment = bgg(play(elapsedMinutes = 75)).comment

        assertTrue(comment, "Played 2026-08-06, 20:15–21:30" in comment)
    }

    @Test
    fun `length is still reported in minutes`() {
        assertEquals(75, bgg(play(elapsedMinutes = 75)).lengthMinutes)
    }

    @Test
    fun `a roster the app never recorded still sends the one seat it knows`() {
        // Plays logged before the roster column existed have an empty roster.
        // Dropping to zero seats would be worse than reporting a solo game.
        val payload = bgg(play(roster = emptyList()))

        assertEquals(1, payload.players.size)
        assertEquals("benoit", payload.players.single().username)
    }
}
