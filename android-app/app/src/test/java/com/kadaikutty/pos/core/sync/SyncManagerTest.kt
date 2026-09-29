package com.kadaikutty.pos.core.sync

import com.kadaikutty.pos.core.auth.Session
import com.kadaikutty.pos.core.auth.SessionStore
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.database.SyncQueueDao
import com.kadaikutty.pos.core.database.SyncQueueEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class SyncManagerTest {

    data class UpdatedPendingRecord(
        val id: String,
        val operation: String,
        val payload: String,
        val updatedAtEpochMs: Long
    )

    class FakeSyncQueueDao : SyncQueueDao {
        var existingPendingItem: SyncQueueEntity? = null
        val enqueuedItems = mutableListOf<SyncQueueEntity>()
        val updatedPendingItems = mutableListOf<UpdatedPendingRecord>()

        override suspend fun enqueue(item: SyncQueueEntity) {
            enqueuedItems.add(item)
        }

        override suspend fun findPending(companyId: String, entityType: String, entityId: String): SyncQueueEntity? {
            return existingPendingItem
        }

        override suspend fun updatePending(id: String, operation: String, payload: String, updatedAtEpochMs: Long) {
            updatedPendingItems.add(UpdatedPendingRecord(id, operation, payload, updatedAtEpochMs))
        }

        override suspend fun pending(companyId: String, limit: Int): List<SyncQueueEntity> = emptyList()
        override fun unresolved(companyId: String, limit: Int): Flow<List<SyncQueueEntity>> = flowOf(emptyList())
        override suspend fun updateStatus(id: String, status: SyncStatus, updatedAtEpochMs: Long, error: String?) {}
        override suspend fun updateAttemptCount(id: String, attemptCount: Int) {}
        override suspend fun retryFailed(companyId: String, updatedAtEpochMs: Long, maxAttempts: Int) {}
        override fun pendingCount(companyId: String): Flow<Int> = flowOf(0)
        override fun oldestPendingCreatedAt(companyId: String): Flow<Long?> = flowOf(null)
        override suspend fun migrateTenantData(oldCompanyId: String, newCompanyId: String) {}
        override suspend fun requeueConflicts(companyId: String, now: Long) {}
        override suspend fun getById(id: String): SyncQueueEntity? = null
        override suspend fun pendingWindow(companyId: String, limit: Int): List<SyncQueueEntity> = emptyList()
        override suspend fun hasUnresolved(companyId: String, entityType: String, entityId: String, maxAttempts: Int): Boolean = false
        override suspend fun unresolvedEntityIds(companyId: String, entityTypes: List<String>, maxAttempts: Int): List<String> = emptyList()
        override suspend fun supersede(companyId: String, entityType: String, entityId: String, note: String, now: Long) {}
        override suspend fun hasPendingDelete(companyId: String, entityType: String, entityId: String): Boolean = false
        override suspend fun markSyncedIfUnchanged(id: String, operation: String, payload: String, now: Long): Int = 0
        override suspend fun markRejectedIfUnchanged(id: String, operation: String, payload: String, attempts: Int, error: String, now: Long): Int = 0
    }

    private lateinit var database: BillingDatabase
    private lateinit var fakeSyncQueueDao: FakeSyncQueueDao
    private lateinit var syncScheduler: SyncScheduler
    private lateinit var sessionStore: SessionStore
    private lateinit var syncManager: SyncManager

    private val testSession = Session(
        userId = "user_100",
        displayName = "Manager",
        permissions = emptySet(),
        companyId = "company_100",
        role = "ADMIN"
    )

    @Before
    fun setUp() {
        database = mock(BillingDatabase::class.java)
        fakeSyncQueueDao = FakeSyncQueueDao()
        syncScheduler = mock(SyncScheduler::class.java)
        sessionStore = mock(SessionStore::class.java)

        `when`(database.syncQueueDao()).thenReturn(fakeSyncQueueDao)
        `when`(sessionStore.activeSession).thenReturn(flowOf(testSession))

        syncManager = SyncManager(database, syncScheduler, sessionStore)
    }

    @Test
    fun `enqueuePartialUpdate on fresh entity creates new PENDING queue item`() = runBlocking {
        fakeSyncQueueDao.existingPendingItem = null

        val updates = mapOf("name" to "Updated Bread", "salePriceMinorUnits" to 3500L)
        syncManager.enqueuePartialUpdate("Product", "prod_1", updates)

        assertEquals(1, fakeSyncQueueDao.enqueuedItems.size)
        val captured = fakeSyncQueueDao.enqueuedItems.first()

        assertEquals("company_100", captured.companyId)
        assertEquals("Product", captured.entityType)
        assertEquals("prod_1", captured.entityId)
        assertEquals("PARTIAL_UPDATE", captured.operation)
        assertEquals(SyncStatus.PENDING, captured.status)

        val json = JSONObject(captured.payload)
        assertEquals("Updated Bread", json.getString("name"))
        assertEquals(3500L, json.getLong("salePriceMinorUnits"))
        assertTrue(json.has("updatedAtEpochMs"))
        verify(syncScheduler).request()
    }

    @Test
    fun `enqueuePartialUpdate merges with existing PARTIAL_UPDATE queue item`() = runBlocking {
        fakeSyncQueueDao.existingPendingItem = SyncQueueEntity(
            id = "queue_entry_1",
            companyId = "company_100",
            entityType = "Product",
            entityId = "prod_1",
            operation = "PARTIAL_UPDATE",
            payload = JSONObject(mapOf("name" to "Old Name", "barcode" to "123456")).toString(),
            status = SyncStatus.PENDING,
            attemptCount = 0,
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )

        val updates = mapOf("salePriceMinorUnits" to 4500L)
        syncManager.enqueuePartialUpdate("Product", "prod_1", updates)

        assertEquals(0, fakeSyncQueueDao.enqueuedItems.size)
        assertEquals(1, fakeSyncQueueDao.updatedPendingItems.size)

        val updated = fakeSyncQueueDao.updatedPendingItems.first()
        assertEquals("queue_entry_1", updated.id)
        assertEquals("PARTIAL_UPDATE", updated.operation)

        val mergedJson = JSONObject(updated.payload)
        assertEquals("Old Name", mergedJson.getString("name"))
        assertEquals("123456", mergedJson.getString("barcode"))
        assertEquals(4500L, mergedJson.getLong("salePriceMinorUnits"))
        assertTrue(mergedJson.has("updatedAtEpochMs"))
        verify(syncScheduler).request()
    }

    @Test
    fun `enqueuePartialUpdate preserves INSERT operation when merged into pending INSERT`() = runBlocking {
        fakeSyncQueueDao.existingPendingItem = SyncQueueEntity(
            id = "queue_entry_2",
            companyId = "company_100",
            entityType = "Product",
            entityId = "prod_new",
            operation = "INSERT",
            payload = JSONObject(mapOf("id" to "prod_new", "name" to "Original New Item")).toString(),
            status = SyncStatus.PENDING,
            attemptCount = 0,
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )

        val updates = mapOf("name" to "Corrected New Item")
        syncManager.enqueuePartialUpdate("Product", "prod_new", updates)

        assertEquals(0, fakeSyncQueueDao.enqueuedItems.size)
        assertEquals(1, fakeSyncQueueDao.updatedPendingItems.size)

        val updated = fakeSyncQueueDao.updatedPendingItems.first()
        assertEquals("queue_entry_2", updated.id)
        // Must preserve INSERT so backend inserts the full merged entity rather than partially updating nonexistent doc
        assertEquals("INSERT", updated.operation)

        val mergedJson = JSONObject(updated.payload)
        assertEquals("Corrected New Item", mergedJson.getString("name"))
        assertEquals("prod_new", mergedJson.getString("id"))
        verify(syncScheduler).request()
    }

    @Test
    fun `lower precedence operation does not overwrite existing higher precedence DELETE`() = runBlocking {
        fakeSyncQueueDao.existingPendingItem = SyncQueueEntity(
            id = "queue_entry_del",
            companyId = "company_100",
            entityType = "Product",
            entityId = "prod_del",
            operation = "DELETE",
            payload = "{}",
            status = SyncStatus.PENDING,
            attemptCount = 0,
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )

        // Incoming is PARTIAL_UPDATE (precedence 2 < DELETE precedence 3)
        syncManager.enqueuePartialUpdate("Product", "prod_del", mapOf("name" to "Zombie Name"))

        // Should NOT update or enqueue; DELETE is preserved intact
        assertEquals(0, fakeSyncQueueDao.enqueuedItems.size)
        assertEquals(0, fakeSyncQueueDao.updatedPendingItems.size)
        verify(syncScheduler).request()
    }

    @Test
    fun `enqueueProducts batches many products with one sync request`() = runBlocking {
        fakeSyncQueueDao.existingPendingItem = null

        val now = 1_700_000_000_000L
        val products = listOf(
            com.kadaikutty.pos.feature.masters.data.ProductEntity(
                id = "prod_1",
                companyId = "company_100",
                name = "Rice",
                categoryId = "cat_1",
                salePriceMinorUnits = 6000L,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                syncStatus = SyncStatus.LOCAL_ONLY
            ),
            com.kadaikutty.pos.feature.masters.data.ProductEntity(
                id = "prod_2",
                companyId = "company_100",
                name = "Oil",
                categoryId = "cat_1",
                salePriceMinorUnits = 18000L,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                syncStatus = SyncStatus.LOCAL_ONLY
            )
        )

        syncManager.enqueueProducts(products, "INSERT")

        assertEquals(2, fakeSyncQueueDao.enqueuedItems.size)
        assertTrue(fakeSyncQueueDao.enqueuedItems.all { it.entityType == "Product" && it.operation == "INSERT" })
        verify(syncScheduler, times(1)).request()
    }

    @Test
    fun `enqueueCategory mirrors the change into the live local backup writer`() = runBlocking {
        val tenantDatabaseManager = mock(com.kadaikutty.pos.core.database.TenantDatabaseManager::class.java)
        `when`(tenantDatabaseManager.getDatabase()).thenReturn(database)
        val liveBackupWriter = mock(com.kadaikutty.pos.core.backup.data.LiveBackupWriter::class.java)
        var capturedEntityType: String? = null
        var capturedEntityId: String? = null
        var capturedOperation: String? = null
        var capturedPayload: String? = null
        org.mockito.Mockito.doAnswer { invocation ->
            capturedEntityType = invocation.getArgument(0)
            capturedEntityId = invocation.getArgument(1)
            capturedOperation = invocation.getArgument(2)
            capturedPayload = invocation.getArgument(3)
            null
        }.`when`(liveBackupWriter).appendChange(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString()
        )
        val syncManagerWithLiveBackup = SyncManager(tenantDatabaseManager, syncScheduler, sessionStore, liveBackupWriter)

        val category = com.kadaikutty.pos.feature.masters.data.CategoryEntity(
            id = "cat_1",
            companyId = "company_100",
            name = "Snacks",
            createdAtEpochMs = 1_700_000_000_000L,
            updatedAtEpochMs = 1_700_000_000_000L,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        syncManagerWithLiveBackup.enqueueCategory(category, "INSERT")

        assertEquals("Category", capturedEntityType)
        assertEquals("cat_1", capturedEntityId)
        assertEquals("INSERT", capturedOperation)
        assertTrue(capturedPayload.orEmpty().contains("Snacks"))
    }
}
