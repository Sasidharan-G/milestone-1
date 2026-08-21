package com.company.billing.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.company.billing.core.auth.AuthRepository
import com.company.billing.core.auth.LoginMode
import com.company.billing.core.auth.LoginResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(val username: String = "", val password: String = "", val mode: LoginMode = LoginMode.Online, val loading: Boolean = false, val error: String? = null, val complete: Boolean = false)
@HiltViewModel class LoginViewModel @Inject constructor(private val authRepository: AuthRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginUiState()); val state = mutableState.asStateFlow()
    fun updateUsername(value: String) = mutableState.update { it.copy(username = value, error = null) }
    fun updatePassword(value: String) = mutableState.update { it.copy(password = value, error = null) }
    fun updateMode(value: LoginMode) = mutableState.update { it.copy(mode = value, error = null) }
    fun login() { val current = state.value; if (current.username.isBlank() || current.password.isBlank()) { mutableState.update { it.copy(error = "Username and password are required") }; return }; viewModelScope.launch { mutableState.update { it.copy(loading = true, error = null) }; val password = current.password.toCharArray(); val result = when (current.mode) { LoginMode.Online -> authRepository.loginOnline(current.username, password); LoginMode.Offline -> authRepository.loginOffline(current.username, password) }; password.fill('\u0000'); mutableState.update { when (result) { is LoginResult.Success -> it.copy(loading = false, complete = true, password = ""); is LoginResult.Failure -> it.copy(loading = false, error = result.message, password = "") } } } }

    fun recoverPassword(email: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val res = authRepository.recoverPassword(email)
            when (res) {
                is com.company.billing.core.auth.RecoveryResult.Success -> onResult(true, null)
                is com.company.billing.core.auth.RecoveryResult.Failure -> onResult(false, res.message)
            }
        }
    }

    fun signInWithGoogle() {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            try {
                authRepository.signInWithGoogle()
                // Do not clear loading yet, because browser will open
            } catch (e: Exception) {
                mutableState.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun handleGoogleSignInSuccess(onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                authRepository.handleGoogleSignInSuccess()
                mutableState.update { it.copy(loading = false, complete = true) }
                onResult(true, null)
            } catch (e: Exception) {
                mutableState.update { it.copy(loading = false, error = e.message) }
                onResult(false, e.message)
            }
        }
    }
}
