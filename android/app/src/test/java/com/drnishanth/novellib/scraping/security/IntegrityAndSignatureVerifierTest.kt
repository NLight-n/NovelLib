package com.drnishanth.novellib.scraping.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrityAndSignatureVerifierTest {

    @Test
    fun testSha256ChecksumVerification() {
        val sampleJson = """{"id":"sample","version":1}"""
        val computedHash = IntegrityAndSignatureVerifier.sha256(sampleJson)

        assertTrue(
            "Checksum with sha256: prefix should match",
            IntegrityAndSignatureVerifier.verifyChecksum(sampleJson, "sha256:$computedHash")
        )
        assertTrue(
            "Raw hex checksum should match",
            IntegrityAndSignatureVerifier.verifyChecksum(sampleJson, computedHash)
        )
    }

    @Test
    fun testDetectTamperedContent() {
        val originalJson = """{"id":"sample","version":1}"""
        val tamperedJson = """{"id":"sample","version":1,"malicious":true}"""
        val originalHash = IntegrityAndSignatureVerifier.sha256(originalJson)

        assertFalse(
            "Tampered content must fail checksum verification",
            IntegrityAndSignatureVerifier.verifyChecksum(tamperedJson, originalHash)
        )
    }
}
