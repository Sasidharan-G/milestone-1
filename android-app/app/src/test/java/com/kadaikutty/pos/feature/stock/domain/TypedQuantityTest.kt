package com.kadaikutty.pos.feature.stock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TypedQuantityTest {
    @Test
    fun `typed weights convert to exact grams`() {
        // (1.005 * 1000).toLong() is 1004; every 3-decimal weight must land on its exact gram.
        assertEquals(1005L, typedQuantityToStorageUnits("1.005", "KG"))
        assertEquals(1001L, typedQuantityToStorageUnits("1.001", "KG"))
        assertEquals(2300L, typedQuantityToStorageUnits("2.3", "KG"))
        assertEquals(500L, typedQuantityToStorageUnits(".5", "LITER"))
        assertEquals(20000L, typedQuantityToStorageUnits("20", "KG"))
        for (grams in 1L..100_000L) {
            assertEquals(grams, typedQuantityToStorageUnits(storageUnitsToTyped(grams, "KG"), "KG"))
        }
    }

    @Test
    fun `pieces must be whole and nothing may be negative or over-precise`() {
        assertEquals(3L, typedQuantityToStorageUnits("3", "PIECE"))
        assertEquals(0L, typedQuantityToStorageUnits("0", "KG"))
        assertNull(typedQuantityToStorageUnits("2.5", "PIECE"))
        assertNull(typedQuantityToStorageUnits("1.0005", "KG"))
        assertNull(typedQuantityToStorageUnits("-1", "KG"))
        assertNull(typedQuantityToStorageUnits("", "KG"))
        assertNull(typedQuantityToStorageUnits("abc", "KG"))
    }

    @Test
    fun `storage units format back in the unit people count in`() {
        assertEquals("25.000", storageUnitsToTyped(25_000, "KG"))
        assertEquals("25.000 Kg", formatQuantity(25_000, "KG"))
        assertEquals("1.500 L", formatQuantity(1_500, "LITER"))
        assertEquals("3 Pcs", formatQuantity(3, "PIECE"))
    }
}
