package com.rescuenet.app.data.local.dao

import androidx.room.*
import com.rescuenet.app.data.local.entity.IncidentEntity
import com.rescuenet.app.data.model.SyncState
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(incident: IncidentEntity)

    @Query("SELECT * FROM incidents ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE eventUuid = :eventUuid")
    suspend fun getByUuid(eventUuid: String): IncidentEntity?

    @Query("SELECT * FROM incidents WHERE syncState != :synced")
    suspend fun getUnsynced(synced: SyncState = SyncState.SYNCED): List<IncidentEntity>

    @Query("UPDATE incidents SET syncState = :state, updatedAtEpochMs = :updatedAt WHERE eventUuid = :eventUuid")
    suspend fun updateSyncState(eventUuid: String, state: SyncState, updatedAt: Long)
}
