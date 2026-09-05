package com.rescuenet.app.data.model

import java.util.UUID

enum class IncidentType {
    MEDICAL, FIRE, FLOOD, EARTHQUAKE, ACCIDENT, TRAPPED,
    MISSING_PERSON, BUILDING_COLLAPSE, SECURITY, OTHER
}

enum class InjuryLevel { NONE, MINOR, SERIOUS, CRITICAL, UNKNOWN }

enum class ResourceNeed {
    AMBULANCE, RESCUE_TEAM, FIRE_SERVICE, FOOD, WATER,
    SHELTER, EVACUATION, MEDICAL_ASSISTANCE, OTHER
}

enum class Severity { CRITICAL, HIGH, MODERATE, RESOLVED }

enum class SafetyStatus { SAFE, NEEDS_HELP, UNKNOWN }

enum class SyncState { LOCAL_ONLY, QUEUED, RELAYING, SYNCED }

data class EmergencyReportDraft(
    val id: String = UUID.randomUUID().toString(),
    val type: IncidentType? = null,
    val peopleCount: Int = 1,
    val injuryLevel: InjuryLevel? = null,
    val needs: Set<ResourceNeed> = emptySet(),
    val description: String = "",
    val hasVoiceNote: Boolean = false,
    val hasPhoto: Boolean = false,
)

/** Output of the AI structuring step. is_simulated mirrors the AIAnalysis.is_simulated DB flag. */
data class AiIncidentSummary(
    val type: IncidentType,
    val severity: Severity,
    val peopleCount: Int,
    val injuriesPresent: Boolean,
    val requiredResources: List<ResourceNeed>,
    val confidencePercent: Int,
    val isSimulated: Boolean,
)

data class FamilyMember(
    val id: String,
    val name: String,
    val status: SafetyStatus,
    val lastUpdatedLabel: String,
)

data class NearbyHelpPlace(
    val id: String,
    val name: String,
    val category: String, // Hospital / Shelter / Police / Fire / Assembly Point
    val distanceKm: Double,
)

data class NetworkStatusInfo(
    val internetConnected: Boolean,
    val nearbyNodeCount: Int,
    val lastSyncLabel: String,
)

data class AlertItem(
    val id: String,
    val title: String,
    val severity: Severity,
    val isDemo: Boolean,
    val timeLabel: String,
)
