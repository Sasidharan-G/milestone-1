package com.kadaikutty.pos.feature.auth

import com.kadaikutty.pos.core.auth.AuthRepository
import com.kadaikutty.pos.core.auth.FIRST_SIGN_IN_NEEDS_INTERNET
import com.kadaikutty.pos.core.auth.LoginResult
import com.kadaikutty.pos.core.auth.SessionSecurityManager
import com.kadaikutty.pos.core.network.BackendApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelFallbackTest {
    private val auth = mock(AuthRepository::class.java)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun login(): LoginViewModel {
        val viewModel = LoginViewModel(auth, mock(BackendApiClient::class.java), mock(SessionSecurityManager::class.java))
        viewModel.updateMobileNumber("9876543210")
        viewModel.updatePassword("123456")
        viewModel.login()
        return viewModel
    }

    @Test
    fun `server rejection never tries the offline credential`() = runBlocking {
        `when`(auth.loginOnline(anyString(), any(CharArray::class.java) ?: CharArray(0)))
            .thenReturn(LoginResult.Failure("Invalid mobile number or password", canTryOffline = false))
        login()
        verify(auth, never()).loginOffline(anyString(), any(CharArray::class.java) ?: CharArray(0))
        Unit
    }

    @Test
    fun `unreachable server falls back to the offline credential`() = runBlocking {
        `when`(auth.loginOnline(anyString(), any(CharArray::class.java) ?: CharArray(0)))
            .thenReturn(LoginResult.Failure("Unable to resolve host", canTryOffline = true))
        `when`(auth.loginOffline(anyString(), any(CharArray::class.java) ?: CharArray(0)))
            .thenReturn(LoginResult.Failure("Invalid mobile number or password"))
        login()
        verify(auth).loginOffline(anyString(), any(CharArray::class.java) ?: CharArray(0))
        Unit
    }

    @Test
    fun `a phone with nothing saved says why the server could not be used`() = runBlocking {
        val reason = "Secure connection failed. Make sure the phone's date and time are correct (set them to Automatic) and try again."
        `when`(auth.loginOnline(anyString(), any(CharArray::class.java) ?: CharArray(0)))
            .thenReturn(LoginResult.Failure(reason, canTryOffline = true, connectionProblem = true))
        `when`(auth.loginOffline(anyString(), any(CharArray::class.java) ?: CharArray(0)))
            .thenReturn(LoginResult.Failure(FIRST_SIGN_IN_NEEDS_INTERNET, neverSignedInHere = true))

        val shown = login().state.value.error.orEmpty()

        assertTrue("the real reason is shown: $shown", shown.contains(reason))
        assertTrue("and what that means for this phone: $shown", shown.contains(FIRST_SIGN_IN_NEEDS_INTERNET))
        assertFalse("never a bare claim of no internet", shown.contains("No internet"))
    }

    @Test
    fun `a failure on this phone after the server accepted the sign-in is named, not blamed on the internet`() = runBlocking {
        val reason = "This phone could not finish signing in (SQLiteException: disk full). Try again; if it keeps happening, contact support."
        `when`(auth.loginOnline(anyString(), any(CharArray::class.java) ?: CharArray(0)))
            .thenReturn(LoginResult.Failure(reason, canTryOffline = true, connectionProblem = false))
        `when`(auth.loginOffline(anyString(), any(CharArray::class.java) ?: CharArray(0)))
            .thenReturn(LoginResult.Failure(FIRST_SIGN_IN_NEEDS_INTERNET, neverSignedInHere = true))

        assertEquals(reason, login().state.value.error)
    }

    @Test
    fun `a wrong password saved on this phone is reported as it is, not buried under the connection error`() = runBlocking {
        `when`(auth.loginOnline(anyString(), any(CharArray::class.java) ?: CharArray(0)))
            .thenReturn(LoginResult.Failure("Can't reach the server.", canTryOffline = true))
        `when`(auth.loginOffline(anyString(), any(CharArray::class.java) ?: CharArray(0)))
            .thenReturn(LoginResult.Failure("Invalid mobile number or password"))

        assertEquals("Invalid mobile number or password", login().state.value.error)
    }
}
