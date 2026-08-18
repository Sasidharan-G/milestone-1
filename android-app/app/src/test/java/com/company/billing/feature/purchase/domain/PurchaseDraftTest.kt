package com.company.billing.feature.purchase.domain

import com.company.billing.core.common.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class PurchaseDraftTest {
    @Test
    fun `purchase total is deterministic minor-unit sum of line items`() {
        val draft = PurchaseDraft(
            supplierId = "supp-123",
            lines = listOf(
                PurchaseLine("p1", 5, Money(100)), // 500 minor units
                PurchaseLine("p2", 2, Money(250))  // 500 minor units
            )
        )
        assertEquals(Money(1000), draft.total) // 1000 minor units
    }
}
