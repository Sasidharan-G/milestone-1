package com.company.billing.feature.billing.domain

import com.company.billing.core.common.Money

data class SaleLine(val productId: String, val productName: String, val quantity: Long, val unitPrice: Money) {
    init { require(quantity > 0) }
    val lineTotal: Money get() = unitPrice * quantity
}
data class SaleDraft(val lines: List<SaleLine>, val customerId: String? = null) {
    init { require(lines.isNotEmpty()) }
    val total: Money get() = lines.fold(Money.Zero) { total, line -> total + line.lineTotal }
}
/** REQUIRES_CLIENT_CONFIRMATION: tax, discounts, rounding, and payment rules. */
interface SalePricingPolicy { fun total(draft: SaleDraft): Money }
object LineTotalOnlyPricing : SalePricingPolicy { override fun total(draft: SaleDraft): Money = draft.total }
interface BillNumberStrategy { fun nextNumber(): String }
