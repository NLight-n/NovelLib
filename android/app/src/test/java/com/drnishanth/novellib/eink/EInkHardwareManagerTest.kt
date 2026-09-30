package com.drnishanth.novellib.eink

import com.drnishanth.novellib.core.eink.EInkHardwareManager
import com.drnishanth.novellib.core.eink.EInkHardwareManager.EinkVendor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EInkHardwareManagerTest {

    @Test
    fun testDetectDeviceVendor_onyxBoox() {
        val vendor1 = EInkHardwareManager.detectDeviceVendor(
            manufacturer = "Onyx",
            brand = "Onyx",
            model = "Poke4Lite",
            product = "Poke4Lite"
        )
        assertEquals(EinkVendor.ONYX, vendor1)

        val vendor2 = EInkHardwareManager.detectDeviceVendor(
            manufacturer = "Onyx",
            brand = "Boox",
            model = "Tab Ultra",
            product = "TabUltra"
        )
        assertEquals(EinkVendor.ONYX, vendor2)
    }

    @Test
    fun testDetectDeviceVendor_bigme() {
        val vendor = EInkHardwareManager.detectDeviceVendor(
            manufacturer = "Bigme",
            brand = "Bigme",
            model = "B751",
            product = "B751"
        )
        assertEquals(EinkVendor.BIGME, vendor)

        val hibreak = EInkHardwareManager.detectDeviceVendor(
            manufacturer = "Bigme",
            brand = "Bigme",
            model = "HiBreak",
            product = "HiBreak"
        )
        assertEquals(EinkVendor.BIGME, hibreak)
    }

    @Test
    fun testDetectDeviceVendor_meebookAndLikebook() {
        val vendor = EInkHardwareManager.detectDeviceVendor(
            manufacturer = "Haoqing",
            brand = "Meebook",
            model = "M7",
            product = "M7"
        )
        assertEquals(EinkVendor.MEEBOOK_BOYUE, vendor)
    }

    @Test
    fun testDetectDeviceVendor_hisenseAndMoaan() {
        val hisense = EInkHardwareManager.detectDeviceVendor(
            manufacturer = "Hisense",
            brand = "Hisense",
            model = "A9",
            product = "A9"
        )
        assertEquals(EinkVendor.HISENSE, hisense)

        val moaan = EInkHardwareManager.detectDeviceVendor(
            manufacturer = "Xiaomi",
            brand = "Moaan",
            model = "InkPalm 5",
            product = "InkPalm5"
        )
        assertEquals(EinkVendor.MOAAN, moaan)
    }

    @Test
    fun testDetectDeviceVendor_standardPhones() {
        val pixel = EInkHardwareManager.detectDeviceVendor(
            manufacturer = "Google",
            brand = "google",
            model = "Pixel 7 Pro",
            product = "cheetah"
        )
        assertEquals(EinkVendor.NONE, pixel)

        val samsung = EInkHardwareManager.detectDeviceVendor(
            manufacturer = "Samsung",
            brand = "samsung",
            model = "SM-S918B",
            product = "dm3q"
        )
        assertEquals(EinkVendor.NONE, samsung)
    }
}
