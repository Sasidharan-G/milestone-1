package com.kadaikutty.pos.core.auth

import com.kadaikutty.pos.core.network.BackendApiClient
import com.kadaikutty.pos.core.network.BackendApiException
import com.kadaikutty.pos.core.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every authenticated backend call now requires an X-Session-Id issued by POST /sessions/register,
 * and that same endpoint revokes every other live session of the caller (single active device).
 * This manager registers the device session right after login, heartbeats it every 60s so a remote
 * sign-out (another device logging in, a password reset, an account being disabled) is detected
 * within one heartbeat interval, and tears it down on logout.
 */
@Singleton
class SessionSecurityManager @Inject constructor(
    private val backendApi: BackendApiClient,
    private val sessionStore: SessionStore,
    private val appPreferences: AppPreferences,
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _isSessionTerminated = MutableStateFlow(false)
    val isSessionTerminated: StateFlow<Boolean> = _isSessionTerminated.asStateFlow()
    private val _terminationReason = MutableStateFlow<String?>(null)
    val terminationReason: StateFlow<String?> = _terminationReason.asStateFlow()
    private val _isMasterSessionTerminated = MutableStateFlow(false)
    val isMasterSessionTerminated: StateFlow<Boolean> = _isMasterSessionTerminated.asStateFlow()
    private val _masterTerminationReason = MutableStateFlow<String?>(null)
    val masterTerminationReason: StateFlow<String?> = _masterTerminationReason.asStateFlow()
    private var heartbeatJob: Job? = null
    private var masterHeartbeatJob: Job? = null

    /** Called right after a successful tenant login. Throws if the session cannot be established. */
    suspend fun registerSession(username: String, companyId: String, role: String, sessionToken: String) {
        val active = sessionStore.activeSession.first()
        val accessToken = active?.accessToken ?: error("Online session token is missing")
        val response = backendApi.registerSession(accessToken, appPreferences.getOrCreateInstallationDeviceId())
        val remoteSessionId = response.optJSONObject("session")?.optString("sessionId").orEmpty()
        require(remoteSessionId.isNotBlank()) { "Backend session registration failed" }
        sessionStore.updateSessionToken(remoteSessionId)
        startHeartbeat(accessToken, remoteSessionId)
    }

    fun startListeningToSession(username: String, sessionToken: String) {
        scope.launch {
            val accessToken = sessionStore.activeSession.first()?.accessToken ?: return@launch
            startHeartbeat(accessToken, sessionToken)
        }
    }

    private fun startHeartbeat(accessToken: String, sessionId: String) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                delay(60_000)
                runCatching { backendApi.heartbeat(accessToken, sessionId) }.onFailure {
                    if (it is BackendApiException && it.statusCode == 401) {
                        _isSessionTerminated.value = true
                        _terminationReason.value = "Your session expired or was revoked. Please sign in again."
                        return@launch
                    }
                }
            }
        }
    }

    fun clearSession(username: String) {
        heartbeatJob?.cancel()
        heartbeatJob = null
        scope.launch {
            val active = sessionStore.activeSession.first() ?: return@launch
            val access = active.accessToken ?: return@launch
            val sessionId = active.sessionToken ?: return@launch
            runCatching { backendApi.revokeCurrentSession(access, sessionId) }
        }
    }

    fun resetSessionTermination() { _isSessionTerminated.value = false; _terminationReason.value = null }

    /** Registers a device session for the just-authenticated master token (see [MasterAuthSession]). */
    suspend fun registerMasterSession(): Boolean {
        val accessToken = MasterAuthSession.accessToken ?: return false
        val response = runCatching { backendApi.registerSession(accessToken, appPreferences.getOrCreateInstallationDeviceId(), "Master Control") }
            .getOrElse { return false }
        val sessionId = response.optJSONObject("session")?.optString("sessionId").orEmpty()
        if (sessionId.isBlank()) return false
        MasterAuthSession.saveSession(sessionId)
        startMasterHeartbeat(accessToken, sessionId)
        return true
    }

    private fun startMasterHeartbeat(accessToken: String, sessionId: String) {
        masterHeartbeatJob?.cancel()
        masterHeartbeatJob = scope.launch {
            while (true) {
                delay(60_000)
                runCatching { backendApi.heartbeat(accessToken, sessionId) }.onFailure {
                    if (it is BackendApiException && it.statusCode == 401) {
                        _isMasterSessionTerminated.value = true
                        _masterTerminationReason.value = "Your master session expired or was revoked. Please sign in again."
                        return@launch
                    }
                }
            }
        }
    }

    fun clearMasterSession() {
        masterHeartbeatJob?.cancel()
        masterHeartbeatJob = null
        val accessToken = MasterAuthSession.accessToken
        val sessionId = MasterAuthSession.sessionId
        if (accessToken != null && sessionId != null) {
            scope.launch { runCatching { backendApi.revokeCurrentSession(accessToken, sessionId) } }
        }
        MasterAuthSession.clear()
    }

    fun resetMasterTermination() { _isMasterSessionTerminated.value = false; _masterTerminationReason.value = null }
}
