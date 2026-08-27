package com.company.billing.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.company.billing.core.security.Permission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SessionStore(private val store: DataStore<Preferences>) {
    private val userId = stringPreferencesKey("session_user_id")
    private val displayName = stringPreferencesKey("session_display_name")
    private val permissionsKey = stringPreferencesKey("session_permissions")
    private val companyIdKey = stringPreferencesKey("session_company_id")
    private val roleKey = stringPreferencesKey("session_role")

    val activeSession: Flow<Session?> = store.data.map { preferences ->
        val id = preferences[userId] ?: return@map null
        val permsString = preferences[permissionsKey].orEmpty()
        val perms = if (permsString.isBlank()) Permission.entries.toSet() else permsString.split(",")
            .mapNotNull {
                try { Permission.valueOf(it.trim()) } catch (e: Exception) { null }
            }.toSet()
        Session(
            userId = id,
            displayName = preferences[displayName]?.ifBlank { "User" } ?: "User",
            permissions = if (perms.isEmpty()) Permission.entries.toSet() else perms,
            companyId = preferences[companyIdKey]?.ifBlank { "company_main" } ?: "company_main",
            role = preferences[roleKey] ?: "ADMIN"
        )
    }

    suspend fun save(session: Session) {
        store.edit {
            it[userId] = session.userId
            it[displayName] = session.displayName
            it[permissionsKey] = session.permissions.joinToString(",") { it.name }
            it[companyIdKey] = session.companyId
            it[roleKey] = session.role
        }
    }

    suspend fun clear() {
        store.edit {
            it.remove(userId)
            it.remove(displayName)
            it.remove(permissionsKey)
            it.remove(companyIdKey)
            it.remove(roleKey)
        }
    }
}
