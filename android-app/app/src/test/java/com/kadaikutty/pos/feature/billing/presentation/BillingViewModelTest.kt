package com.kadaikutty.pos.feature.billing.presentation

import com.kadaikutty.pos.core.auth.SessionStore
import com.kadaikutty.pos.core.auth.UserSession
import com.kadaikutty.pos.core.common.Money
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.database.DraftCartDao
import com.kadaikutty.pos.core.database.MasterDao
import com.kadaikutty.pos.core.database.SaleDao
import com.kadaikutty.pos.core.preferences.AppPreferences
import com.kadaikutty.pos.core.sharing.ShareManager
import com.kadaikutty.pos.feature.billing.domain.SaleRepository
import com.kadaikutty.pos.feature.masters.data.ProductEntity
import com.kadaikutty.pos.core.sync.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any

@OptIn(ExperimentalCoroutinesApi::class)
class BillingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit val database: BillingDatabase
    private lateinit val masterDao: MasterDao
    private lateinit val saleDao: SaleDao
    private lateinit val draftCartDao: DraftCartDao
    private lateinit val saleRepository: SaleRepository
    private lateinit val shareManager: ShareManager
    private lateinit val appPreferences: AppPreferences
    private lateinit val sessionStore: SessionStore

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        database = mock(BillingDatabase::class.java)
        masterDao = mock(MasterDao::class.java)
        saleDao = mock(SaleDao::class.java)
        draftCartDao = mock(DraftCartDao::class.java)
        
        `when`(database.masterDao()).thenReturn(masterDao)
        `when`(database.saleDao()).thenReturn(saleDao)
        `when`(database.draftCartDao()).thenReturn(draftCartDao)

        saleRepository = mock(SaleRepository::class.java)
        shareManager = mock(ShareManager::class.java)
        appPreferences = mock(AppPreferences::class.java)
        sessionStore = mock(SessionStore::class.java)

        // Mock Session
        val dummySession = UserSession("user1", "user1", "John", "comp1", listOf())
        `when`(sessionStore.activeSession).thenReturn(flowOf(dummySession))

        // Mock initial cart
        `when`(draftCartDao.getDraftCart("comp1")).thenReturn(flowOf(emptyList()))
        
        // Mock sales
        `when`(saleDao.getSales("comp1")).thenReturn(flowOf(emptyList()))
        
        // Mock customers
        `when`(masterDao.customers("comp1", "")).thenReturn(flowOf(emptyList()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test barcode scan adds product to cart automatically`() = runTest {
        // 1. Arrange: Sample Product from the internet
        val internetBarcode = "8901030940023" // Parle-G Biscuit Barcode
        val sampleProduct = ProductEntity(
            id = "prod1",
            companyId = "comp1",
            name = "Parle-G Biscuit",
            barcode = internetBarcode,
            purchasePriceMinorUnits = 400,
            salePriceMinorUnits = 500, // Rs 5.00
            taxRate = 0.0,
            unitType = "PCS",
            category = "Snacks",
            minStockLevel = 10,
            createdAtEpochMs = 0L,
            syncStatus = SyncStatus.PENDING
        )
        
        // Mock products state
        `when`(masterDao.products("comp1", "")).thenReturn(flowOf(listOf(sampleProduct)))

        val viewModel = BillingViewModel(
            database,
            saleRepository,
            shareManager,
            appPreferences,
            sessionStore
        )

        // Wait for coroutines to load products flow
        testScheduler.advanceUntilIdle()

        // 2. Act: Simulate barcode scanner returning the string
        var productNotFoundCalled = false
        viewModel.onBarcodeScanned(internetBarcode) {
            productNotFoundCalled = true
        }

        // 3. Assert
        assertEquals("Product should have been found!", false, productNotFoundCalled)
        
        val lines = viewModel.lines.value
        assertEquals("Cart should have 1 item", 1, lines.size)
        assertEquals("Item name should match", "Parle-G Biscuit", lines[0].productName)
        assertEquals("Quantity should be 1 PCS", 1L, lines[0].quantity)
        assertEquals("Price should be correctly parsed as Rs 5.00", 500L, lines[0].unitPrice.minorUnits)
    }
}
