package com.rescuenet.app.data.battery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** See the execution caveat in the mesh/repository test files — applies equally here. */
class BatteryAwareScanPolicyTest {

    @Test
    fun `scans normally at healthy battery with nothing pending`() {
        val healthy = BatteryState(percent = 80, isCharging = false)
        assertTrue(BatteryAwareScanPolicy.shouldScan(healthy, hasPendingOutbox = false))
    }

    @Test
    fun `pauses scanning at critical battery with nothing pending and not charging`() {
        val critical = BatteryState(percent = 10, isCharging = false)
        assertFalse(BatteryAwareScanPolicy.shouldScan(critical, hasPendingOutbox = false))
    }

    @Test
    fun `never pauses while charging, regardless of battery percent`() {
        val criticalButCharging = BatteryState(percent = 5, isCharging = true)
        assertTrue(BatteryAwareScanPolicy.shouldScan(criticalButCharging, hasPendingOutbox = false))
    }

    @Test
    fun `never pauses when a report is queued, even at critical battery`() {
        val criticalWithPending = BatteryState(percent = 5, isCharging = false)
        assertTrue(
            "a queued emergency report must outweigh battery conservation",
            BatteryAwareScanPolicy.shouldScan(criticalWithPending, hasPendingOutbox = true)
        )
    }

    @Test
    fun `unknown battery state fails open rather than silently disabling relay`() {
        assertTrue(BatteryAwareScanPolicy.shouldScan(null, hasPendingOutbox = false))
    }

    @Test
    fun `exactly at the critical threshold is treated as still critical (boundary is exclusive above)`() {
        val atThreshold = BatteryState(percent = BatteryAwareScanPolicy.CRITICAL_BATTERY_PERCENT, isCharging = false)
        assertFalse(BatteryAwareScanPolicy.shouldScan(atThreshold, hasPendingOutbox = false))

        val oneAboveThreshold = BatteryState(percent = BatteryAwareScanPolicy.CRITICAL_BATTERY_PERCENT + 1, isCharging = false)
        assertTrue(BatteryAwareScanPolicy.shouldScan(oneAboveThreshold, hasPendingOutbox = false))
    }
}
