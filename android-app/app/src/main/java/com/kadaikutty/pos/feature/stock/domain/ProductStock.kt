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

/**
 * Opening stock in a bulk import is typed in whole units (kg, litres, pieces), like the minimum
 * level. KG and LITER stock is kept in thousandths, so 25 kg is stored as 25000. Returns 0 for
 * anything that is not a usable positive quantity, which means "no opening stock given".
 */
fun openingStockToStorageUnits(typed: Double, unitType: String?): Long {
    if (!typed.isFinite() || typed <= 0.0) return 0L
    val scaled = typed * (if (unitType == "KG" || unitType == "LITER") 1000.0 else 1.0)
    val rounded = Math.round(scaled)
    return if (rounded in 1..com.kadaikutty.pos.core.common.CheckoutMath.MAX_QUANTITY) rounded else 0L
}
