package com.company.billing.core.sync

enum class SyncStatus { LOCAL_ONLY, PENDING, SYNCING, SYNCED, FAILED, CONFLICT }
data class SyncMetadata(val id: String, val status: SyncStatus, val updatedAtEpochMs: Long, val lastError: String? = null)

/**
 * Conflict resolution policy for sync operations.
 * Server-wins strategy: when conflict detected, fetch server version and overwrite local.
 */
sealed interface ConflictPolicy {
    data class ServerWins(val notifyUser: Boolean = true) : ConflictPolicy
    data class LocalWins(val notifyUser: Boolean = true) : ConflictPolicy
    data class Manual(val conflictData: String) : ConflictPolicy // For future UI resolution
    
    companion object {
        val DEFAULT = ServerWins(notifyUser = true)
    }
}

