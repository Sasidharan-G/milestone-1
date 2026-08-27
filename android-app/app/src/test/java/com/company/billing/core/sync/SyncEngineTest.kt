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
    fun `testSyncManagerEnqueuesCategoryCorrectly`() {
        runBlocking {
            var enqueuedItem: SyncQueueEntity? = null
val fakeDao = object : SyncQueueDao {
                override suspend fun enqueue(item: SyncQueueEntity) {
                    enqueuedItem = item
                }
                override suspend fun pending(companyId: String, limit: Int): List<SyncQueueEntity> = emptyList()
                override suspend fun pendingAfterCursor(companyId: String, cursor: Long, limit: Int): List<SyncQueueEntity> = emptyList()
                override suspend fun findPending(companyId: String, entityType: String, entityId: String): SyncQueueEntity? = null
                override suspend fun updateStatus(id: String, status: SyncStatus, updatedAtEpochMs: Long, error: String?) {}
                override suspend fun updatePending(id: String, operation: String, payload: String, updatedAtEpochMs: Long) {}
                override suspend fun updateLastSyncedAt(id: String, lastSyncedAt: Long) {}
                override suspend fun updateAttemptCount(id: String, attemptCount: Int) {}
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

    @Test
    fun testDedupInsertThenDeleteKeepsDelete() {
        runBlocking {
        var enqueuedItem: SyncQueueEntity? = null
        var updatedPending: SyncQueueEntity? = null
        var updateCalled = false

        val existingItem = SyncQueueEntity(
            id = "existing-id",
            companyId = "company-123",
            entityType = "Category",
            entityId = "cat-1",
            operation = "INSERT",
            payload = "{}",
            status = SyncStatus.PENDING,
            attemptCount = 0,
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )

        val fakeDao = object : SyncQueueDao {
            override suspend fun enqueue(item: SyncQueueEntity) {
                enqueuedItem = item
            }
            override suspend fun pending(companyId: String, limit: Int): List<SyncQueueEntity> = emptyList()
            override suspend fun pendingAfterCursor(companyId: String, cursor: Long, limit: Int): List<SyncQueueEntity> = emptyList()
            override suspend fun findPending(companyId: String, entityType: String, entityId: String): SyncQueueEntity? {
                return if (companyId == "company-123" && entityType == "Category" && entityId == "cat-1") {
                    existingItem
                } else null
            }
            override suspend fun updateStatus(id: String, status: SyncStatus, updatedAtEpochMs: Long, error: String?) {}
            override suspend fun updatePending(id: String, operation: String, payload: String, updatedAtEpochMs: Long) {
                updateCalled = true
                updatedPending = SyncQueueEntity(
                    id = id,
                    companyId = "company-123",
                    entityType = "Category",
                    entityId = "cat-1",
                    operation = operation,
                    payload = payload,
                    status = SyncStatus.PENDING,
                    attemptCount = 0,
                    createdAtEpochMs = 1000L,
                    updatedAtEpochMs = updatedAtEpochMs
                )
            }
            override suspend fun updateLastSyncedAt(id: String, lastSyncedAt: Long) {}
            override suspend fun updateAttemptCount(id: String, attemptCount: Int) {}
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

        // First INSERT (already in queue as PENDING)
        // Then DELETE - should UPDATE existing to DELETE
        manager.enqueueCategory(category, "DELETE")

        assertEquals(true, updateCalled)
        assertNotNull(updatedPending)
        assertEquals("DELETE", updatedPending?.operation)
        assertEquals("cat-1", updatedPending?.entityId)
        verify(mockScheduler).request()
        }
    }

    @Test
    fun testDedupUpdateThenInsertKeepsInsert() {
        runBlocking {
        var updateCalled = false
        var updatedPending: SyncQueueEntity? = null

        val existingItem = SyncQueueEntity(
            id = "existing-id",
            companyId = "company-123",
            entityType = "Product",
            entityId = "prod-1",
            operation = "UPDATE",
            payload = "{}",
            status = SyncStatus.PENDING,
            attemptCount = 0,
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )

        val fakeDao = object : SyncQueueDao {
            override suspend fun enqueue(item: SyncQueueEntity) {}
            override suspend fun pending(companyId: String, limit: Int): List<SyncQueueEntity> = emptyList()
            override suspend fun pendingAfterCursor(companyId: String, cursor: Long, limit: Int): List<SyncQueueEntity> = emptyList()
            override suspend fun findPending(companyId: String, entityType: String, entityId: String): SyncQueueEntity? {
                return if (companyId == "company-123" && entityType == "Product" && entityId == "prod-1") {
                    existingItem
                } else null
            }
            override suspend fun updateStatus(id: String, status: SyncStatus, updatedAtEpochMs: Long, error: String?) {}
            override suspend fun updatePending(id: String, operation: String, payload: String, updatedAtEpochMs: Long) {
                updateCalled = true
                updatedPending = SyncQueueEntity(
                    id = id,
                    companyId = "company-123",
                    entityType = "Product",
                    entityId = "prod-1",
                    operation = operation,
                    payload = payload,
                    status = SyncStatus.PENDING,
                    attemptCount = 0,
                    createdAtEpochMs = 1000L,
                    updatedAtEpochMs = updatedAtEpochMs
                )
            }
            override suspend fun updateLastSyncedAt(id: String, lastSyncedAt: Long) {}
            override suspend fun updateAttemptCount(id: String, attemptCount: Int) {}
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
        val product = com.company.billing.feature.masters.data.ProductEntity(
            id = "prod-1",
            companyId = "company-123",
            name = "Test Product",
            categoryId = "cat-1",
            purchasePriceMinorUnits = 100L,
            salePriceMinorUnits = 200L,
            unitType = "PCS",
            createdAtEpochMs = 123456L,
            updatedAtEpochMs = 123456L,
            syncStatus = SyncStatus.LOCAL_ONLY
        )

        // Existing is UPDATE, new is INSERT - INSERT has higher precedence (2 vs 1)
        manager.enqueueProduct(product, "INSERT")

        assertEquals(true, updateCalled)
        assertNotNull(updatedPending)
        assertEquals("INSERT", updatedPending?.operation)
        }
    }
}
