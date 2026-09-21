package com.kadaikutty.pos.feature.stock.domain

data class ProductStock(
    val productId: String,
    val productName: String,
    val categoryName: String,
    val currentStock: Long,
    val minStockLevel: Double = 0.0,
    val unitType: String = "PIECE",
)

/**
 * Stock of KG and LITER products is kept in thousandths (grams, millilitres), but the minimum
 * level is typed in whole units. Comparing the two directly meant a loose item only showed as low
 * once it was down to a few grams.
 */
fun isLowStock(currentStock: Long, minStockLevel: Double, unitType: String?): Boolean =
    minStockLevel > 0.0 && currentStock.toDouble() <= minStockLevel * (if (unitType == "KG" || unitType == "LITER") 1000.0 else 1.0)
