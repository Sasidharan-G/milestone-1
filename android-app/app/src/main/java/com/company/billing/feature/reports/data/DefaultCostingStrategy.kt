package com.company.billing.feature.reports.data

import com.company.billing.core.common.Money
import com.company.billing.feature.purchase.data.PurchaseDao
import com.company.billing.feature.reports.domain.CostingStrategy

class DefaultCostingStrategy(private val purchaseDao: PurchaseDao) : CostingStrategy {
    override suspend fun getProductCost(productId: String, quantity: Long): Money {
        val avgPrice = purchaseDao.getAveragePurchasePrice(productId)
        return if (avgPrice != null) {
            Money(avgPrice.toLong()) * quantity
        } else {
            Money.Zero
        }
    }
}
