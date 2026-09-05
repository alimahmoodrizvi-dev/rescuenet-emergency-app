package com.rescuenet.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local-only profile. Per the Part 19/25 privacy architecture, RescueNet does not require
 * a phone number or real-name account to use core emergency features — `deviceUuid` is the
 * anonymous identifier used everywhere until/unless the user opts into an organizational
 * role (Volunteer/Operator/Admin) in Phase 5, which requires real backend authentication.
 */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val deviceUuid: String,
    val displayName: String?,
    val languagePref: String,     // "en" | "ur"
    val onboardingComplete: Boolean,
    val createdAtEpochMs: Long,
)
