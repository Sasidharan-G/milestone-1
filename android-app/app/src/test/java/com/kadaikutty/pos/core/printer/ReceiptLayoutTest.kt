package com.kadaikutty.pos.core.printer

import com.kadaikutty.pos.core.printer.data.ReceiptLayout
import com.kadaikutty.pos.core.printer.data.EscPosFormatter
import com.kadaikutty.pos.core.printer.domain.PrintDocument
import com.kadaikutty.pos.core.printer.domain.PrintLine
import org.junit.Assert.*
import org.junit.Test

class ReceiptLayoutTest {
    @Test fun longNamesAndAmountsArePreservedAtEveryPaperWidth() {
        for (width in listOf(32, 48, 64)) {
            val name = "Premium extra long product description without losing any characters"
            val wrapped = ReceiptLayout.wrap(name, width)
            assertEquals(name, wrapped.joinToString(" "))
            assertTrue(wrapped.all { it.length <= width })
            val amount = "1234567890123456789012345678901234567890.99"
            val rows = ReceiptLayout.columns("Grand total including discounts", amount, width)
            assertTrue(rows.all { it.length <= width })
            assertTrue(rows.joinToString("").replace(" ", "").contains(amount))
        }
    }
    @Test fun controlCharactersCannotInjectPrinterCommands() {
        assertEquals(listOf("Store@"), ReceiptLayout.wrap("Store\u001b@", 32))
    }
    @Test fun fractionalQuantityAndSavedAmountArePrintedWithoutRecalculation() {
        val doc = PrintDocument("Shop", emptyList(), listOf(PrintLine("Rice", 0, "100.00", "25.00", "0.250")),
            listOf("TOTAL" to "25.00"), "Thanks", paperWidth = 48)
        val receipt = String(EscPosFormatter().format(doc), Charsets.UTF_8)
        assertTrue(receipt.contains("0.250 x 100.00"))
        assertTrue(receipt.contains("25.00"))
        assertTrue(receipt.contains("-".repeat(48)))
        assertFalse(receipt.contains(String(EscPosFormatter.FEED_AND_CUT)))
    }
}
