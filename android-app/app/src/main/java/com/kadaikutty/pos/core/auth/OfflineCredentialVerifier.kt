package com.kadaikutty.pos.core.auth

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class OfflineCredential(val username: String, val userId: String, val displayName: String, val salt: ByteArray, val verifier: ByteArray)

/** Stores a derived verifier only; raw online passwords are never persisted. */
class OfflineCredentialVerifier {
    fun create(username: String, password: CharArray, userId: String, displayName: String): OfflineCredential {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        return OfflineCredential(username, userId, displayName, salt, derive(password, salt))
    }
    fun matches(credential: OfflineCredential, password: CharArray): Boolean = constantTimeEquals(credential.verifier, derive(password, credential.salt))
    private fun derive(password: CharArray, salt: ByteArray): ByteArray = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS).let { spec -> try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() } }
    private fun constantTimeEquals(first: ByteArray, second: ByteArray): Boolean { if (first.size != second.size) return false; var diff = 0; first.indices.forEach { diff = diff or (first[it].toInt() xor second[it].toInt()) }; return diff == 0 }
    private companion object { const val SALT_BYTES = 16; const val ITERATIONS = 210_000; const val KEY_BITS = 256 }
}
