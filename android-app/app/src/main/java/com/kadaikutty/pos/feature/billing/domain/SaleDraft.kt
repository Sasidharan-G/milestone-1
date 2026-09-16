package com.kadaikutty.pos.feature.billing.domain

import com.kadaikutty.pos.core.common.Money

data class SaleLine(
    val productId: String,
    val productName: String,
    val quantity: Long,
    val unitPrice: Money,
    val unitType: String = "PIECE",
    val discount: Money = Money.Zero
) {
    init { require(quantity in 1..com.kadaikutty.pos.core.common.CheckoutMath.MAX_QUANTITY); require(unitPrice.minorUnits in 0..com.kadaikutty.pos.core.common.CheckoutMath.MAX_AMOUNT); require(discount.minorUnits >= 0) }
    val lineTotal: Money get() = Money(maxOf(0L, com.kadaikutty.pos.core.common.CheckoutMath.lineTotal(unitPrice.minorUnits, quantity, unitType) - discount.minorUnits))
}
data class SaleDraft(
    val lines: List<SaleLine>,
    val customerId: String? = null,
    val paymentMode: String = "CASH",
    val paidCash: Money = Money.Zero,
    val paidUpi: Money = Money.Zero,
    val creditApplied: Money = Money.Zero,
    val globalDiscount: Money = Money.Zero,
    val requestId: String = com.kadaikutty.pos.core.common.newRecordId(),
    val editingSaleId: String? = null,
    val expectedRevision: Long? = null,
    val previousDue: Long = 0L,
    val nextCartRequestId: String = com.kadaikutty.pos.core.common.newRecordId()
) {
    init { require(lines.isNotEmpty()) }
    val subtotal: Money get() = lines.fold(Money.Zero) { total, line -> total + line.lineTotal }
    val total: Money get() = Money(maxOf(0L, subtotal.minorUnits - globalDiscount.minorUnits))
}
