package com.drnishanth.novellib.sync

import com.drnishanth.novellib.core.sync.security.DeviceIdentityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdentityManagerTest {

    @Test
    fun testSasCodeSymmetry() {
        val keyA = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE11111111111111111111111111111111"
        val keyB = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE22222222222222222222222222222222"
        val nonceA = "nonce-device-alpha"
        val nonceB = "nonce-device-beta"

        // Device A calculates SAS code
        val codeFromA = DeviceIdentityManager.computeSasCode(keyA, keyB, nonceA, nonceB)

        // Device B calculates SAS code (keys and nonces passed in reverse parameter order)
        val codeFromB = DeviceIdentityManager.computeSasCode(keyB, keyA, nonceB, nonceA)

        // Both devices MUST compute the exact same 6-digit verification string
        assertEquals(codeFromA, codeFromB)
        assertEquals(7, codeFromA.length) // "### ###" format
        assertTrue(codeFromA.matches(Regex("\\d{3} \\d{3}")))
    }

    @Test
    fun testSasCodeDivergenceOnDifferentKeys() {
        val keyA = "Key-Alpha"
        val keyB = "Key-Beta"
        val keyC = "Key-Gamma" // MitM attacker key
        val nonceA = "nonce-1"
        val nonceB = "nonce-2"

        val legitimateCode = DeviceIdentityManager.computeSasCode(keyA, keyB, nonceA, nonceB)
        val tamperedCode = DeviceIdentityManager.computeSasCode(keyA, keyC, nonceA, nonceB)

        assertNotEquals(legitimateCode, tamperedCode)
    }
}
