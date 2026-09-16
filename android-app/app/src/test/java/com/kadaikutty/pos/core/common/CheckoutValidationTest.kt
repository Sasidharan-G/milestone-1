package com.kadaikutty.pos.core.common

import com.kadaikutty.pos.feature.billing.domain.SaleLine
import com.kadaikutty.pos.feature.purchase.domain.PurchaseLine
import org.junit.Assert.assertEquals
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
    @Test fun allocationPreservesEveryPaise() {
        assertEquals(listOf(0L, 1L, 1L), CheckoutMath.allocate(2, listOf(1, 1, 1)))
        assertEquals(10001L, CheckoutMath.allocate(10001, listOf(19, 37, 83)).sum())
    }
    @Test(expected = IllegalArgumentException::class) fun upiOverpaymentRejected() { CheckoutMath.payment(100, 0, 101, false) }
}
