package com.kadaikutty.pos.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kadaikutty.pos.core.sync.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun enqueue(item: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue WHERE companyId = :companyId AND status = 'PENDING' ORDER BY createdAtEpochMs, id LIMIT :limit")
    suspend fun pending(companyId: String, limit: Int): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE companyId = :companyId AND entityType = :entityType AND entityId = :entityId AND status IN ('PENDING', 'FAILED') LIMIT 1")
    suspend fun findPending(companyId: String, entityType: String, entityId: String): SyncQueueEntity?

    @Query("UPDATE sync_queue SET status = :status, updatedAtEpochMs = :updatedAtEpochMs, lastError = :error WHERE id = :id")
    suspend fun updateStatus(id: String, status: SyncStatus, updatedAtEpochMs: Long, error: String? = null)

    @Query("UPDATE sync_queue SET operation = :operation, payload = :payload, status = 'PENDING', attemptCount = 0, lastError = NULL, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id")
    suspend fun updatePending(id: String, operation: String, payload: String, updatedAtEpochMs: Long)

    @Query("SELECT * FROM sync_queue WHERE companyId = :companyId AND status != 'SYNCED' ORDER BY createdAtEpochMs DESC, id DESC LIMIT :limit")
    fun unresolved(companyId: String, limit: Int): Flow<List<SyncQueueEntity>>

    @Query("UPDATE sync_queue SET lastSyncedAtEpochMs = :lastSyncedAt WHERE id = :id")
    suspend fun updateLastSyncedAt(id: String, lastSyncedAt: Long)

    @Query("UPDATE sync_queue SET attemptCount = :attemptCount WHERE id = :id")
    suspend fun updateAttemptCount(id: String, attemptCount: Int)

    // attemptCount is deliberately preserved: resetting it here meant an item could never reach
    // the dead-letter threshold and retried the same rejected payload forever.
    @Query("UPDATE sync_queue SET status = 'PENDING', lastError = NULL, updatedAtEpochMs = :updatedAtEpochMs WHERE companyId = :companyId AND status = 'FAILED' AND attemptCount < :maxAttempts")
    suspend fun retryFailed(companyId: String, updatedAtEpochMs: Long, maxAttempts: Int)

    @Query("SELECT COUNT(*) FROM sync_queue WHERE companyId = :companyId AND status != 'SYNCED'")
    fun pendingCount(companyId: String): Flow<Int>

    @Query("SELECT MIN(createdAtEpochMs) FROM sync_queue WHERE companyId = :companyId AND status != 'SYNCED'")
    fun oldestPendingCreatedAt(companyId: String): Flow<Long?>

    @Query("DELETE FROM sync_queue WHERE companyId = :companyId")
    suspend fun clearByCompany(companyId: String)

    @Query("UPDATE sync_queue SET companyId = :newCompanyId, payload = replace(payload, '\"company_main\"', '\"' || :newCompanyId || '\"') WHERE companyId = :oldCompanyId")
    suspend fun migrateTenantData(oldCompanyId: String, newCompanyId: String)
}

@Dao
interface SyncDeadLetterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SyncDeadLetterEntity)

    @Query("SELECT * FROM sync_dead_letter WHERE companyId = :companyId ORDER BY lastAttemptAtEpochMs DESC LIMIT :limit")
    suspend fun getDeadLetters(companyId: String, limit: Int): List<SyncDeadLetterEntity>

    @Query("DELETE FROM sync_dead_letter WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM sync_dead_letter WHERE companyId = :companyId")
    suspend fun deleteAllForCompany(companyId: String)
}
