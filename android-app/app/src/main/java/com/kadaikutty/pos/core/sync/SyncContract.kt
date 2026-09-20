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

fun categorizeSyncError(e: Throwable): Pair<String, String> {
    val msg = (e.message ?: "").lowercase()
    val errorType = when {
        msg.contains("permission-denied") || msg.contains("unauthenticated") || msg.contains("auth") || msg.contains("unauthorized") -> "AUTH"
        msg.contains("timeout") || msg.contains("timed out") || msg.contains("deadline_exceeded") || msg.contains("504") -> "TIMEOUT"
        msg.contains("conflict") || msg.contains("already-exists") || msg.contains("failed-precondition") || msg.contains("aborted") -> "CONFLICT"
        msg.contains("space") || msg.contains("enospc") || msg.contains("storage") || msg.contains("quota") || msg.contains("resource-exhausted") || msg.contains("disk") -> "STORAGE"
        e is java.io.IOException || msg.contains("network") || msg.contains("offline") || msg.contains("unable to resolve host") || msg.contains("connection") -> "NETWORK"
        else -> "UNKNOWN"
    }

    return when (errorType) {
        "AUTH" -> Pair(
            "Cloud session renewing in background",
            "Authentication is renewing automatically. Tap Retry to synchronize now."
        )
        "TIMEOUT" -> Pair(
            "Server timeout: Cloud database response took too long",
            "Cloud servers are experiencing delays. Please wait a moment and tap Retry."
        )
        "CONFLICT" -> Pair(
            "Data conflict: Record was updated from another device",
            "Check Sync Diagnostics to review conflicting records or tap Retry to pull latest updates."
        )
        "STORAGE" -> Pair(
            "Insufficient storage: Device or cloud quota exceeded",
            "Free up storage space on your device or contact administrator to increase quota."
        )
        "NETWORK" -> Pair(
            "Network error: Unable to reach cloud database",
            "Please check your Wi-Fi or mobile data connection and tap Retry."
        )
        else -> Pair(
            "Cloud sync encountered an issue: ${e.message?.take(60) ?: "Unknown error"}",
            "Check your internet connection and tap Retry to synchronize."
        )
    }
}

