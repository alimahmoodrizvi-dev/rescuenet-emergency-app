package com.rescuenet.app.data.viewmodel

import com.rescuenet.app.data.model.AiIncidentSummary
import com.rescuenet.app.data.model.EmergencyReportDraft
import com.rescuenet.app.data.model.InjuryLevel

/**
 * Pure logic for combining an AI-structured summary with what the user already explicitly
 * chose on the type/details screens (Part 6 — "AI drafts, human confirms"). Extracted out of
 * [EmergencyReportViewModel] specifically so it can be exercised by plain JVM unit tests
 * (app/src/test/.../IncidentSummaryMergerTest.kt) without needing a ViewModel, Hilt, or
 * Android framework classes at all.
 */
object IncidentSummaryMerger {

    /** An explicit button tap always wins over the AI's guess at the same field — the person
     *  picking a button is a more reliable signal than text extraction. AI only fills in what
     *  the user didn't explicitly state: severity always, and incident type for the
     *  voice-only flow where draft.type is still null. */
    fun merge(ai: AiIncidentSummary, draft: EmergencyReportDraft): AiIncidentSummary = ai.copy(
        type = draft.type ?: ai.type,
        peopleCount = draft.peopleCount, // always user-set via the stepper, never ambiguous
        injuriesPresent = when (draft.injuryLevel) {
            // UNKNOWN means "the user doesn't know" — that's not an explicit statement
            // either way, so it must defer to the AI's read, same as no answer at all (null).
            // (This distinction matters: treating UNKNOWN the same as NONE would silently
            // report "no injuries" for a user who explicitly said they don't know.)
            null, InjuryLevel.UNKNOWN -> ai.injuriesPresent
            InjuryLevel.NONE -> false
            InjuryLevel.MINOR, InjuryLevel.SERIOUS, InjuryLevel.CRITICAL -> true
        },
        requiredResources = (draft.needs.toList() + ai.requiredResources).distinct(),
    )

    /** When the user skipped the optional freeform description, synthesize a short one from
     *  their structured selections so the AI/offline-fallback extractor still has something
     *  concrete to reason about instead of an empty string. */
    fun buildSyntheticDescription(draft: EmergencyReportDraft): String {
        val typePart = draft.type?.name?.replace('_', ' ')?.lowercase() ?: "emergency"
        val injuryPart = draft.injuryLevel?.let { if (it != InjuryLevel.NONE) "injuries: ${it.name.lowercase()}" else null }
        return listOfNotNull("$typePart reported", "${draft.peopleCount} people affected", injuryPart).joinToString(", ")
    }
}
