package com.kadaikutty.pos.core.printer.data

import com.kadaikutty.pos.core.printer.domain.PrintDocument
import com.kadaikutty.pos.core.printer.domain.PrinterDriver
import com.kadaikutty.pos.core.printer.domain.PrinterError
import com.kadaikutty.pos.core.printer.domain.PrinterResult
import kotlinx.coroutines.sync.withLock

class PrinterManager(
    private val bluetoothDriver: PrinterDriver,
    private val usbDriver: PrinterDriver
) {
    enum class PrinterType { Bluetooth, Usb, Network }
    private val networkDriver = NetworkPrinterDriver()

    private var activeDriver: PrinterDriver? = null
    private var activeType: PrinterType? = null

    suspend fun printJob(type: PrinterType, deviceId: String, document: PrintDocument): PrinterResult =
        PrinterJobLock.mutex.withLock {
            try {
                selectDriver(type)
                val result = connect(deviceId)
                if (result is PrinterResult.Failure) result else print(document)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                PrinterResult.Failure(PrinterError.WriteFailed(e.message ?: "Unable to print"))
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { disconnect() }
            }
        }

    fun selectDriver(type: PrinterType) {
        activeType = type
        activeDriver = when (type) {
            PrinterType.Bluetooth -> bluetoothDriver
            PrinterType.Usb -> usbDriver
            PrinterType.Network -> networkDriver
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
