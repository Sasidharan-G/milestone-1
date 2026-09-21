package com.kadaikutty.pos.core.auth

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * One salted verifier per mobile number, written only after the server accepted that password on
 * this device. It lets a known user sign in while the server cannot be reached; it is never a way
 * in for someone the server has not admitted here, and it is removed the moment the server rejects
 * the password or the account (see DefaultAuthRepository.loginOnline).
 */
class OfflineCredentialStore(private val store: DataStore<Preferences>) {
    private val credentialsMapKey = stringPreferencesKey("offline_credentials_map_json")

    // Single-credential keys from older builds. Read once so an upgraded device keeps offline
    // sign-in, and cleared on the next save or remove so they never outlive the map.
    private val legacyUsername = stringPreferencesKey("offline_username")
    private val legacyUserId = stringPreferencesKey("offline_user_id")
    private val legacyDisplayName = stringPreferencesKey("offline_display_name")
    private val legacySalt = stringPreferencesKey("offline_salt")
    private val legacyVerifier = stringPreferencesKey("offline_verifier")
    private val legacyCompanyId = stringPreferencesKey("offline_company_id")
    private val legacyKeys = listOf(legacyUsername, legacyUserId, legacyDisplayName, legacySalt, legacyVerifier, legacyCompanyId)

    fun getCredential(targetUsername: String): Flow<OfflineCredential?> = store.data.map { p ->
        readMap(p).optJSONObject(targetUsername)?.let { obj ->
            runCatching {
                OfflineCredential(
                    username = obj.getString("username"),
                    userId = obj.getString("userId"),
                    displayName = obj.getString("displayName"),
                    salt = Base64.decode(obj.getString("salt"), Base64.NO_WRAP),
                    verifier = Base64.decode(obj.getString("verifier"), Base64.NO_WRAP),
                    companyId = obj.optString("companyId", "company_main")
                )
            }.getOrNull()
        } ?: legacyCredential(p, targetUsername)
    }

    suspend fun save(value: OfflineCredential) {
        store.edit { p ->
            val map = readMap(p)
            map.put(value.username, JSONObject().apply {
                put("username", value.username)
                put("userId", value.userId)
                put("displayName", value.displayName)
                put("salt", Base64.encodeToString(value.salt, Base64.NO_WRAP))
                put("verifier", Base64.encodeToString(value.verifier, Base64.NO_WRAP))
                put("companyId", value.companyId)
            })
            p[credentialsMapKey] = map.toString()
            legacyKeys.forEach { p.remove(it) }
        }
    }

    /** Forgets one user's offline sign-in. Called when the server says the password or account is no longer valid. */
    suspend fun remove(targetUsername: String) {
        store.edit { p ->
            val map = readMap(p)
            map.remove(targetUsername)
            p[credentialsMapKey] = map.toString()
            if (p[legacyUsername] == targetUsername) legacyKeys.forEach { p.remove(it) }
        }
    }

    /** Same as [remove], for callers that only know the account id. */
    suspend fun removeByUserId(targetUserId: String) {
        store.edit { p ->
            val map = readMap(p)
            map.keys().asSequence().toList()
                .filter { map.optJSONObject(it)?.optString("userId") == targetUserId }
                .forEach { map.remove(it) }
            p[credentialsMapKey] = map.toString()
            if (p[legacyUserId] == targetUserId) legacyKeys.forEach { p.remove(it) }
        }
    }

    /** Moves a stored credential to the server's canonical user id, keeping the same password verifier. */
    suspend fun rekey(targetUsername: String, fromUserId: String, toUserId: String) {
        store.edit { p ->
            val map = readMap(p)
            val obj = map.optJSONObject(targetUsername) ?: return@edit
            if (obj.optString("userId") != fromUserId) return@edit
            obj.put("userId", toUserId)
            map.put(targetUsername, obj)
            p[credentialsMapKey] = map.toString()
        }
    }

    private fun readMap(p: Preferences): JSONObject =
        runCatching { p[credentialsMapKey]?.let(::JSONObject) }.getOrNull() ?: JSONObject()

    private fun legacyCredential(p: Preferences, targetUsername: String): OfflineCredential? {
        if (p[legacyUsername] != targetUsername) return null
        val s = p[legacySalt] ?: return null
        val v = p[legacyVerifier] ?: return null
        val id = p[legacyUserId] ?: return null
        return OfflineCredential(
            username = targetUsername,
            userId = id,
            displayName = p[legacyDisplayName] ?: "User",
            salt = Base64.decode(s, Base64.NO_WRAP),
            verifier = Base64.decode(v, Base64.NO_WRAP),
            companyId = p[legacyCompanyId] ?: "company_main"
        )
    }
}
