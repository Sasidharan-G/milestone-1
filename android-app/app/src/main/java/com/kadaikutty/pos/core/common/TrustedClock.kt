package com.kadaikutty.pos.core.common

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings

/**
 * "When did this edit happen", in server time, even when the edit is made offline.
 *
 * Offline edits are ordered by time when two devices change the same thing (see ConflictPolicy),
 * so the phone's own clock cannot be used: it can be wrong, or moved on purpose to win.
 *
 * Every server response carries the server's time. The clock remembers that time together with
 * the phone's monotonic uptime at that moment, and afterwards counts forward from it on uptime,
 * which the user cannot change. So after one online moment, moving the phone's date changes
 * nothing here until the phone restarts. After a restart uptime starts over, so until the next
 * server contact it falls back to the phone clock plus the last measured difference: still right
 * if the phone clock was simply off, but not proof against someone changing it while offline.
 */
object TrustedClock {
    data class Anchor(val serverEpochMs: Long, val elapsedMs: Long, val bootCount: Int, val offsetMs: Long)

    @Volatile private var anchor: Anchor? = null
    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var bootCountProvider: () -> Int = { -1 }

    fun init(context: Context) {
        val appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("trusted_clock", Context.MODE_PRIVATE)
        bootCountProvider = { runCatching { Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1) }
        anchor = prefs?.let { p ->
            if (!p.contains("server")) null
            else Anchor(p.getLong("server", 0L), p.getLong("elapsed", 0L), p.getInt("boot", -1), p.getLong("offset", 0L))
        }
    }

    /** Server time for right now, as well as this device can know it. */
    fun now(): Long {
        // No server contact recorded yet: nothing to correct with, and no reason to touch uptime.
        val current = anchor ?: return System.currentTimeMillis()
        return correctedNow(System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCountProvider(), current)
    }

    /** Called with the server's time from any response. */
    fun onServerTime(serverEpochMs: Long) {
        if (serverEpochMs <= 0L) return
        val elapsed = SystemClock.elapsedRealtime()
        val next = Anchor(serverEpochMs, elapsed, bootCountProvider(), serverEpochMs - System.currentTimeMillis())
        anchor = next
        prefs?.edit()?.putLong("server", next.serverEpochMs)?.putLong("elapsed", next.elapsedMs)
            ?.putInt("boot", next.bootCount)?.putLong("offset", next.offsetMs)?.apply()
    }

    /** Pure so it can be tested; see TrustedClockTest. */
    fun correctedNow(wallNow: Long, elapsedNow: Long, bootCount: Int, anchor: Anchor?): Long {
        if (anchor == null) return wallNow
        val sameBoot = bootCount >= 0 && bootCount == anchor.bootCount && elapsedNow >= anchor.elapsedMs
        return if (sameBoot) anchor.serverEpochMs + (elapsedNow - anchor.elapsedMs) else wallNow + anchor.offsetMs
    }
}
