package com.company.billing.feature.reports.data

import com.company.billing.core.auth.Session
import com.company.billing.core.auth.SessionStore
import com.company.billing.core.common.Money
import com.company.billing.core.database.ReportDao
import com.company.billing.core.database.SaleAmountRow
import com.company.billing.feature.reports.domain.CostingStrategy
import com.company.billing.feature.reports.domain.ReportQuery
import com.company.billing.feature.reports.domain.ReportType
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class ReportRepositoryImplTest {

    private lateinit var mockDao: ReportDao
    private lateinit var mockCostingStrategy: CostingStrategy
    private lateinit var mockSessionStore: SessionStore
    private lateinit var repository: ReportRepositoryImpl

    @Before
    fun setup() {
        mockDao = mock(ReportDao::class.java)
        mockCostingStrategy = mock(CostingStrategy::class.java)
        mockSessionStore = mock(SessionStore::class.java)
        
        val activeSession = Session("u1", "User", emptySet(), "token", "c1", "admin")
        `when`(mockSessionStore.activeSession).thenReturn(flowOf(activeSession))
        
        repository = ReportRepositoryImpl(mockDao, mockCostingStrategy, mockSessionStore)
    }

    @Test
    fun `query SALE_AMOUNT returns formatted ReportData`() = runTest {
        val rows = listOf(SaleAmountRow("2026-08-20", 5000L))
        `when`(mockDao.getSaleAmountReport("c1", null, null)).thenReturn(rows)
        
        val query = ReportQuery(ReportType.SALE_AMOUNT, null, null)
        val result = repository.query(query)
        
        assertEquals("Sale Amount Report", result.title)
        assertEquals(2, result.columns.size)
        assertEquals(1, result.rows.size)
        assertEquals("2026-08-20", result.rows[0][0])
        assertEquals(Money(5000L).toString(), result.rows[0][1])
    }
}
