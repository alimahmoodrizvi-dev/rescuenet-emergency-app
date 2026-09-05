package com.rescuenet.app.data.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class BatteryState(val percent: Int, val isCharging: Boolean)

/**
 * Reads current battery level/charging state via the sticky ACTION_BATTERY_CHANGED
 * broadcast — no permission required, no continuous listener needed, just a point-in-time
 * read whenever [MeshRelayManager]'s periodic policy check wants one (Part 30 — battery-aware
 * scanning).
 */
@Singleton
class BatteryLevelProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun currentState(): BatteryState? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        return BatteryState(percent = (level * 100) / scale, isCharging = isCharging)
    }
}
