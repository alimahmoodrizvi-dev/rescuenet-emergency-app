package com.rescuenet.app.data.repository

import com.rescuenet.app.data.local.dao.IncidentDao
import com.rescuenet.app.data.local.dao.SyncQueueDao
import com.rescuenet.app.data.local.entity.IncidentEntity
import com.rescuenet.app.data.local.entity.SyncQueueEntity
import com.rescuenet.app.data.mesh.MeshRelayManager
import com.rescuenet.app.data.model.*
import com.rescuenet.app.data.offline.OfflineQueueManager
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IncidentRepository @Inject constructor(
    private val incidentDao: IncidentDao,
    private val syncQueueDao: SyncQueueDao,
    private val offlineQueueManager: OfflineQueueManager,
    private val meshRelayManager: MeshRelayManager,
) {
    fun observeIncidents(): Flow<List<IncidentEntity>> = incidentDao.observeAll()

    /**
     * Persists a completed report locally (this always succeeds, online or offline — the
     * local write is the source of truth, per Part 21) and enqueues it for sync. Returns the
     * generated event UUID so the UI/AI-summary step can reference the same record.
     */
    suspend fun submitReport(
        draft: EmergencyReportDraft,
        aiSummary: AiIncidentSummary?,
        location: com.rescuenet.app.data.location.DeviceLocation?,
    ): String {
        val eventUuid = draft.id.ifBlank { UUID.randomUUID().toString() }
        val now = System.currentTimeMillis()

        val entity = IncidentEntity(
            eventUuid = eventUuid,
            type = draft.type ?: IncidentType.OTHER,
            peopleCount = draft.peopleCount,
            injuryLevel = draft.injuryLevel ?: InjuryLevel.UNKNOWN,
            needs = draft.needs.toList(),
            description = draft.description,
            hasPhoto = draft.hasPhoto,
            hasVoiceNote = draft.hasVoiceNote,
            latitude = location?.latitude,
            longitude = location?.longitude,
            locationAccuracyM = location?.accuracyMeters,
            aiSeverity = aiSummary?.severity,
            aiConfidencePercent = aiSummary?.confidencePercent,
            aiIsSimulated = aiSummary?.isSimulated ?: true,
            syncState = SyncState.QUEUED,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        incidentDao.upsert(entity)

        val payload = buildJsonPayload(entity)
        syncQueueDao.enqueue(
            SyncQueueEntity(
                eventUuid = eventUuid,
                entityType = "incident",
                entityId = eventUuid,
                payloadJson = payload,
                createdAtEpochMs = now,
            )
        )

        offlineQueueManager.scheduleSyncAsSoonAsOnline()

        // Also hand the report to the mesh relay path (Part 21: local queue -> peer-to-peer
        // -> sync). Harmless when already online — it just gives nearby peers a copy too,
        // deduped by eventUuid everywhere downstream — and is exactly what lets a report
        // reach the internet via a *different* phone when this one has no signal.
        meshRelayManager.queueForRelay(eventUuid, payload)

        return eventUuid
    }

    suspend fun getSyncState(eventUuid: String): SyncState? =
        incidentDao.getByUuid(eventUuid)?.syncState

    private fun buildJsonPayload(entity: IncidentEntity): String {
        // Minimal, human-inspectable payload for the Phase 3 local queue. Phase 5 replaces
        // this with the full EmergencyIncidents schema shape from Part 9 once there's a real
        // POST /api/incidents endpoint to send it to.
        val obj = JsonObject(
            mapOf(
                "event_uuid" to JsonPrimitive(entity.eventUuid),
                "type" to JsonPrimitive(entity.type.name),
                "people_count" to JsonPrimitive(entity.peopleCount),
                "injury_level" to JsonPrimitive(entity.injuryLevel.name),
                "description" to JsonPrimitive(entity.description),
                "latitude" to (entity.latitude?.let { JsonPrimitive(it) } ?: JsonPrimitive("unknown")),
                "longitude" to (entity.longitude?.let { JsonPrimitive(it) } ?: JsonPrimitive("unknown")),
            )
        )
        return Json.encodeToString(JsonObject.serializer(), obj)
    }
}
