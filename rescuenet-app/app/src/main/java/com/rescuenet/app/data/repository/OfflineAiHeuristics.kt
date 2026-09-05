package com.rescuenet.app.data.repository

import com.rescuenet.app.data.model.AiIncidentSummary
import com.rescuenet.app.data.model.IncidentType
import com.rescuenet.app.data.model.ResourceNeed
import com.rescuenet.app.data.model.Severity

/**
 * Pure, deterministic keyword-based extraction — the offline fallback used by [AiRepository]
 * when the backend is unreachable. Deliberately mirrors the backend's own `MockProvider`
 * (rescuenet-backend/app/ai_provider.py) keyword-by-keyword, so a report structured offline
 * and one structured by the backend's safe-default provider behave the same way.
 *
 * Extracted into its own object (no Android/Hilt/coroutine dependencies) specifically so it
 * can be exercised by plain JVM unit tests — see
 * app/src/test/.../OfflineAiHeuristicsTest.kt — without needing Robolectric or a device.
 */
object OfflineAiHeuristics {

    private val typeKeywords: Map<IncidentType, List<String>> = mapOf(
        IncidentType.FLOOD to listOf("flood", "pani", "paani", "water", "seelab"),
        IncidentType.FIRE to listOf("fire", "aag", "jal raha", "burning"),
        IncidentType.EARTHQUAKE to listOf("earthquake", "zalzala", "zilzala", "shaking"),
        IncidentType.MEDICAL to listOf("injured", "zakhmi", "bimar", "heart attack", "unconscious", "behosh"),
        IncidentType.TRAPPED to listOf("trapped", "phans", "phansa", "stuck"),
        IncidentType.BUILDING_COLLAPSE to listOf("collapse", "gir gaya", "building fell", "rubble"),
        IncidentType.ACCIDENT to listOf("accident", "hadsa", "crash"),
        IncidentType.MISSING_PERSON to listOf("missing", "gum", "lost person"),
        IncidentType.SECURITY to listOf("security", "attack", "threat"),
    )
    private val injuryKeywords = listOf("injured", "zakhmi", "hurt", "bleeding", "critical", "unconscious", "behosh")
    private val peopleCountRegex = Regex("""(\d+)\s*(people|log|persons|afraad)""")

    fun analyze(rawText: String): AiIncidentSummary {
        val text = rawText.lowercase()

        val type = typeKeywords.entries.firstOrNull { (_, kws) -> kws.any { it in text } }?.key ?: IncidentType.OTHER

        val peopleMatch = peopleCountRegex.find(text)
        val peopleCount = peopleMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1

        val injuriesPresent = injuryKeywords.any { it in text }

        val severity = when {
            injuriesPresent && ("critical" in text || "behosh" in text || "unconscious" in text) -> Severity.CRITICAL
            injuriesPresent || peopleCount >= 5 -> Severity.HIGH
            else -> Severity.MODERATE
        }

        val resources = buildList {
            if (type == IncidentType.FLOOD) { add(ResourceNeed.RESCUE_TEAM); add(ResourceNeed.EVACUATION) }
            if (type == IncidentType.FIRE) { add(ResourceNeed.FIRE_SERVICE); add(ResourceNeed.EVACUATION) }
            if (injuriesPresent) { add(ResourceNeed.AMBULANCE); add(ResourceNeed.MEDICAL_ASSISTANCE) }
        }.distinct()

        var confidence = 40
        if (type != IncidentType.OTHER) confidence += 25
        if (peopleMatch != null) confidence += 15
        if (injuriesPresent) confidence += 10
        confidence = confidence.coerceAtMost(90) // the offline fallback never claims near-certainty either

        return AiIncidentSummary(
            type = type,
            severity = severity,
            peopleCount = peopleCount,
            injuriesPresent = injuriesPresent,
            requiredResources = resources,
            confidencePercent = confidence,
            isSimulated = true,
        )
    }
}
