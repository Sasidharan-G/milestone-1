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
        paperWidth: Int
    ): PrintDocument = PrintDocument(
        title = shopName,
        headers = listOfNotNull(
            shopAddress.takeIf { it.isNotBlank() },
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
        },
        footer = "Thank you for shopping!",
        paperWidth = paperWidth
    )

    /** True for "0.00", "0", "₹0.00" and the like - whatever Money renders a zero as. */
    private fun isZeroAmount(value: String): Boolean =
        value.none { it in '1'..'9' }
}
