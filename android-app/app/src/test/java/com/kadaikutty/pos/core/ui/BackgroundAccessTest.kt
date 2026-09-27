package com.kadaikutty.pos.core.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the "Allow background sync" prompt shows. */
class BackgroundAccessTest {
    private val now = 1_000_000L

    @Test
    fun `nothing to ask when the battery is fine and there is no auto-start step`() {
        assertFalse(BackgroundAccess.shouldAskFor(batteryNeeded = false, autoStartPending = false, snoozeUntil = 0L, now = now))
    }

    @Test
    fun `asks while the battery is restricted and the owner has not snoozed it`() {
        assertTrue(BackgroundAccess.shouldAskFor(batteryNeeded = true, autoStartPending = false, snoozeUntil = 0L, now = now))
        assertFalse(BackgroundAccess.shouldAskFor(batteryNeeded = true, autoStartPending = false, snoozeUntil = now + 1, now = now))
    }

    @Test
    fun `an owner who confirmed the phone's own battery setting is not asked about the battery again`() {
        // Vivo, Oppo and Xiaomi never report that setting to the app, so the prompt used to stay for ever.
        // batteryNeeded is false once it is confirmed, whatever the system API says.
        assertFalse(BackgroundAccess.shouldAskFor(batteryNeeded = false, autoStartPending = false, snoozeUntil = 0L, now = now))
    }

    @Test
    fun `the auto-start step is still asked after the battery step is settled`() {
        assertTrue(BackgroundAccess.shouldAskFor(batteryNeeded = false, autoStartPending = true, snoozeUntil = 0L, now = now))
        assertFalse(BackgroundAccess.shouldAskFor(batteryNeeded = false, autoStartPending = true, snoozeUntil = now + 1, now = now))
    }
}
