package com.company.billing.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.company.billing.core.sync.SyncStatus

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val payload: String,
    val status: SyncStatus,
    val attemptCount: Int,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val lastError: String? = null,
)

