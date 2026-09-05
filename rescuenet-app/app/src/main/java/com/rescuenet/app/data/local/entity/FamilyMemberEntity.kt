package com.rescuenet.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rescuenet.app.data.model.SafetyStatus

@Entity(tableName = "family_members")
data class FamilyMemberEntity(
    @PrimaryKey val id: String,
    val name: String,
    val status: SafetyStatus,
    val lastUpdatedEpochMs: Long,
    /** True for the device owner's own status row, used by the "I'm Safe" button. */
    val isSelf: Boolean = false,
)
