package com.company.billing.feature.stock.domain

enum class StockMovementType { PURCHASE, SALE }
data class StockMovement(val productId: String, val quantityDelta: Long, val type: StockMovementType, val referenceId: String) {
    init { require(quantityDelta != 0L); require((type == StockMovementType.SALE && quantityDelta < 0L) || (type == StockMovementType.PURCHASE && quantityDelta > 0L)) }
}
