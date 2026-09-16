package com.kadaikutty.pos.core.printer.data

import com.kadaikutty.pos.core.printer.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/** Raw ESC/POS on a shop LAN. Address format: IP or hostname, optionally :port. */
class NetworkPrinterDriver : PrinterDriver {
    private var socket: Socket? = null
    override suspend fun connect(deviceId: String): PrinterResult = withContext(Dispatchers.IO) {
        disconnect()
        val parts = deviceId.trim().split(':')
        val port = if (parts.size == 2) parts[1].toIntOrNull() else 9100
        if (parts.size !in 1..2 || parts[0].isBlank() || port == null || port !in 1..65535) {
            return@withContext PrinterResult.Failure(PrinterError.ConnectionFailed("Enter a printer host and valid port (default 9100)"))
        }
        val candidate = Socket()
        try {
            candidate.connect(InetSocketAddress(parts[0], port), 5000)
            socket = candidate
            PrinterResult.Success
        } catch (e: Exception) {
            candidate.close()
            PrinterResult.Failure(PrinterError.ConnectionFailed("Cannot connect to network printer: ${e.message}"))
        }
    }
    override suspend fun print(document: PrintDocument): PrinterResult = withContext(Dispatchers.IO) {
        val connected = socket ?: return@withContext PrinterResult.Failure(PrinterError.ConnectionFailed("Printer not connected"))
        try {
            val bytes = ReceiptEncoder.encode(document)
            PrinterIoDeadline.run(connected, 60) {
                connected.getOutputStream().write(bytes)
                connected.getOutputStream().flush()
            }
            PrinterResult.Success
        } catch (e: Exception) {
            PrinterResult.Failure(PrinterError.WriteFailed("Print transfer interrupted; check the receipt before retrying"))
        }
    }
    override suspend fun disconnect(): Unit = withContext(Dispatchers.IO) {
        try { socket?.close() } finally { socket = null }
        Unit
    }
}
