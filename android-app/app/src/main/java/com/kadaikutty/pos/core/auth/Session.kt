package com.kadaikutty.pos.core.auth

import com.kadaikutty.pos.core.security.Permission

data class Session(
    val userId: String,
    val displayName: String,
    val permissions: Set<Permission>,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val companyId: String,
    val role: String,
    val sessionToken: String? = null,
    val deviceId: String? = null
)
/**
 * What an offline sign-in says when this phone has nothing to sign in with: the number never signed
 * in here, so there is no saved credential. It must not claim "no internet": the person reached this
 * point because the server could not be used, and the screen adds the real reason before this text.
 */
const val FIRST_SIGN_IN_NEEDS_INTERNET = "This phone has not signed in with this number before, so the first sign-in needs the internet."

sealed interface LoginResult {
    data class Success(val session: Session) : LoginResult
    /**
     * [neverSignedInHere]: the offline fallback had no saved credential for this number on this phone.
     * [connectionProblem]: the server could not be reached or was not itself (no internet, DNS, wrong
     * clock, timeout, busy server), as opposed to this phone failing to finish a sign-in the server accepted.
     */
    data class Failure(
        val message: String,
        val canTryOffline: Boolean = false,
        val neverSignedInHere: Boolean = false,
        val connectionProblem: Boolean = false,
    ) : LoginResult
}
sealed interface RegisterResult { data class Success(val companyId: String) : RegisterResult; data class Failure(val message: String) : RegisterResult }
sealed interface RecoveryResult { data object Success : RecoveryResult; data class Failure(val message: String) : RecoveryResult }

interface AuthRepository {
    suspend fun loginOnline(username: String, password: CharArray): LoginResult
    suspend fun loginOffline(username: String, password: CharArray): LoginResult
    suspend fun logout()
    
    // Direct Instant Registration
    suspend fun registerMerchant(mobileNumber: String, password: CharArray, ownerName: String, businessName: String): RegisterResult

    // Registration Flow (OTP)
    fun sendRegistrationOtp(mobileNumber: String, activity: android.app.Activity, onCodeSent: (String) -> Unit, onVerificationFailed: (String) -> Unit)
    suspend fun verifyRegistrationOtpAndRegister(verificationId: String, otp: String, mobileNumber: String, password: CharArray, ownerName: String, businessName: String): RegisterResult

    // Password Recovery Flow
    fun sendPasswordResetOtp(mobileNumber: String, activity: android.app.Activity, onCodeSent: (String) -> Unit, onVerificationFailed: (String) -> Unit)
    suspend fun verifyOtpAndResetPassword(verificationId: String, otp: String, newPassword: CharArray, mobileNumber: String = ""): RecoveryResult
    suspend fun changeMasterPin(verificationId: String, otp: String, newPin: CharArray, mobileNumber: String): RecoveryResult
}
