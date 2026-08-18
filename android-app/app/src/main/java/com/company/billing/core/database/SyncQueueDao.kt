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

    @Query("SELECT * FROM sync_queue WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAtEpochMs LIMIT :limit")
    suspend fun pending(limit: Int): List<SyncQueueEntity>

    @Query("UPDATE sync_queue SET status = :status, updatedAtEpochMs = :updatedAtEpochMs, lastError = :error WHERE id = :id")
    suspend fun updateStatus(id: String, status: SyncStatus, updatedAtEpochMs: Long, error: String? = null)

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status != 'SYNCED'")
    fun pendingCount(): Flow<Int>
}

