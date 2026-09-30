package com.drnishanth.novellib.core.eink

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.View

/**
 * Hardware manager and controller for Electronic Paper Display (E-Ink) devices.
 * Supports specialized display refresh triggers, vendor detection (Bigme, Onyx Boox,
 * Meebook, Hisense, etc.), and electrophoretic ghosting reduction.
 */
object EInkHardwareManager {

    private const val TAG = "EInkHardwareManager"

    // Onyx Boox Broadcast Actions
    private const val ACTION_ONYX_REFRESH_SCREEN = "action.com.onyx.REFRESH_SCREEN"
    private const val ACTION_ONYX_EPD_REFRESH = "action.com.onyx.epd.refresh"

    // Bigme Broadcast Actions
    private const val ACTION_BIGME_REFRESH_SCREEN = "com.bigme.action.REFRESH_SCREEN"
    private const val ACTION_SERVER_DISPLAY_REFRESH = "com.android.server.display.REFRESH_SCREEN"

    enum class EinkVendor {
        ONYX,
        BIGME,
        MEEBOOK_BOYUE,
        DASUNG,
        POCKETBOOK,
        HISENSE,
        MOAAN,
        SUPERNOTE,
        KINDLE_KOBO,
        GENERIC_EINK,
        NONE
    }

    data class DeviceHardwareInfo(
        val manufacturer: String,
        val brand: String,
        val model: String,
        val product: String,
        val vendor: EinkVendor,
        val isEInk: Boolean
    )

    data class RefreshResult(
        val vendorMethodInvoked: Boolean,
        val visualFlashRequested: Boolean,
        val details: String
    )

    /**
     * Resolves the hardware profile for the current running device.
     */
    fun getDeviceHardwareInfo(): DeviceHardwareInfo {
        val manufacturer = Build.MANUFACTURER ?: ""
        val brand = Build.BRAND ?: ""
        val model = Build.MODEL ?: ""
        val product = Build.PRODUCT ?: ""

        val vendor = detectDeviceVendor(manufacturer, brand, model, product)
        return DeviceHardwareInfo(
            manufacturer = manufacturer,
            brand = brand,
            model = model,
            product = product,
            vendor = vendor,
            isEInk = vendor != EinkVendor.NONE
        )
    }

    /**
     * Identifies whether the current device is a known E-Ink hardware reader.
     */
    fun isEInkDevice(): Boolean {
        return getDeviceHardwareInfo().isEInk
    }

    /**
     * Pure testable logic to detect E-Ink vendor given device hardware strings.
     */
    fun detectDeviceVendor(
        manufacturer: String,
        brand: String,
        model: String,
        product: String
    ): EinkVendor {
        val combined = "$manufacturer $brand $model $product".lowercase()

        return when {
            combined.containsAny("onyx", "boox", "poke", "nova", "leaf", "palma", "note_air", "tab_ultra", "tab_mini") -> EinkVendor.ONYX
            combined.containsAny("bigme", "hibreak", "b751", "inknote", "goodereader") -> EinkVendor.BIGME
            combined.containsAny("meebook", "boyue", "likebook", "m6", "m7", "p78", "p10") -> EinkVendor.MEEBOOK_BOYUE
            combined.containsAny("dasung", "notereader") -> EinkVendor.DASUNG
            combined.containsAny("pocketbook", "inkpad") -> EinkVendor.POCKETBOOK
            combined.containsAny("hisense", "a5pro", "a7cc", "a9", "touch_music_reader") -> EinkVendor.HISENSE
            combined.containsAny("moaan", "inkpalm") -> EinkVendor.MOAAN
            combined.containsAny("supernote", "ratta") -> EinkVendor.SUPERNOTE
            combined.containsAny("kobo", "tolino", "kindle", "lab126") -> EinkVendor.KINDLE_KOBO
            combined.containsAny("eink", "epaper", "e-ink", "epdc") -> EinkVendor.GENERIC_EINK
            else -> EinkVendor.NONE
        }
    }

    /**
     * Dispatches screen refresh commands to clear ghosting.
     * Invokes vendor-specific EPDC APIs/broadcasts, and requests a momentary
     * visual flash overlay if needed.
     */
    fun triggerScreenRefresh(context: Context, view: View? = null): RefreshResult {
        val vendor = getDeviceHardwareInfo().vendor
        var vendorSuccess = false
        val executedActions = mutableListOf<String>()

        // 1. Try Onyx SDK Reflection if on Onyx device or view provided
        if (view != null && (vendor == EinkVendor.ONYX || vendor == EinkVendor.NONE)) {
            try {
                val epdControllerClass = Class.forName("com.onyx.android.sdk.api.device.epd.EpdController")
                val updateModeClass = Class.forName("com.onyx.android.sdk.api.device.epd.EpdController\$UpdateMode")
                val gcMode = updateModeClass.getField("GC").get(null)
                val refreshMethod = epdControllerClass.getMethod("refreshScreen", View::class.java, updateModeClass)
                refreshMethod.invoke(null, view, gcMode)
                vendorSuccess = true
                executedActions.add("Onyx EpdController.refreshScreen")
            } catch (e: Throwable) {
                Log.d(TAG, "Onyx EpdController reflection not available: ${e.message}")
            }
        }

        // 2. Dispatch Vendor Broadcast Intents
        when (vendor) {
            EinkVendor.ONYX -> {
                sendBroadcastSafely(context, ACTION_ONYX_REFRESH_SCREEN)
                sendBroadcastSafely(context, ACTION_ONYX_EPD_REFRESH)
                executedActions.add("Onyx broadcast intents sent")
                vendorSuccess = true
            }
            EinkVendor.BIGME -> {
                sendBroadcastSafely(context, ACTION_BIGME_REFRESH_SCREEN)
                sendBroadcastSafely(context, ACTION_SERVER_DISPLAY_REFRESH)
                executedActions.add("Bigme broadcast intents sent")
                vendorSuccess = true
            }
            else -> {
                // Also broadcast standard refresh actions as best-effort fallback
                sendBroadcastSafely(context, ACTION_SERVER_DISPLAY_REFRESH)
            }
        }

        // View invalidation
        view?.postInvalidate()

        return RefreshResult(
            vendorMethodInvoked = vendorSuccess,
            visualFlashRequested = true, // Visual flash ensures residual pigment is cleared universally
            details = if (executedActions.isNotEmpty()) executedActions.joinToString(", ") else "Universal visual clear"
        )
    }

    private fun sendBroadcastSafely(context: Context, action: String) {
        try {
            val intent = Intent(action)
            context.sendBroadcast(intent)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed sending broadcast $action: ${e.message}")
        }
    }

    private fun String.containsAny(vararg candidates: String): Boolean {
        return candidates.any { this.contains(it) }
    }
}
