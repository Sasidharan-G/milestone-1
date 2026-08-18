package com.company.billing.core.printer.data

import com.company.billing.core.printer.domain.PrintDocument
import com.company.billing.core.printer.domain.PrinterDriver
import com.company.billing.core.printer.domain.PrinterError
import com.company.billing.core.printer.domain.PrinterResult

class PrinterManager(
    private val bluetoothDriver: PrinterDriver,
    private val usbDriver: PrinterDriver
) {
    enum class PrinterType { Bluetooth, Usb }

    private var activeDriver: PrinterDriver? = null
    private var activeType: PrinterType? = null

    fun selectDriver(type: PrinterType) {
        activeType = type
        activeDriver = when (type) {
            PrinterType.Bluetooth -> bluetoothDriver
            PrinterType.Usb -> usbDriver
        }
    }

    fun getActiveType(): PrinterType? = activeType

    suspend fun connect(deviceId: String): PrinterResult {
        val driver = activeDriver
            ?: return PrinterResult.Failure(PrinterError.DeviceNotFound("No active printer driver selected"))
        return driver.connect(deviceId)
    }

    suspend fun print(document: PrintDocument): PrinterResult {
        val driver = activeDriver
            ?: return PrinterResult.Failure(PrinterError.DeviceNotFound("No active printer driver selected"))
        return driver.print(document)
    }

    suspend fun disconnect() {
        activeDriver?.disconnect()
    }
}
