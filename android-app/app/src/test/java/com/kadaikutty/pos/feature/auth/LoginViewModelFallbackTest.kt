package com.kadaikutty.pos.feature.auth

import com.kadaikutty.pos.core.auth.AuthRepository
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

    private fun login() {
        val viewModel = LoginViewModel(auth, mock(BackendApiClient::class.java), mock(SessionSecurityManager::class.java))
        viewModel.updateMobileNumber("9876543210")
        viewModel.updatePassword("123456")
        viewModel.login()
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
}
