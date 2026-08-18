package com.company.billing.feature.stock.domain

data class ProductStock(
    val productId: String,
    val productName: String,
    val categoryName: String,
    val currentStock: Long
)
