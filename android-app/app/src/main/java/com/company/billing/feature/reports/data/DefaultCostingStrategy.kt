package com.company.billing.feature.reports.data

import com.company.billing.core.common.Money
import com.company.billing.feature.purchase.data.PurchaseDao
import com.company.billing.feature.reports.domain.CostingStrategy
import kotlinx.coroutines.flow.first
import com.company.billing.core.auth.SessionStore

class DefaultCostingStrategy(
    private val purchaseDao: PurchaseDao,
    private val sessionStore: SessionStore
) : CostingStrategy {
    override suspend fun getProductCost(productId: String, quantity: Long): Money {
        val session = sessionStore.activeSession.first() ?: return Money.Zero
        val avgPrice = purchaseDao.getAveragePurchasePrice(session.companyId, productId)
        return if (avgPrice != null) {
            Money(avgPrice.toLong()) * quantity
        } else {
            Money.Zero
        }
    }
}
