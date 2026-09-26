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
    // True while a device session is live, so returning to the foreground knows to resume beating.
    @Volatile private var heartbeatWanted = false

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
        heartbeatWanted = false
        _terminationReason.value = reason
        _isSessionTerminated.value = true
        scope.launch {
            sessionStore.clear()
        }
    }

    /**
     * Registers this device for a sign-in that is still being completed and returns the device
     * session id. It takes the access token directly because the session is written to the store
     * only afterwards, in one piece (see DefaultAuthRepository.loginOnline): reading the token from
     * a half-written session let background calls register a second device session, which revoked
     * the first. Throws if the session cannot be established.
     */
    suspend fun registerSession(accessToken: String): String {
        resetSessionTermination()
        require(accessToken.isNotBlank()) { "Online session token is missing" }
        val response = backendApi.registerSession(accessToken, appPreferences.getOrCreateInstallationDeviceId(), appPreferences.getDeviceModelName())
        val remoteSessionId = response.optJSONObject("session")?.optString("sessionId").orEmpty()
        require(remoteSessionId.isNotBlank()) { "Backend session registration failed" }
        startHeartbeat()
        return remoteSessionId
    }

    /** Resumes heartbeating for a session restored from the store. Reads nothing from the caller. */
    fun startListeningToSession() {
        resetSessionTermination()
        startHeartbeat()
    }

    /**
     * Stops beating while the app is in the background (a beat is a request plus a database write, and
     * the session only needs to stay fresh while the shop is actually using the app) and beats at once
     * on return, which also notices a sign-out that happened meanwhile.
     */
    fun onAppBackgrounded() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    fun onAppForegrounded() {
        if (heartbeatWanted && heartbeatJob == null) startHeartbeat(immediate = true)
    }

    private fun startHeartbeat(immediate: Boolean = false) {
        heartbeatWanted = true
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            var first = immediate
            while (true) {
                if (!first) delay(60_000)
                first = false
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
        heartbeatWanted = false
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
