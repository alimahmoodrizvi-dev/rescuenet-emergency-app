package com.rescuenet.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rescuenet.app.data.location.LocationProvider
import com.rescuenet.app.data.model.*
import com.rescuenet.app.data.repository.AiRepository
import com.rescuenet.app.data.repository.IncidentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ReportSendState {
    object Idle : ReportSendState()
    object AnalyzingWithAi : ReportSendState()
    object QueuedOffline : ReportSendState()
    object Synced : ReportSendState()
}

/**
 * Drives the multi-step "Need Help" flow.
 *
 * Phase 6: AI structuring is real (AiRepository -> backend /api/ai/analyze-incident, with an
 * offline rule-based fallback) instead of the Phase 2-5 mocked delay. Per Part 6 ("AI drafts,
 * human confirms"), the AI result is *merged* with what the user already explicitly chose on
 * the type/details screens rather than overriding it: an explicit type/injury-level selection
 * always wins over the AI's guess at the same field, since the person picking a button is a
 * more reliable signal than text extraction. AI fills in what the user didn't explicitly
 * state — chiefly severity and, for the voice-only flow, incident type.
 */
@HiltViewModel
class EmergencyReportViewModel @Inject constructor(
    private val incidentRepository: IncidentRepository,
    private val locationProvider: LocationProvider,
    private val aiRepository: AiRepository,
) : ViewModel() {

    private val _draft = MutableStateFlow(EmergencyReportDraft())
    val draft: StateFlow<EmergencyReportDraft> = _draft

    private val _aiSummary = MutableStateFlow<AiIncidentSummary?>(null)
    val aiSummary: StateFlow<AiIncidentSummary?> = _aiSummary

    private val _sendState = MutableStateFlow<ReportSendState>(ReportSendState.Idle)
    val sendState: StateFlow<ReportSendState> = _sendState

    private val _capturedLocation = MutableStateFlow<com.rescuenet.app.data.location.DeviceLocation?>(null)
    val capturedLocation: StateFlow<com.rescuenet.app.data.location.DeviceLocation?> = _capturedLocation

    fun setType(type: IncidentType) { _draft.value = _draft.value.copy(type = type) }
    fun setPeopleCount(count: Int) { _draft.value = _draft.value.copy(peopleCount = count.coerceAtLeast(1)) }
    fun setInjuryLevel(level: InjuryLevel) { _draft.value = _draft.value.copy(injuryLevel = level) }
    fun toggleNeed(need: ResourceNeed) {
        val current = _draft.value.needs
        _draft.value = _draft.value.copy(needs = if (need in current) current - need else current + need)
    }
    fun setDescription(text: String) { _draft.value = _draft.value.copy(description = text) }

    /** Used by Voice Mode: the transcript becomes the description, and — since the voice
     *  flow has no explicit type/injury selection yet — AI is allowed to set those (the merge
     *  logic in requestAiSummary only treats a field as "user-explicit" once draft.type etc.
     *  is non-null, so setting the description alone doesn't pre-empt AI's type guess). */
    fun setDescriptionFromVoice(text: String) { _draft.value = _draft.value.copy(description = text, hasVoiceNote = true) }

    fun requestAiSummary(isOffline: Boolean) {
        viewModelScope.launch {
            _sendState.value = ReportSendState.AnalyzingWithAi

            val locationJob = launch { _capturedLocation.value = locationProvider.getCurrentLocation() }

            val rawText = _draft.value.description.ifBlank {
                // No freeform text (typed-flow users often skip it) — synthesize a short
                // description from the structured selections so the AI/fallback extractor
                // still has something concrete to reason about instead of an empty string.
                IncidentSummaryMerger.buildSyntheticDescription(_draft.value)
            }
            val aiResult = aiRepository.analyzeIncidentText(rawText, languageHint = null, isOffline = isOffline)
            val merged = IncidentSummaryMerger.merge(aiResult, _draft.value)

            locationJob.join()
            _aiSummary.value = merged

            incidentRepository.submitReport(
                draft = _draft.value,
                aiSummary = merged,
                location = _capturedLocation.value,
            )

            _sendState.value = if (isOffline) ReportSendState.QueuedOffline else ReportSendState.Synced
        }
    }

    fun reset() {
        _draft.value = EmergencyReportDraft()
        _aiSummary.value = null
        _sendState.value = ReportSendState.Idle
        _capturedLocation.value = null
    }
}
