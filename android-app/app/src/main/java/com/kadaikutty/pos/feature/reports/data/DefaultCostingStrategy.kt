package com.kadaikutty.pos.feature.reports.data

import com.kadaikutty.pos.core.common.Money
import com.kadaikutty.pos.feature.purchase.data.PurchaseDao
import com.kadaikutty.pos.feature.masters.data.MasterDao
import com.kadaikutty.pos.feature.reports.domain.CostingStrategy
import kotlinx.coroutines.flow.first
import com.kadaikutty.pos.core.auth.SessionStore

class DefaultCostingStrategy(
    private val tenantDatabaseManager: com.kadaikutty.pos.core.database.TenantDatabaseManager?,
    private val sessionStore: SessionStore,
    private val fallbackPurchaseDao: PurchaseDao? = null,
    private val fallbackMasterDao: MasterDao? = null,
) : CostingStrategy {
    constructor(
        tenantDatabaseManager: com.kadaikutty.pos.core.database.TenantDatabaseManager,
        sessionStore: SessionStore,
    ) : this(tenantDatabaseManager, sessionStore, null, null)

    constructor(
        purchaseDao: PurchaseDao,
        masterDao: MasterDao,
        sessionStore: SessionStore,
    ) : this(null, sessionStore, purchaseDao, masterDao)

    private val purchaseDao: PurchaseDao
        get() = tenantDatabaseManager?.getDatabase()?.purchaseDao() ?: fallbackPurchaseDao ?: error("No PurchaseDao available")

    private val masterDao: MasterDao
        get() = tenantDatabaseManager?.getDatabase()?.masterDao() ?: fallbackMasterDao ?: error("No MasterDao available")

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
