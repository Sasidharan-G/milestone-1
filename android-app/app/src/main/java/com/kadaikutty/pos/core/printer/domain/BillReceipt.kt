package com.kadaikutty.pos.core.printer.domain

/** One priced line of a bill, already formatted for the paper. */
data class BillItem(
    val name: String,
    val quantityText: String,
    val price: String,
    val total: String
)

/**
 * Builds the bill's [PrintDocument]. Kept free of Android and of any driver so the layout of a
 * receipt can be asserted in a unit test; printing it is PrinterManager's job.
 */
object BillReceipt {

    fun document(
        shopName: String,
        shopAddress: String,
        billNumber: String,
        date: String,
        customerName: String,
        items: List<BillItem>,
        subtotal: String,
        discount: String,
        grandTotal: String,
        paperWidth: Int,
        shopPhone: String = "",
        gstNumber: String = "",
        paymentMode: String = "",
        cancelled: Boolean = false,
        /** Rate-wise GST inside the total, e.g. "GST 5% on ₹100.00" to "₹4.76". */
        gstRows: List<Pair<String, String>> = emptyList(),
        /** Picture file of the shop logo to print above the header; empty prints none. */
        logoPath: String = ""
    ): PrintDocument = PrintDocument(
        title = shopName,
        headers = listOfNotNull(
            if (cancelled) "*** CANCELLED BILL ***" else null,
            shopAddress.takeIf { it.isNotBlank() },
            shopPhone.takeIf { it.isNotBlank() }?.let { "Ph: $it" },
            gstNumber.takeIf { it.isNotBlank() }?.let { "GSTIN: $it" },
            "Bill No: $billNumber",
            "Date: $date",
            "Customer: $customerName"
        ),
        lines = items.map { PrintLine(it.name, 0, it.price, it.total, it.quantityText) },
        totals = buildList {
            add("Subtotal" to subtotal)
            // A zero discount is a line that tells the customer nothing, so it is left off.
            if (discount.isNotBlank() && !isZeroAmount(discount)) add("Discount" to discount)
            add("TOTAL" to grandTotal)
            addAll(gstRows)
            if (paymentMode.isNotBlank()) add("Paid by" to paymentMode)
        },
        footer = "Thank you for shopping!",
        paperWidth = paperWidth,
        logoPath = logoPath
    )

    /** True for "0.00", "0", "₹0.00" and the like - whatever Money renders a zero as. */
    private fun isZeroAmount(value: String): Boolean =
        value.none { it in '1'..'9' }
}
