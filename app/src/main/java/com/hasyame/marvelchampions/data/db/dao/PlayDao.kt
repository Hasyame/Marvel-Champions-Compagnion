package com.hasyame.marvelchampions.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import kotlinx.coroutines.flow.Flow

/** One row of a win-rate table. */
data class WinRateRow(
    val key: String,
    val played: Int,
    val won: Int,
    /** Total time spent on these games, so "hours with this hero" is answerable. */
    val totalMillis: Long = 0,
)

@Dao
interface PlayDao {

    /**
     * IGNORE rather than REPLACE. Ids are stable, and a duplicate insert is a
     * double tap or a retry, not an instruction to overwrite a recorded game.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(play: PlayEntity)

    @Query("SELECT * FROM plays ORDER BY playedAt DESC")
    fun observePlays(): Flow<List<PlayEntity>>

    @Query("SELECT * FROM plays WHERE id = :id")
    suspend fun getPlay(id: String): PlayEntity?

    @Query("DELETE FROM plays WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE plays SET reportedToBgg = 1 WHERE id = :id")
    suspend fun markReported(id: String)

    // Counting in SQL rather than in memory: a play history grows for years and
    // there is no reason to carry all of it into the statistics screen.

    @Query(
        """
        SELECT heroName AS `key`, COUNT(*) AS played, SUM(won) AS won, SUM(elapsedMillis) AS totalMillis
        FROM plays GROUP BY heroCode ORDER BY played DESC, `key` ASC
        """,
    )
    fun observeByHero(): Flow<List<WinRateRow>>

    @Query(
        """
        SELECT scenarioName AS `key`, COUNT(*) AS played, SUM(won) AS won, SUM(elapsedMillis) AS totalMillis
        FROM plays GROUP BY scenarioCode ORDER BY played DESC, `key` ASC
        """,
    )
    fun observeByScenario(): Flow<List<WinRateRow>>

    @Query(
        """
        SELECT difficulty AS `key`, COUNT(*) AS played, SUM(won) AS won, SUM(elapsedMillis) AS totalMillis
        FROM plays GROUP BY difficulty ORDER BY played DESC, `key` ASC
        """,
    )
    fun observeByDifficulty(): Flow<List<WinRateRow>>

    /**
     * Solo against multiplayer.
     *
     * Worth its own split because the win rates differ enormously, and a
     * single blended figure describes neither: a player who wins two thirds
     * solo and a third in a group is not a 50% player at anything.
     */
    @Query(
        """
        SELECT CASE WHEN players <= 1 THEN 'solo' ELSE 'group' END AS `key`,
               COUNT(*) AS played, SUM(won) AS won, SUM(elapsedMillis) AS totalMillis
        FROM plays GROUP BY `key` ORDER BY played DESC
        """,
    )
    fun observeBySoloOrGroup(): Flow<List<WinRateRow>>

    /**
     * Aspects are stored as a comma-separated string because a hero can be
     * played with more than one, so they are grouped in memory rather than in
     * SQL. The rows are small — one per play — and splitting a list in SQLite
     * would need a recursive CTE for no benefit.
     */
    @Query("SELECT aspects, won, elapsedMillis FROM plays")
    fun observeAspectRows(): Flow<List<AspectRow>>

    /**
     * Hero and aspect together, which is the pairing a player actually asks
     * about: not "how does Justice do" but "how does Justice do for this
     * hero". Split in memory for the same reason as aspects alone.
     */
    @Query("SELECT heroName, aspects, won, elapsedMillis FROM plays")
    fun observeHeroAspectRows(): Flow<List<HeroAspectRow>>

    /** For a restore, which replaces rather than merges. */
    @Query("DELETE FROM plays")
    suspend fun deleteAll()

    @Query("SELECT * FROM plays")
    suspend fun getAllPlays(): List<PlayEntity>
}

data class AspectRow(val aspects: String, val won: Boolean, val elapsedMillis: Long)

data class HeroAspectRow(
    val heroName: String,
    val aspects: String,
    val won: Boolean,
    val elapsedMillis: Long,
)
