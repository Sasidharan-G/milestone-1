package com.kadaikutty.pos.feature.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleSyncDetectorTest {

    private val threshold = StaleSyncDetector.STALE_THRESHOLD_MS
    private val now = 10_000_000_000L

    @Test
    fun `no pending rows never triggers regardless of how old now is`() {
        assertFalse(StaleSyncDetector.isStale(oldestPendingCreatedAtEpochMs = null, nowEpochMs = now, thresholdMs = threshold))
        assertFalse(StaleSyncDetector.isStale(oldestPendingCreatedAtEpochMs = null, nowEpochMs = now + threshold * 100, thresholdMs = threshold))
    }

    @Test
    fun `just under the threshold does not trigger`() {
        val oldestPendingAt = now - threshold + 1
        assertFalse(StaleSyncDetector.isStale(oldestPendingAt, now, threshold))
    }

    @Test
    fun `just over the threshold triggers`() {
        val oldestPendingAt = now - threshold - 1
        assertTrue(StaleSyncDetector.isStale(oldestPendingAt, now, threshold))
    }

    @Test
    fun `exactly at the threshold does not trigger`() {
        val oldestPendingAt = now - threshold
        assertFalse(StaleSyncDetector.isStale(oldestPendingAt, now, threshold))
    }

    @Test
    fun `a pending row from the future never triggers`() {
        val oldestPendingAt = now + 1_000L
        assertFalse(StaleSyncDetector.isStale(oldestPendingAt, now, threshold))
    }
}
