package com.kadaikutty.pos.feature.billing.domain

import com.kadaikutty.pos.core.common.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class SaleDraftTest {
    @Test fun `bill total is deterministic minor-unit sum`() {
        val bill = SaleDraft(listOf(SaleLine("p1", "Product", 3, Money(250)), SaleLine("p2", "Other", 2, Money(125))))
        assertEquals(Money(1000), bill.total)
    }
}
