package com.kadaikutty.pos.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kadaikutty.pos.core.auth.AuthRepository
import com.kadaikutty.pos.core.auth.LoginResult
import com.kadaikutty.pos.core.auth.MasterAuthSession
import com.kadaikutty.pos.core.auth.SessionSecurityManager
import com.kadaikutty.pos.core.network.BackendApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.kadaikutty.pos.BuildConfig

data class LoginUiState(
    val mobileNumber: String = "", 
    val password: String = "", 
    val loading: Boolean = false, 
    val error: String? = null, 
    val complete: Boolean = false,
    val isSuperMaster: Boolean = false,
    val showResetOtpDialog: Boolean = false,
    val resetOtp: String = "",
    val newPasswordString: String = "",
    val resetVerificationId: String? = null,
    // Once the user explicitly flips the toggle themselves, the auto-suggestion below backs off
    // and never overrides their choice again for this screen visit.
)
@HiltViewModel class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val backendApiClient: BackendApiClient,
    private val sessionSecurityManager: SessionSecurityManager,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginUiState()); val state = mutableState.asStateFlow()

    fun updateMobileNumber(value: String) {
        mutableState.update { it.copy(mobileNumber = value, error = null) }
    }
    fun updatePassword(value: String) = mutableState.update { it.copy(password = value, error = null) }
    fun updateResetOtp(value: String) = mutableState.update { it.copy(resetOtp = value, error = null) }
    fun updateNewPassword(value: String) = mutableState.update { it.copy(newPasswordString = value, error = null) }
    fun dismissResetDialog() = mutableState.update { it.copy(showResetOtpDialog = false, resetOtp = "", newPasswordString = "", resetVerificationId = null) }
    
    fun validateMasterPin(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val token = runCatching {
                backendApiClient.loginMaster(BuildConfig.MASTER_SUPPORT_PHONE, pin)
                    .getJSONObject("tokens")
                    .getString("accessToken")
            }.getOrNull()
            // Establishing the device session is required: every master API call now needs the
            // X-Session-Id it returns, so a PIN check that got a token but no session is a failure.
            val sessionEstablished = if (token != null) {
                MasterAuthSession.save(token)
                sessionSecurityManager.registerMasterSession()
            } else false
            if (token != null && !sessionEstablished) MasterAuthSession.clear()
            onResult(sessionEstablished)
        }
    }

    fun requestMasterResetOtp(
        activity: android.app.Activity,
        customMobile: String? = null,
        onCodeSent: (String, String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                mutableState.update { it.copy(loading = true, error = null) }
                val phone = customMobile?.ifBlank { null } ?: BuildConfig.MASTER_SUPPORT_PHONE
                val clean = phone.replace("[^0-9]".toRegex(), "").takeLast(10)
                val phoneWithCode = "+91$clean"

                authRepository.sendPasswordResetOtp(
                    mobileNumber = phoneWithCode,
                    activity = activity,
                    onCodeSent = { vId ->
                        mutableState.update { it.copy(loading = false) }
                        onCodeSent(vId, phoneWithCode)
                    }
                ) { err ->
                    mutableState.update { it.copy(loading = false, error = err) }
                    onError(err)
                }
            } catch (e: Exception) {
                mutableState.update { it.copy(loading = false, error = e.message) }
                onError(e.message ?: "Failed to send Master SMS OTP")
            }
        }
    }

    fun verifyMasterOtpAndSetNewPin(
        verificationId: String,
        otp: String,
        newPin: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                mutableState.update { it.copy(loading = true, error = null) }
                when (val result = authRepository.changeMasterPin(verificationId, otp, newPin.toCharArray(), BuildConfig.MASTER_SUPPORT_PHONE)) {
                    is com.kadaikutty.pos.core.auth.RecoveryResult.Success -> {
                        mutableState.update { it.copy(loading = false) }
                        onResult(true, null)
                    }
                    is com.kadaikutty.pos.core.auth.RecoveryResult.Failure -> {
                        mutableState.update { it.copy(loading = false, error = result.message) }
                        onResult(false, result.message)
                    }
                }
            } catch (e: Exception) {
                mutableState.update { it.copy(loading = false, error = e.message) }
                onResult(false, e.message)
            }
        }
    }

    fun login() {
        val current = state.value
        val cleanPhone = current.mobileNumber.trim()
        if (cleanPhone.isBlank() || current.password.isBlank()) {
            mutableState.update { it.copy(error = "Mobile Number and password are required") }
            return
        }

        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            val password = current.password.toCharArray()
            // Online first, always. Only when the server could not be reached does this fall back to
            // the credential saved by this number's last successful online sign-in here. A wrong
            // password or a disabled account is a server answer, not a connection problem, so it
            // never falls through — see DefaultAuthRepository.isServerRejection.
            val online = authRepository.loginOnline(cleanPhone, password)
            val result = if (online is LoginResult.Failure && online.canTryOffline) {
                authRepository.loginOffline(cleanPhone, password)
            } else {
                online
            }
            password.fill('\u0000')
            
            // No artificial delay - animation runs for exact duration of login
            
            mutableState.update {
                when (result) {
                    is LoginResult.Success -> it.copy(loading = false, complete = true, isSuperMaster = (result.session.role == "SUPER_ADMIN"), password = "")
                    is LoginResult.Failure -> it.copy(loading = false, error = result.message, password = "")
                }
            }
        }
    }

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
        if (current.newPasswordString.length != 6 || !current.newPasswordString.all { it.isDigit() }) {
            mutableState.update { it.copy(error = "New password must be exactly 6 numeric digits") }
            return
        }

        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            val passChars = current.newPasswordString.toCharArray()
            val res = authRepository.verifyOtpAndResetPassword(
                verificationId = verificationId,
                otp = current.resetOtp,
                newPassword = passChars,
                mobileNumber = current.mobileNumber
            )
            passChars.fill('\u0000')
            when (res) {
                is com.kadaikutty.pos.core.auth.RecoveryResult.Success -> {
                    mutableState.update { it.copy(loading = false, showResetOtpDialog = false, resetOtp = "", newPasswordString = "", resetVerificationId = null, error = null) }
                    onResult(true, null)
                }
                is com.kadaikutty.pos.core.auth.RecoveryResult.Failure -> {
                    mutableState.update { it.copy(loading = false, error = res.message) }
                    onResult(false, res.message)
                }
            }
        }
    }

    fun clearError() {
        mutableState.update { it.copy(error = null) }
    }
}
