package com.kadaikutty.pos.core.printer.data

import com.kadaikutty.pos.core.printer.domain.PrintDocument

/**
 * Turns a receipt into the bytes every printer is sent. The whole receipt is drawn as a picture
 * (see [ReceiptRaster]) so Tamil names, the rupee sign and the shop logo print the same on any
 * ESC/POS printer and never depend on its fonts or code pages.
 */
object ReceiptEncoder {
    fun encode(doc: PrintDocument): ByteArray {
        val content = listOf(doc.title, doc.footer) + doc.headers +
            doc.lines.flatMap { listOf(it.name, it.quantityText, it.price, it.total) } +
            doc.totals.flatMap { listOf(it.first, it.second) }
        require(content.sumOf { it.length.toLong() } <= 200_000) { "Receipt too large; split the print job" }
        return try {
            ReceiptRaster.encode(doc)
        } catch (e: Exception) {
            // A logo that fails to render must never stop a bill from printing: try again without it.
            if (doc.logoPath.isNotBlank()) ReceiptRaster.encode(doc.copy(logoPath = "")) else throw e
        } catch (e: OutOfMemoryError) {
            if (doc.logoPath.isNotBlank()) ReceiptRaster.encode(doc.copy(logoPath = "")) else throw e
        }
    }
}
