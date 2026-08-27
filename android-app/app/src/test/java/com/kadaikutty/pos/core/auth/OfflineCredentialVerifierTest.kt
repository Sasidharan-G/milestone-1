package com.kadaikutty.pos.core.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class OfflineCredentialVerifierTest {
    private val verifier = OfflineCredentialVerifier()
    @Test fun `accepts matching password without storing it`() { val credential = verifier.create("cashier", "safe-password".toCharArray(), "u1", "User"); assertTrue(verifier.matches(credential, "safe-password".toCharArray())) }
    @Test fun `rejects invalid password`() { val credential = verifier.create("cashier", "safe-password".toCharArray(), "u1", "User"); assertFalse(verifier.matches(credential, "wrong".toCharArray())) }
}
