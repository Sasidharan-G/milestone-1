package com.company.billing.feature.purchase.data

import com.company.billing.core.common.AppResult
import com.company.billing.core.common.Money
import com.company.billing.feature.billing.data.StockMovementEntity
import com.company.billing.feature.purchase.domain.PurchaseDraft
import com.company.billing.feature.purchase.domain.PurchaseLine
import com.company.billing.feature.stock.domain.ProductStock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseRepositoryTest {
    @Test
    fun `save maps draft to purchase entities and inserts atomically`() = runBlocking {
        var savedPurchase: PurchaseEntity? = null
        var savedItems: List<PurchaseItemEntity> = emptyList()
        var savedMovements: List<StockMovementEntity> = emptyList()
        
        val fakeDao = object : PurchaseDao {
            override suspend fun insertPurchase(purchase: PurchaseEntity) {}
            override suspend fun insertItems(items: List<PurchaseItemEntity>) {}
            override suspend fun insertStockMovements(movements: List<StockMovementEntity>) {}
            override suspend fun savePurchase(purchase: PurchaseEntity, items: List<PurchaseItemEntity>, movements: List<StockMovementEntity>) {
                savedPurchase = purchase
                savedItems = items
                savedMovements = movements
            }
            override fun getStockBalances(): Flow<List<ProductStock>> = emptyFlow()
            override fun getPurchases(): Flow<List<PurchaseEntity>> = emptyFlow()
            override fun getPurchaseItems(purchaseId: String): Flow<List<PurchaseItemEntity>> = emptyFlow()
            override suspend fun getAveragePurchasePrice(productId: String): Double? = null
        }
        
        val mockSyncManager = org.mockito.Mockito.mock(com.company.billing.core.sync.SyncManager::class.java)
        val repository = PurchaseRepositoryImpl(fakeDao, mockSyncManager)
        val draft = PurchaseDraft(
            supplierId = "supp-1",
            lines = listOf(
                PurchaseLine("p1", 5, Money(100)),
                PurchaseLine("p2", 2, Money(250))
            )
        )
        
        val result = repository.save(draft)
        assertTrue(result is AppResult.Success)
        val purchaseId = (result as AppResult.Success).value
        
        assertEquals(purchaseId, savedPurchase?.id)
        assertEquals("supp-1", savedPurchase?.supplierId)
        assertEquals(1000L, savedPurchase?.totalMinorUnits) // 5*100 + 2*250 = 1000
        
        assertEquals(2, savedItems.size)
        assertEquals("p1", savedItems[0].productId)
        assertEquals(5L, savedItems[0].quantity)
        assertEquals(100L, savedItems[0].unitValueMinorUnits)
        assertEquals(500L, savedItems[0].lineTotalMinorUnits)
        
        assertEquals(2, savedMovements.size)
        assertEquals("p1", savedMovements[0].productId)
        assertEquals(5L, savedMovements[0].quantityDelta) // Positive delta for stock inward
        assertEquals("PURCHASE", savedMovements[0].type)
    }
}
