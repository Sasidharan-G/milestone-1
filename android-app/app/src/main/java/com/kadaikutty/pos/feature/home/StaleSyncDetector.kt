package com.kadaikutty.pos.feature.home

// Pure threshold logic behind the Home screen's "unsynced changes" warning banner, kept separate
// from HomeViewModel so it's directly unit-testable without constructing the ViewModel's Room/
// WorkManager/WebSocket dependencies. This is a visibility aid only, not a guard: it can't be
// engineered away (a device that's never gone online has nothing to compare against), so the goal
// is just to stop the user from being blindsided by data that's been sitting unsynced.
object StaleSyncDetector {
    const val STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000L

    fun isStale(oldestPendingCreatedAtEpochMs: Long?, nowEpochMs: Long, thresholdMs: Long = STALE_THRESHOLD_MS): Boolean {
        if (oldestPendingCreatedAtEpochMs == null) return false
        return nowEpochMs - oldestPendingCreatedAtEpochMs > thresholdMs
    }
}
