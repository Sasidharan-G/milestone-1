package com.company.billing.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.company.billing.core.sync.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun enqueue(item: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue WHERE companyId = :companyId AND status IN ('PENDING', 'FAILED') ORDER BY createdAtEpochMs LIMIT :limit")
    suspend fun pending(companyId: String, limit: Int): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE companyId = :companyId AND status IN ('PENDING', 'FAILED') AND createdAtEpochMs > :cursor ORDER BY createdAtEpochMs LIMIT :limit")
    suspend fun pendingAfterCursor(companyId: String, cursor: Long, limit: Int): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE companyId = :companyId AND entityType = :entityType AND entityId = :entityId AND status IN ('PENDING', 'FAILED') LIMIT 1")
    suspend fun findPending(companyId: String, entityType: String, entityId: String): SyncQueueEntity?

    @Query("UPDATE sync_queue SET status = :status, updatedAtEpochMs = :updatedAtEpochMs, lastError = :error WHERE id = :id")
    suspend fun updateStatus(id: String, status: SyncStatus, updatedAtEpochMs: Long, error: String? = null)

    @Query("UPDATE sync_queue SET operation = :operation, payload = :payload, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id")
    suspend fun updatePending(id: String, operation: String, payload: String, updatedAtEpochMs: Long)

    @Query("UPDATE sync_queue SET lastSyncedAtEpochMs = :lastSyncedAt WHERE id = :id")
    suspend fun updateLastSyncedAt(id: String, lastSyncedAt: Long)

    @Query("UPDATE sync_queue SET attemptCount = :attemptCount WHERE id = :id")
    suspend fun updateAttemptCount(id: String, attemptCount: Int)

    @Query("SELECT COUNT(*) FROM sync_queue WHERE companyId = :companyId AND status != 'SYNCED'")
    fun pendingCount(companyId: String): Flow<Int>
}

@Dao
interface SyncDeadLetterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SyncDeadLetterEntity)

    @Query("SELECT * FROM sync_dead_letter WHERE companyId = :companyId ORDER BY lastAttemptAtEpochMs DESC LIMIT :limit")
    suspend fun getDeadLetters(companyId: String, limit: Int): List<SyncDeadLetterEntity>

    @Query("SELECT COUNT(*) FROM sync_dead_letter WHERE companyId = :companyId")
    fun deadLetterCount(companyId: String): Flow<Int>

    @Query("DELETE FROM sync_dead_letter WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM sync_dead_letter WHERE companyId = :companyId")
    suspend fun deleteAllForCompany(companyId: String)
}

