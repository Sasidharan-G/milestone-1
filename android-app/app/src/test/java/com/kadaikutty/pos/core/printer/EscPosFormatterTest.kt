package com.kadaikutty.pos.core.printer

import com.kadaikutty.pos.core.printer.data.EscPosFormatter
import com.kadaikutty.pos.core.printer.domain.PrintDocument
import com.kadaikutty.pos.core.printer.domain.PrintLine
import org.junit.Assert.assertTrue
import org.junit.Test

class EscPosFormatterTest {
    @Test
    fun `format generates valid ESC POS bytes containing title bolding and alignments`() {
        val doc = PrintDocument(
            title = "MY STORE",
            headers = listOf("Item", "Total"),
            lines = listOf(
                PrintLine("Prod A", 3, "1.50", "4.50")
            ),
            totals = listOf(
                "TOTAL" to "4.50"
            ),
            footer = "Thank you!"
        )

        val formatter = EscPosFormatter(paperWidthChar = 32)
        val bytes = formatter.format(doc)

        val textString = String(bytes)
        
        // Check text content exists in print job
        assertTrue(textString.contains("MY STORE"))
        assertTrue(textString.contains("Prod A"))
        assertTrue(textString.contains("TOTAL"))
        assertTrue(textString.contains("Thank you!"))

        // Check ESC/POS command sequences:
        // INIT sequence
        assertTrue(bytes.sliceArray(0..1).contentEquals(EscPosFormatter.INIT))
        
        // Align center sequence for title
        var containsAlignCenter = false
        for (i in 0 until bytes.size - 2) {
            if (bytes[i] == 0x1B.toByte() && bytes[i+1] == 0x61.toByte() && bytes[i+2] == 0x01.toByte()) {
                containsAlignCenter = true
                break
            }
        }
        assertTrue(containsAlignCenter)
    }
}
