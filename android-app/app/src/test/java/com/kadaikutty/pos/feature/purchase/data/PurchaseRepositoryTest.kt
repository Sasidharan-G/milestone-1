package com.kadaikutty.pos.feature.purchase.data

import com.kadaikutty.pos.core.common.AppResult
import com.kadaikutty.pos.core.common.Money
import com.kadaikutty.pos.feature.billing.data.StockMovementEntity
import com.kadaikutty.pos.feature.purchase.domain.PurchaseDraft
import com.kadaikutty.pos.feature.purchase.domain.PurchaseLine
import com.kadaikutty.pos.feature.stock.domain.ProductStock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

import kotlinx.coroutines.flow.flowOf
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

class PurchaseRepositoryTest {
    @Test
    fun `save maps draft to purchase entities and inserts atomically`() = runBlocking {
        var savedPurchase: PurchaseEntity? = null
        var savedItems: List<PurchaseItemEntity> = emptyList()
        var savedMovements: List<StockMovementEntity> = emptyList()
        
        val fakeDao = object : PurchaseDao {
            override suspend fun insertPurchase(purchase: PurchaseEntity) {}
            override suspend fun insertPurchases(items: List<PurchaseEntity>) {}
            override fun insertPurchasesSync(items: List<PurchaseEntity>) {}
            override suspend fun insertItems(items: List<PurchaseItemEntity>) {}
            override fun insertItemsSync(items: List<PurchaseItemEntity>) {}
            override suspend fun insertStockMovements(movements: List<StockMovementEntity>) {}
            override suspend fun savePurchase(purchase: PurchaseEntity, items: List<PurchaseItemEntity>, movements: List<StockMovementEntity>) {
                savedPurchase = purchase
                savedItems = items
                savedMovements = movements
            }
            override suspend fun deletePurchasesByCompany(companyId: String) {}
            override fun deletePurchasesByCompanySync(companyId: String) {}
            override suspend fun deletePurchaseItemsByCompany(companyId: String) {}
            override fun deletePurchaseItemsByCompanySync(companyId: String) {}
            override fun getStockBalances(companyId: String): Flow<List<ProductStock>> = emptyFlow()
            override fun getPurchases(companyId: String): Flow<List<PurchaseEntity>> = emptyFlow()
            override fun getPurchaseItems(companyId: String, purchaseId: String): Flow<List<PurchaseItemEntity>> = emptyFlow()
            override suspend fun getAveragePurchasePrice(companyId: String, productId: String): Double? = null
            override fun getPurchasesForSupplier(companyId: String, supplierId: String): Flow<List<PurchaseEntity>> = emptyFlow()
        }
        
        val mockSyncManager = mock(com.kadaikutty.pos.core.sync.SyncManager::class.java)
        val mockSessionStore = mock(com.kadaikutty.pos.core.auth.SessionStore::class.java)
        val fakeSession = com.kadaikutty.pos.core.auth.Session(
            userId = "user-123",
            displayName = "Test User",
            permissions = emptySet(),
            accessToken = "token",
            companyId = "company-123",
            role = "COMPANY_ADMIN"
        )
        doReturn(flowOf(fakeSession)).`when`(mockSessionStore).activeSession

        val repository = PurchaseRepositoryImpl(fakeDao, mockSyncManager, mockSessionStore)
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
