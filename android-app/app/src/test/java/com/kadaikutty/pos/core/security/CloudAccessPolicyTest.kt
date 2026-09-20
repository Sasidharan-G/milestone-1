package com.kadaikutty.pos.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudAccessPolicyTest {
    private val now = 1_000_000_000_000L
    private val oneDay = 24 * 60 * 60 * 1000L

    @Test
    fun `offline-tier accounts never get cloud access regardless of dates`() {
        assertFalse(CloudAccessPolicy.isAllowed(isCloudTier = false, cloudAccessGrantedUntilEpochMs = now + oneDay, mustCheckInByEpochMs = now + oneDay, nowEpochMs = now))
        assertEquals(null, CloudAccessPolicy.daysRemaining(isCloudTier = false, cloudAccessGrantedUntilEpochMs = now + oneDay, mustCheckInByEpochMs = now + oneDay, nowEpochMs = now))
    }

    @Test
    fun `offline-tier is never a lockout - it is a permanent, ordinary state`() {
        assertFalse(CloudAccessPolicy.isExpiredLockout(isCloudTier = false, cloudAccessGrantedUntilEpochMs = now - oneDay, mustCheckInByEpochMs = now - oneDay, nowEpochMs = now))
    }

    @Test
    fun `a cloud-tier account that missed its deadline is a lockout`() {
        assertTrue(CloudAccessPolicy.isExpiredLockout(isCloudTier = true, cloudAccessGrantedUntilEpochMs = now - oneDay, mustCheckInByEpochMs = null, nowEpochMs = now))
    }

    @Test
    fun `a cloud-tier account within its window is not a lockout`() {
        assertFalse(CloudAccessPolicy.isExpiredLockout(isCloudTier = true, cloudAccessGrantedUntilEpochMs = now + oneDay, mustCheckInByEpochMs = null, nowEpochMs = now))
    }

    @Test
    fun `existing accounts with no dates set are unrestricted (safe migration default)`() {
        assertTrue(CloudAccessPolicy.isAllowed(isCloudTier = true, cloudAccessGrantedUntilEpochMs = null, mustCheckInByEpochMs = null, nowEpochMs = now))
        assertEquals(null, CloudAccessPolicy.daysRemaining(isCloudTier = true, cloudAccessGrantedUntilEpochMs = null, mustCheckInByEpochMs = null, nowEpochMs = now))
    }

    @Test
    fun `master-granted end date cuts access exactly on that date, even mid check-in window`() {
        val grantedUntil = now + 2 * oneDay
        val checkInDeadline = now + 7 * oneDay // still far away
        assertTrue(CloudAccessPolicy.isAllowed(true, grantedUntil, checkInDeadline, now))
        assertFalse(CloudAccessPolicy.isAllowed(true, grantedUntil, checkInDeadline, grantedUntil))
    }

    @Test
    fun `missed check-in cuts access even when the granted end date is far away`() {
        val grantedUntil = now + 30 * oneDay
        val checkInDeadline = now + 7 * oneDay
        assertTrue(CloudAccessPolicy.isAllowed(true, grantedUntil, checkInDeadline, now))
        assertFalse(CloudAccessPolicy.isAllowed(true, grantedUntil, checkInDeadline, checkInDeadline))
    }

    @Test
    fun `daysRemaining reports whichever deadline is sooner`() {
        val soon = now + 3 * oneDay
        val later = now + 7 * oneDay
        assertEquals(3L, CloudAccessPolicy.daysRemaining(true, soon, later, now))
        assertEquals(3L, CloudAccessPolicy.daysRemaining(true, later, soon, now))
    }

    @Test
    fun `nextCheckInDeadline pushes the rolling window forward by 7 days`() {
        assertEquals(now + 7 * oneDay, CloudAccessPolicy.nextCheckInDeadline(now))
    }
}
