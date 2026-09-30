package com.drnishanth.novellib.scraping.security

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object IntegrityAndSignatureVerifier {

    /**
     * Verifies that the raw JSON content matches the expected SHA-256 checksum string.
     * Expected format: "sha256:<hex_hash>" or just "<hex_hash>".
     */
    fun verifyChecksum(content: String, expectedChecksum: String): Boolean {
        val cleanExpected = expectedChecksum.removePrefix("sha256:").trim().lowercase()
        val computedHash = sha256(content).lowercase()
        return cleanExpected == computedHash
    }

    /**
     * Calculates the SHA-256 hex digest of a string using UTF-8 encoding.
     */
    fun sha256(content: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(content.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies a digital signature against an X.509 encoded public key.
     * Algorithm defaults to SHA256withECDSA (or SHA256withRSA).
     */
    fun verifySignature(
        content: String,
        signatureBase64: String?,
        publicKeyBase64: String?,
        algorithm: String = "SHA256withECDSA"
    ): Boolean {
        if (signatureBase64.isNullOrBlank() || publicKeyBase64.isNullOrBlank()) {
            // If signature is not provided and no key is configured, rely on verified SHA-256 checksum
            return true
        }

        return try {
            val keyBytes = Base64.getDecoder().decode(publicKeyBase64)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance(if (algorithm.contains("ECDSA")) "EC" else "RSA")
            val pubKey: PublicKey = keyFactory.generatePublic(keySpec)

            val sig = Signature.getInstance(algorithm)
            sig.initVerify(pubKey)
            sig.update(content.toByteArray(Charsets.UTF_8))

            val signatureBytes = Base64.getDecoder().decode(signatureBase64)
            sig.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }
}
