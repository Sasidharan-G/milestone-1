package com.kadaikutty.pos.feature.purchase.presentation

import com.kadaikutty.pos.feature.purchase.data.PurchaseEntity
import com.kadaikutty.pos.feature.purchase.data.PurchaseItemEntity
import com.kadaikutty.pos.feature.purchase.domain.PurchaseDraft

/** Whether an edited purchase is still exactly what was saved (then the app says "No changes made" and saves nothing). */
object PurchaseEditCheck {
    private val SIMPLE_MODES = setOf("CASH", "UPI", "GPAY", "CREDIT")

    fun unchanged(saved: PurchaseEntity, savedItems: List<PurchaseItemEntity>, draft: PurchaseDraft): Boolean {
        if (draft.supplierId != saved.supplierId) return false
        if (draft.invoiceNumber.orEmpty().trim() != saved.invoiceNumber.orEmpty().trim()) return false
        if (draft.notes.orEmpty().trim() != saved.notes.orEmpty().trim()) return false
        if (draft.paymentMode != saved.paymentMode) return false
        if (draft.paymentMode !in SIMPLE_MODES &&
            (draft.paidCash.minorUnits != saved.paidCashMinorUnits || draft.paidUpi.minorUnits != saved.paidUpiMinorUnits ||
                draft.creditApplied.minorUnits != saved.creditAppliedMinorUnits)
        ) return false
        if (draft.total.minorUnits != saved.totalMinorUnits) return false
        if (draft.lines.size != savedItems.size) return false
        val before = savedItems.associate { it.productId to (it.quantity to it.unitValueMinorUnits) }
        return draft.lines.all { before[it.productId] == (it.quantity to it.unitValue.minorUnits) }
    }
}
