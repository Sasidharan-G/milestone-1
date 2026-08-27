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
    init { require(quantity > 0) }
    val lineTotal: Money get() = if (unitType == "KG" || unitType == "LITER") {
        Money(maxOf(0L, ((unitPrice.minorUnits * quantity) / 1000) - discount.minorUnits))
    } else {
        Money(maxOf(0L, (unitPrice * quantity).minorUnits - discount.minorUnits))
    }
}
data class SaleDraft(
    val lines: List<SaleLine>,
    val customerId: String? = null,
    val paymentMode: String = "CASH",
    val paidCash: Money = Money.Zero,
    val paidUpi: Money = Money.Zero,
    val creditApplied: Money = Money.Zero,
    val globalDiscount: Money = Money.Zero
) {
    init { require(lines.isNotEmpty()) }
    val subtotal: Money get() = lines.fold(Money.Zero) { total, line -> total + line.lineTotal }
    val total: Money get() = Money(maxOf(0L, subtotal.minorUnits - globalDiscount.minorUnits))
}
/** REQUIRES_CLIENT_CONFIRMATION: tax, discounts, rounding, and payment rules. */
interface SalePricingPolicy { fun total(draft: SaleDraft): Money }
object LineTotalOnlyPricing : SalePricingPolicy { override fun total(draft: SaleDraft): Money = draft.total }
interface BillNumberStrategy { fun nextNumber(): String }
