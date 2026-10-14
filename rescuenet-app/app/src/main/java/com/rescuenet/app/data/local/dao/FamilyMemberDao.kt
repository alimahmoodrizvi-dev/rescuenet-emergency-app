package com.rescuenet.app.data.local.dao

import androidx.room.*
import com.rescuenet.app.data.local.entity.FamilyMemberEntity
import com.rescuenet.app.data.model.SafetyStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyMemberDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(member: FamilyMemberEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun upsertAll(members: List<FamilyMemberEntity>)

    @Query("SELECT * FROM family_members ORDER BY isSelf DESC, name ASC")
    fun observeAll(): Flow<List<FamilyMemberEntity>>

    @Query("UPDATE family_members SET status = :status, lastUpdatedEpochMs = :updatedAt WHERE isSelf = 1")
    suspend fun updateSelfStatus(status: SafetyStatus, updatedAt: Long)

    @Query("UPDATE family_members SET status = :status, lastUpdatedEpochMs = :updatedAt WHERE name = :name")
    suspend fun updateStatusByName(name: String, status: SafetyStatus, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM family_members")
    suspend fun count(): Int

    /** Never deletes the device owner's own row, even if called with its id. */
    @Query("DELETE FROM family_members WHERE id = :id AND isSelf = 0")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM family_members WHERE LOWER(name) = LOWER(:name)")
    suspend fun countByName(name: String): Int
}
