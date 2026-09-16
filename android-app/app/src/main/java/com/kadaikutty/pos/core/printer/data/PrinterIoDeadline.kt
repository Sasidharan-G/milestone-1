package com.kadaikutty.pos.core.printer.data

import java.io.Closeable
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Closing the socket interrupts blocking platform connect/write calls. */
internal object PrinterIoDeadline {
    private val timer = Executors.newSingleThreadScheduledExecutor { action ->
        Thread(action, "printer-timeout").apply { isDaemon = true }
    }
    fun <T> run(socket: Closeable, seconds: Long, action: () -> T): T {
        val expired = AtomicBoolean(false)
        val timeout = timer.schedule({
            expired.set(true)
            try { socket.close() } catch (_: IOException) { }
        }, seconds, TimeUnit.SECONDS)
        try {
            val result = action()
            if (expired.get()) throw IOException("Printer timed out; check paper and connection before retrying")
            return result
        } finally { timeout.cancel(false) }
    }
}
