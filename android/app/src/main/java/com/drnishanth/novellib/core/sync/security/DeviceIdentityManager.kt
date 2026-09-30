package com.drnishanth.novellib.core.sync.security

import android.content.Context
import android.os.Build
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class DeviceIdentityManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("device_identity_prefs", Context.MODE_PRIVATE)

    val deviceName: String
        get() = prefs.getString(KEY_DEVICE_NAME, null) ?: Build.MODEL ?: "NovelLib Device"

    val deviceType: String
        get() {
            val model = (Build.MODEL ?: "").lowercase()
            return when {
                model.contains("bigme") || model.contains("onyx") || model.contains("boox") || model.contains("ink") -> "eink"
                context.resources.configuration.smallestScreenWidthDp >= 600 -> "tablet"
                else -> "phone"
            }
        }

    val keyPair: KeyPair by lazy {
        getOrCreateKeyPair()
    }

    val publicKeyBase64: String
        get() = Base64.getEncoder().encodeToString(keyPair.public.encoded)

    val deviceId: String
        get() {
            val digest = MessageDigest.getInstance("SHA-256").digest(keyPair.public.encoded)
            val hex = digest.take(6).joinToString("") { "%02x".format(it) }
            return "dev_$hex"
        }

    fun signData(data: ByteArray): ByteArray {
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(data)
        return signer.sign()
    }

    fun verifySignature(publicKeyBase64: String, data: ByteArray, signatureBytes: ByteArray): Boolean {
        return try {
            val keyBytes = Base64.getDecoder().decode(publicKeyBase64)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("EC")
            val pubKey = keyFactory.generatePublic(keySpec)

            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(pubKey)
            verifier.update(data)
            verifier.verify(signatureBytes)
        } catch (_: Exception) {
            false
        }
    }

    private fun getOrCreateKeyPair(): KeyPair {
        val pubBase64 = prefs.getString(KEY_PUBLIC_KEY, null)
        val privBase64 = prefs.getString(KEY_PRIVATE_KEY, null)

        if (pubBase64 != null && privBase64 != null) {
            try {
                val keyFactory = KeyFactory.getInstance("EC")
                val pubBytes = Base64.getDecoder().decode(pubBase64)
                val privBytes = Base64.getDecoder().decode(privBase64)

                val pubKey = keyFactory.generatePublic(X509EncodedKeySpec(pubBytes))
                val privKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                return KeyPair(pubKey, privKey)
            } catch (_: Exception) {}
        }

        // Generate fresh EC (secp256r1) key pair
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val newKeyPair = kpg.generateKeyPair()

        val pubEncoded = Base64.getEncoder().encodeToString(newKeyPair.public.encoded)
        val privEncoded = Base64.getEncoder().encodeToString(newKeyPair.private.encoded)

        prefs.edit()
            .putString(KEY_PUBLIC_KEY, pubEncoded)
            .putString(KEY_PRIVATE_KEY, privEncoded)
            .apply()

        return newKeyPair
    }

    companion object {
        private const val KEY_PUBLIC_KEY = "device_public_key"
        private const val KEY_PRIVATE_KEY = "device_private_key"
        private const val KEY_DEVICE_NAME = "device_custom_name"

        /**
         * Computes a deterministic 6-digit Short Authentication String (SAS) code
         * from the pairing participants' public keys and nonces.
         */
        fun computeSasCode(keyA: String, keyB: String, nonceA: String, nonceB: String): String {
            // Lexicographical sorting ensures both devices compute the identical code regardless of who initiated
            val sortedKeys = listOf(keyA, keyB).sorted()
            val sortedNonces = listOf(nonceA, nonceB).sorted()

            val combined = (sortedKeys[0] + ":" + sortedKeys[1] + "|" + sortedNonces[0] + ":" + sortedNonces[1]).toByteArray(Charsets.UTF_8)
            val digest = MessageDigest.getInstance("SHA-256").digest(combined)

            // Derive 6-digit integer from first 4 bytes
            val intVal = ((digest[0].toInt() and 0xFF) shl 24) or
                    ((digest[1].toInt() and 0xFF) shl 16) or
                    ((digest[2].toInt() and 0xFF) shl 8) or
                    (digest[3].toInt() and 0xFF)

            val positiveVal = Math.abs(intVal) % 1_000_000
            val codeStr = String.format("%06d", positiveVal)
            return "${codeStr.substring(0, 3)} ${codeStr.substring(3)}"
        }
    }
}
