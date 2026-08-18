package com.company.billing.core.printer.data

import com.company.billing.core.printer.domain.PrintDocument
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

        // Initialize printer
        stream.write(INIT)

        // Title
        stream.write(ALIGN_CENTER)
        stream.write(BOLD_ON)
        stream.write(doc.title.toByteArray())
        stream.write(LINE_FEED)
        stream.write(BOLD_OFF)
        stream.write(LINE_FEED)

        // Divider
        stream.write(ALIGN_LEFT)
        stream.write(getDividerLine().toByteArray())
        stream.write(LINE_FEED)

        // Headers
        if (doc.headers.isNotEmpty()) {
            stream.write(BOLD_ON)
            stream.write(doc.headers.joinToString("  ").toByteArray())
            stream.write(LINE_FEED)
            stream.write(BOLD_OFF)
            stream.write(getDividerLine().toByteArray())
            stream.write(LINE_FEED)
        }

        // Lines
        for (line in doc.lines) {
            // Row 1: Product Name (Bold)
            stream.write(BOLD_ON)
            stream.write(line.name.toByteArray())
            stream.write(LINE_FEED)
            stream.write(BOLD_OFF)

            // Row 2: "Qty x Price" on left, "Total" on right
            val leftText = "  ${line.quantity} x ${line.price}"
            val rightText = line.total
            val spacesCount = paperWidthChar - leftText.length - rightText.length
            val spaces = if (spacesCount > 0) " ".repeat(spacesCount) else " "
            stream.write((leftText + spaces + rightText).toByteArray())
            stream.write(LINE_FEED)
        }

        stream.write(getDividerLine().toByteArray())
        stream.write(LINE_FEED)

        // Totals
        for ((label, value) in doc.totals) {
            val spacesCount = paperWidthChar - label.length - value.length
            val spaces = if (spacesCount > 0) " ".repeat(spacesCount) else " "
            stream.write(BOLD_ON)
            stream.write((label + spaces + value).toByteArray())
            stream.write(LINE_FEED)
            stream.write(BOLD_OFF)
        }

        stream.write(LINE_FEED)

        // Footer
        if (doc.footer.isNotBlank()) {
            stream.write(ALIGN_CENTER)
            stream.write(doc.footer.toByteArray())
            stream.write(LINE_FEED)
        }

        // Space and Cut
        stream.write(LINE_FEED)
        stream.write(LINE_FEED)
        stream.write(FEED_AND_CUT)

        return stream.toByteArray()
    }

    private fun getDividerLine(): String {
        return "-".repeat(paperWidthChar)
    }
}
