package com.kadaikutty.pos.core.network

import android.util.Log
import com.kadaikutty.pos.BuildConfig
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Realtime "something changed for your company" channel. The server authenticates the socket
 * with the same bearer token as the REST API and only lets it join its own tenant room, so a
 * connection is only attempted when an online session with an access token exists.
 */
@Singleton
class WebSocketManager @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: Socket? = null
    private var connectedCompanyId: String? = null
    private val _dataChangedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val dataChangedFlow = _dataChangedFlow.asSharedFlow()

    private val _sessionRevokedFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val sessionRevokedFlow = _sessionRevokedFlow.asSharedFlow()

    // Master Control connects a second, independent socket (its own auth context — a different
    // userId/companyId than any tenant session that may also be live in this process) so the two
    // connections never clobber each other by fighting over a single `socket` field.
    private var masterSocket: Socket? = null
    private val _masterOverviewChangedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val masterOverviewChangedFlow = _masterOverviewChangedFlow.asSharedFlow()

    @Synchronized
    fun connect(companyId: String, accessToken: String, sessionId: String? = null) {
        if (socket?.connected() == true && connectedCompanyId == companyId) return
        if (sessionId.isNullOrBlank()) {
            Log.w(TAG, "Tenant socket not opened: active device session is required")
            return
        }
        disconnect()
        try {
            val options = IO.Options().apply {
                forceNew = true
                reconnection = true
                auth = mapOf("token" to accessToken, "sessionId" to sessionId)
            }
            val newSocket = IO.socket(BuildConfig.BACKEND_BASE_URL.trimEnd('/'), options)
            newSocket.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Connected; joining company room")
                newSocket.emit("join_company", companyId)
            }
            newSocket.on("data_changed") {
                scope.launch { _dataChangedFlow.emit(Unit) }
            }
            newSocket.on("license_changed") {
                scope.launch { _dataChangedFlow.emit(Unit) }
            }
            newSocket.on("account_changed") {
                scope.launch { _dataChangedFlow.emit(Unit) }
            }
            newSocket.on("shop_profile_changed") {
                scope.launch { _dataChangedFlow.emit(Unit) }
            }
            newSocket.on("staff_changed") {
                scope.launch { _dataChangedFlow.emit(Unit) }
            }
            newSocket.on("session_revoked") { args ->
                val payload = args.firstOrNull() as? org.json.JSONObject
                val reason = payload?.optString("reason").orEmpty()
                val deviceName = payload?.optString("deviceName")
                val msg = if (reason == "SIGNED_IN_ELSEWHERE") {
                    "Your account was signed in on another device${if (!deviceName.isNullOrBlank()) " ($deviceName)" else ""}. Active session ended."
                } else "Your session has been signed out."
                Log.w(TAG, "session_revoked event received: $msg")
                scope.launch { _sessionRevokedFlow.emit(msg) }
            }
            newSocket.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.w(TAG, "Connect error: ${args.firstOrNull()}")
            }
            newSocket.on(Socket.EVENT_DISCONNECT) { Log.d(TAG, "Disconnected") }
            newSocket.connect()
            socket = newSocket
            connectedCompanyId = companyId
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to socket: ${e.message}")
        }
    }

    @Synchronized
    fun disconnect() {
        socket?.off()
        socket?.disconnect()
        socket = null
        connectedCompanyId = null
    }

    /**
     * Master Control's realtime channel. The server auto-joins every authenticated socket to a
     * `user:<userId>` room (see server/src/index.ts), so master_overview_changed reaches this
     * connection with no explicit room-join call needed — connecting with the master token is enough.
     */
    @Synchronized
    fun connectMaster(accessToken: String, sessionId: String?) {
        if (masterSocket?.connected() == true) return
        if (sessionId.isNullOrBlank()) {
            Log.w(TAG, "Master socket not opened: active device session is required")
            return
        }
        disconnectMaster()
        try {
            val options = IO.Options().apply {
                forceNew = true
                reconnection = true
                auth = mapOf("token" to accessToken, "sessionId" to sessionId)
            }
            val newSocket = IO.socket(BuildConfig.BACKEND_BASE_URL.trimEnd('/'), options)
            newSocket.on(Socket.EVENT_CONNECT) { Log.d(TAG, "Master socket connected") }
            newSocket.on("master_overview_changed") {
                scope.launch { _masterOverviewChangedFlow.emit(Unit) }
            }
            newSocket.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.w(TAG, "Master socket connect error: ${args.firstOrNull()}")
            }
            newSocket.connect()
            masterSocket = newSocket
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting master socket: ${e.message}")
        }
    }

    @Synchronized
    fun disconnectMaster() {
        masterSocket?.off()
        masterSocket?.disconnect()
        masterSocket = null
    }

    private companion object { const val TAG = "WebSocketManager" }
}
