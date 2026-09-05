package com.rescuenet.app.data.offline

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rescuenet.app.data.local.dao.IncidentDao
import com.rescuenet.app.data.local.dao.NetworkMessageDao
import com.rescuenet.app.data.local.dao.SyncQueueDao
import com.rescuenet.app.data.local.entity.SyncQueueEntity
import com.rescuenet.app.data.mesh.MeshMessageCrypto
import com.rescuenet.app.data.model.SyncState
import com.rescuenet.app.data.remote.BackendSession
import com.rescuenet.app.data.remote.BackendSessionManager
import com.rescuenet.app.data.remote.SyncBatchItemDto
import com.rescuenet.app.data.remote.SyncBatchRequestDto
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Drains two things whenever WorkManager decides connectivity + constraints allow it
 * (scheduled with a "requires network" constraint, so this naturally fires the moment the OS
 * reports connectivity back — including after a period of offline mesh relay per Part 21):
 *
 *  1. The local sync queue (this device's own reports/status updates).
 *  2. The relayed-message store (Part 7/21) — anything this device picked up from a peer
 *     over the mesh while offline, now that it can finally hand it off. These are decrypted
 *     locally (see MeshMessageCrypto) and pushed through the exact same backend endpoint as
 *     this device's own reports — the backend doesn't need to know or care whether a report
 *     arrived directly or via three hops of mesh relay.
 *
 * Both loops call the real backend (rescuenet-backend/) via POST /api/sync/batch, which
 * dedups by event_uuid exactly like this worker already dedups locally — so a message that
 * reaches the backend twice (e.g. relayed by two different phones) is still stored once
 * server-side. Auth is shared with AiRepository via BackendSessionManager rather than each
 * caller re-registering the device independently.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncQueueDao: SyncQueueDao,
    private val incidentDao: IncidentDao,
    private val networkMessageDao: NetworkMessageDao,
    private val sessionManager: BackendSessionManager,
) : CoroutineWorker(context, params) {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        val session = sessionManager.getSession() ?: return Result.retry() // backend unreachable right now

        val queueOk = drainSyncQueue(session)
        val relayOk = drainRelayedMessages(session)
        return if (queueOk && relayOk) Result.success() else Result.retry()
    }

    private suspend fun drainSyncQueue(session: BackendSession): Boolean {
        val pending = syncQueueDao.getAll()
        var allOk = true
        for (entry in pending) {
            val ok = syncOne(session, entry)
            if (ok) {
                syncQueueDao.remove(entry.eventUuid)
            } else {
                syncQueueDao.recordAttempt(entry.eventUuid, System.currentTimeMillis())
                allOk = false
            }
        }
        return allOk
    }

    private suspend fun syncOne(session: BackendSession, entry: SyncQueueEntity): Boolean {
        return try {
            val payloadObject = json.parseToJsonElement(entry.payloadJson).jsonObject
            val response = session.api.syncBatch(
                session.bearerToken,
                SyncBatchRequestDto(
                    deviceUuid = session.deviceUuid,
                    items = listOf(SyncBatchItemDto(entityType = entry.entityType, payload = payloadObject)),
                ),
            )
            val ok = response.isSuccessful && (response.body()?.rejected ?: 1) == 0
            if (ok && entry.entityType == "incident") {
                incidentDao.updateSyncState(entry.eventUuid, SyncState.SYNCED, System.currentTimeMillis())
            }
            ok
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun drainRelayedMessages(session: BackendSession): Boolean {
        val undelivered = networkMessageDao.getUndelivered()
        var allOk = true
        for (message in undelivered) {
            val ok = try {
                // Decrypt what the mesh carried (see MeshMessageCrypto's DEMO-ONLY key
                // warning) back into the same incident JSON shape IncidentRepository
                // originally produced, then push it through the identical backend path as
                // a directly-created report.
                val plaintext = MeshMessageCrypto.decryptFromBase64(message.encryptedPayloadBase64)
                val payloadObject = json.parseToJsonElement(plaintext).jsonObject
                val response = session.api.syncBatch(
                    session.bearerToken,
                    SyncBatchRequestDto(
                        deviceUuid = session.deviceUuid,
                        items = listOf(SyncBatchItemDto(entityType = "incident", payload = payloadObject)),
                    ),
                )
                response.isSuccessful && (response.body()?.rejected ?: 1) == 0
            } catch (e: Exception) {
                false
            }

            if (ok) {
                networkMessageDao.markDelivered(message.messageUuid, System.currentTimeMillis())
            } else {
                allOk = false
            }
        }
        return allOk
    }
}
