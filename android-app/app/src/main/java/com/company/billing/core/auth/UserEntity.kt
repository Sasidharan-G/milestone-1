package com.company.billing.core.auth

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.company.billing.core.security.Permission

@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true)]
)
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val displayName: String,
    val salt: String, // Base64 derived salt
    val verifier: String, // Base64 derived verifier
    val permissions: String, // Comma-separated permissions list (e.g. "USER_MANAGE,PRODUCT_VIEW")
    val companyId: String,
    val role: String,
    val lastOnlineVerifiedAt: Long,
    val offlineValidUntil: Long
) {
    fun toPermissionsSet(): Set<Permission> {
        if (permissions.isBlank()) return emptySet()
        return permissions.split(",")
            .mapNotNull {
                try { Permission.valueOf(it.trim()) } catch (e: Exception) { null }
            }.toSet()
    }
}
