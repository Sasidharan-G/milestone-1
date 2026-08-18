package com.company.billing.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SessionStore(private val store: DataStore<Preferences>) {
    private val userId = stringPreferencesKey("session_user_id")
    private val displayName = stringPreferencesKey("session_display_name")
    val activeSession: Flow<Session?> = store.data.map { preferences -> preferences[userId]?.let { id -> Session(id, preferences[displayName].orEmpty(), emptySet()) } }
    suspend fun save(session: Session) { store.edit { it[userId] = session.userId; it[displayName] = session.displayName } }
    suspend fun clear() { store.edit { it.remove(userId); it.remove(displayName) } }
}
