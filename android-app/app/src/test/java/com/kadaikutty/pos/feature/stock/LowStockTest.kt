package com.kadaikutty.pos.feature.stock

import com.kadaikutty.pos.feature.stock.domain.isLowStock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LowStockTest {
    @Test
    fun `loose items compare grams against a minimum typed in kilograms`() {
        // 3 kg left, minimum 5 kg: low. Stock is kept in grams.
        assertTrue(isLowStock(3_000, 5.0, "KG"))
        assertFalse(isLowStock(6_000, 5.0, "KG"))
        assertTrue(isLowStock(500, 0.5, "LITER"))
    }

    @Test
    fun `pieces compare as they are`() {
        assertTrue(isLowStock(2, 5.0, "PIECE"))
        assertFalse(isLowStock(6, 5.0, "PIECE"))
    }

    @Test
    fun `no minimum means never low`() {
        assertFalse(isLowStock(0, 0.0, "KG"))
        assertFalse(isLowStock(-5, 0.0, "PIECE"))
    }
}
