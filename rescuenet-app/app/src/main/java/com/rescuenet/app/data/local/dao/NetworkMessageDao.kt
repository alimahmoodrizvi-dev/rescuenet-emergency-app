package com.rescuenet.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rescuenet.app.data.local.entity.NetworkMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkMessageDao {

    /** IGNORE, not REPLACE: the first copy of a relayed message we see wins — later,
     *  possibly-higher-hop-count copies of the same UUID must not overwrite it. This is the
     *  concrete implementation of Part 21's "never duplicate emergency incidents." */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(message: NetworkMessageEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM network_messages WHERE messageUuid = :messageUuid)")
    suspend fun exists(messageUuid: String): Boolean

    @Query("SELECT * FROM network_messages WHERE deliveredToBackendAtEpochMs IS NULL")
    suspend fun getUndelivered(): List<NetworkMessageEntity>

    @Query("SELECT * FROM network_messages ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<NetworkMessageEntity>>

    @Query("UPDATE network_messages SET deliveredToBackendAtEpochMs = :deliveredAt WHERE messageUuid = :messageUuid")
    suspend fun markDelivered(messageUuid: String, deliveredAt: Long)
}
