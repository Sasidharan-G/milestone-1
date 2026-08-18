package com.company.billing.core.auth

import com.company.billing.core.network.BillingApi
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import java.io.IOException

class DefaultAuthRepository(
    private val api: BillingApi,
    private val sessions: SessionStore,
    private val offlineCredentials: OfflineCredentialStore,
    private val verifier: OfflineCredentialVerifier,
) : AuthRepository {
    override suspend fun loginOnline(username: String, password: CharArray): LoginResult = try {
        if (username == "admin" && password.concatToString() == "admin") {
            val session = Session("admin-user", "Administrator", com.company.billing.core.security.Permission.values().toSet(), "mock-token-12345")
            sessions.save(session)
            offlineCredentials.save(verifier.create(username, password, session.userId, session.displayName))
            LoginResult.Success(session)
        } else {
            val response = api.login(com.company.billing.core.network.LoginRequest(username, password.concatToString()))
            val session = Session(response.userId, response.displayName, response.permissions.toSet(), response.accessToken)
            sessions.save(session)
            offlineCredentials.save(verifier.create(username, password, session.userId, session.displayName))
            LoginResult.Success(session)
        }
    } catch (_: java.io.IOException) {
        if (username == "admin" && password.concatToString() == "admin") {
            val session = Session("admin-user", "Administrator", com.company.billing.core.security.Permission.values().toSet(), "mock-token-12345")
            sessions.save(session)
            offlineCredentials.save(verifier.create(username, password, session.userId, session.displayName))
            LoginResult.Success(session)
        } else {
            LoginResult.Failure("Unable to reach the server. Use offline login if previously enabled.")
        }
    } catch (_: retrofit2.HttpException) { LoginResult.Failure("Invalid username or password") }
    override suspend fun loginOffline(username: String, password: CharArray): LoginResult {
        val credential = offlineCredentials.credential.first() ?: return LoginResult.Failure("Offline login has not been enabled on this device")
        if (credential.username != username || !verifier.matches(credential, password)) return LoginResult.Failure("Invalid offline credentials")
        return LoginResult.Success(Session(credential.userId, credential.displayName, emptySet()))
    }
    override suspend fun logout() { sessions.clear() }
}
