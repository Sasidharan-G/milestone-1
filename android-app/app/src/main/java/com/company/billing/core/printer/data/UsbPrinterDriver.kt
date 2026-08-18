package com.company.billing.core.printer.data

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.company.billing.core.printer.domain.PrintDocument
import com.company.billing.core.printer.domain.PrinterDriver
import com.company.billing.core.printer.domain.PrinterError
import com.company.billing.core.printer.domain.PrinterResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class UsbPrinterDriver(private val context: Context) : PrinterDriver {

    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var endpointOut: UsbEndpoint? = null
    private val escPosFormatter = EscPosFormatter()

    override suspend fun connect(deviceId: String): PrinterResult = withContext(Dispatchers.IO) {
        try {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
                ?: return@withContext PrinterResult.Failure(PrinterError.DeviceNotFound("USB manager not found"))

            val device = usbManager.deviceList[deviceId]
                ?: return@withContext PrinterResult.Failure(PrinterError.DeviceNotFound("USB device $deviceId not found"))

            if (!usbManager.hasPermission(device)) {
                return@withContext PrinterResult.Failure(PrinterError.ConnectionFailed("USB permission not granted for device: $deviceId"))
            }

            disconnect() // Ensure old connection is closed

            // Find bulk transfer output interface and endpoint
            var outEndpoint: UsbEndpoint? = null
            var targetInterface: UsbInterface? = null

            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                for (j in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(j)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_OUT) {
                        outEndpoint = ep
                        targetInterface = iface
                        break
                    }
                }
                if (outEndpoint != null) break
            }

            if (targetInterface == null || outEndpoint == null) {
                return@withContext PrinterResult.Failure(PrinterError.ConnectionFailed("USB bulk OUT endpoint not found"))
            }

            val conn = usbManager.openDevice(device)
                ?: return@withContext PrinterResult.Failure(PrinterError.ConnectionFailed("Failed to open USB device connection"))

            if (!conn.claimInterface(targetInterface, true)) {
                conn.close()
                return@withContext PrinterResult.Failure(PrinterError.ConnectionFailed("Failed to claim USB interface"))
            }

            connection = conn
            usbInterface = targetInterface
            endpointOut = outEndpoint
            PrinterResult.Success
        } catch (e: Exception) {
            PrinterResult.Failure(PrinterError.ConnectionFailed("USB connection failed: ${e.message}"))
        }
    }

    override suspend fun print(document: PrintDocument): PrinterResult = withContext(Dispatchers.IO) {
        val conn = connection
        val endpoint = endpointOut
        if (conn == null || endpoint == null) {
            return@withContext PrinterResult.Failure(PrinterError.ConnectionFailed("USB printer not connected"))
        }

        try {
            val formattedBytes = escPosFormatter.format(document)
            val result = conn.bulkTransfer(endpoint, formattedBytes, formattedBytes.size, 5000)
            if (result >= 0) {
                PrinterResult.Success
            } else {
                PrinterResult.Failure(PrinterError.WriteFailed("USB bulk transfer failed with status code: $result"))
            }
        } catch (e: Exception) {
            PrinterResult.Failure(PrinterError.WriteFailed("USB print writing failed: ${e.message}"))
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                val conn = connection
                val iface = usbInterface
                if (conn != null && iface != null) {
                    conn.releaseInterface(iface)
                }
                conn?.close()
            } catch (ignored: Exception) {
            } finally {
                connection = null
                usbInterface = null
                endpointOut = null
            }
        }
    }
}
