package com.kadaikutty.pos.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kadaikutty.pos.core.auth.AuthRepository
import com.kadaikutty.pos.core.auth.LoginMode
import com.kadaikutty.pos.core.auth.LoginResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val mobileNumber: String = "", 
    val password: String = "", 
    val mode: LoginMode = LoginMode.Online, 
    val loading: Boolean = false, 
    val error: String? = null, 
    val complete: Boolean = false,
    val showResetOtpDialog: Boolean = false,
    val resetOtp: String = "",
    val newPasswordString: String = "",
    val resetVerificationId: String? = null
)
@HiltViewModel class LoginViewModel @Inject constructor(private val authRepository: AuthRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginUiState()); val state = mutableState.asStateFlow()
    fun updateMobileNumber(value: String) = mutableState.update { it.copy(mobileNumber = value, error = null) }
    fun updatePassword(value: String) = mutableState.update { it.copy(password = value, error = null) }
    fun updateMode(value: LoginMode) = mutableState.update { it.copy(mode = value, error = null) }
    fun updateResetOtp(value: String) = mutableState.update { it.copy(resetOtp = value, error = null) }
    fun updateNewPassword(value: String) = mutableState.update { it.copy(newPasswordString = value, error = null) }
    fun dismissResetDialog() = mutableState.update { it.copy(showResetOtpDialog = false, resetOtp = "", newPasswordString = "", resetVerificationId = null) }
    
    fun login() { val current = state.value; if (current.mobileNumber.isBlank() || current.password.isBlank()) { mutableState.update { it.copy(error = "Mobile Number and password are required") }; return }; viewModelScope.launch { mutableState.update { it.copy(loading = true, error = null) }; val password = current.password.toCharArray(); val result = when (current.mode) { LoginMode.Online -> authRepository.loginOnline(current.mobileNumber, password); LoginMode.Offline -> authRepository.loginOffline(current.mobileNumber, password) }; password.fill('\u0000'); mutableState.update { when (result) { is LoginResult.Success -> it.copy(loading = false, complete = true, password = ""); is LoginResult.Failure -> it.copy(loading = false, error = result.message, password = "") } } } }

    fun requestPasswordResetOtp(
        mobileNumber: String,
        activity: android.app.Activity,
        onCodeSent: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val cleanPhone = mobileNumber.trim().replace(" ", "").replace("-", "")
        if (cleanPhone.length < 10 || !cleanPhone.all { it.isDigit() || it == '+' }) {
            onError("Please provide a valid mobile number")
            return
        }
        val phoneWithCode = if (cleanPhone.startsWith("+")) cleanPhone else "+91$cleanPhone"

        mutableState.update { it.copy(loading = true, error = null) }
        authRepository.sendPasswordResetOtp(
            mobileNumber = phoneWithCode,
            activity = activity,
            onCodeSent = { verificationId ->
                mutableState.update { it.copy(loading = false, showResetOtpDialog = true, resetVerificationId = verificationId) }
                onCodeSent(verificationId)
            },
            onVerificationFailed = { error ->
                mutableState.update { it.copy(loading = false, error = error) }
                onError(error)
            }
        )
    }

    fun verifyOtpAndResetPassword(
        onResult: (Boolean, String?) -> Unit
    ) {
        val current = state.value
        val verificationId = current.resetVerificationId
        if (verificationId == null || current.resetOtp.isBlank() || current.newPasswordString.isBlank()) {
            mutableState.update { it.copy(error = "All fields are required") }
            return
        }
        if (current.newPasswordString.length < 6) {
            mutableState.update { it.copy(error = "Password must be at least 6 characters") }
            return
        }

        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            val passChars = current.newPasswordString.toCharArray()
            val res = authRepository.verifyOtpAndResetPassword(verificationId, current.resetOtp, passChars)
            passChars.fill('\u0000')
            when (res) {
                is com.kadaikutty.pos.core.auth.RecoveryResult.Success -> {
                    mutableState.update { it.copy(loading = false, showResetOtpDialog = false) }
                    onResult(true, null)
                }
                is com.kadaikutty.pos.core.auth.RecoveryResult.Failure -> {
                    mutableState.update { it.copy(loading = false, error = res.message) }
                    onResult(false, res.message)
                }
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
                val res = authRepository.handleGoogleSignInSuccess()
                when (res) {
                    is com.kadaikutty.pos.core.auth.GoogleSignInResult.Success -> {
                        mutableState.update { it.copy(loading = false, complete = true) }
                        onResult(true, null)
                    }
                    is com.kadaikutty.pos.core.auth.GoogleSignInResult.NewUserNeedsCompanyDetails -> {
                        mutableState.update { it.copy(loading = false, error = "No business account found for this Google email. Please tap 'New business? Register here'.") }
                        onResult(false, "No business account found")
                    }
                    is com.kadaikutty.pos.core.auth.GoogleSignInResult.Failure -> {
                        mutableState.update { it.copy(loading = false, error = res.message) }
                        onResult(false, res.message)
                    }
                }
            } catch (e: Exception) {
                mutableState.update { it.copy(loading = false, error = e.message) }
                onResult(false, e.message)
            }
        }
    }
}
