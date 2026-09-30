package com.drnishanth.novellib.core.security

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object Argon2SecurityManager {
    private const val ITERATIONS = 3
    private const val MEMORY_KB = 32768 // 32MB suitable for mobile devices
    private const val PARALLELISM = 1
    private const val HASH_LENGTH = 32
    private const val SALT_LENGTH = 16

    private val secureRandom = SecureRandom()

    /**
     * Hashes a plaintext password using Argon2id with a cryptographically secure random salt.
     * Returns a standard formatted string: $argon2id$v=19$m=32768,t=3,p=1$<salt>$<hash>
     */
    fun hashPassword(password: String): String {
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)

        val hash = generateArgon2id(password.toCharArray(), salt, ITERATIONS, MEMORY_KB, PARALLELISM)

        val saltBase64 = Base64.getEncoder().withoutPadding().encodeToString(salt)
        val hashBase64 = Base64.getEncoder().withoutPadding().encodeToString(hash)

        return "\$argon2id\$v=19\$m=$MEMORY_KB,t=$ITERATIONS,p=$PARALLELISM\$$saltBase64\$$hashBase64"
    }

    /**
     * Verifies a password against the stored Argon2id hash string using constant-time comparison.
     */
    fun verifyPassword(password: String, storedHashString: String): Boolean {
        return try {
            val parts = storedHashString.split("$").filter { it.isNotEmpty() }
            if (parts.size < 5 || parts[0] != "argon2id") return false

            val params = parts[2].split(",")
            var m = MEMORY_KB
            var t = ITERATIONS
            var p = PARALLELISM

            for (param in params) {
                val kv = param.split("=")
                if (kv.size == 2) {
                    when (kv[0]) {
                        "m" -> m = kv[1].toInt()
                        "t" -> t = kv[1].toInt()
                        "p" -> p = kv[1].toInt()
                    }
                }
            }

            val salt = Base64.getDecoder().decode(parts[3])
            val expectedHash = Base64.getDecoder().decode(parts[4])

            val computedHash = generateArgon2id(password.toCharArray(), salt, t, m, p)
            MessageDigest.isEqual(computedHash, expectedHash)
        } catch (e: Exception) {
            false
        }
    }

    private fun generateArgon2id(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        memoryKb: Int,
        parallelism: Int
    ): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(iterations)
            .withMemoryAsKB(memoryKb)
            .withParallelism(parallelism)
            .withSalt(salt)
            .build()

        val generator = Argon2BytesGenerator()
        generator.init(params)

        val result = ByteArray(HASH_LENGTH)
        val passwordBytes = String(password).toByteArray(Charsets.UTF_8)
        generator.generateBytes(passwordBytes, result, 0, result.size)
        return result
    }
}
