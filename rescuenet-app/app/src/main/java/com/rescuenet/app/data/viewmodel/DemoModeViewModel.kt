package com.rescuenet.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rescuenet.app.data.model.*
import com.rescuenet.app.data.offline.OfflineQueueManager
import com.rescuenet.app.data.repository.AiRepository
import com.rescuenet.app.data.repository.FamilyRepository
import com.rescuenet.app.data.repository.IncidentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 8: drives the Part 31 competition demo scenario through the app's *real*
 * repositories rather than just animating UI state. What's genuinely real at each step is
 * documented per-step below; what remains necessarily simulated (because the judge's phone
 * usually still has real internet in the room) is labeled as such rather than glossed over —
 * see DemoRunbook.md for the full honest breakdown and for how to make the offline segment
 * genuinely real using airplane mode or a second phone.
 */
@HiltViewModel
class DemoModeViewModel @Inject constructor(
    private val incidentRepository: IncidentRepository,
    private val aiRepository: AiRepository,
    private val familyRepository: FamilyRepository,
    private val offlineQueueManager: OfflineQueueManager,
) : ViewModel() {

    data class DemoState(
        val aiParsedSummary: AiIncidentSummary? = null,
        val createdEventUuid: String? = null,
        val clusterSeedEventUuids: List<String> = emptyList(),
        val busy: Boolean = false,
    )

    private val _state = MutableStateFlow(DemoState())
    val state: StateFlow<DemoState> = _state

    companion object {
        const val URDU_EXAMPLE_TEXT = "Mere ghar mein pani aa raha hai. Hum 6 log hain aur meri mother injured hain."
    }

    /** Step 3 — "AI converts it into a structured report." Genuinely calls AiRepository
     *  (backend if reachable, offline fallback otherwise) on the exact Part 6 example text. */
    fun runAiParsingStep() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val result = aiRepository.analyzeIncidentText(URDU_EXAMPLE_TEXT, languageHint = "ur", isOffline = false)
            _state.value = _state.value.copy(aiParsedSummary = result, busy = false)
        }
    }

    /** Step 5 — "Offline message creation." Genuinely persists a real EmergencyIncident row
     *  via IncidentRepository (Room + sync queue + mesh outbox) using the AI-parsed result
     *  from the previous step — this is the same code path a real citizen report takes. */
    fun createDemoIncident() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val summary = _state.value.aiParsedSummary
            val draft = EmergencyReportDraft(
                type = summary?.type ?: IncidentType.FLOOD,
                peopleCount = summary?.peopleCount ?: 6,
                injuryLevel = InjuryLevel.SERIOUS,
                needs = setOf(ResourceNeed.RESCUE_TEAM, ResourceNeed.MEDICAL_ASSISTANCE),
                description = URDU_EXAMPLE_TEXT,
            )
            val eventUuid = incidentRepository.submitReport(
                draft = draft,
                aiSummary = summary,
                location = DEMO_LOCATION,
            )
            _state.value = _state.value.copy(createdEventUuid = eventUuid, busy = false)
        }
    }

    /** Step 6 — "Internet returns… messages synchronize automatically." Genuinely forces an
     *  immediate sync attempt (OfflineQueueManager.runSyncNow) rather than waiting for
     *  WorkManager's normal scheduling window, so this step resolves promptly on stage. */
    fun triggerImmediateSync() {
        offlineQueueManager.runSyncNow()
    }

    /** Step 8 — "AI identifies an emergency cluster." Creates two additional nearby,
     *  clearly-synthetic flood reports through the same real IncidentRepository path, purely
     *  so the Command Center's clustering endpoint (Part 12) has three real, nearby rows to
     *  actually find — the clustering logic that runs on them afterward is entirely genuine,
     *  only the seed data is synthetic (and stays labeled SIMULATION throughout the app). */
    fun seedNearbyIncidentsForClustering() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val seeds = listOf(
                EmergencyReportDraft(type = IncidentType.FLOOD, peopleCount = 4, injuryLevel = InjuryLevel.UNKNOWN, description = "Demo cluster seed 1"),
                EmergencyReportDraft(type = IncidentType.FLOOD, peopleCount = 3, injuryLevel = InjuryLevel.MINOR, description = "Demo cluster seed 2"),
            )
            val ids = seeds.mapIndexed { index, draft ->
                incidentRepository.submitReport(
                    draft = draft,
                    aiSummary = AiIncidentSummary(
                        type = IncidentType.FLOOD, severity = Severity.HIGH, peopleCount = draft.peopleCount,
                        injuriesPresent = false, requiredResources = listOf(ResourceNeed.RESCUE_TEAM),
                        confidencePercent = 70, isSimulated = true,
                    ),
                    location = DEMO_LOCATION.copy(
                        latitude = DEMO_LOCATION.latitude + (index + 1) * 0.003,
                        longitude = DEMO_LOCATION.longitude + (index + 1) * 0.002,
                    ),
                )
            }
            triggerImmediateSync()
            _state.value = _state.value.copy(clusterSeedEventUuids = ids, busy = false)
        }
    }

    /** Step 11 — "Family dashboard shows Ali needs help." See FamilyRepository's doc comment
     *  on setMemberStatusForDemo for why this is explicitly a local-only demo write. */
    fun markAliNeedsHelp() {
        viewModelScope.launch { familyRepository.setMemberStatusForDemo("Ali", SafetyStatus.NEEDS_HELP) }
    }

    fun reset() {
        _state.value = DemoState()
    }
}

/** Karachi coordinates matching the backend's seeded reference data (rescuenet-backend's
 *  _seed_demo_reference_data), so the demo incident lands near the seeded hospitals/shelters
 *  and Rescue Team 3/4 on the Command Center map instead of somewhere unrelated. */
private val DEMO_LOCATION = com.rescuenet.app.data.location.DeviceLocation(
    latitude = 24.8690, longitude = 67.0340, accuracyMeters = 12f,
)
