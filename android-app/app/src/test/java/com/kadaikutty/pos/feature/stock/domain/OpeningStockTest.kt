package com.kadaikutty.pos.feature.stock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a bulk import does with the "Opening Stock" column, and why the low-stock alert needs it. */
class OpeningStockTest {
    @Test
    fun `loose items are stored in thousandths like every other KG and LITER quantity`() {
        assertEquals(25_000L, openingStockToStorageUnits(25.0, "KG"))
        assertEquals(2_500L, openingStockToStorageUnits(2.5, "LITER"))
    }

    @Test
    fun `counted items are stored as typed`() {
        assertEquals(40L, openingStockToStorageUnits(40.0, "PACK"))
        assertEquals(12L, openingStockToStorageUnits(12.0, "PIECE"))
        assertEquals(12L, openingStockToStorageUnits(12.0, null))
    }

    @Test
    fun `an empty, zero, negative or unusable cell means no opening stock`() {
        assertEquals(0L, openingStockToStorageUnits(0.0, "PIECE"))
        assertEquals(0L, openingStockToStorageUnits(-5.0, "PIECE"))
        assertEquals(0L, openingStockToStorageUnits(Double.NaN, "PIECE"))
        assertEquals(0L, openingStockToStorageUnits(Double.POSITIVE_INFINITY, "KG"))
        assertEquals(0L, openingStockToStorageUnits(1e12, "PIECE"))
    }

    @Test
    fun `an imported product with a minimum level but no stock is low, one with opening stock is not`() {
        // This is the reported problem: 100 products imported with Min Stock 5 and nothing on hand.
        assertTrue(isLowStock(currentStock = 0, minStockLevel = 5.0, unitType = "PACK"))
        assertFalse(isLowStock(currentStock = openingStockToStorageUnits(15.0, "PACK"), minStockLevel = 5.0, unitType = "PACK"))
        assertFalse(isLowStock(currentStock = openingStockToStorageUnits(25.0, "KG"), minStockLevel = 10.0, unitType = "KG"))
        assertTrue(isLowStock(currentStock = openingStockToStorageUnits(8.0, "KG"), minStockLevel = 10.0, unitType = "KG"))
    }
}
