package com.kadaikutty.pos.core.auth

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kadaikutty.pos.core.security.Permission

@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true)]
)
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val displayName: String,
    // Vestigial. Offline login derives and checks its credential through OfflineCredentialStore,
    // which keeps the real salt and verifier; nothing reads these two columns. They are written
    // blank everywhere so no credential material is duplicated into a second store that no code
    // path validates. They stay declared only because dropping them needs a Room migration that
    // recreates the table on live shop databases.
    val salt: String,
    val verifier: String,
    val permissions: String, // Comma-separated permissions list (e.g. "USER_MANAGE,PRODUCT_VIEW")
    val companyId: String,
    val role: String,
    val lastOnlineVerifiedAt: Long,
    val offlineValidUntil: Long,
    // Cloud access is deliberately kept OUT of the permissions CSV / role auto-grant above, so an
    // offline-tier ADMIN still gets every other ADMIN capability but never cloud sync — see
    // CloudAccessPolicy for how these three fields combine into an allow/deny decision.
    val isCloudTier: Boolean = true,
    val cloudAccessGrantedUntilEpochMs: Long? = null,
    val mustCheckInByEpochMs: Long? = null
) {
    fun toPermissionsSet(): Set<Permission> {
        val declared = if (permissions.isBlank()) emptySet() else permissions.split(",")
            .mapNotNull {
                try { Permission.valueOf(it.trim()) } catch (e: Exception) { null }
            }.toSet()
        // Checked before the role/empty fallbacks below so a deactivated account can never be
        // promoted back to a full permission set.
        if (declared.contains(Permission.ACCOUNT_INACTIVE)) return declared
        if (role == "ADMIN" || role == "SUPER_ADMIN") return Permission.ALL_ACTIVE
        if (permissions.isBlank()) return emptySet()
        return if (declared.isEmpty()) Permission.ALL_ACTIVE else declared
    }
}
