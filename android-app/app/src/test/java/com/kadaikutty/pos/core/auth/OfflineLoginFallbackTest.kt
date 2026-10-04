package com.kadaikutty.pos.core.auth

import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.network.BackendApiClient
import com.kadaikutty.pos.core.network.BackendApiException
import kotlinx.coroutines.flow.flowOf
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
    fun `a number that never signed in here is told so, truthfully, and flagged for the screen to explain`() = runBlocking {
        `when`(credentials.getCredential("9876543210")).thenReturn(flowOf(null))
        val backend = mock(BackendApiClient::class.java)

        val result = repository(backend).loginOffline("9876543210", "123456".toCharArray()) as LoginResult.Failure

        assertTrue(result.neverSignedInHere)
        assertEquals(FIRST_SIGN_IN_NEEDS_INTERNET, result.message)
        assertTrue("it must not claim there is no internet: ${result.message}", !result.message.contains("No internet", ignoreCase = true))
    }

    @Test
    fun `a connection failure is explained in plain words and marked as a connection problem`() = runBlocking {
        val result = loginFailsWith(java.net.UnknownHostException("Unable to resolve host"))
        assertTrue(result.connectionProblem)
        assertTrue("it explains the cause: ${result.message}", result.message.contains("Can't reach the server"))
        assertTrue(result.canTryOffline)
    }

    @Test
    fun `a failure on this phone after the server accepted the sign-in is named and is not a connection problem`() = runBlocking {
        val result = loginFailsWith(IllegalStateException("disk full"))
        assertTrue(!result.connectionProblem)
        assertTrue("it names the failure: ${result.message}", result.message.contains("IllegalStateException") && result.message.contains("disk full"))
        assertTrue("and never blames the internet: ${result.message}", !result.message.contains("internet", ignoreCase = true))
        assertTrue(result.canTryOffline)
    }

    @Test
    fun `a busy server counts as a connection problem`() = runBlocking {
        for (status in listOf(429, 502, 503)) {
            assertTrue(loginFailsWith(BackendApiException("BUSY", "Please try again shortly", true, status)).connectionProblem)
        }
    }

    @Test
    fun `server outage allows offline fallback and keeps the stored credential`() = runBlocking {
        val result = loginFailsWith(BackendApiException("UPSTREAM", "Bad gateway", true, 502))
        assertTrue(result.canTryOffline)
        verify(credentials, never()).remove(anyString())
    }
}
