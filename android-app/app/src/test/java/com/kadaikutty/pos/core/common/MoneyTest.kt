package com.kadaikutty.pos.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {

    @Test
    fun `zero constant has 0 minor units and formats correctly`() {
        assertEquals(0L, Money.Zero.minorUnits)
        assertEquals(Money(0), Money.Zero)
        assertEquals("₹0.00", Money.Zero.toString())
    }

    @Test
    fun `addition correctly sums minor units`() {
        val m1 = Money(1500) // ₹15.00
        val m2 = Money(2550) // ₹25.50
        val result = m1 + m2
        assertEquals(4050L, result.minorUnits)
        assertEquals("₹40.50", result.toString())
    }

    @Test
    fun `addition throws on arithmetic overflow`() {
        val maxMoney = Money(Long.MAX_VALUE)
        assertThrows(ArithmeticException::class.java) {
            maxMoney + Money(1)
        }
    }

    @Test
    fun `subtraction correctly calculates difference`() {
        val m1 = Money(5000)
        val m2 = Money(1500)
        val result = m1 - m2
        assertEquals(3500L, result.minorUnits)
        assertEquals("₹35.00", result.toString())
    }

    @Test
    fun `subtraction throws on arithmetic underflow`() {
        val minMoney = Money(Long.MIN_VALUE)
        assertThrows(ArithmeticException::class.java) {
            minMoney - Money(1)
        }
    }

    @Test
    fun `multiplication correctly scales minor units by quantity`() {
        val unitPrice = Money(1250) // ₹12.50
        val total = unitPrice * 4
        assertEquals(5000L, total.minorUnits)
        assertEquals("₹50.00", total.toString())
    }

    @Test
    fun `multiplication throws on arithmetic overflow`() {
        val largeMoney = Money(Long.MAX_VALUE / 2 + 10)
        assertThrows(ArithmeticException::class.java) {
            largeMoney * 2
        }
    }

    @Test
    fun `formatting produces 2 decimal places with rupee symbol`() {
        assertEquals("₹0.05", Money(5).toString())
        assertEquals("₹0.50", Money(50).toString())
        assertEquals("₹1.00", Money(100).toString())
        assertEquals("₹12345.67", Money(1234567).toString())
    }
}
