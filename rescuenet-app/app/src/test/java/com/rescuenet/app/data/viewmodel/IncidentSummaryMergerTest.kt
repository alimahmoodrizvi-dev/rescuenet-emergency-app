package com.rescuenet.app.data.viewmodel

import com.rescuenet.app.data.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** See the execution caveat in OfflineAiHeuristicsTest.kt — applies equally here. */
class IncidentSummaryMergerTest {

    private fun aiSummary(
        type: IncidentType = IncidentType.OTHER,
        peopleCount: Int = 1,
        injuriesPresent: Boolean = false,
        resources: List<ResourceNeed> = emptyList(),
    ) = AiIncidentSummary(
        type = type, severity = Severity.MODERATE, peopleCount = peopleCount,
        injuriesPresent = injuriesPresent, requiredResources = resources,
        confidencePercent = 70, isSimulated = true,
    )

    @Test
    fun `explicit user type selection wins over AI guess`() {
        val ai = aiSummary(type = IncidentType.FIRE) // AI guessed FIRE
        val draft = EmergencyReportDraft(type = IncidentType.FLOOD) // user explicitly picked FLOOD

        val merged = IncidentSummaryMerger.merge(ai, draft)

        assertEquals(IncidentType.FLOOD, merged.type) // user's explicit pick must win
    }

    @Test
    fun `ai type guess is used when user made no explicit selection (voice-only flow)`() {
        val ai = aiSummary(type = IncidentType.EARTHQUAKE)
        val draft = EmergencyReportDraft(type = null) // voice flow — no button tap happened

        val merged = IncidentSummaryMerger.merge(ai, draft)

        assertEquals(IncidentType.EARTHQUAKE, merged.type)
    }

    @Test
    fun `people count always comes from the draft stepper, never from AI`() {
        val ai = aiSummary(peopleCount = 99) // whatever AI extracted from text
        val draft = EmergencyReportDraft(peopleCount = 3) // user explicitly set the stepper to 3

        val merged = IncidentSummaryMerger.merge(ai, draft)

        assertEquals(3, merged.peopleCount)
    }

    @Test
    fun `explicit injury level overrides ai injuries guess`() {
        val aiSaysNoInjury = aiSummary(injuriesPresent = false)
        val draftWithSeriousInjury = EmergencyReportDraft(injuryLevel = InjuryLevel.SERIOUS)

        val merged = IncidentSummaryMerger.merge(aiSaysNoInjury, draftWithSeriousInjury)

        assertTrue("an explicit SERIOUS injury selection must override the AI's 'no injury' guess", merged.injuriesPresent)
    }

    @Test
    fun `injury level NONE explicitly means no injuries regardless of ai guess`() {
        val aiSaysInjured = aiSummary(injuriesPresent = true)
        val draftExplicitlyNone = EmergencyReportDraft(injuryLevel = InjuryLevel.NONE)

        val merged = IncidentSummaryMerger.merge(aiSaysInjured, draftExplicitlyNone)

        assertFalse(merged.injuriesPresent)
    }

    @Test
    fun `injury level UNKNOWN falls back to the ai guess, since UNKNOWN is not an explicit statement`() {
        val aiSaysInjured = aiSummary(injuriesPresent = true)
        val draftUnknown = EmergencyReportDraft(injuryLevel = InjuryLevel.UNKNOWN)

        val merged = IncidentSummaryMerger.merge(aiSaysInjured, draftUnknown)

        assertTrue(merged.injuriesPresent) // UNKNOWN isn't a real answer, so AI's read stands
    }

    @Test
    fun `null injury level (not yet answered) falls back to the ai guess`() {
        val aiSaysInjured = aiSummary(injuriesPresent = true)
        val draftNoAnswerYet = EmergencyReportDraft(injuryLevel = null)

        val merged = IncidentSummaryMerger.merge(aiSaysInjured, draftNoAnswerYet)

        assertTrue(merged.injuriesPresent)
    }

    @Test
    fun `required resources union user selections and ai suggestions without duplicates`() {
        val ai = aiSummary(resources = listOf(ResourceNeed.RESCUE_TEAM, ResourceNeed.EVACUATION))
        val draft = EmergencyReportDraft(needs = setOf(ResourceNeed.AMBULANCE, ResourceNeed.RESCUE_TEAM)) // overlaps on RESCUE_TEAM

        val merged = IncidentSummaryMerger.merge(ai, draft)

        assertEquals(setOf(ResourceNeed.AMBULANCE, ResourceNeed.RESCUE_TEAM, ResourceNeed.EVACUATION), merged.requiredResources.toSet())
        assertEquals(3, merged.requiredResources.size) // no duplicate RESCUE_TEAM entry
    }

    @Test
    fun `severity and confidence always come from ai, never from the draft`() {
        val ai = aiSummary().copy(severity = Severity.CRITICAL, confidencePercent = 55)
        val draft = EmergencyReportDraft(type = IncidentType.OTHER)

        val merged = IncidentSummaryMerger.merge(ai, draft)

        assertEquals(Severity.CRITICAL, merged.severity)
        assertEquals(55, merged.confidencePercent)
    }

    @Test
    fun `synthetic description includes type, people count, and injury when present`() {
        val draft = EmergencyReportDraft(
            type = IncidentType.FIRE, peopleCount = 4, injuryLevel = InjuryLevel.MINOR,
        )
        val description = IncidentSummaryMerger.buildSyntheticDescription(draft)

        assertTrue(description.contains("fire", ignoreCase = true))
        assertTrue(description.contains("4"))
        assertTrue(description.contains("minor", ignoreCase = true))
    }

    @Test
    fun `synthetic description omits injury clause when injury level is NONE`() {
        val draft = EmergencyReportDraft(type = IncidentType.ACCIDENT, peopleCount = 1, injuryLevel = InjuryLevel.NONE)
        val description = IncidentSummaryMerger.buildSyntheticDescription(draft)

        assertFalse(description.contains("injur", ignoreCase = true))
    }

    @Test
    fun `synthetic description falls back to generic emergency label when type is unset`() {
        val draft = EmergencyReportDraft(type = null, peopleCount = 2)
        val description = IncidentSummaryMerger.buildSyntheticDescription(draft)

        assertTrue(description.contains("emergency", ignoreCase = true))
    }
}
