package com.kadaikutty.pos.core.auth

import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.network.BackendApiClient
import com.kadaikutty.pos.core.network.BackendApiException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.IOException

/**
 * The one rule offline sign-in stands on: a server that answered "no" is never outvoted by the
 * copy of the password this device remembers.
 */
class OfflineLoginFallbackTest {
    private val credentials = mock(OfflineCredentialStore::class.java)

    private fun repository(backend: BackendApiClient) = DefaultAuthRepository(
        mock(SessionStore::class.java), credentials, OfflineCredentialVerifier(),
        mock(BillingDatabase::class.java), backend, mock(SessionSecurityManager::class.java)
    )

    private fun loginFailsWith(error: Exception): LoginResult.Failure = runBlocking {
        // A fresh mock per call: re-stubbing one that already throws would throw inside `when`.
        val backend = mock(BackendApiClient::class.java)
        `when`(backend.login(anyString(), anyString())).thenAnswer { throw error }
        repository(backend).loginOnline("9876543210", "123456".toCharArray()) as LoginResult.Failure
    }

    @Test
    fun `wrong password forbids offline fallback and forgets the stored credential`() = runBlocking {
        val result = loginFailsWith(BackendApiException("AUTH_INVALID", "Invalid mobile number or password", false, 401))
        assertEquals(false, result.canTryOffline)
        verify(credentials).remove("9876543210")
    }

    @Test
    fun `disabled or deleted account forbids offline fallback and forgets the stored credential`() = runBlocking {
        for (status in listOf(403, 404)) {
            val result = loginFailsWith(BackendApiException("ACCOUNT_INACTIVE", "Disabled", false, status))
            assertEquals(false, result.canTryOffline)
        }
        verify(credentials, org.mockito.Mockito.times(2)).remove("9876543210")
    }

    @Test
    fun `no network allows offline fallback and keeps the stored credential`() = runBlocking {
        val result = loginFailsWith(IOException("Unable to resolve host"))
        assertTrue(result.canTryOffline)
        verify(credentials, never()).remove(anyString())
    }

    @Test
    fun `server outage allows offline fallback and keeps the stored credential`() = runBlocking {
        val result = loginFailsWith(BackendApiException("UPSTREAM", "Bad gateway", true, 502))
        assertTrue(result.canTryOffline)
        verify(credentials, never()).remove(anyString())
    }
}
