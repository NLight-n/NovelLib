package com.drnishanth.novellib.eink

import com.drnishanth.novellib.core.eink.BatteryDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryDiagnosticsTest {

    @Test
    fun testEvaluateBatteryStatus_normal() {
        val status = BatteryDiagnostics.evaluateBatteryStatus(
            batteryLevel = 80,
            isCharging = false,
            isPowerSaveMode = false,
            isEInk = false
        )

        assertFalse(status.isLowBattery)
        assertFalse(status.isCharging)
        assertEquals(80, status.batteryLevel)
        assertTrue(status.recommendations.isEmpty())
    }

    @Test
    fun testEvaluateBatteryStatus_lowBatteryOnEInk() {
        val status = BatteryDiagnostics.evaluateBatteryStatus(
            batteryLevel = 10,
            isCharging = false,
            isPowerSaveMode = true,
            isEInk = true
        )

        assertTrue(status.isLowBattery)
        assertTrue(status.isPowerSaveMode)
        assertTrue(status.recommendations.any { it.contains("Low battery") })
        assertTrue(status.recommendations.any { it.contains("E-Ink refresh interval") })
        assertTrue(status.recommendations.any { it.contains("Power Saver") })
    }

    @Test
    fun testEvaluateBatteryStatus_chargingWithLowLevel() {
        val status = BatteryDiagnostics.evaluateBatteryStatus(
            batteryLevel = 8,
            isCharging = true,
            isPowerSaveMode = false,
            isEInk = true
        )

        assertFalse("Should not be considered low battery emergency if currently charging", status.isLowBattery)
        assertTrue(status.isCharging)
    }
}
