package com.kadaikutty.pos.feature.purchase.domain

import com.kadaikutty.pos.core.common.Money

data class PurchaseLine(
    val productId: String, 
    val quantity: Long, 
    val unitValue: Money,
    val unitType: String = "PIECE",
    val supplierId: String? = null
) { 
    init { require(quantity > 0L) }
    val total: Money get() = if (unitType == "KG" || unitType == "LITER") {
        Money((unitValue.minorUnits * quantity) / 1000)
    } else {
        Money((unitValue.minorUnits * quantity))
    }
}

data class PurchaseDraft(
    val supplierId: String, 
    val lines: List<PurchaseLine>,
    val invoiceNumber: String? = null,
    val notes: String? = null,
    val paymentMode: String = "CASH",
    val paidCash: Money = Money.Zero,
    val paidUpi: Money = Money.Zero,
    val creditApplied: Money = Money.Zero
) { 
    init { require(lines.isNotEmpty()) }
    val total: Money get() = lines.fold(Money.Zero) { sum, line -> sum + line.total } 
}
