package com.rescuenet.app.data.repository

import com.rescuenet.app.data.local.dao.FamilyMemberDao
import com.rescuenet.app.data.local.dao.SyncQueueDao
import com.rescuenet.app.data.local.entity.FamilyMemberEntity
import com.rescuenet.app.data.local.entity.SyncQueueEntity
import com.rescuenet.app.data.location.LocationProvider
import com.rescuenet.app.data.model.SafetyStatus
import com.rescuenet.app.data.offline.OfflineQueueManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class AddMemberResult { ADDED, EMPTY, DUPLICATE }

@Singleton
class FamilyRepository @Inject constructor(
    private val familyMemberDao: FamilyMemberDao,
    private val syncQueueDao: SyncQueueDao,
    private val offlineQueueManager: OfflineQueueManager,
    private val locationProvider: LocationProvider,
) {
    fun observeMembers(): Flow<List<FamilyMemberEntity>> = familyMemberDao.observeAll()

    /** Seeds demo/starter contacts on first launch only — real "add contact" flow is Phase 5
     *  once there's a backend to resolve a phone number into a linked account. */
    suspend fun seedIfEmpty() {
        if (familyMemberDao.count() > 0) return
        val now = System.currentTimeMillis()
        familyMemberDao.upsertAll(
            listOf(
                FamilyMemberEntity(id = "self", name = "You", status = SafetyStatus.UNKNOWN, lastUpdatedEpochMs = now, isSelf = true),
                FamilyMemberEntity(id = UUID.randomUUID().toString(), name = "Ahmed", status = SafetyStatus.SAFE, lastUpdatedEpochMs = now),
                FamilyMemberEntity(id = UUID.randomUUID().toString(), name = "Sara", status = SafetyStatus.SAFE, lastUpdatedEpochMs = now),
                FamilyMemberEntity(id = UUID.randomUUID().toString(), name = "Ali", status = SafetyStatus.UNKNOWN, lastUpdatedEpochMs = now),
            )
        )
    }

    /**
     * Adds a family contact to this phone. Stored locally only: the database layout is
     * unchanged (adding a column would wipe local data on update), so a contact is a name
     * plus a status. Live status for a contact needs that person's own phone to be linked,
     * which isn't built yet — see the note shown on the Family screen.
     */
    suspend fun addMember(rawName: String): AddMemberResult {
        val name = rawName.trim().take(MAX_NAME_LENGTH)
        if (name.isEmpty()) return AddMemberResult.EMPTY
        if (familyMemberDao.countByName(name) > 0) return AddMemberResult.DUPLICATE
        familyMemberDao.upsert(
            FamilyMemberEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                status = SafetyStatus.UNKNOWN,
                lastUpdatedEpochMs = System.currentTimeMillis(),
            )
        )
        return AddMemberResult.ADDED
    }

    suspend fun removeMember(id: String) = familyMemberDao.deleteById(id)

    /** "I'm Safe" — writes locally first (so the screen reflects it instantly, even fully
     *  offline), then queues it for sync (Part 8: "queue the status locally and transmit it
     *  when connectivity returns"). Where the person is when they say they're safe is attached
     *  if a location is available within a few seconds — but nobody waits on GPS to be safe. */
    suspend fun markSelfSafe() {
        val now = System.currentTimeMillis()
        familyMemberDao.updateSelfStatus(SafetyStatus.SAFE, now)

        val location = withTimeoutOrNull(LOCATION_WAIT_MS) { locationProvider.getCurrentLocation() }
        val locationJson = location?.let { ",\"latitude\":${it.latitude},\"longitude\":${it.longitude}" } ?: ""

        syncQueueDao.enqueue(
            SyncQueueEntity(
                eventUuid = UUID.randomUUID().toString(),
                entityType = "safety_status",
                entityId = "self",
                payloadJson = "{\"status\":\"SAFE\",\"at\":$now$locationJson}",
                createdAtEpochMs = now,
            )
        )
        offlineQueueManager.scheduleSyncAsSoonAsOnline()
        // The person just acted, so try to send right now rather than waiting out any retry
        // delay left over from an earlier failed sync.
        offlineQueueManager.runSyncNow()
    }

    /**
     * Demo Mode (Part 16) hook only — in real use, a family member's status changes because
     * *their own* phone posts it (see markSelfSafe/POST /api/safety-status), never because
     * this device wrote it directly. This exists purely to make the Home screen's family
     * board react visibly during a judge presentation without needing a second physical
     * phone signed in as "Ali" — it writes to the same local Room table but does NOT enqueue
     * a sync-queue entry, since there is no real event to report to the backend on someone
     * else's behalf.
     */
    suspend fun setMemberStatusForDemo(name: String, status: SafetyStatus) {
        familyMemberDao.updateStatusByName(name, status, System.currentTimeMillis())
    }

    private companion object {
        const val MAX_NAME_LENGTH = 60
        const val LOCATION_WAIT_MS = 4_000L
    }
}
