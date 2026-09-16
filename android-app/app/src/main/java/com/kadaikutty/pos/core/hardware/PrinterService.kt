package com.kadaikutty.pos.core.hardware

import android.content.Context
import com.kadaikutty.pos.core.printer.data.BluetoothPrinterDriver
import com.kadaikutty.pos.core.printer.data.UsbPrinterDriver
import com.kadaikutty.pos.core.printer.data.NetworkPrinterDriver
import com.kadaikutty.pos.core.printer.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object PrinterService {
    private val jobs = com.kadaikutty.pos.core.printer.data.PrinterJobLock.mutex

    suspend fun printReceipt(
        context: Context, macAddress: String, shopName: String, shopAddress: String,
        billNumber: String, date: String, customerName: String, items: List<PrintItem>,
        subtotal: String, discount: String, grandTotal: String,
        printerType: String = "Bluetooth", paperWidth: Int = 32
    ): Result<Unit> = jobs.withLock {
        val driver: PrinterDriver = when (printerType) {
            "Bluetooth" -> BluetoothPrinterDriver(context)
            "Usb" -> UsbPrinterDriver(context)
            "Network" -> NetworkPrinterDriver()
            else -> return@withLock Result.failure(IllegalArgumentException("Unsupported printer connection"))
        }
        try {
            val connected = driver.connect(macAddress)
            if (connected is PrinterResult.Failure) {
                return@withLock Result.failure(Exception(connected.error.message))
            }
            val doc = PrintDocument(
                title = shopName,
                headers = listOfNotNull(shopAddress.takeIf { it.isNotBlank() },
                    "Bill No: $billNumber", "Date: $date", "Customer: $customerName"),
                lines = items.map { PrintLine(it.name, 0, it.price, it.total, it.qty) },
                totals = buildList {
                    add("Subtotal" to subtotal)
                    if (discount.isNotBlank() && discount != "0.00") add("Discount" to discount)
                    add("TOTAL" to grandTotal)
                },
                footer = "Thank you for shopping!", paperWidth = paperWidth
            )
            when (val result = driver.print(doc)) {
                is PrinterResult.Success -> Result.success(Unit)
                is PrinterResult.Failure -> Result.failure(Exception(result.error.message))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { driver.disconnect() }
        }
    }
}

data class PrintItem(val name: String, val qty: String, val price: String, val total: String)
