package com.kadaikutty.pos.core.hardware

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

object PrinterService {
    // Standard SPP UUID
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // ESC/POS Commands
    private val INIT = byteArrayOf(27, 64)
    private val ALIGN_CENTER = byteArrayOf(27, 97, 1)
    private val ALIGN_LEFT = byteArrayOf(27, 97, 0)
    private val BOLD_ON = byteArrayOf(27, 69, 1)
    private val BOLD_OFF = byteArrayOf(27, 69, 0)
    private val CUT = byteArrayOf(29, 86, 66, 0)

    suspend fun printReceipt(
        context: Context,
        macAddress: String,
        shopName: String,
        shopAddress: String,
        billNumber: String,
        date: String,
        customerName: String,
        items: List<PrintItem>,
        subtotal: String,
        discount: String,
        grandTotal: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return@withContext Result.failure(Exception("Bluetooth permission not granted. Please allow Nearby Devices permission in settings."))
            }
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter ?: return@withContext Result.failure(Exception("Bluetooth not supported on this device."))

        if (!adapter.isEnabled) {
            return@withContext Result.failure(Exception("Bluetooth is disabled. Please turn it on."))
        }

        var device: BluetoothDevice? = null
        try {
            device = adapter.getRemoteDevice(macAddress)
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("Invalid Printer MAC Address. Please check settings."))
        }

        try {
            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            val out = socket.outputStream

            // Initialize
            out.write(INIT)
            
            // Header
            out.write(ALIGN_CENTER)
            out.write(BOLD_ON)
            out.write(shopName.toByteArray())
            out.write("\n".toByteArray())
            out.write(BOLD_OFF)
            if (shopAddress.isNotBlank()) {
                out.write(shopAddress.toByteArray())
                out.write("\n".toByteArray())
            }
            out.write("\n".toByteArray())

            // Bill Info
            out.write(ALIGN_LEFT)
            out.write("Bill No : $billNumber\n".toByteArray())
            out.write("Date    : $date\n".toByteArray())
            out.write("Customer: $customerName\n".toByteArray())
            out.write("--------------------------------\n".toByteArray())
            
            // Items Header (32 chars)
            out.write("Item        Qty  Price   Total\n".toByteArray())
            out.write("--------------------------------\n".toByteArray())
            for (item in items) {
                val name = item.name.take(11).padEnd(11)
                val qty = item.qty.take(4).padEnd(4)
                val price = item.price.take(7).padEnd(7)
                val total = item.total.take(7).padStart(7)
                out.write("$name $qty $price $total\n".toByteArray())
            }
            out.write("--------------------------------\n".toByteArray())

            // Totals
            out.write(ALIGN_LEFT)
            out.write("Subtotal: ".padEnd(24).toByteArray())
            out.write(subtotal.padStart(8).toByteArray() + "\n".toByteArray())
            
            if (discount != "0.00" && discount.isNotBlank()) {
                out.write("Discount: ".padEnd(24).toByteArray())
                out.write(("-"+discount).padStart(8).toByteArray() + "\n".toByteArray())
            }

            out.write(BOLD_ON)
            out.write("Total   : ".padEnd(24).toByteArray())
            out.write(grandTotal.padStart(8).toByteArray() + "\n".toByteArray())
            out.write(BOLD_OFF)

            out.write("\n".toByteArray())
            out.write(ALIGN_CENTER)
            out.write("Thank you for shopping!\n".toByteArray())
            out.write("\n\n\n\n".toByteArray()) // Feed paper
            
            // Cut (supported on some printers)
            try { out.write(CUT) } catch (e: Exception) {}
            
            out.flush()
            socket.close()

            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(Exception("Failed to connect to printer. Is it turned on?"))
        } catch (e: Exception) {
            Result.failure(Exception("Printing error: ${e.message}"))
        }
    }
}

data class PrintItem(val name: String, val qty: String, val price: String, val total: String)
