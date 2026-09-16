package com.kadaikutty.pos.feature.reports.data

import com.kadaikutty.pos.core.auth.Session
import com.kadaikutty.pos.core.auth.SessionStore
import com.kadaikutty.pos.core.common.Money
import com.kadaikutty.pos.core.sync.SyncStatus
import com.kadaikutty.pos.feature.masters.data.MasterDao
import com.kadaikutty.pos.feature.masters.data.ProductEntity
import com.kadaikutty.pos.feature.purchase.data.PurchaseDao
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class CostingStrategyTest {

    private lateinit var purchaseDao: PurchaseDao
    private lateinit var masterDao: MasterDao
    private lateinit var sessionStore: SessionStore
    private lateinit var costingStrategy: DefaultCostingStrategy

    private val testSession = Session(
        userId = "user_1",
        displayName = "Test User",
        permissions = emptySet(),
        companyId = "comp_test",
        role = "ADMIN"
    )

    @Before
    fun setUp() {
        purchaseDao = mock(PurchaseDao::class.java)
        masterDao = mock(MasterDao::class.java)
        sessionStore = mock(SessionStore::class.java)
        costingStrategy = DefaultCostingStrategy(purchaseDao, masterDao, sessionStore)
    }

    @Test
    fun `returns Money Zero when there is no active session`() = runBlocking {
        `when`(sessionStore.activeSession).thenReturn(flowOf(null))

        val cost = costingStrategy.getProductCost("prod_1", 10)

        assertEquals(Money.Zero, cost)
    }

    @Test
    fun `uses average purchase price when available for standard piece unit`() = runBlocking {
        `when`(sessionStore.activeSession).thenReturn(flowOf(testSession))
        val product = ProductEntity(
            id = "prod_1",
            companyId = "comp_test",
            name = "Soap",
            categoryId = "cat_1",
            purchasePriceMinorUnits = 3000L, // 30.00 fallback
            salePriceMinorUnits = 4000L,
            unitType = "PIECE",
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L,
            syncStatus = SyncStatus.SYNCED
        )
        `when`(masterDao.getProductById("comp_test", "prod_1")).thenReturn(product)
        // Average purchase price recorded is 2500 (25.00)
        `when`(purchaseDao.getAveragePurchasePrice("comp_test", "prod_1")).thenReturn(2500.0)

        val cost = costingStrategy.getProductCost("prod_1", 4)

        // 2500.0 * 4 = 10000 minor units (100.00)
        assertEquals(Money(10000L), cost)
    }

    @Test
    fun `falls back to master purchase price when average purchase price is null`() = runBlocking {
        `when`(sessionStore.activeSession).thenReturn(flowOf(testSession))
        val product = ProductEntity(
            id = "prod_2",
            companyId = "comp_test",
            name = "Toothpaste",
            categoryId = "cat_1",
            purchasePriceMinorUnits = 4500L, // 45.00
            salePriceMinorUnits = 6000L,
            unitType = "PIECE",
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L,
            syncStatus = SyncStatus.SYNCED
        )
        `when`(masterDao.getProductById("comp_test", "prod_2")).thenReturn(product)
        `when`(purchaseDao.getAveragePurchasePrice("comp_test", "prod_2")).thenReturn(null)

        val cost = costingStrategy.getProductCost("prod_2", 2)

        // 4500 * 2 = 9000 minor units (90.00)
        assertEquals(Money(9000L), cost)
    }

    @Test
    fun `calculates fractional cost for KG unit where quantity is in grams`() = runBlocking {
        `when`(sessionStore.activeSession).thenReturn(flowOf(testSession))
        val product = ProductEntity(
            id = "prod_kg",
            companyId = "comp_test",
            name = "Sugar",
            categoryId = "cat_1",
            purchasePriceMinorUnits = 4000L, // 40.00 per kg
            salePriceMinorUnits = 5000L,
            unitType = "KG",
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L,
            syncStatus = SyncStatus.SYNCED
        )
        `when`(masterDao.getProductById("comp_test", "prod_kg")).thenReturn(product)
        `when`(purchaseDao.getAveragePurchasePrice("comp_test", "prod_kg")).thenReturn(null)

        // 500 grams of sugar at 4000 per kg
        val cost = costingStrategy.getProductCost("prod_kg", 500)

        // (4000 * 500) / 1000 = 2000 minor units (20.00)
        assertEquals(Money(2000L), cost)
    }

    @Test
    fun `calculates fractional cost for LITER unit where quantity is in milliliters`() = runBlocking {
        `when`(sessionStore.activeSession).thenReturn(flowOf(testSession))
        val product = ProductEntity(
            id = "prod_oil",
            companyId = "comp_test",
            name = "Sunflower Oil",
            categoryId = "cat_1",
            purchasePriceMinorUnits = 15000L, // 150.00 per liter
            salePriceMinorUnits = 18000L,
            unitType = "LITER",
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L,
            syncStatus = SyncStatus.SYNCED
        )
        `when`(masterDao.getProductById("comp_test", "prod_oil")).thenReturn(product)
        `when`(purchaseDao.getAveragePurchasePrice("comp_test", "prod_oil")).thenReturn(null)

        // 250 ml of oil
        val cost = costingStrategy.getProductCost("prod_oil", 250)

        // (15000 * 250) / 1000 = 3750 minor units (37.50)
        assertEquals(Money(3750L), cost)
    }
}
