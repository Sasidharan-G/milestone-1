package com.kadaikutty.pos.core.network

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketManager @Inject constructor() {

    private var socket: Socket? = null
    private val _dataChangedFlow = MutableSharedFlow<Unit>()
    val dataChangedFlow = _dataChangedFlow.asSharedFlow()

    fun connect(companyId: String) {
        if (socket?.connected() == true) return

        try {
            // Using 10.0.2.2 for Android Emulator connecting to local server
            val options = IO.Options()
            options.forceNew = true
            socket = IO.socket("http://10.0.2.2:3000", options)
            
            socket?.on(Socket.EVENT_CONNECT) {
                Log.d("WebSocketManager", "Connected to server")
                socket?.emit("join_company", companyId)
            }

            socket?.on("data_changed") {
                Log.d("WebSocketManager", "Received data_changed event")
                CoroutineScope(Dispatchers.IO).launch {
                    _dataChangedFlow.emit(Unit)
                }
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d("WebSocketManager", "Disconnected from server")
            }

            socket?.connect()
        } catch (e: Exception) {
            Log.e("WebSocketManager", "Error connecting to socket: ${e.message}")
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }
}
