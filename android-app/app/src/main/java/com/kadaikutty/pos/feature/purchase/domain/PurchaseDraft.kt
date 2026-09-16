package com.kadaikutty.pos.feature.purchase.domain

import com.kadaikutty.pos.core.common.Money

data class PurchaseLine(
    val productId: String, 
    val quantity: Long, 
    val unitValue: Money,
    val unitType: String = "PIECE",
    val supplierId: String? = null
) { 
    init { require(quantity in 1..com.kadaikutty.pos.core.common.CheckoutMath.MAX_QUANTITY); require(unitValue.minorUnits in 0..com.kadaikutty.pos.core.common.CheckoutMath.MAX_AMOUNT) }
    val total: Money get() = Money(com.kadaikutty.pos.core.common.CheckoutMath.lineTotal(unitValue.minorUnits, quantity, unitType))
}

data class PurchaseDraft(
    val supplierId: String, 
    val lines: List<PurchaseLine>,
    val invoiceNumber: String? = null,
    val notes: String? = null,
    val paymentMode: String = "CASH",
    val paidCash: Money = Money.Zero,
    val paidUpi: Money = Money.Zero,
    val creditApplied: Money = Money.Zero,
    val requestId: String = com.kadaikutty.pos.core.common.newRecordId(),
    val editingPurchaseId: String? = null,
    val expectedRevision: Long? = null
) { 
    init { require(lines.isNotEmpty()) }
    val total: Money get() = lines.fold(Money.Zero) { sum, line -> sum + line.total } 
}
