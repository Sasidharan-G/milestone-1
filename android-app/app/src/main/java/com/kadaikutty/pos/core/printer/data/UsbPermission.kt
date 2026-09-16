package com.kadaikutty.pos.core.printer.data

import android.app.PendingIntent
import android.content.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

internal suspend fun requestUsbPermission(context: Context, manager: UsbManager, device: UsbDevice): Boolean {
    if (manager.hasPermission(device)) return true
    val action = "${context.packageName}.USB_PERMISSION.${UUID.randomUUID()}"
    val result = CompletableDeferred<Boolean>()
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == action) result.complete(manager.hasPermission(device))
        }
    }
    if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
    else {
        @Suppress("DEPRECATION")
        context.registerReceiver(receiver, IntentFilter(action))
    }
    val pending = PendingIntent.getBroadcast(context, 0, Intent(action).setPackage(context.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    return try {
        manager.requestPermission(device, pending)
        withTimeoutOrNull(30000) { result.await() } ?: false
    } finally {
        context.unregisterReceiver(receiver)
        pending.cancel()
    }
}
