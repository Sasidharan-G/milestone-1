package com.kadaikutty.pos.core.common

import com.kadaikutty.pos.core.common.TrustedClock.Anchor
import org.junit.Assert.assertEquals
import org.junit.Test

class TrustedClockTest {
    // Server said 10:00:00 when the phone had been up 1,000 s; the phone clock read 09:55:00.
    private val serverAt = 1_000_000_000_000L
    private val anchor = Anchor(serverEpochMs = serverAt, elapsedMs = 1_000_000L, bootCount = 7, offsetMs = 300_000L)

    @Test
    fun `no server contact yet - the phone clock is all there is`() {
        assertEquals(123L, TrustedClock.correctedNow(123L, 5L, 7, null))
    }

    @Test
    fun `same boot - counts forward from server time on uptime, whatever the phone clock says`() {
        // 60 s of uptime later. The user has meanwhile moved the phone date a year back.
        val movedBack = serverAt - 365L * 24 * 3600 * 1000
        assertEquals(serverAt + 60_000L, TrustedClock.correctedNow(movedBack, 1_060_000L, 7, anchor))
    }

    @Test
    fun `after a restart - phone clock plus the last measured difference`() {
        assertEquals(2_000L + 300_000L, TrustedClock.correctedNow(2_000L, 5_000L, 8, anchor))
    }

    @Test
    fun `uptime going backwards means a restart even if the boot count is unknown`() {
        val unknownBoot = anchor.copy(bootCount = -1)
        assertEquals(2_000L + 300_000L, TrustedClock.correctedNow(2_000L, 1_060_000L, -1, unknownBoot))
        assertEquals(2_000L + 300_000L, TrustedClock.correctedNow(2_000L, 500L, 7, anchor))
    }
}
