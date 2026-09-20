package com.kadaikutty.pos.core.printer.data

import com.kadaikutty.pos.core.printer.domain.PrintDocument
import java.io.ByteArrayOutputStream

class EscPosFormatter(private val paperWidthChar: Int = 32) {

    companion object {
        val INIT = byteArrayOf(0x1B, 0x40)
        val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
        val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
        val ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
        val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
        val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
        val FEED_AND_CUT = byteArrayOf(0x1D, 0x56, 0x41, 0x00)
        val LINE_FEED = byteArrayOf(0x0A)
    }

    fun format(doc: PrintDocument): ByteArray {
        val stream = ByteArrayOutputStream()
        val width = doc.paperWidth.takeIf { it in 24..64 } ?: paperWidthChar
        fun text(value: String) {
            ReceiptLayout.wrap(value, width).forEach {
                stream.write(it.toByteArray(Charsets.UTF_8))
                stream.write(LINE_FEED)
            }
        }
        val divider = "-".repeat(width)

        // Initialize printer
        stream.write(INIT)

        // Title
        stream.write(ALIGN_CENTER)
        stream.write(BOLD_ON)
        text(doc.title)
        stream.write(BOLD_OFF)
        stream.write(LINE_FEED)

        // Divider
        stream.write(ALIGN_LEFT)
        stream.write(divider.toByteArray())
        stream.write(LINE_FEED)

        // Headers
        if (doc.headers.isNotEmpty()) {
            stream.write(BOLD_ON)
            doc.headers.forEach { text(it) }
            stream.write(BOLD_OFF)
            stream.write(divider.toByteArray())
            stream.write(LINE_FEED)
        }

        // Lines
        for (line in doc.lines) {
            // Row 1: Product Name (Bold)
            stream.write(BOLD_ON)
            text(line.name)
            stream.write(BOLD_OFF)

            // Row 2: "Qty x Price" on left, "Total" on right
            val leftText = "${line.quantityText} x ${line.price}"
            val rightText = line.total
            ReceiptLayout.columns(leftText, rightText, width).forEach { text(it) }
        }

        stream.write(divider.toByteArray())
        stream.write(LINE_FEED)

        // Totals
        for ((label, value) in doc.totals) {
            stream.write(BOLD_ON)
            ReceiptLayout.columns(label, value, width).forEach { text(it) }
            stream.write(BOLD_OFF)
        }

        stream.write(LINE_FEED)

        // Footer
        if (doc.footer.isNotBlank()) {
            stream.write(ALIGN_CENTER)
            text(doc.footer)
        }

        // Space and Cut
        stream.write(LINE_FEED)
        stream.write(LINE_FEED)
        if (doc.cutPaper) stream.write(FEED_AND_CUT)

        return stream.toByteArray()
    }
}
