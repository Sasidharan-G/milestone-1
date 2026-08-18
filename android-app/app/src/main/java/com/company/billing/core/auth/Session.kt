package com.company.billing.core.auth

import com.company.billing.core.security.Permission

data class Session(val userId: String, val displayName: String, val permissions: Set<Permission>, val accessToken: String? = null)
sealed interface LoginMode { data object Online : LoginMode; data object Offline : LoginMode }
sealed interface LoginResult { data class Success(val session: Session) : LoginResult; data class Failure(val message: String) : LoginResult }
interface AuthRepository {
    suspend fun loginOnline(username: String, password: CharArray): LoginResult
    suspend fun loginOffline(username: String, password: CharArray): LoginResult
    suspend fun logout()
}
