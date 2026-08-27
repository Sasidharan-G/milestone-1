package com.kadaikutty.pos.core.auth

import com.kadaikutty.pos.core.database.BillingDatabase
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class DefaultAuthRepositoryTest {

    private lateinit var supabase: SupabaseClient
    private lateinit var sessionStore: SessionStore
    private lateinit var offlineCredStore: OfflineCredentialStore
    private lateinit var verifier: OfflineCredentialVerifier
    private lateinit var database: BillingDatabase
    private lateinit var authRepository: DefaultAuthRepository

    @Before
    fun setUp() {
        supabase = mock(SupabaseClient::class.java)
        sessionStore = mock(SessionStore::class.java)
        offlineCredStore = mock(OfflineCredentialStore::class.java)
        verifier = mock(OfflineCredentialVerifier::class.java)
        database = mock(BillingDatabase::class.java)

        authRepository = DefaultAuthRepository(
            supabase = supabase,
            sessions = sessionStore,
            offlineCredentials = offlineCredStore,
            verifier = verifier,
            database = database
        )
    }

    @Test
    fun testOfflineLoginEnforcesSevenDayLimit() = runBlocking {
        val username = "cashier@company.com"
        val password = "password123".toCharArray()
        
        // Mock a user that has expired credentials (e.g. offline valid until 1 hour ago)
        val expiredTime = System.currentTimeMillis() - 3600000L
        val expiredUser = UserEntity(
            id = "user-123",
            username = username,
            displayName = "Cashier User",
            salt = "salt",
            verifier = "verifier",
            permissions = "CASH_TRANSACTION",
            companyId = "company-456",
            role = "CASHIER",
            lastOnlineVerifiedAt = System.currentTimeMillis() - (8 * 24 * 60 * 60 * 1000L), // 8 days ago
            offlineValidUntil = expiredTime
        )
        
        val mockUserDao = mock(com.kadaikutty.pos.core.auth.UserDao::class.java)
        `when`(database.userDao()).thenReturn(mockUserDao)
        `when`(mockUserDao.getUserByUsername(username)).thenReturn(expiredUser)
        
        val result = authRepository.loginOffline(username, password)
        assertTrue(result is LoginResult.Failure)
        assertEquals("Offline login expired. Please connect to the internet to sign in.", (result as LoginResult.Failure).message)
    }

    @Test
    fun testOfflineLoginAllowsValidCredentialsWithinLimit() = runBlocking {
        val username = "cashier@company.com"
        val password = "password123".toCharArray()
        
        // Mock a user that has valid credentials (e.g. offline valid until 6 days from now)
        val validUntil = System.currentTimeMillis() + (6 * 24 * 60 * 60 * 1000L)
        val validUser = UserEntity(
            id = "user-123",
            username = username,
            displayName = "Cashier User",
            salt = "c2FsdA==", // Valid base64 encoding of "salt" to avoid Base64 decoding failures
            verifier = "dmVyaWZpZXI=", // Valid base64 encoding of "verifier"
            permissions = "CASH_TRANSACTION",
            companyId = "company-456",
            role = "CASHIER",
            lastOnlineVerifiedAt = System.currentTimeMillis(),
            offlineValidUntil = validUntil
        )
        
        val mockUserDao = mock(com.kadaikutty.pos.core.auth.UserDao::class.java)
        `when`(database.userDao()).thenReturn(mockUserDao)
        `when`(mockUserDao.getUserByUsername(username)).thenReturn(validUser)
        `when`(verifier.matches(anyCredential(), anyCharArray())).thenReturn(true)
        
        val result = authRepository.loginOffline(username, password)
        assertTrue(result is LoginResult.Success)
    }

    private fun anyCredential(): OfflineCredential {
        org.mockito.ArgumentMatchers.any<OfflineCredential>()
        return OfflineCredential("", "", "", byteArrayOf(), byteArrayOf())
    }

    private fun anyCharArray(): CharArray {
        org.mockito.ArgumentMatchers.any<CharArray>()
        return charArrayOf()
    }
}
