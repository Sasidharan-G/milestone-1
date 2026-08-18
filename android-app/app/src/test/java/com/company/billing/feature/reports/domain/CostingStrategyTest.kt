package com.company.billing.feature.reports.domain

import com.company.billing.core.common.Money
import com.company.billing.feature.billing.data.StockMovementEntity
import com.company.billing.feature.purchase.data.PurchaseDao
import com.company.billing.feature.purchase.data.PurchaseEntity
import com.company.billing.feature.purchase.data.PurchaseItemEntity
import com.company.billing.feature.stock.domain.ProductStock
import com.company.billing.feature.reports.data.DefaultCostingStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CostingStrategyTest {
    @Test
    fun `calculates product costing based on average purchase price`() = runBlocking {
        val fakeDao = object : PurchaseDao {
            override suspend fun insertPurchase(purchase: PurchaseEntity) {}
            override suspend fun insertItems(items: List<PurchaseItemEntity>) {}
            override suspend fun insertStockMovements(movements: List<StockMovementEntity>) {}
            override suspend fun savePurchase(purchase: PurchaseEntity, items: List<PurchaseItemEntity>, movements: List<StockMovementEntity>) {}
            override fun getStockBalances(): Flow<List<ProductStock>> = emptyFlow()
            override fun getPurchases(): Flow<List<PurchaseEntity>> = emptyFlow()
            override fun getPurchaseItems(purchaseId: String): Flow<List<PurchaseItemEntity>> = emptyFlow()
            override suspend fun getAveragePurchasePrice(productId: String): Double? {
                return if (productId == "p1") 150.0 else null
            }
        }
        
        val costing = DefaultCostingStrategy(fakeDao)
        val costP1 = costing.getProductCost("p1", 4)
        val costP2 = costing.getProductCost("p2", 10)
        
        assertEquals(Money(600), costP1) // 150 * 4 = 600 minor units
        assertEquals(Money.Zero, costP2)  // Null returns Zero
    }
}
