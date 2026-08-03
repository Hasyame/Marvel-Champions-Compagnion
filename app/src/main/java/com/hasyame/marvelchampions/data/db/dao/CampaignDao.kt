package com.hasyame.marvelchampions.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hasyame.marvelchampions.data.db.entity.CampaignEventEntity
import com.hasyame.marvelchampions.data.db.entity.CampaignRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CampaignDao {

    /**
     * Creates a run.
     *
     * Deliberately **not** `onConflict = REPLACE`. In SQLite that is a DELETE
     * followed by an INSERT, and `campaign_events` references this table with
     * ON DELETE CASCADE — so replacing a run silently destroys its entire event
     * log, which is the only place campaign state exists. Updates go through
     * [updateRun] and the targeted queries below.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRun(run: CampaignRunEntity)

    /** An UPDATE statement, so child rows are untouched. */
    @Update
    suspend fun updateRun(run: CampaignRunEntity)

    @Query(
        """
        UPDATE campaign_runs
        SET timerAccumulatedMillis = :accumulated,
            timerRunningSince = :runningSince,
            timerScenarioId = :scenarioId
        WHERE id = :runId
        """,
    )
    suspend fun updateTimer(
        runId: String,
        accumulated: Long,
        runningSince: Long?,
        scenarioId: String?,
    )

    @Query("UPDATE campaign_runs SET finished = :finished WHERE id = :runId")
    suspend fun setFinished(runId: String, finished: Boolean)

    @Query("UPDATE campaign_runs SET templateJson = :templateJson WHERE id = :runId")
    suspend fun setTemplateJson(runId: String, templateJson: String)

    @Query("SELECT * FROM campaign_runs ORDER BY finished, createdAt DESC")
    fun observeRuns(): Flow<List<CampaignRunEntity>>

    @Query("SELECT * FROM campaign_runs ORDER BY finished, createdAt DESC")
    suspend fun getRuns(): List<CampaignRunEntity>

    @Query("SELECT * FROM campaign_runs WHERE id = :id")
    suspend fun getRun(id: String): CampaignRunEntity?

    @Query("SELECT * FROM campaign_runs WHERE id = :id")
    fun observeRun(id: String): Flow<CampaignRunEntity?>

    @Query("DELETE FROM campaign_runs WHERE id = :id")
    suspend fun deleteRun(id: String)

    /**
     * Appends an event. IGNORE rather than REPLACE: the log is append-only and
     * ids are stable, so re-inserting the same event during a device merge must
     * be a no-op rather than a rewrite.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun appendEvent(event: CampaignEventEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun appendEvents(events: List<CampaignEventEntity>)

    @Query("SELECT * FROM campaign_events WHERE runId = :runId ORDER BY timestamp, id")
    suspend fun getEvents(runId: String): List<CampaignEventEntity>

    @Query("SELECT * FROM campaign_events WHERE runId = :runId ORDER BY timestamp, id")
    fun observeEvents(runId: String): Flow<List<CampaignEventEntity>>

    @Query("SELECT COUNT(*) FROM campaign_events WHERE runId = :runId")
    suspend fun countEvents(runId: String): Int

    /**
     * For a restore, which replaces rather than merges.
     *
     * Events cascade with their run, so this empties both tables.
     */
    @Query("DELETE FROM campaign_runs")
    suspend fun deleteAllRuns()
}
