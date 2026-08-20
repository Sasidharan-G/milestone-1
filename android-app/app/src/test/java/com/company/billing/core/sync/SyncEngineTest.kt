package com.company.billing.core.sync

import com.company.billing.core.database.BillingDatabase
import com.company.billing.core.database.SyncQueueDao
import com.company.billing.core.database.SyncQueueEntity
import com.company.billing.feature.masters.data.CategoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.verify

class SyncEngineTest {
    @Test
    fun `syncManager enqueues category correctly and schedules sync`() {
        runBlocking {
            var enqueuedItem: SyncQueueEntity? = null
            val fakeDao = object : SyncQueueDao {
                override suspend fun enqueue(item: SyncQueueEntity) {
                    enqueuedItem = item
                }
                override suspend fun pending(companyId: String, limit: Int): List<SyncQueueEntity> = emptyList()
                override suspend fun updateStatus(id: String, status: SyncStatus, updatedAtEpochMs: Long, error: String?) {}
                override fun pendingCount(companyId: String): Flow<Int> = emptyFlow()
            }

            val mockDb = mock(BillingDatabase::class.java)
            doReturn(fakeDao).`when`(mockDb).syncQueueDao()

            val mockScheduler = mock(SyncScheduler::class.java)
            val mockOperation = mock(androidx.work.Operation::class.java)
            doReturn(mockOperation).`when`(mockScheduler).request()

            val mockSessionStore = mock(com.company.billing.core.auth.SessionStore::class.java)
            val fakeSession = com.company.billing.core.auth.Session(
                userId = "user-123",
                displayName = "Test User",
                permissions = emptySet(),
                accessToken = "token",
                companyId = "company-123",
                role = "COMPANY_ADMIN"
            )
            doReturn(flowOf(fakeSession)).`when`(mockSessionStore).activeSession

            val manager = SyncManager(mockDb, mockScheduler, mockSessionStore)
            val category = CategoryEntity(
                id = "cat-1",
                companyId = "company-123",
                name = "Groceries",
                createdAtEpochMs = 123456L,
                updatedAtEpochMs = 123456L,
                syncStatus = SyncStatus.LOCAL_ONLY
            )

            manager.enqueueCategory(category, "INSERT")

            assertNotNull(enqueuedItem)
            assertEquals("Category", enqueuedItem?.entityType)
            assertEquals("cat-1", enqueuedItem?.entityId)
            verify(mockScheduler).request()
        }
    }
}
