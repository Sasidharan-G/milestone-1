package com.kadaikutty.pos.feature.reports.presentation

import com.kadaikutty.pos.core.auth.Session
import com.kadaikutty.pos.core.auth.SessionStore
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.database.ReportDao
import com.kadaikutty.pos.core.export.domain.ExcelExporter
import com.kadaikutty.pos.core.export.domain.PdfExporter
import com.kadaikutty.pos.feature.reports.domain.ReportData
import com.kadaikutty.pos.feature.reports.domain.ReportQuery
import com.kadaikutty.pos.feature.reports.domain.ReportService
import com.kadaikutty.pos.feature.reports.domain.ReportType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockService: ReportService
    private lateinit var mockPdfExporter: PdfExporter
    private lateinit var mockExcelExporter: ExcelExporter
    private lateinit var mockDb: BillingDatabase
    private lateinit var mockDao: ReportDao
    private lateinit var mockSessionStore: SessionStore
    private lateinit var viewModel: ReportsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockService = mock(ReportService::class.java)
        mockPdfExporter = mock(PdfExporter::class.java)
        mockExcelExporter = mock(ExcelExporter::class.java)
        mockDb = mock(BillingDatabase::class.java)
        mockDao = mock(ReportDao::class.java)
        `when`(mockDb.reportDao()).thenReturn(mockDao)
        
        mockSessionStore = mock(SessionStore::class.java)
        val activeSession = Session("u1", "User", emptySet(), "token", "c1", "admin")
        `when`(mockSessionStore.activeSession).thenReturn(flowOf(activeSession))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadReport loads data correctly and updates KPIs`() = runTest {
        `when`(mockDao.getTotalSalesSum("c1", null, null)).thenReturn(1000L)
        `when`(mockDao.getTotalPurchasesSum("c1", null, null)).thenReturn(500L)
        `when`(mockDao.getTotalExpensesSum("c1", null, null)).thenReturn(100L)
        
        val fakeData = ReportData("Test Report", listOf("Col1"), listOf(listOf("Val1")))
        
        // Use exact matching or safe matchers to avoid Kotlin NPE
        `when`(mockService.generate(any(ReportQuery::class.java) ?: ReportQuery(ReportType.SALE_AMOUNT, null, null))).thenReturn(fakeData)
        
        viewModel = ReportsViewModel(mockService, mockPdfExporter, mockExcelExporter, mockDb, mockSessionStore)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(1000L, viewModel.totalSalesSum.value)
        assertEquals(500L, viewModel.purchaseCostSum.value)
        assertEquals(400L, viewModel.netProfitSum.value) // 1000 - 500 - 100
        assertNotNull(viewModel.reportData.value)
        assertEquals("Test Report", viewModel.reportData.value?.title)
    }
}
