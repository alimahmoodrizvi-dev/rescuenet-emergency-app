package com.rescuenet.app.data.repository

import com.rescuenet.app.data.model.IncidentType
import com.rescuenet.app.data.model.ResourceNeed
import com.rescuenet.app.data.model.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NOTE on execution: this test file was written against and reasoned through carefully, but
 * could not be compiled/run in the sandbox this project was built in — there's no Android
 * SDK or Gradle-with-network access available here (see TESTING.md for the full explanation
 * and what to do to actually run it). Treat this as reviewed-by-eye against the exact same
 * logic already verified server-side in rescuenet-backend/tests/, not as independently
 * machine-verified on the Android side.
 */
class OfflineAiHeuristicsTest {

    @Test
    fun `part 6 urdu example parses to flood, 6 people, injuries present`() {
        val result = OfflineAiHeuristics.analyze(
            "Mere ghar mein pani aa raha hai. Hum 6 log hain aur meri mother injured hain."
        )
        assertEquals(IncidentType.FLOOD, result.type)
        assertEquals(6, result.peopleCount)
        assertTrue(result.injuriesPresent)
        assertEquals(Severity.HIGH, result.severity) // injuries present -> at least HIGH
    }

    @Test
    fun `english fire report is detected`() {
        val result = OfflineAiHeuristics.analyze("There is a fire in the building, everyone is evacuating")
        assertEquals(IncidentType.FIRE, result.type)
        assertTrue(ResourceNeed.FIRE_SERVICE in result.requiredResources)
    }

    @Test
    fun `unrecognized text falls back to OTHER with low confidence`() {
        val result = OfflineAiHeuristics.analyze("asdkfj random text with no signal")
        assertEquals(IncidentType.OTHER, result.type)
        assertTrue("confidence should be low for unrecognized text, was ${result.confidencePercent}", result.confidencePercent <= 50)
    }

    @Test
    fun `empty text never crashes and never claims high confidence`() {
        val result = OfflineAiHeuristics.analyze("")
        assertEquals(IncidentType.OTHER, result.type)
        assertEquals(1, result.peopleCount) // defaults to 1, never 0 or negative
        assertTrue(result.confidencePercent < 70)
    }

    @Test
    fun `confidence never exceeds 90 even with maximal signal`() {
        val result = OfflineAiHeuristics.analyze("flood water everywhere, 12 people trapped, injured and bleeding, critical")
        assertTrue("offline fallback must never claim near-certainty, was ${result.confidencePercent}", result.confidencePercent <= 90)
    }

    @Test
    fun `critical severity requires both injury and explicit critical language`() {
        val justInjured = OfflineAiHeuristics.analyze("someone is injured")
        assertEquals(Severity.HIGH, justInjured.severity) // injured alone -> HIGH, not CRITICAL

        val criticalAndInjured = OfflineAiHeuristics.analyze("critical, person is unconscious and injured")
        assertEquals(Severity.CRITICAL, criticalAndInjured.severity)
    }

    @Test
    fun `five or more people escalates severity even without injuries`() {
        val result = OfflineAiHeuristics.analyze("earthquake, 8 people affected, no injuries reported")
        assertEquals(Severity.HIGH, result.severity)
    }

    @Test
    fun `four or fewer people with no injuries stays moderate`() {
        val result = OfflineAiHeuristics.analyze("earthquake, 2 people affected")
        assertEquals(Severity.MODERATE, result.severity)
        assertFalse(result.injuriesPresent)
    }

    @Test
    fun `people count regex only matches when a counting word follows the number`() {
        // "6 log" (Urdu "people") should match; a bare number with no counting word should not.
        val withCountWord = OfflineAiHeuristics.analyze("hum 6 log hain")
        assertEquals(6, withCountWord.peopleCount)

        val bareNumber = OfflineAiHeuristics.analyze("apartment 6, water rising")
        assertEquals(1, bareNumber.peopleCount) // falls back to the default, doesn't misread "6" as a headcount
    }

    @Test
    fun `flood resources include rescue team and evacuation`() {
        val result = OfflineAiHeuristics.analyze("seelab aa gaya, ghar mein pani hai")
        assertTrue(ResourceNeed.RESCUE_TEAM in result.requiredResources)
        assertTrue(ResourceNeed.EVACUATION in result.requiredResources)
    }

    @Test
    fun `is always marked as simulated since this is the offline fallback`() {
        val result = OfflineAiHeuristics.analyze("fire")
        assertTrue(result.isSimulated)
    }
}
