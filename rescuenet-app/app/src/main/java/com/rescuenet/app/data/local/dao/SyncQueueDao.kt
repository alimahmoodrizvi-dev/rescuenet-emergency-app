package com.rescuenet.app.data.local.dao

import androidx.room.*
import com.rescuenet.app.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(entry: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue ORDER BY createdAtEpochMs ASC")
    suspend fun getAll(): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue ORDER BY createdAtEpochMs ASC")
    fun observeAll(): Flow<List<SyncQueueEntity>>

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM sync_queue WHERE eventUuid = :eventUuid")
    suspend fun remove(eventUuid: String)

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1, lastAttemptEpochMs = :attemptedAt WHERE eventUuid = :eventUuid")
    suspend fun recordAttempt(eventUuid: String, attemptedAt: Long)
}
