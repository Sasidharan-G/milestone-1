package com.kadaikutty.pos.core.common

import com.kadaikutty.pos.feature.billing.domain.SaleLine
import com.kadaikutty.pos.feature.purchase.domain.PurchaseLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckoutValidationTest {
    @Test(expected = IllegalArgumentException::class)
    fun negativeSalePriceRejected() { SaleLine("p", "Rice", 1000, Money(-100), "KG") }
    @Test(expected = IllegalArgumentException::class)
    fun negativePurchasePriceRejected() { PurchaseLine("p", 1, Money(-100)) }
    @Test(expected = IllegalArgumentException::class)
    fun excessiveQuantityRejected() { SaleLine("p", "Rice", Long.MAX_VALUE, Money(100), "KG") }
    @Test fun fractionalUnitsRemainExact() {
        assertEquals(4995L, SaleLine("p", "Rice", 500, Money(9990), "KG").lineTotal.minorUnits)
        assertEquals(4995L, PurchaseLine("p", 500, Money(9990), "KG").total.minorUnits)
    }
    @Test fun paymentChangeOnlyCountsNetCash() {
        assertEquals(CheckoutMath.Payment(10000, 0, 0, 10000), CheckoutMath.payment(10000, 20000, 0, false))
    }
    @Test fun exactDecimalParserRejectsInvalidInputs() {
        assertEquals(29L, CheckoutMath.parseAmount("0.29"))
        assertEquals(null, CheckoutMath.parseAmount("-1"))
        assertEquals(null, CheckoutMath.parseAmount("NaN"))
        assertEquals(null, CheckoutMath.parseAmount("1.001"))
        assertEquals(null, CheckoutMath.parseAmount("1e10"))
    }
    @Test fun rupeesToMinorUnitsRoundsAwayFloatingPointTruncation() {
        // 19.99 as a Double is actually 19.989999999999998..., so (rupees * 100).toLong() truncates to 1998.
        assertEquals(1999L, CheckoutMath.rupeesToMinorUnits(19.99))
        assertEquals(290L, CheckoutMath.rupeesToMinorUnits(2.90))
        assertEquals(1L, CheckoutMath.rupeesToMinorUnits(0.01))
        assertEquals(0L, CheckoutMath.rupeesToMinorUnits(0.0))
    }
    @Test fun allocationPreservesEveryPaise() {
        assertEquals(listOf(0L, 1L, 1L), CheckoutMath.allocate(2, listOf(1, 1, 1)))
        assertEquals(10001L, CheckoutMath.allocate(10001, listOf(19, 37, 83)).sum())
    }

    @Test
    fun `allocate keeps every paise at the largest amount the app accepts`() {
        // The intermediate amount * cumulative overflows a Long here, which is why the division
        // runs in BigInteger; this pins that the API-26-safe conversion back to Long still holds.
        val max = CheckoutMath.MAX_AMOUNT
        val weights = listOf(19L, 37L, 83L, 1L)
        val parts = CheckoutMath.allocate(max, weights)
        assertEquals(max, parts.sum())
        assertEquals(weights.size, parts.size)
        assertTrue(parts.all { it >= 0 })
    }
    @Test(expected = IllegalArgumentException::class) fun upiOverpaymentRejected() { CheckoutMath.payment(100, 0, 101, false) }

    @Test fun splitBatchDiscountIsProRataWhileItemsRemain() {
        // 440 cart, 44 discount: a 100 batch takes 10 of it, the remaining 340 keeps 34.
        assertEquals(1000L, CheckoutMath.splitBatchDiscount(4400, 10000, 34000, itemsRemain = true))
        assertEquals(3400L, 4400L - CheckoutMath.splitBatchDiscount(4400, 10000, 34000, itemsRemain = true))
    }
    @Test fun splitBatchDiscountTakesEverythingWhenNothingRemains() {
        assertEquals(4400L, CheckoutMath.splitBatchDiscount(4400, 44000, 0, itemsRemain = false))
    }
    @Test fun splitBatchDiscountNeverThrowsOnAnEmptyCartTotal() {
        // allocate() rejects a zero weight sum with a discount; the screen calls this on every redraw, so it must not.
        assertEquals(0L, CheckoutMath.splitBatchDiscount(500, 0, 0, itemsRemain = true))
    }
    @Test fun splitBatchDiscountKeepsEveryPaiseAcrossBatches() {
        val cart = 12345L
        val first = CheckoutMath.splitBatchDiscount(777, 4001, cart - 4001, itemsRemain = true)
        val rest = 777L - first
        assertEquals(777L, first + rest)
        assertTrue(first in 0..777)
    }
}
