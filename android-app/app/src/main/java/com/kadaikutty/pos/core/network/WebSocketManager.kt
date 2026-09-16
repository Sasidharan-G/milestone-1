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

    @Synchronized
    fun connect(companyId: String, accessToken: String, sessionId: String? = null) {
        if (socket?.connected() == true && connectedCompanyId == companyId) return
        disconnect()
        try {
            val options = IO.Options().apply {
                forceNew = true
                reconnection = true
                auth = if (sessionId.isNullOrBlank()) mapOf("token" to accessToken) else mapOf("token" to accessToken, "sessionId" to sessionId)
            }
            val newSocket = IO.socket(BuildConfig.BACKEND_BASE_URL.trimEnd('/'), options)
            newSocket.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Connected; joining company room")
                newSocket.emit("join_company", companyId)
            }
            newSocket.on("data_changed") {
                scope.launch { _dataChangedFlow.emit(Unit) }
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

    private companion object { const val TAG = "WebSocketManager" }
}
