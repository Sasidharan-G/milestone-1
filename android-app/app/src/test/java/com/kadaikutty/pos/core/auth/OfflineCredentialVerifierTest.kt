package com.kadaikutty.pos.core.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OfflineCredentialVerifierTest {

    private lateinit var verifier: OfflineCredentialVerifier

    @Before
    fun setup() {
        verifier = OfflineCredentialVerifier()
    }

    @Test
    fun `create and matches returns true for correct password`() {
        // Arrange
        val password = "StrongPassword123!".toCharArray()
        
        // Act
        val credential = verifier.create("testuser", password, "user-123", "Test User")
        val result = verifier.matches(credential, "StrongPassword123!".toCharArray())

        // Assert
        assertTrue("Password should match the credential", result)
    }

    @Test
    fun `matches returns false for incorrect password`() {
        // Arrange
        val password = "StrongPassword123!".toCharArray()
        val wrongPassword = "WrongPassword456?".toCharArray()
        
        // Act
        val credential = verifier.create("testuser", password, "user-123", "Test User")
        val result = verifier.matches(credential, wrongPassword)

        // Assert
        assertFalse("Password should not match the credential", result)
    }

    @Test
    fun `different salts produce different verifier hashes for same password`() {
        // Arrange
        val password = "SamePassword".toCharArray()
        
        // Act
        val credential1 = verifier.create("user1", password, "u1", "U1")
        val credential2 = verifier.create("user2", password, "u2", "U2")

        // Assert
        // They should have different salts
        assertFalse(credential1.salt.contentEquals(credential2.salt))
        // And therefore different verifier hashes
        assertFalse(credential1.verifier.contentEquals(credential2.verifier))
        
        // But both should still match their own passwords
        assertTrue(verifier.matches(credential1, password))
        assertTrue(verifier.matches(credential2, password))
    }
}
