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
    // Judged on server-corrected time: moving the phone's date back does not stretch a license.
    val isExpired: Boolean
        get() {
            if (licenseStatus == "REVOKED" || licenseStatus == "EXPIRED" || licenseStatus == "PENDING_APPROVAL") return true
            if (validUntilEpochMs <= 0L) return true
            return com.kadaikutty.pos.core.common.TrustedClock.now() >= validUntilEpochMs
        }

    val remainingHours: Long
        get() {
            val diff = validUntilEpochMs - com.kadaikutty.pos.core.common.TrustedClock.now()
            return if (diff > 0) diff / (1000 * 60 * 60) else 0L
        }

    val remainingDays: Long
        get() {
            val diff = validUntilEpochMs - com.kadaikutty.pos.core.common.TrustedClock.now()
            return if (diff > 0) (diff / (1000 * 60 * 60 * 24)) + 1 else 0L
        }

    /** "23h" under two days (whole days would round a 1-day grant up to "2d"), else "364d". */
    val remainingLabel: String
        get() {
            val hours = remainingHours
            return if (hours < 48) "${hours}h" else "${hours / 24}d"
        }

    /** Plan name from what was actually granted, e.g. "1-DAY", "30-DAY", "1-YEAR", "1-YEAR +30D". */
    val planLabel: String
        get() = when (licenseType) {
            "YEARLY" -> "$yearsGranted-YEAR" + if (daysGranted > 0) " +${daysGranted}D" else ""
            "CUSTOM_DAYS", "CUSTOM" -> "$daysGranted-DAY"
            else -> "PAID"
        }

    val isExpiringSoon: Boolean
        get() = !isExpired && remainingDays in 1..7
}
