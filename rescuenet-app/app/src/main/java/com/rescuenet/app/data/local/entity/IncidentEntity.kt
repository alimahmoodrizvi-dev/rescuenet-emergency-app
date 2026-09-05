package com.rescuenet.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rescuenet.app.data.model.InjuryLevel
import com.rescuenet.app.data.model.IncidentType
import com.rescuenet.app.data.model.ResourceNeed
import com.rescuenet.app.data.model.Severity
import com.rescuenet.app.data.model.SyncState

/**
 * Local record of an emergency report. `eventUuid` is the same UUID used in the Phase 1
 * `EmergencyIncidents.event_uuid` schema and in relayed NetworkMessages — it is the
 * de-duplication key everywhere in the system, generated once on-device and never reissued.
 */
@Entity(tableName = "incidents")
data class IncidentEntity(
    @PrimaryKey val eventUuid: String,
    val type: IncidentType,
    val peopleCount: Int,
    val injuryLevel: InjuryLevel,
    val needs: List<ResourceNeed>,
    val description: String,
    val hasPhoto: Boolean,
    val hasVoiceNote: Boolean,
    val latitude: Double?,
    val longitude: Double?,
    val locationAccuracyM: Float?,
    val aiSeverity: Severity?,
    val aiConfidencePercent: Int?,
    val aiIsSimulated: Boolean,
    val syncState: SyncState,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
