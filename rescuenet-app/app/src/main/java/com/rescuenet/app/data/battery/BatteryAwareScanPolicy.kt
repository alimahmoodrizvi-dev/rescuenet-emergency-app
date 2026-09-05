package com.rescuenet.app.data.battery

/**
 * Pure decision logic for whether mesh scanning/advertising should keep running (Part 30 —
 * "Battery-aware scanning... Do not continuously scan Bluetooth at maximum power"). Extracted
 * as a standalone object (no Android dependency) specifically so the actual policy — the part
 * worth getting right, since being too conservative could mean missing a real emergency
 * relay — is unit-testable (see BatteryAwareScanPolicyTest.kt) independent of how battery
 * state is read.
 *
 * Policy, stated plainly: never stop scanning while charging (no battery cost concern) or
 * while there's something waiting to relay (a pending report matters more than battery
 * life). Only pause when battery is critically low, unplugged, AND nothing is queued.
 */
object BatteryAwareScanPolicy {

    const val CRITICAL_BATTERY_PERCENT = 15

    fun shouldScan(battery: BatteryState?, hasPendingOutbox: Boolean): Boolean {
        if (battery == null) return true // unknown state — fail open, don't silently disable relay
        if (battery.isCharging) return true
        if (hasPendingOutbox) return true // a queued report always outweighs battery conservation
        return battery.percent > CRITICAL_BATTERY_PERCENT
    }
}
