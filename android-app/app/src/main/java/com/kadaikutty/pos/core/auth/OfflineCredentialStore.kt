package com.kadaikutty.pos.core.auth

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class OfflineCredentialStore(private val store: DataStore<Preferences>) {
    private val username = stringPreferencesKey("offline_username")
    private val userId = stringPreferencesKey("offline_user_id")
    private val displayName = stringPreferencesKey("offline_display_name")
    private val salt = stringPreferencesKey("offline_salt")
    private val verifier = stringPreferencesKey("offline_verifier")
    private val companyIdKey = stringPreferencesKey("offline_company_id")
    private val credentialsMapKey = stringPreferencesKey("offline_credentials_map_json")

    val credential: Flow<OfflineCredential?> = store.data.map { p ->
        listOf(username, userId, displayName, salt, verifier).takeIf { keys -> keys.all { p[it] != null } }?.let {
            OfflineCredential(
                username = p[username]!!,
                userId = p[userId]!!,
                displayName = p[displayName]!!,
                salt = Base64.decode(p[salt], Base64.NO_WRAP),
                verifier = Base64.decode(p[verifier], Base64.NO_WRAP),
                companyId = p[companyIdKey] ?: "company_main"
            )
        }
    }

    fun getCredential(targetUsername: String): Flow<OfflineCredential?> = store.data.map { p ->
        val rawJson = p[credentialsMapKey]
        if (!rawJson.isNullOrBlank()) {
            try {
                val map = JSONObject(rawJson)
                if (map.has(targetUsername)) {
                    val obj = map.getJSONObject(targetUsername)
                    return@map OfflineCredential(
                        username = obj.getString("username"),
                        userId = obj.getString("userId"),
                        displayName = obj.getString("displayName"),
                        salt = Base64.decode(obj.getString("salt"), Base64.NO_WRAP),
                        verifier = Base64.decode(obj.getString("verifier"), Base64.NO_WRAP),
                        companyId = obj.optString("companyId", "company_main")
                    )
                }
            } catch (e: Exception) {
                // Fallback to legacy single credential
            }
        }

        // Fallback to single primary credential if username matches or if map is empty
        val primaryUser = p[username]
        if (primaryUser == targetUsername || primaryUser != null) {
            val s = p[salt]
            val v = p[verifier]
            val uId = p[userId]
            val dName = p[displayName]
            if (s != null && v != null && uId != null && dName != null) {
                return@map OfflineCredential(
                    username = primaryUser,
                    userId = uId,
                    displayName = dName,
                    salt = Base64.decode(s, Base64.NO_WRAP),
                    verifier = Base64.decode(v, Base64.NO_WRAP),
                    companyId = p[companyIdKey] ?: "company_main"
                )
            }
        }
        null
    }

    suspend fun save(value: OfflineCredential) {
        store.edit { p ->
            p[username] = value.username
            p[userId] = value.userId
            p[displayName] = value.displayName
            p[salt] = Base64.encodeToString(value.salt, Base64.NO_WRAP)
            p[verifier] = Base64.encodeToString(value.verifier, Base64.NO_WRAP)
            p[companyIdKey] = value.companyId

            val mapObj = try {
                p[credentialsMapKey]?.let { JSONObject(it) } ?: JSONObject()
            } catch (e: Exception) {
                JSONObject()
            }
            val userObj = JSONObject().apply {
                put("username", value.username)
                put("userId", value.userId)
                put("displayName", value.displayName)
                put("salt", Base64.encodeToString(value.salt, Base64.NO_WRAP))
                put("verifier", Base64.encodeToString(value.verifier, Base64.NO_WRAP))
                put("companyId", value.companyId)
            }
            mapObj.put(value.username, userObj)
            p[credentialsMapKey] = mapObj.toString()
        }
    }
}
