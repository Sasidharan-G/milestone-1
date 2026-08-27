package com.kadaikutty.pos.core.printer

import com.kadaikutty.pos.core.printer.data.PrinterManager
import com.kadaikutty.pos.core.printer.domain.PrintDocument
import com.kadaikutty.pos.core.printer.domain.PrinterDriver
import com.kadaikutty.pos.core.printer.domain.PrinterError
import com.kadaikutty.pos.core.printer.domain.PrinterResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterManagerTest {
    @Test
    fun `manager returns connection error if connect or print called without active driver selection`() = runBlocking {
        val fakeBt = object : PrinterDriver {
            override suspend fun connect(deviceId: String): PrinterResult = PrinterResult.Success
            override suspend fun print(document: PrintDocument): PrinterResult = PrinterResult.Success
            override suspend fun disconnect() {}
        }
        val fakeUsb = object : PrinterDriver {
            override suspend fun connect(deviceId: String): PrinterResult = PrinterResult.Success
            override suspend fun print(document: PrintDocument): PrinterResult = PrinterResult.Success
            override suspend fun disconnect() {}
        }

        val manager = PrinterManager(fakeBt, fakeUsb)
        val result = manager.connect("dev-123")
        assertTrue(result is PrinterResult.Failure)
        assertEquals("No active printer driver selected", (result as PrinterResult.Failure).error.message)
    }

    @Test
    fun `manager routes connect and print correctly to selected driver`() = runBlocking {
        var btConnected = false
        var btPrinted = false
        var btDisconnected = false

        val fakeBt = object : PrinterDriver {
            override suspend fun connect(deviceId: String): PrinterResult {
                btConnected = true
                return PrinterResult.Success
            }
            override suspend fun print(document: PrintDocument): PrinterResult {
                btPrinted = true
                return PrinterResult.Success
            }
            override suspend fun disconnect() {
                btDisconnected = true
            }
        }
        val fakeUsb = object : PrinterDriver {
            override suspend fun connect(deviceId: String): PrinterResult = PrinterResult.Success
            override suspend fun print(document: PrintDocument): PrinterResult = PrinterResult.Success
            override suspend fun disconnect() {}
        }

        val manager = PrinterManager(fakeBt, fakeUsb)
        manager.selectDriver(PrinterManager.PrinterType.Bluetooth)
        assertEquals(PrinterManager.PrinterType.Bluetooth, manager.getActiveType())

        val connRes = manager.connect("bt-mac-addr")
        assertTrue(connRes is PrinterResult.Success)
        assertTrue(btConnected)

        val doc = PrintDocument("Test", emptyList(), emptyList(), emptyList(), "")
        val printRes = manager.print(doc)
        assertTrue(printRes is PrinterResult.Success)
        assertTrue(btPrinted)

        manager.disconnect()
        assertTrue(btDisconnected)
    }
}
