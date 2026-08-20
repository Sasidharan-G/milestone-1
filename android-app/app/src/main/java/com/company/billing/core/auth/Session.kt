package com.company.billing.core.auth

import com.company.billing.core.security.Permission

data class Session(
    val userId: String,
    val displayName: String,
    val permissions: Set<Permission>,
    val accessToken: String? = null,
    val companyId: String,
    val role: String
)
sealed interface LoginMode { data object Online : LoginMode; data object Offline : LoginMode }
sealed interface LoginResult { data class Success(val session: Session) : LoginResult; data class Failure(val message: String) : LoginResult }
sealed interface RegisterResult { data class Success(val companyId: String) : RegisterResult; data class Failure(val message: String) : RegisterResult }
sealed interface RecoveryResult { data object Success : RecoveryResult; data class Failure(val message: String) : RecoveryResult }

interface AuthRepository {
    suspend fun loginOnline(username: String, password: CharArray): LoginResult
    suspend fun loginOffline(username: String, password: CharArray): LoginResult
    suspend fun logout()
    suspend fun registerCompany(email: String, password: CharArray, ownerName: String, businessName: String): RegisterResult
    suspend fun recoverPassword(email: String): RecoveryResult
}
