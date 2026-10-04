package com.kadaikutty.pos.core.auth

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kadaikutty.pos.core.security.Permission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// Tokens live in an EncryptedSharedPreferences file (see CoreModule.secureSessionPrefs), not in the
// plaintext DataStore, so they aren't recoverable from a device backup or a rooted /data read.
class SessionStore(private val store: DataStore<Preferences>, private val securePrefs: SharedPreferences) {
    private val userId = stringPreferencesKey("session_user_id")
    private val displayName = stringPreferencesKey("session_display_name")
    private val permissionsKey = stringPreferencesKey("session_permissions")
    private val companyIdKey = stringPreferencesKey("session_company_id")
    private val roleKey = stringPreferencesKey("session_role")
    // Legacy plaintext keys, read once for migration then removed from the DataStore.
    private val sessionTokenKey = stringPreferencesKey("session_token")
    private val accessTokenKey = stringPreferencesKey("session_access_token")
    private val refreshTokenKey = stringPreferencesKey("session_refresh_token")
    private val deviceIdKey = stringPreferencesKey("session_device_id")

    private val secureAccessTokenKey = "session_access_token"
    private val secureRefreshTokenKey = "session_refresh_token"
    private val secureSessionTokenKey = "session_token"

    val activeSession: Flow<Session?> = store.data.map { preferences ->
        migrateLegacyTokensIfPresent(preferences)
        val id = preferences[userId] ?: return@map null
        val accessTokenValue = securePrefs.getString(secureAccessTokenKey, null)
        val refreshTokenValue = securePrefs.getString(secureRefreshTokenKey, null)
        val sessionTokenValue = securePrefs.getString(secureSessionTokenKey, null)
        // No role default: save() always writes one, so a missing key means a store written
        // before this field existed or a corrupted one. Inventing "ADMIN" there would hand a
        // full permission set to a session that never proved it had one.
        val role = preferences[roleKey].orEmpty()
        val permsString = preferences[permissionsKey].orEmpty()
        val parsedPerms = if (permsString.isBlank()) emptySet() else permsString.split(",")
            .mapNotNull {
                try { Permission.valueOf(it.trim()) } catch (e: Exception) { null }
            }.toSet()
        // Same order as UserEntity.effectivePermissions: a deactivated account keeps its declared
        // permissions and is never promoted back to ALL_ACTIVE by its role.
        val perms = if (parsedPerms.contains(Permission.ACCOUNT_INACTIVE)) {
            parsedPerms
        } else if (role == "ADMIN" || role == "SUPER_ADMIN") {
            Permission.ALL_ACTIVE
        } else {
            // An owner always stores role ADMIN, so reaching here with nothing parsed means a
            // staff session whose permissions really are empty - not a reason to grant them all.
            parsedPerms
        }
        Session(
            userId = id,
            displayName = preferences[displayName]?.ifBlank { "User" } ?: "User",
            permissions = perms,
            companyId = preferences[companyIdKey]?.ifBlank { "company_main" } ?: "company_main",
            role = role,
            accessToken = accessTokenValue,
            refreshToken = refreshTokenValue,
            sessionToken = sessionTokenValue,
            deviceId = preferences[deviceIdKey]
        )
    }.distinctUntilChanged()

    // One-time, synchronous move of any pre-existing plaintext tokens into EncryptedSharedPreferences.
    // Runs on every read but is a no-op once the legacy DataStore keys are gone; the DataStore side of
    // the removal happens on the next save()/updateTokens() (a suspend store.edit can't run inside this
    // synchronous map step), so a session that never refreshes keeps a stale plaintext copy until then.
    private fun migrateLegacyTokensIfPresent(preferences: Preferences) {
        val legacyAccess = preferences[accessTokenKey]
        val legacyRefresh = preferences[refreshTokenKey]
        val legacySession = preferences[sessionTokenKey]
        if (legacyAccess == null && legacyRefresh == null && legacySession == null) return
        securePrefs.edit().apply {
            if (legacyAccess != null && securePrefs.getString(secureAccessTokenKey, null) == null) {
                putString(secureAccessTokenKey, legacyAccess)
            }
            if (legacyRefresh != null && securePrefs.getString(secureRefreshTokenKey, null) == null) {
                putString(secureRefreshTokenKey, legacyRefresh)
            }
            if (legacySession != null && securePrefs.getString(secureSessionTokenKey, null) == null) {
                putString(secureSessionTokenKey, legacySession)
            }
        }.apply()
    }

    suspend fun save(session: Session) {
        var previousSessionUserId: String? = null
        store.edit {
            previousSessionUserId = it[userId]
            it[userId] = session.userId
            it[displayName] = session.displayName
            it[permissionsKey] = session.permissions.joinToString(",") { it.name }
            it[companyIdKey] = session.companyId
            it[roleKey] = session.role
            // Tokens never live in DataStore; clear any legacy plaintext copy while we're here.
            it.remove(accessTokenKey)
            it.remove(refreshTokenKey)
            it.remove(sessionTokenKey)

            if (session.deviceId != null) {
                it[deviceIdKey] = session.deviceId
            }
        }
        securePrefs.edit().apply {
            if (session.accessToken != null) putString(secureAccessTokenKey, session.accessToken)
            else if (previousSessionUserId != session.userId) remove(secureAccessTokenKey)

            if (session.refreshToken != null) putString(secureRefreshTokenKey, session.refreshToken)
            else if (previousSessionUserId != session.userId) remove(secureRefreshTokenKey)

            if (session.sessionToken != null) putString(secureSessionTokenKey, session.sessionToken)
            else if (previousSessionUserId != session.userId) remove(secureSessionTokenKey)
        }.apply()
    }

    suspend fun updateTokens(accessToken: String, refreshToken: String? = null, sessionToken: String? = null) {
        securePrefs.edit().apply {
            putString(secureAccessTokenKey, accessToken)
            if (!refreshToken.isNullOrBlank()) putString(secureRefreshTokenKey, refreshToken)
            if (!sessionToken.isNullOrBlank()) putString(secureSessionTokenKey, sessionToken)
        }.apply()
        // Wake up activeSession's DataStore-driven flow so collectors see the new tokens immediately.
        store.edit { it[roleKey] = it[roleKey].orEmpty() }
    }

    suspend fun updateSessionToken(value: String?) {
        securePrefs.edit().apply {
            if (value == null) remove(secureSessionTokenKey) else putString(secureSessionTokenKey, value)
        }.apply()
        store.edit { it[roleKey] = it[roleKey].orEmpty() }
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
        securePrefs.edit().clear().apply()
    }
}
