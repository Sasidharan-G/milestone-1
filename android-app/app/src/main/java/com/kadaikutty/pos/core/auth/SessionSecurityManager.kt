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
 * Every authenticated backend call requires an X-Session-Id issued by POST /sessions/register,
 * and that same endpoint revokes every other live session of the caller (single active device).
 * This manager registers the device session right after login, heartbeats it every 60s so a remote
 * sign-out is detected within one heartbeat interval, and tears it down on logout.
 */
@Singleton
class SessionSecurityManager @Inject constructor(
    private val backendApi: BackendApiClient,
    private val sessionStore: SessionStore,
    private val appPreferences: AppPreferences,
    private val webSocketManager: com.kadaikutty.pos.core.network.WebSocketManager,
    private val offlineCredentials: OfflineCredentialStore,
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

    init {
        scope.launch {
            webSocketManager.sessionRevokedFlow.collect { reason ->
                // A disabled or deleted account must not keep signing in offline on this device.
                // Done before notifySessionRevoked, whose clear() would lose the user id.
                if (webSocketManager.lastRevokeDisabledAccount) {
                    sessionStore.activeSession.first()?.userId?.let { offlineCredentials.removeByUserId(it) }
                }
                notifySessionRevoked(reason)
            }
        }
    }

    fun notifySessionRevoked(reason: String = "Your account was logged in on another device. Active session ended.") {
        heartbeatJob?.cancel()
        heartbeatJob = null
        _terminationReason.value = reason
        _isSessionTerminated.value = true
        scope.launch {
            sessionStore.clear()
        }
    }

    /**
     * Called right after a successful tenant login. Throws if the session cannot be established.
     *
     * It takes nothing: the identity comes from the stored session and the device id from
     * preferences. It used to declare username, companyId, role and sessionToken, none of which
     * it read - and the caller was passing a user id as "username".
     */
    suspend fun registerSession(): String {
        resetSessionTermination()
        val active = sessionStore.activeSession.first()
        val accessToken = active?.accessToken ?: error("Online session token is missing")
        val response = backendApi.registerSession(accessToken, appPreferences.getOrCreateInstallationDeviceId())
        val remoteSessionId = response.optJSONObject("session")?.optString("sessionId").orEmpty()
        require(remoteSessionId.isNotBlank()) { "Backend session registration failed" }
        sessionStore.updateSessionToken(remoteSessionId)
        startHeartbeat()
        return remoteSessionId
    }

    /** Resumes heartbeating for a session restored from the store. Reads nothing from the caller. */
    fun startListeningToSession() {
        resetSessionTermination()
        startHeartbeat()
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                delay(60_000)
                val session = sessionStore.activeSession.first() ?: return@launch
                val accessToken = session.accessToken
                val sessionId = session.sessionToken
                if (accessToken.isNullOrBlank() || sessionId.isNullOrBlank()) {
                    continue
                }
                val result = runCatching { backendApi.heartbeat(accessToken, sessionId) }
                result.onFailure { error ->
                    if (error is BackendApiException && error.statusCode == 401) {
                        if (error.code == "SESSION_REVOKED" || error.message.contains("signed out", ignoreCase = true) || error.message.contains("another device", ignoreCase = true)) {
                            notifySessionRevoked("Your account was logged in on another device. Active session ended.")
                        } else {
                            val recovered = runCatching { backendApi.autoRecoverSession(forceRefresh = true) }.getOrNull()
                            if (recovered == null) {
                                notifySessionRevoked("Your session has expired. Please sign in again.")
                            }
                        }
                    }
                }
            }
        }
    }

    /** Revokes this device's backend session. Must run before the local session store is cleared. */
    suspend fun clearSession() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        val active = sessionStore.activeSession.first() ?: return
        val access = active.accessToken ?: return
        val sessionId = active.sessionToken ?: return
        runCatching { backendApi.revokeCurrentSession(access, sessionId) }
    }

    fun resetSessionTermination() { _isSessionTerminated.value = false; _terminationReason.value = null }

    /** Registers a device session for the just-authenticated master token (see [MasterAuthSession]). */
    suspend fun registerMasterSession(): Boolean {
        resetMasterTermination()
        val accessToken = MasterAuthSession.accessToken ?: return false
        val response = runCatching { backendApi.registerSession(accessToken, appPreferences.getOrCreateInstallationDeviceId(), "Master Control") }
            .getOrElse { return false }
        val sessionId = response.optJSONObject("session")?.optString("sessionId").orEmpty()
        if (sessionId.isBlank()) return false
        MasterAuthSession.saveSession(sessionId)
        startMasterHeartbeat()
        return true
    }

    private fun startMasterHeartbeat() {
        masterHeartbeatJob?.cancel()
        masterHeartbeatJob = scope.launch {
            while (true) {
                delay(60_000)
                val accessToken = MasterAuthSession.accessToken ?: return@launch
                val sessionId = MasterAuthSession.sessionId ?: return@launch
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
