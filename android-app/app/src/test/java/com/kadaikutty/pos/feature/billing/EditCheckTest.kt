package com.kadaikutty.pos.feature.billing

import com.kadaikutty.pos.core.common.Money
import com.kadaikutty.pos.core.sync.SyncStatus
import com.kadaikutty.pos.feature.billing.data.SaleEntity
import com.kadaikutty.pos.feature.billing.data.SaleItemEntity
import com.kadaikutty.pos.feature.billing.domain.SaleDraft
import com.kadaikutty.pos.feature.billing.domain.SaleLine
import com.kadaikutty.pos.feature.billing.presentation.BillEditCheck
import com.kadaikutty.pos.feature.purchase.data.PurchaseEntity
import com.kadaikutty.pos.feature.purchase.data.PurchaseItemEntity
import com.kadaikutty.pos.feature.purchase.domain.PurchaseDraft
import com.kadaikutty.pos.feature.purchase.domain.PurchaseLine
import com.kadaikutty.pos.feature.purchase.presentation.PurchaseEditCheck
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Editing a bill or purchase and saving it untouched must be recognised, so the app can say "No changes made". */
class EditCheckTest {
    private val sale = SaleEntity(id = "s1", companyId = "c", billNumber = "B1", totalMinorUnits = 2500, createdAtEpochMs = 0, syncStatus = SyncStatus.LOCAL_ONLY, customerId = "cust", paymentMode = "CASH")
    private val items = listOf(
        SaleItemEntity("c", "s1", "p1", 2, 1000, 2000),
        SaleItemEntity("c", "s1", "p2", 1, 500, 500),
    )
    private fun line(id: String, qty: Long, price: Long) = SaleLine(productId = id, productName = id, quantity = qty, unitPrice = Money(price), unitType = "PIECE")
    private fun draft(vararg lines: SaleLine, customer: String? = "cust", mode: String = "CASH", discount: Long = 0, due: Long = 0) =
        SaleDraft(lines = lines.toList(), customerId = customer, paymentMode = mode, globalDiscount = Money(discount), previousDue = due)

    @Test fun anUntouchedBillIsUnchanged() = assertTrue(BillEditCheck.unchanged(sale, items, draft(line("p1", 2, 1000), line("p2", 1, 500))))
    @Test fun lineOrderDoesNotMatter() = assertTrue(BillEditCheck.unchanged(sale, items, draft(line("p2", 1, 500), line("p1", 2, 1000))))
    @Test fun aChangedQuantityIsAChange() = assertFalse(BillEditCheck.unchanged(sale, items, draft(line("p1", 3, 1000), line("p2", 1, 500))))
    @Test fun aChangedPriceIsAChange() = assertFalse(BillEditCheck.unchanged(sale, items, draft(line("p1", 2, 900), line("p2", 1, 500))))
    @Test fun anAddedOrRemovedLineIsAChange() {
        assertFalse(BillEditCheck.unchanged(sale, items, draft(line("p1", 2, 1000))))
        assertFalse(BillEditCheck.unchanged(sale, items, draft(line("p1", 2, 1000), line("p2", 1, 500), line("p3", 1, 100))))
    }
    @Test fun aDifferentProductWithTheSameNumbersIsAChange() = assertFalse(BillEditCheck.unchanged(sale, items, draft(line("p1", 2, 1000), line("p9", 1, 500))))
    @Test fun aNewCustomerIsAChange() = assertFalse(BillEditCheck.unchanged(sale, items, draft(line("p1", 2, 1000), line("p2", 1, 500), customer = "other")))
    @Test fun aNewPaymentModeIsAChange() = assertFalse(BillEditCheck.unchanged(sale, items, draft(line("p1", 2, 1000), line("p2", 1, 500), mode = "UPI")))
    @Test fun aDiscountIsAChange() = assertFalse(BillEditCheck.unchanged(sale, items, draft(line("p1", 2, 1000), line("p2", 1, 500), discount = 100)))
    @Test fun aSettledPreviousBalanceIsAChange() = assertFalse(BillEditCheck.unchanged(sale, items, draft(line("p1", 2, 1000), line("p2", 1, 500), due = 300)))

    private val purchase = PurchaseEntity(id = "pu1", companyId = "c", supplierId = "sup", totalMinorUnits = 3000, createdAtEpochMs = 0, syncStatus = SyncStatus.LOCAL_ONLY, invoiceNumber = "INV-1", notes = null, paymentMode = "CASH")
    private val purchaseItems = listOf(PurchaseItemEntity("c", "pu1", "p1", 3, 1000, 3000))
    private fun purchaseDraft(qty: Long = 3, price: Long = 1000, invoice: String? = "INV-1", notes: String? = null, supplier: String = "sup") =
        PurchaseDraft(supplierId = supplier, lines = listOf(PurchaseLine("p1", qty, Money(price))), invoiceNumber = invoice, notes = notes, paymentMode = "CASH")

    @Test fun anUntouchedPurchaseIsUnchanged() = assertTrue(PurchaseEditCheck.unchanged(purchase, purchaseItems, purchaseDraft()))
    @Test fun emptyAndMissingNotesAreTheSame() = assertTrue(PurchaseEditCheck.unchanged(purchase, purchaseItems, purchaseDraft(notes = "  ")))
    @Test fun purchaseChangesAreSeen() {
        assertFalse(PurchaseEditCheck.unchanged(purchase, purchaseItems, purchaseDraft(qty = 4)))
        assertFalse(PurchaseEditCheck.unchanged(purchase, purchaseItems, purchaseDraft(price = 1100)))
        assertFalse(PurchaseEditCheck.unchanged(purchase, purchaseItems, purchaseDraft(invoice = "INV-2")))
        assertFalse(PurchaseEditCheck.unchanged(purchase, purchaseItems, purchaseDraft(notes = "late delivery")))
        assertFalse(PurchaseEditCheck.unchanged(purchase, purchaseItems, purchaseDraft(supplier = "other")))
    }
}
