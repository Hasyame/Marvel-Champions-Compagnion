package com.hasyame.marvelchampions.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hasyame.marvelchampions.data.db.entity.CampaignEventEntity
import com.hasyame.marvelchampions.data.db.entity.CampaignRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CampaignDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRun(run: CampaignRunEntity)

    @Query("SELECT * FROM campaign_runs ORDER BY finished, createdAt DESC")
    fun observeRuns(): Flow<List<CampaignRunEntity>>

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
}
