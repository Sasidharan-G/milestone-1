package com.kadaikutty.pos.core.security

/**
 * Pure decision logic for whether a user may use cloud-sync features right now, and how many
 * days are left before that access lapses. Two independent deadlines can each cut access off:
 * a Master-granted end date, and a rolling "must reconnect within N days" check-in deadline.
 * Whichever comes first wins — see CloudAccessPolicyTest for the matrix.
 *
 * isCloudTier == false is a permanent, ordinary state (an offline-only registered account), not a
 * failure — it hides cloud buttons but must NEVER trigger the full-app lockout screen. Only an
 * account that WAS cloud-tier and then missed a deadline counts as "expired" for that purpose.
 */
object CloudAccessPolicy {
    const val CHECK_IN_WINDOW_DAYS = 7L
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    /**
     * Every deadline here is compared against the device clock, which the user controls. Winding
     * the date back would otherwise extend cloud access indefinitely. Callers pass the highest
     * timestamp this install has ever observed, and time is never allowed to read as earlier than
     * that, so a rollback buys nothing while a genuine forward correction still applies.
     *
     * This is a deterrent, not proof: a fresh install has no history to compare against. The
     * authoritative deadline is still the server's, re-applied on the next successful check-in.
     */
    fun effectiveNow(nowEpochMs: Long, highestSeenEpochMs: Long?): Long =
        if (highestSeenEpochMs == null) nowEpochMs else maxOf(nowEpochMs, highestSeenEpochMs)

    fun isAllowed(
        isCloudTier: Boolean,
        cloudAccessGrantedUntilEpochMs: Long?,
        mustCheckInByEpochMs: Long?,
        nowEpochMs: Long = System.currentTimeMillis(),
        highestSeenEpochMs: Long? = null
    ): Boolean {
        if (!isCloudTier) return false
        val now = effectiveNow(nowEpochMs, highestSeenEpochMs)
        if (cloudAccessGrantedUntilEpochMs != null && now >= cloudAccessGrantedUntilEpochMs) return false
        if (mustCheckInByEpochMs != null && now >= mustCheckInByEpochMs) return false
        return true
    }

    /** True only for a cloud-tier account whose access has lapsed — the trigger for the full-app lock screen. */
    fun isExpiredLockout(
        isCloudTier: Boolean,
        cloudAccessGrantedUntilEpochMs: Long?,
        mustCheckInByEpochMs: Long?,
        nowEpochMs: Long = System.currentTimeMillis(),
        highestSeenEpochMs: Long? = null
    ): Boolean = isCloudTier && !isAllowed(isCloudTier, cloudAccessGrantedUntilEpochMs, mustCheckInByEpochMs, nowEpochMs, highestSeenEpochMs)

    /** Days left until the SOONER of the two deadlines, for the dashboard countdown ring.
     *  Null when not applicable: offline-tier accounts, or a cloud-tier account with no deadline set. */
    fun daysRemaining(
        isCloudTier: Boolean,
        cloudAccessGrantedUntilEpochMs: Long?,
        mustCheckInByEpochMs: Long?,
        nowEpochMs: Long = System.currentTimeMillis(),
        highestSeenEpochMs: Long? = null
    ): Long? {
        if (!isCloudTier) return null
        val deadlines = listOfNotNull(cloudAccessGrantedUntilEpochMs, mustCheckInByEpochMs)
        val nearest = deadlines.minOrNull() ?: return null
        val remainingMs = nearest - effectiveNow(nowEpochMs, highestSeenEpochMs)
        return if (remainingMs <= 0) 0L else (remainingMs + DAY_MS - 1) / DAY_MS // round up to whole days
    }

    /** Call on every successful online check-in to push the rolling connectivity deadline forward. */
    fun nextCheckInDeadline(nowEpochMs: Long = System.currentTimeMillis()): Long =
        nowEpochMs + CHECK_IN_WINDOW_DAYS * DAY_MS
}
