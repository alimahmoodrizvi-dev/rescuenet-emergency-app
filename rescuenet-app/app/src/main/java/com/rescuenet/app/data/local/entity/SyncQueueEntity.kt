package com.rescuenet.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per event awaiting delivery to the backend — an incident report, a safety-status
 * update, or (in Phase 4) a relayed NetworkMessage payload from another device. This is the
 * "Local emergency queue" box in the Part 21 offline-first architecture diagram.
 *
 * `entityType`/`entityId` point back at the source row (e.g. "incident" / eventUuid) so the
 * worker can look up the latest data rather than trusting a possibly-stale copy in the queue.
 */
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey val eventUuid: String,
    val entityType: String,       // "incident" | "safety_status" | "relayed_message"
    val entityId: String,
    val payloadJson: String,
    val retryCount: Int = 0,
    val createdAtEpochMs: Long,
    val lastAttemptEpochMs: Long? = null,
)
