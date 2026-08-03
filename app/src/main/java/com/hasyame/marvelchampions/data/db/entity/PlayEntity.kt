package com.hasyame.marvelchampions.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One game that was played.
 *
 * Deliberately flat and self-describing: hero and scenario **names** are stored
 * alongside their codes, so a play from two years ago still reads correctly
 * after a card database rebuild, a language change, or a pack being renamed.
 * A history that needs a live lookup to be legible is a history that eventually
 * stops being legible.
 *
 * Campaign scenarios are logged here too, tagged with [campaignRunId], so win
 * rates cover everything played rather than only one-off games.
 */
@Entity(
    tableName = "plays",
    indices = [
        Index("playedAt"),
        Index("heroCode"),
        Index("scenarioCode"),
    ],
)
data class PlayEntity(
    @PrimaryKey val id: String,

    /** When the game finished, as epoch milliseconds. */
    val playedAt: Long,

    val scenarioCode: String,
    val scenarioName: String,

    /** As recorded: `standard`, `expert`, or a campaign's own difficulty. */
    val difficulty: String,

    /**
     * The hero, for a solo game or the first player in a group.
     *
     * Win rate "per hero" is the question people ask, and it needs one hero per
     * row to answer. Multi-hero games record the rest in [otherHeroes] for
     * display without complicating the statistics.
     */
    val heroCode: String,
    val heroName: String,
    val aspects: String,

    /** Comma-separated names of the other heroes at the table, if any. */
    val otherHeroes: String = "",

    val players: Int = 1,
    val won: Boolean,
    val elapsedMillis: Long = 0,
    val notes: String = "",

    /** Set when the play came from a campaign, so it can be traced back. */
    val campaignRunId: String? = null,

    /** Whether this play has been sent to BoardGameGeek, so it is not sent twice. */
    val reportedToBgg: Boolean = false,
)
