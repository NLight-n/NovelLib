package com.drnishanth.novellib.core.eink

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager

/**
 * Diagnostics and battery constraints monitor for optimizing E-Ink and mobile reading sessions.
 */
object BatteryDiagnostics {

    data class BatteryStatus(
        val batteryLevel: Int,
        val isCharging: Boolean,
        val isPowerSaveMode: Boolean,
        val isLowBattery: Boolean,
        val recommendations: List<String>
    )

    /**
     * Resolves live system battery diagnostics.
     */
    fun getBatteryStatus(context: Context): BatteryStatus {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

        val percentage = if (level >= 0 && scale > 0) ((level.toFloat() / scale.toFloat()) * 100).toInt() else 100
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSaveMode = powerManager?.isPowerSaveMode ?: false

        return evaluateBatteryStatus(
            batteryLevel = percentage,
            isCharging = isCharging,
            isPowerSaveMode = isPowerSaveMode,
            isEInk = EInkHardwareManager.isEInkDevice()
        )
    }

    /**
     * Pure testable evaluation of battery metrics and reading optimizations.
     */
    fun evaluateBatteryStatus(
        batteryLevel: Int,
        isCharging: Boolean,
        isPowerSaveMode: Boolean,
        isEInk: Boolean
    ): BatteryStatus {
        val isLow = batteryLevel < 15 && !isCharging
        val recommendations = mutableListOf<String>()

        if (isLow) {
            recommendations.add("Low battery: background novel checks deferred")
            if (isEInk) {
                recommendations.add("Consider increasing E-Ink refresh interval to conserve battery")
            }
        }

        if (isPowerSaveMode) {
            recommendations.add("Android Power Saver active: animations disabled")
        }

        if (isEInk && !isLow) {
            recommendations.add("E-Ink display detected: static rendering active")
        }

        return BatteryStatus(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            isPowerSaveMode = isPowerSaveMode,
            isLowBattery = isLow,
            recommendations = recommendations
        )
    }
}
