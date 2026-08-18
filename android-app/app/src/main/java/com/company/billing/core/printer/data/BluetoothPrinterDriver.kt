package com.company.billing.core.printer.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.company.billing.core.printer.domain.PrintDocument
import com.company.billing.core.printer.domain.PrinterDriver
import com.company.billing.core.printer.domain.PrinterError
import com.company.billing.core.printer.domain.PrinterResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

class BluetoothPrinterDriver(private val context: Context) : PrinterDriver {

    companion object {
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    }

    private var socket: BluetoothSocket? = null
    private val escPosFormatter = EscPosFormatter()

    @SuppressLint("MissingPermission")
    override suspend fun connect(deviceId: String): PrinterResult = withContext(Dispatchers.IO) {
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter
                ?: return@withContext PrinterResult.Failure(PrinterError.DeviceNotFound("Bluetooth adapter not found"))

            if (!adapter.isEnabled) {
                return@withContext PrinterResult.Failure(PrinterError.ConnectionFailed("Bluetooth is disabled"))
            }

            val device = try {
                adapter.getRemoteDevice(deviceId)
            } catch (e: IllegalArgumentException) {
                return@withContext PrinterResult.Failure(PrinterError.DeviceNotFound("Invalid Bluetooth MAC address: $deviceId"))
            }

            disconnect() // Ensure old connection is closed

            val socketConnection = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socketConnection.connect()
            socket = socketConnection
            PrinterResult.Success
        } catch (se: SecurityException) {
            PrinterResult.Failure(PrinterError.ConnectionFailed("Bluetooth permission denied (BLUETOOTH_CONNECT)"))
        } catch (e: IOException) {
            PrinterResult.Failure(PrinterError.ConnectionFailed("Failed to connect: ${e.message}"))
        }
    }

    override suspend fun print(document: PrintDocument): PrinterResult = withContext(Dispatchers.IO) {
        val activeSocket = socket
            ?: return@withContext PrinterResult.Failure(PrinterError.ConnectionFailed("Printer not connected"))

        try {
            val formattedBytes = escPosFormatter.format(document)
            activeSocket.outputStream.write(formattedBytes)
            activeSocket.outputStream.flush()
            PrinterResult.Success
        } catch (e: IOException) {
            PrinterResult.Failure(PrinterError.WriteFailed("Failed to write print payload: ${e.message}"))
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                socket?.close()
            } catch (ignored: IOException) {
            } finally {
                socket = null
            }
        }
    }
}
