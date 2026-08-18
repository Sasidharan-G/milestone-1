package com.company.billing.core.sync

enum class SyncStatus { LOCAL_ONLY, PENDING, SYNCING, SYNCED, FAILED, CONFLICT }
data class SyncMetadata(val id: String, val status: SyncStatus, val updatedAtEpochMs: Long, val lastError: String? = null)
/** REQUIRES_CLIENT_CONFIRMATION: SYNC_CONFLICT_POLICY */
interface ConflictPolicy { fun resolve(): Nothing }

