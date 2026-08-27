package com.company.billing.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sync_dead_letter", indices = [Index("companyId"), Index("entityType"), Index("entityId")])
data class SyncDeadLetterEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val payload: String,
    val lastError: String,
    val attemptCount: Int,
    val createdAtEpochMs: Long,
    val lastAttemptAtEpochMs: Long,
    val originalQueueId: String,
)