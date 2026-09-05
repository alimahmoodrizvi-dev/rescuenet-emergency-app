package com.rescuenet.app.data.repository

import com.rescuenet.app.data.model.AiIncidentSummary
import com.rescuenet.app.data.model.IncidentType
import com.rescuenet.app.data.model.ResourceNeed
import com.rescuenet.app.data.model.Severity
import com.rescuenet.app.data.remote.AIAnalyzeIncidentRequestDto
import com.rescuenet.app.data.remote.AIAnalyzeIncidentResponseDto
import com.rescuenet.app.data.remote.BackendSessionManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 6: the AI abstraction layer described in Part 8 of the architecture doc, client
 * side. Prefers the real backend (`POST /api/ai/analyze-incident`, verified working in
 * Phase 5 — it's the same endpoint that correctly parsed the Urdu/English Part 6 example
 * during backend testing) and falls back to [OfflineAiHeuristics] — a deterministic
 * on-device rule-based extractor — when the backend is unreachable or the device is offline.
 *
 * Per Part 29 ("the emergency-report function should still work locally when possible") and
 * Part 26 (AI must never overstate certainty), this method is designed to never throw and
 * never block report submission — a failed or unreachable AI call degrades to the offline
 * fallback rather than failing the whole report.
 */
@Singleton
class AiRepository @Inject constructor(
    private val sessionManager: BackendSessionManager,
) {

    suspend fun analyzeIncidentText(rawText: String, languageHint: String?, isOffline: Boolean): AiIncidentSummary {
        if (!isOffline) {
            val remoteResult = tryRemoteAnalysis(rawText, languageHint)
            if (remoteResult != null) return remoteResult
        }
        return OfflineAiHeuristics.analyze(rawText)
    }

    private suspend fun tryRemoteAnalysis(rawText: String, languageHint: String?): AiIncidentSummary? {
        return try {
            val session = sessionManager.getSession() ?: return null
            val response = session.api.analyzeIncident(
                session.bearerToken,
                AIAnalyzeIncidentRequestDto(rawText = rawText, languageHint = languageHint),
            )
            if (!response.isSuccessful) return null
            response.body()?.toUiModel()
        } catch (e: Exception) {
            null // network hiccup, timeout, backend down — offline fallback takes over
        }
    }

    private fun AIAnalyzeIncidentResponseDto.toUiModel(): AiIncidentSummary = AiIncidentSummary(
        type = runCatching { IncidentType.valueOf(incidentType) }.getOrDefault(IncidentType.OTHER),
        severity = runCatching { Severity.valueOf(severity) }.getOrDefault(Severity.MODERATE),
        peopleCount = peopleCount ?: 1,
        injuriesPresent = injuriesPresent ?: false,
        requiredResources = requiredResources.mapNotNull { runCatching { ResourceNeed.valueOf(it) }.getOrNull() },
        confidencePercent = confidence,
        isSimulated = isSimulated,
    )
}
