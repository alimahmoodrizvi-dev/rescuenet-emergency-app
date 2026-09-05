package com.rescuenet.app.data.repository

import com.rescuenet.app.data.local.dao.FamilyMemberDao
import com.rescuenet.app.data.local.dao.SyncQueueDao
import com.rescuenet.app.data.local.entity.FamilyMemberEntity
import com.rescuenet.app.data.local.entity.SyncQueueEntity
import com.rescuenet.app.data.model.SafetyStatus
import com.rescuenet.app.data.offline.OfflineQueueManager
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FamilyRepository @Inject constructor(
    private val familyMemberDao: FamilyMemberDao,
    private val syncQueueDao: SyncQueueDao,
    private val offlineQueueManager: OfflineQueueManager,
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

    /** "I'm Safe" — always writes locally first, then queues for sync so it works fully
     *  offline (Part 8: "queue the status locally and transmit it when connectivity returns"). */
    suspend fun markSelfSafe() {
        val now = System.currentTimeMillis()
        familyMemberDao.updateSelfStatus(SafetyStatus.SAFE, now)

        val eventUuid = UUID.randomUUID().toString()
        syncQueueDao.enqueue(
            SyncQueueEntity(
                eventUuid = eventUuid,
                entityType = "safety_status",
                entityId = "self",
                payloadJson = """{"status":"SAFE","at":$now}""",
                createdAtEpochMs = now,
            )
        )
        offlineQueueManager.scheduleSyncAsSoonAsOnline()
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
}
