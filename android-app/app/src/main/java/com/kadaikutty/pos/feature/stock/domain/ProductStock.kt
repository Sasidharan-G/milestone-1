package com.kadaikutty.pos.feature.stock.domain

data class ProductStock(
    val productId: String,
    val productName: String,
    val categoryName: String,
    val currentStock: Long,
    val minStockLevel: Double = 0.0
)
