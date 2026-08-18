package com.company.billing.core.auth

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OfflineCredentialStore(private val store: DataStore<Preferences>) {
    private val username = stringPreferencesKey("offline_username"); private val userId = stringPreferencesKey("offline_user_id"); private val displayName = stringPreferencesKey("offline_display_name"); private val salt = stringPreferencesKey("offline_salt"); private val verifier = stringPreferencesKey("offline_verifier")
    val credential: Flow<OfflineCredential?> = store.data.map { p -> listOf(username, userId, displayName, salt, verifier).takeIf { keys -> keys.all { p[it] != null } }?.let { OfflineCredential(p[username]!!, p[userId]!!, p[displayName]!!, Base64.decode(p[salt], Base64.NO_WRAP), Base64.decode(p[verifier], Base64.NO_WRAP)) } }
    suspend fun save(value: OfflineCredential) { store.edit { p -> p[username] = value.username; p[userId] = value.userId; p[displayName] = value.displayName; p[salt] = Base64.encodeToString(value.salt, Base64.NO_WRAP); p[verifier] = Base64.encodeToString(value.verifier, Base64.NO_WRAP) } }
}
