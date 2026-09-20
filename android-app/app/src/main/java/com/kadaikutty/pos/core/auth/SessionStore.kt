package com.kadaikutty.pos.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kadaikutty.pos.core.security.Permission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SessionStore(private val store: DataStore<Preferences>) {
    private val userId = stringPreferencesKey("session_user_id")
    private val displayName = stringPreferencesKey("session_display_name")
    private val permissionsKey = stringPreferencesKey("session_permissions")
    private val companyIdKey = stringPreferencesKey("session_company_id")
    private val roleKey = stringPreferencesKey("session_role")
    private val sessionTokenKey = stringPreferencesKey("session_token")
    private val accessTokenKey = stringPreferencesKey("session_access_token")
    private val refreshTokenKey = stringPreferencesKey("session_refresh_token")
    private val deviceIdKey = stringPreferencesKey("session_device_id")

    val activeSession: Flow<Session?> = store.data.map { preferences ->
        val id = preferences[userId] ?: return@map null
        val role = preferences[roleKey] ?: "ADMIN"
        val permsString = preferences[permissionsKey].orEmpty()
        val parsedPerms = if (permsString.isBlank()) emptySet() else permsString.split(",")
            .mapNotNull {
                try { Permission.valueOf(it.trim()) } catch (e: Exception) { null }
            }.toSet()
        val perms = if (role == "ADMIN" || role == "SUPER_ADMIN" || parsedPerms.isEmpty()) {
            Permission.ALL_ACTIVE
        } else {
            parsedPerms
        }
        Session(
            userId = id,
            displayName = preferences[displayName]?.ifBlank { "User" } ?: "User",
            permissions = perms,
            companyId = preferences[companyIdKey]?.ifBlank { "company_main" } ?: "company_main",
            role = role,
            accessToken = preferences[accessTokenKey],
            refreshToken = preferences[refreshTokenKey],
            sessionToken = preferences[sessionTokenKey],
            deviceId = preferences[deviceIdKey]
        )
    }

    suspend fun save(session: Session) {
        store.edit {
            val previousUserId = it[userId]
            it[userId] = session.userId
            it[displayName] = session.displayName
            it[permissionsKey] = session.permissions.joinToString(",") { it.name }
            it[companyIdKey] = session.companyId
            it[roleKey] = session.role

            if (session.accessToken != null) {
                it[accessTokenKey] = session.accessToken
            } else if (previousUserId != session.userId) {
                it.remove(accessTokenKey)
            }

            if (session.refreshToken != null) {
                it[refreshTokenKey] = session.refreshToken
            } else if (previousUserId != session.userId) {
                it.remove(refreshTokenKey)
            }

            if (session.sessionToken != null) {
                it[sessionTokenKey] = session.sessionToken
            } else if (previousUserId != session.userId) {
                it.remove(sessionTokenKey)
            }

            if (session.deviceId != null) {
                it[deviceIdKey] = session.deviceId
            }
        }
    }

    suspend fun updateTokens(accessToken: String, refreshToken: String? = null, sessionToken: String? = null) {
        store.edit {
            it[accessTokenKey] = accessToken
            if (!refreshToken.isNullOrBlank()) it[refreshTokenKey] = refreshToken
            if (!sessionToken.isNullOrBlank()) it[sessionTokenKey] = sessionToken
        }
    }

    suspend fun updateSessionToken(value: String?) {
        store.edit { if (value == null) it.remove(sessionTokenKey) else it[sessionTokenKey] = value }
    }

    suspend fun clear() {
        store.edit {
            it.remove(userId)
            it.remove(displayName)
            it.remove(permissionsKey)
            it.remove(companyIdKey)
            it.remove(roleKey)
            it.remove(sessionTokenKey)
            it.remove(accessTokenKey)
            it.remove(refreshTokenKey)
            it.remove(deviceIdKey)
        }
    }
}
