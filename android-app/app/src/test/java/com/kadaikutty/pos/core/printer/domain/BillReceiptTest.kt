package com.kadaikutty.pos.core.printer.domain

import com.kadaikutty.pos.core.printer.data.PrinterManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillReceiptTest {

    private fun document(
        shopAddress: String = "12 Main Road",
        discount: String = "₹0.00",
        items: List<BillItem> = listOf(BillItem("Rice 1kg", "2", "₹50.00", "₹100.00"))
    ) = BillReceipt.document(
        shopName = "Kadaikutty Stores",
        shopAddress = shopAddress,
        billNumber = "B-1001",
        date = "21/09/26 10:30",
        customerName = "Walk-in Customer",
        items = items,
        subtotal = "₹100.00",
        discount = discount,
        grandTotal = "₹100.00",
        paperWidth = 32
    )

    @Test
    fun `a zero discount is left off the bill whatever currency symbol it carries`() {
        val totals = document(discount = "₹0.00").totals.map { it.first }
        assertEquals(listOf("Subtotal", "TOTAL"), totals)
        assertEquals(listOf("Subtotal", "TOTAL"), document(discount = "0.00").totals.map { it.first })
        assertEquals(listOf("Subtotal", "TOTAL"), document(discount = "").totals.map { it.first })
    }

    @Test
    fun `a real discount is printed between the subtotal and the total`() {
        val totals = document(discount = "₹10.00").totals
        assertEquals(listOf("Subtotal", "Discount", "TOTAL"), totals.map { it.first })
        assertEquals("₹10.00", totals[1].second)
    }

    @Test
    fun `a blank shop address does not print an empty header line`() {
        assertTrue(document(shopAddress = "").headers.none { it.isBlank() })
        assertEquals(3, document(shopAddress = "").headers.size)
        assertEquals(4, document(shopAddress = "12 Main Road").headers.size)
    }

    @Test
    fun `item quantity text survives as typed so fractional weights are not rounded`() {
        val line = document(items = listOf(BillItem("Tomato", "1.250", "₹40.00", "₹50.00"))).lines.single()
        assertEquals("1.250", line.quantityText)
        assertEquals("Tomato", line.name)
        assertEquals("₹50.00", line.total)
    }

    @Test
    fun `the header carries the bill number, date and customer`() {
        val headers = document().headers
        assertTrue(headers.contains("Bill No: B-1001"))
        assertTrue(headers.contains("Date: 21/09/26 10:30"))
        assertTrue(headers.contains("Customer: Walk-in Customer"))
        assertFalse(headers.contains("Kadaikutty Stores"))
        assertEquals("Kadaikutty Stores", document().title)
    }

    @Test
    fun `an unknown stored printer type falls back to Bluetooth`() {
        assertEquals(PrinterManager.PrinterType.Usb, PrinterManager.PrinterType.fromSetting("Usb"))
        assertEquals(PrinterManager.PrinterType.Network, PrinterManager.PrinterType.fromSetting("Network"))
        assertEquals(PrinterManager.PrinterType.Bluetooth, PrinterManager.PrinterType.fromSetting("Bluetooth"))
        assertEquals(PrinterManager.PrinterType.Bluetooth, PrinterManager.PrinterType.fromSetting(null))
        assertEquals(PrinterManager.PrinterType.Bluetooth, PrinterManager.PrinterType.fromSetting("Serial"))
    }

    @Test
    fun `phone, GSTIN and payment mode are printed when the shop has them`() {
        val doc = BillReceipt.document(
            shopName = "Kadaikutty Stores", shopAddress = "", billNumber = "B-1", date = "d", customerName = "c",
            items = emptyList(), subtotal = "₹1.00", discount = "", grandTotal = "₹1.00", paperWidth = 32,
            shopPhone = "9876543210", gstNumber = "33ABCDE1234F1Z5", paymentMode = "UPI"
        )
        assertTrue(doc.headers.contains("Ph: 9876543210"))
        assertTrue(doc.headers.contains("GSTIN: 33ABCDE1234F1Z5"))
        assertEquals("Paid by" to "UPI", doc.totals.last())
        // Left off entirely when blank.
        assertFalse(document().headers.any { it.startsWith("Ph:") || it.startsWith("GSTIN:") })
    }
}
