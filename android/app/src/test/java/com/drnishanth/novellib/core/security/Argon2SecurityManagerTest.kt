package com.drnishanth.novellib.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Argon2SecurityManagerTest {

    @Test
    fun testPasswordHashingAndVerification() {
        val password = "SecretFamilyPassword123"
        val hash = Argon2SecurityManager.hashPassword(password)

        assertTrue("Hash should start with \$argon2id", hash.startsWith("\$argon2id"))
        assertTrue("Verification should succeed with correct password", Argon2SecurityManager.verifyPassword(password, hash))
        assertFalse("Verification should fail with incorrect password", Argon2SecurityManager.verifyPassword("WrongPassword", hash))
    }

    @Test
    fun testDifferentSaltsProduceDifferentHashes() {
        val password = "IdenticalPassword"
        val hash1 = Argon2SecurityManager.hashPassword(password)
        val hash2 = Argon2SecurityManager.hashPassword(password)

        assertNotEquals("Hashes for the same password must differ due to random salting", hash1, hash2)
        assertTrue(Argon2SecurityManager.verifyPassword(password, hash1))
        assertTrue(Argon2SecurityManager.verifyPassword(password, hash2))
    }
}
