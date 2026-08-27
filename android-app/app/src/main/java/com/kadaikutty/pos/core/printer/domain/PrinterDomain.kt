package com.kadaikutty.pos.core.printer.domain

sealed interface PrinterError {
    val message: String
    data class ConnectionFailed(override val message: String) : PrinterError
    data class WriteFailed(override val message: String) : PrinterError
    data class DeviceNotFound(override val message: String) : PrinterError
    data class InvalidDocument(override val message: String) : PrinterError
}

sealed interface PrinterResult {
    data object Success : PrinterResult
    data class Failure(val error: PrinterError) : PrinterResult
}

data class PrintLine(
    val name: String,
    val quantity: Long,
    val price: String,
    val total: String
)

data class PrintDocument(
    val title: String,
    val headers: List<String>,
    val lines: List<PrintLine>,
    val totals: List<Pair<String, String>>,
    val footer: String
)

interface PrinterDriver {
    suspend fun connect(deviceId: String): PrinterResult
    suspend fun print(document: PrintDocument): PrinterResult
    suspend fun disconnect()
}
