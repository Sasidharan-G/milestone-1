package com.kadaikutty.pos.core.license

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "company_licenses")
data class LicenseEntity(
    @PrimaryKey val companyId: String,
    val businessName: String = "",
    val ownerName: String = "",
    val ownerMobile: String = "",
    val licenseStatus: String = "PENDING_APPROVAL", // "PENDING_APPROVAL", "TRIAL", "ACTIVE_PAID", "EXPIRED", "REVOKED"
    val licenseType: String = "TRIAL_2_DAYS",       // "TRIAL_2_DAYS", "YEARLY", "CUSTOM"
    val yearsGranted: Int = 0,
    val daysGranted: Int = 0,
    val activatedAtEpochMs: Long = 0L,
    val validUntilEpochMs: Long = 0L,
    val lastVerifiedAtEpochMs: Long = 0L,
    val highestSeenClockEpochMs: Long = 0L,
    val renewalCount: Int = 0,
    val notes: String = ""
) {
    val isExpired: Boolean
        get() {
            if (licenseStatus == "REVOKED" || licenseStatus == "EXPIRED" || licenseStatus == "PENDING_APPROVAL") return true
            if (validUntilEpochMs <= 0L) return true
            return System.currentTimeMillis() >= validUntilEpochMs
        }

    val remainingHours: Long
        get() {
            val diff = validUntilEpochMs - System.currentTimeMillis()
            return if (diff > 0) diff / (1000 * 60 * 60) else 0L
        }

    val remainingDays: Long
        get() {
            val diff = validUntilEpochMs - System.currentTimeMillis()
            return if (diff > 0) (diff / (1000 * 60 * 60 * 24)) + 1 else 0L
        }

    val isExpiringSoon: Boolean
        get() = !isExpired && remainingDays in 1..7
}
