package com.company.billing.feature.reports.data

import com.company.billing.core.common.Money
import com.company.billing.feature.purchase.data.PurchaseDao
import com.company.billing.feature.masters.data.MasterDao
import com.company.billing.feature.reports.domain.CostingStrategy
import kotlinx.coroutines.flow.first
import com.company.billing.core.auth.SessionStore

class DefaultCostingStrategy(
    private val purchaseDao: PurchaseDao,
    private val masterDao: MasterDao,
    private val sessionStore: SessionStore
) : CostingStrategy {
    override suspend fun getProductCost(productId: String, quantity: Long): Money {
        val session = sessionStore.activeSession.first() ?: return Money.Zero
        val companyId = session.companyId
        
        val product = masterDao.getProductById(companyId, productId)
        val avgPrice = purchaseDao.getAveragePurchasePrice(companyId, productId)
        
        // Use average purchase price if recorded, else fall back to product master purchase price
        val unitCostMinorUnits: Double = avgPrice ?: (product?.purchasePriceMinorUnits?.toDouble() ?: 0.0)
        
        val totalCostMinorUnits = if (product?.unitType == "KG" || product?.unitType == "LITER") {
            // For KG/LITER, quantity is stored in grams/milliliters (1000 = 1 Kg/L)
            (unitCostMinorUnits * quantity) / 1000.0
        } else {
            unitCostMinorUnits * quantity
        }
        
        return Money(totalCostMinorUnits.toLong())
    }
}
