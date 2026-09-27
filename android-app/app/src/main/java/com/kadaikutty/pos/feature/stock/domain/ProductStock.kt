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

/**
 * The unit typed in a bulk-import file or scanned label almost never matches one of the five app
 * unit types exactly ("Kgs", "ltr", "pcs", "Nos", "Bottles"...). Maps the common shorthand a shop
 * actually writes to a supported type; anything unrecognised is kept as PIECE, which is safe (it
 * only changes whether ×1000 storage scaling applies, and a wrongly-scaled loose item is worse
 * than one counted as pieces).
 */
val SUPPORTED_UNIT_TYPES = listOf("PIECE", "KG", "LITER", "BOX", "PACK", "BAG", "BOTTLE", "TUB")

fun normalizeUnitType(raw: String?): String {
    val u = raw?.trim()?.uppercase().orEmpty()
    if (u.isBlank()) return "PIECE"
    if (u in SUPPORTED_UNIT_TYPES) return u
    return when {
        u.startsWith("KG") || u == "KILOGRAM" || u == "KILOGRAMS" || u == "KILO" || u == "KILOS" -> "KG"
        u == "LTR" || u == "LTRS" || u.startsWith("LITRE") || u.startsWith("LITER") -> "LITER"
        u == "PCS" || u == "PC" || u == "NOS" || u == "NO" || u == "UNIT" || u == "UNITS" || u == "EACH" -> "PIECE"
        u == "PKT" || u == "PKTS" || u == "PACKET" || u == "PACKETS" || u == "PACKS" -> "PACK"
        u == "BOXES" -> "BOX"
        u == "BAGS" -> "BAG"
        u == "BTL" || u == "BTLS" || u == "BOTTLES" -> "BOTTLE"
        u == "TUBS" -> "TUB"
        else -> "PIECE"
    }
}
