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

    /**
     * Conflicts left behind by builds that could not resolve them. Sent again, the server replays
     * the stored CONFLICT with its record, and ConflictResolver settles them.
     */
    @Query("UPDATE sync_queue SET status = 'PENDING', updatedAtEpochMs = :now WHERE companyId = :companyId AND status = 'CONFLICT'")
    suspend fun requeueConflicts(companyId: String, now: Long)

    @Query("SELECT * FROM sync_queue WHERE id = :id")
    suspend fun getById(id: String): SyncQueueEntity?

    /** Pending work in send order, wider than one batch so held-back children cannot starve it. */
    @Query("SELECT * FROM sync_queue WHERE companyId = :companyId AND status = 'PENDING' ORDER BY createdAtEpochMs, id LIMIT :limit")
    suspend fun pendingWindow(companyId: String, limit: Int): List<SyncQueueEntity>

    /**
     * Still going to be sent: pending, awaiting conflict resolution, or failed but not yet
     * dead-lettered. A pull must not overwrite such a record, or the local change is lost and the
     * version check that would have caught the conflict is bypassed.
     */
    @Query("SELECT COUNT(*) > 0 FROM sync_queue WHERE companyId = :companyId AND entityType = :entityType AND entityId = :entityId AND (status IN ('PENDING', 'CONFLICT') OR (status = 'FAILED' AND attemptCount < :maxAttempts))")
    suspend fun hasUnresolved(companyId: String, entityType: String, entityId: String, maxAttempts: Int): Boolean

    @Query("SELECT DISTINCT entityId FROM sync_queue WHERE companyId = :companyId AND entityType IN (:entityTypes) AND (status IN ('PENDING', 'CONFLICT') OR (status = 'FAILED' AND attemptCount < :maxAttempts))")
    suspend fun unresolvedEntityIds(companyId: String, entityTypes: List<String>, maxAttempts: Int): List<String>

    /** Closes every open queue item for one record once a conflict resolution has taken it over. */
    @Query("UPDATE sync_queue SET status = 'SYNCED', lastError = :note, updatedAtEpochMs = :now WHERE companyId = :companyId AND entityType = :entityType AND entityId = :entityId AND status != 'SYNCED'")
    suspend fun supersede(companyId: String, entityType: String, entityId: String, note: String, now: Long)

    /**
     * Marks an item synced only if it still holds exactly what was sent. A write made to the same
     * record while the push was in flight is folded into this same row; marking it synced by id
     * alone closed that newer write too, and it never reached the cloud. Returns the rows changed.
     */
    @Query("UPDATE sync_queue SET status = 'SYNCED', lastError = NULL, updatedAtEpochMs = :now, lastSyncedAtEpochMs = :now WHERE id = :id AND status = 'PENDING' AND operation = :operation AND payload = :payload")
    suspend fun markSyncedIfUnchanged(id: String, operation: String, payload: String, now: Long): Int

    /** The server refused this exact write for good; it is not retried. Same unchanged-check as above. */
    @Query("UPDATE sync_queue SET status = 'FAILED', attemptCount = :attempts, lastError = :error, updatedAtEpochMs = :now WHERE id = :id AND status = 'PENDING' AND operation = :operation AND payload = :payload")
    suspend fun markRejectedIfUnchanged(id: String, operation: String, payload: String, attempts: Int, error: String, now: Long): Int

    @Query("SELECT COUNT(*) > 0 FROM sync_queue WHERE companyId = :companyId AND entityType = :entityType AND entityId = :entityId AND operation = 'DELETE' AND status IN ('PENDING', 'FAILED', 'CONFLICT')")
    suspend fun hasPendingDelete(companyId: String, entityType: String, entityId: String): Boolean

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
