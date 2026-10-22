package com.rescuenet.app.data.repository

import com.rescuenet.app.data.local.dao.IncidentDao
import com.rescuenet.app.data.local.dao.SyncQueueDao
import com.rescuenet.app.data.local.entity.IncidentEntity
import com.rescuenet.app.data.local.entity.SyncQueueEntity
import com.rescuenet.app.data.mesh.MeshRelayManager
import com.rescuenet.app.data.model.*
import com.rescuenet.app.data.offline.OfflineQueueManager
import com.rescuenet.app.data.settings.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IncidentRepository @Inject constructor(
    private val incidentDao: IncidentDao,
    private val syncQueueDao: SyncQueueDao,
    private val offlineQueueManager: OfflineQueueManager,
    private val meshRelayManager: MeshRelayManager,
    private val settingsDataStore: SettingsDataStore,
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

        // The number the person chose to share so rescuers can call them back (blank = none).
        val reporterPhone = settingsDataStore.myPhone.first().ifBlank { null }
        val payload = buildJsonPayload(entity, reporterPhone)
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

    private fun buildJsonPayload(entity: IncidentEntity, reporterPhone: String?): String =
        buildIncidentPayloadJson(
            eventUuid = entity.eventUuid,
            type = entity.type.name,
            peopleCount = entity.peopleCount,
            injuryLevel = entity.injuryLevel.name,
            description = entity.description,
            needs = entity.needs.map { it.name },
            latitude = entity.latitude,
            longitude = entity.longitude,
            locationAccuracyM = entity.locationAccuracyM,
            reporterPhone = reporterPhone,
        )
}
