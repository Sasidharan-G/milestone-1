package com.kadaikutty.pos.feature.billing.presentation

import com.kadaikutty.pos.feature.billing.data.SaleEntity
import com.kadaikutty.pos.feature.billing.data.SaleItemEntity
import com.kadaikutty.pos.feature.billing.domain.SaleDraft

/**
 * Whether an edited bill is still exactly the bill that was saved. Saving it again would only rewrite
 * the record and sync it for nothing, so the app says "No changes made" instead.
 *
 * Compared: the customer, every line's product, quantity and price, the total, the payment mode and, for
 * a mixed payment, the amounts paid each way. A settled previous balance always counts as a change.
 */
object BillEditCheck {
    private val SIMPLE_MODES = setOf("CASH", "UPI", "GPAY", "CREDIT")

    fun unchanged(saved: SaleEntity, savedItems: List<SaleItemEntity>, draft: SaleDraft): Boolean {
        if (draft.previousDue != 0L) return false
        if (draft.customerId != saved.customerId) return false
        if (draft.paymentMode != saved.paymentMode) return false
        if (draft.paymentMode !in SIMPLE_MODES &&
            (draft.paidCash.minorUnits != saved.paidCashMinorUnits || draft.paidUpi.minorUnits != saved.paidUpiMinorUnits ||
                draft.creditApplied.minorUnits != saved.creditAppliedMinorUnits)
        ) return false
        if (draft.total.minorUnits != saved.totalMinorUnits) return false
        if (draft.lines.size != savedItems.size) return false
        val before = savedItems.associate { it.productId to (it.quantity to it.unitPriceMinorUnits) }
        return draft.lines.all { before[it.productId] == (it.quantity to it.unitPrice.minorUnits) }
    }
}
