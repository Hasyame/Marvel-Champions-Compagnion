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

    @Query("SELECT COUNT(*) FROM plays")
    fun observeCount(): Flow<Int>

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
     * Aspects are stored as a comma-separated string because a hero can be
     * played with more than one, so they are grouped in memory rather than in
     * SQL. The rows are small — one per play — and splitting a list in SQLite
     * would need a recursive CTE for no benefit.
     */
    @Query("SELECT aspects, won, elapsedMillis FROM plays")
    fun observeAspectRows(): Flow<List<AspectRow>>
}

data class AspectRow(val aspects: String, val won: Boolean, val elapsedMillis: Long)
