package com.hasyame.marvelchampions.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A campaign run.
 *
 * Only the identity and the timer live here. **All campaign state is derived
 * from [CampaignEventEntity]** by folding, so nothing about counters, flags or
 * progress is stored — storing it would let the two disagree.
 */
@Entity(tableName = "campaign_runs")
data class CampaignRunEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val templateName: String,
    val difficulty: String,
    val createdAt: Long,
    val finished: Boolean = false,
    /** The template JSON as imported, so a run stays readable if the file moves. */
    val templateJson: String,

    // Timer, wall-clock based so it survives a reboot.
    val timerAccumulatedMillis: Long = 0,
    val timerRunningSince: Long? = null,
    val timerScenarioId: String? = null,
)

/**
 * One entry of the append-only log.
 *
 * [id] is stable and generated once, which is what makes merging two devices'
 * logs idempotent.
 */
@Entity(
    tableName = "campaign_events",
    foreignKeys = [
        ForeignKey(
            entity = CampaignRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("runId"), Index("timestamp")],
)
data class CampaignEventEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val timestamp: Long,
    /** The serialised CampaignEvent. Kept whole so new event types can be added. */
    val payload: String,
)
