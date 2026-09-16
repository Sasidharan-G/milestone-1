package com.kadaikutty.pos.core.auth

import com.kadaikutty.pos.core.security.Permission

data class Session(
    val userId: String,
    val displayName: String,
    val permissions: Set<Permission>,
    val accessToken: String? = null,
    val companyId: String,
    val role: String,
    val sessionToken: String? = null,
    val deviceId: String? = null
)
sealed interface LoginMode { data object Online : LoginMode; data object Offline : LoginMode }
sealed interface LoginResult { data class Success(val session: Session) : LoginResult; data class Failure(val message: String) : LoginResult }
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
