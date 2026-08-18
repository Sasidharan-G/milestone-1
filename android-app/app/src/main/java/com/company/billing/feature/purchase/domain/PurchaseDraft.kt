package com.company.billing.feature.purchase.domain

import com.company.billing.core.common.Money
data class PurchaseLine(val productId: String, val quantity: Long, val unitValue: Money) { init { require(quantity > 0L) }; val total: Money get() = unitValue * quantity }
data class PurchaseDraft(val supplierId: String, val lines: List<PurchaseLine>) { init { require(lines.isNotEmpty()) }; val total: Money get() = lines.fold(Money.Zero) { sum, line -> sum + line.total } }
