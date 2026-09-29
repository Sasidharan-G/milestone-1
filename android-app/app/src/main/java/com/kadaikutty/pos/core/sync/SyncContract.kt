package com.kadaikutty.pos.core.sync

enum class SyncStatus { LOCAL_ONLY, PENDING, SYNCING, SYNCED, FAILED, CONFLICT }

sealed interface SyncNotificationState {
    data object Idle : SyncNotificationState
    data object InProgress : SyncNotificationState
    data class Success(val message: String = "Cloud sync completed") : SyncNotificationState
    data class Failed(
        val reason: String,
        val suggestedNextSteps: String
    ) : SyncNotificationState
}

