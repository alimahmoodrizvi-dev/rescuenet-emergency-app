package com.rescuenet.app.data.repository

import com.rescuenet.app.data.local.dao.UserProfileDao
import com.rescuenet.app.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Authentication" at Phase 3 scope: an anonymous device-bound profile, matching the Part 19
 * privacy principle that core citizen features never require a real-name account. Real
 * backend authentication (for Volunteer/Operator/Admin roles, Part 24) arrives in Phase 5 —
 * at that point this profile's deviceUuid becomes the identifier the backend links a role to.
 */
@Singleton
class UserRepository @Inject constructor(
    private val userProfileDao: UserProfileDao,
) {
    fun observeProfile(): Flow<UserProfileEntity?> = userProfileDao.observe()

    suspend fun ensureProfileExists(): UserProfileEntity {
        userProfileDao.get()?.let { return it }
        val profile = UserProfileEntity(
            deviceUuid = UUID.randomUUID().toString(),
            displayName = null,
            languagePref = "en",
            onboardingComplete = false,
            createdAtEpochMs = System.currentTimeMillis(),
        )
        userProfileDao.upsert(profile)
        return profile
    }

    suspend fun completeOnboarding(languagePref: String) {
        val current = userProfileDao.get() ?: ensureProfileExists()
        userProfileDao.upsert(current.copy(languagePref = languagePref, onboardingComplete = true))
    }
}
