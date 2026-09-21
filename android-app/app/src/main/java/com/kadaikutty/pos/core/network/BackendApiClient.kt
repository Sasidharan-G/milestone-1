package com.kadaikutty.pos.core.network

import com.kadaikutty.pos.BuildConfig
import com.kadaikutty.pos.core.auth.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class BackendApiException(
    val code: String,
    override val message: String,
    val retryable: Boolean,
    val statusCode: Int,
) : IOException(message)

@Singleton
class BackendApiClient @Inject constructor(
    private val sessionStore: SessionStore,
    private val appPreferences: com.kadaikutty.pos.core.preferences.AppPreferences
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun pushSync(token: String, companyId: String, operations: JSONArray, sessionId: String? = null, epoch: Long = 0L): JSONObject =
        request("POST", "sync/push", token, JSONObject().put("companyId", companyId).put("epoch", epoch).put("operations", operations), allowConflict = true, sessionId = sessionId)

    suspend fun pullSync(token: String, companyId: String, cursor: String, limit: Int = 200, sessionId: String? = null): JSONObject =
        request("GET", "sync/pull?companyId=$companyId&cursor=$cursor&limit=$limit", token, sessionId = sessionId)

    suspend fun currentLicense(token: String): JSONObject = request("GET", "license/current", token)

    suspend fun purgeCloudData(token: String): JSONObject = request("POST", "sync/purge", token, JSONObject())

    suspend fun uploadBackup(token: String, fileName: String, schemaVersion: Int, content: ByteArray): JSONObject {
        val checksum = java.security.MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
        val intent = request("POST", "backups/upload-intent", token, JSONObject()
            .put("fileName", fileName).put("sizeBytes", content.size).put("checksumSha256", checksum).put("schemaVersion", schemaVersion))
        putBinary(token, intent.getString("uploadUrl"), intent.optJSONObject("requiredHeaders"), content)
        return request("POST", "backups/${intent.getString("backupId")}/complete", token, JSONObject())
    }

    suspend fun uploadBackupFile(token: String, file: java.io.File, fileName: String, schemaVersion: Int): JSONObject {
        val checksum = java.security.MessageDigest.getInstance("SHA-256").let { digest ->
            file.inputStream().use { input ->
                val buf = ByteArray(8192)
                var read: Int
                while (input.read(buf).also { read = it } > 0) { digest.update(buf, 0, read) }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        val intent = request("POST", "backups/upload-intent", token, JSONObject()
            .put("fileName", fileName).put("sizeBytes", file.length()).put("checksumSha256", checksum).put("schemaVersion", schemaVersion))
        putFileBinary(token, intent.getString("uploadUrl"), intent.optJSONObject("requiredHeaders"), file)
        return request("POST", "backups/${intent.getString("backupId")}/complete", token, JSONObject())
    }

    suspend fun listBackups(token: String): JSONArray =
        request("GET", "backups", token).getJSONArray("backups")

    suspend fun downloadLatestBackup(token: String): ByteArray {
        val list = listBackups(token)
        require(list.length() > 0) { "No cloud backups are available" }
        val backupId = list.getJSONObject(0).getString("backupId")
        val intent = request("POST", "backups/$backupId/download-intent", token, JSONObject())
        return getBinary(token, intent.getString("downloadUrl"))
    }

    suspend fun deleteBackup(token: String, backupId: String) {
        request("DELETE", "backups/$backupId", token)
    }

    suspend fun registerSession(token: String, deviceId: String, deviceName: String? = null): JSONObject =
        request("POST", "sessions/register", token, JSONObject().put("deviceId", deviceId).also { obj ->
            deviceName?.let { obj.put("deviceName", it) }
        })

    // Every authenticated call now requires X-Session-Id; heartbeat/revoke pass it explicitly
    // (rather than relying on the tenant SessionStore) so this also works for the master session,
    // which is held only in memory (MasterAuthSession), not in the tenant DataStore.
    suspend fun heartbeat(token: String, sessionId: String): JSONObject =
        request("POST", "sessions/heartbeat", token, JSONObject().put("sessionId", sessionId), sessionId = sessionId)

    suspend fun revokeCurrentSession(token: String, sessionId: String) {
        request("DELETE", "sessions/current", token, JSONObject(), sessionId = sessionId)
    }

    suspend fun sendOtp(mobileNumber: String): JSONObject =
        request("POST", "otp/send", body = JSONObject().put("mobileNumber", mobileNumber))

    suspend fun verifyOtp(mobileNumber: String, otp: String, requestId: String): JSONObject =
        request("POST", "otp/verify", body = JSONObject()
            .put("mobileNumber", mobileNumber).put("otp", otp).put("requestId", requestId))

    suspend fun login(username: String, password: String): JSONObject =
        request("POST", "auth/login", body = JSONObject().put("username", username).put("password", password))

    suspend fun loginMaster(mobileNumber: String, pin: String): JSONObject =
        request("POST", "auth/master/login", body = JSONObject().put("mobileNumber", mobileNumber).put("pin", pin))

    suspend fun registerMerchant(mobileNumber: String, ownerName: String, businessName: String, password: String, otpProof: String): JSONObject =
        request("POST", "auth/register", body = JSONObject()
            .put("mobileNumber", mobileNumber).put("ownerName", ownerName).put("businessName", businessName)
            .put("password", password).put("resetToken", otpProof))

    suspend fun resetPassword(mobileNumber: String, password: String, otpProof: String): JSONObject =
        request("POST", "auth/password/reset", body = JSONObject()
            .put("mobileNumber", mobileNumber).put("password", password).put("resetToken", otpProof))

    suspend fun changeMasterPin(mobileNumber: String, pin: String, otpProof: String, newMobileNumber: String? = null): JSONObject =
        request("POST", "auth/master/pin", body = JSONObject()
            .put("mobileNumber", mobileNumber).put("pin", pin).put("resetToken", otpProof).also { obj ->
                newMobileNumber?.let { obj.put("newMobileNumber", it) }
            })

    suspend fun createStaff(token: String, mobileNumber: String, displayName: String, password: String, permissions: Collection<String>): JSONObject =
        request("POST", "staff", token, JSONObject().put("mobileNumber", mobileNumber).put("displayName", displayName)
            .put("password", password).put("permissions", JSONArray(permissions)))

    suspend fun updateStaff(token: String, userId: String, displayName: String?, password: String?, permissions: Collection<String>): JSONObject =
        request("PATCH", "staff/$userId", token, JSONObject().put("permissions", JSONArray(permissions)).also {
            displayName?.let { name -> it.put("displayName", name) }; password?.let { value -> it.put("password", value) }
        })

    suspend fun deactivateStaff(token: String, userId: String): JSONObject = request("DELETE", "staff/$userId", token, JSONObject())

    suspend fun listStaff(token: String): JSONArray = request("GET", "staff", token).getJSONArray("staff")

    suspend fun updateShopProfile(token: String, shopName: String, ownerName: String, gstNumber: String, address: String, phone: String, email: String): JSONObject =
        request("PATCH", "account/shop-profile", token, JSONObject()
            .put("shopName", shopName).put("ownerName", ownerName).put("gstNumber", gstNumber)
            .put("address", address).put("phone", phone).put("email", email))

    suspend fun refreshAuthToken(refreshToken: String): JSONObject =
        request("POST", "auth/refresh", body = JSONObject().put("refreshToken", refreshToken))

    private val authMutex = kotlinx.coroutines.sync.Mutex()

    suspend fun autoRecoverSession(forceRefresh: Boolean = false): Pair<String, String>? = authMutex.withLock {
        val currentSession = sessionStore.activeSession.first() ?: return@withLock null
        var activeAccessToken = currentSession.accessToken?.trim()?.takeIf { it.isNotBlank() }
        var activeRefreshToken = currentSession.refreshToken?.trim()?.takeIf { it.isNotBlank() }
        var activeSessionId = currentSession.sessionToken?.trim()?.takeIf { it.isNotBlank() }

        if ((activeAccessToken.isNullOrBlank() || forceRefresh) && !activeRefreshToken.isNullOrBlank()) {
            val attempt = runCatching {
                val refreshResp = refreshAuthToken(activeRefreshToken)
                val tokens = refreshResp.optJSONObject("tokens")
                val newAccess = tokens?.optString("accessToken")?.trim()?.takeIf { it.isNotBlank() }
                val newRefresh = tokens?.optString("refreshToken")?.trim()?.takeIf { it.isNotBlank() } ?: activeRefreshToken
                if (newAccess != null) newAccess to newRefresh else null
            }
            // The server looked at the refresh token and refused it (expired, revoked,
            // account gone). The stored access token is then dead too. Handing it back made every
            // caller retry with it forever: the app looked signed in while nothing synced. Null
            // makes callers end the session so the user signs in again. A network failure is not a
            // refusal and keeps the tokens, so going offline never signs anyone out.
            val refused = attempt.exceptionOrNull().let { it is BackendApiException && it.statusCode in 400..499 && it.statusCode != 408 && it.statusCode != 429 }
            if (refused) return@withLock null
            val refreshResult = attempt.getOrNull()

            if (refreshResult != null) {
                activeAccessToken = refreshResult.first
                activeRefreshToken = refreshResult.second
                sessionStore.updateTokens(accessToken = activeAccessToken, refreshToken = activeRefreshToken)
            }
        }

        if (activeAccessToken.isNullOrBlank()) return@withLock null

        if (activeSessionId.isNullOrBlank()) {
            activeSessionId = sessionStore.activeSession.first()?.sessionToken?.trim()?.takeIf { it.isNotBlank() }
        }

        if (activeSessionId.isNullOrBlank()) {
            val deviceId = appPreferences.getOrCreateInstallationDeviceId()
            val model = appPreferences.getDeviceModelName()
            val regResult = runCatching {
                val regResp = registerSession(activeAccessToken, deviceId, model)
                val newSessionId = regResp.optJSONObject("session")?.optString("sessionId")?.trim()?.takeIf { it.isNotBlank() }
                if (newSessionId != null) {
                    sessionStore.updateSessionToken(newSessionId)
                    newSessionId
                } else null
            }.getOrNull()

            if (regResult != null) {
                activeSessionId = regResult
            }
        }

        Pair(activeAccessToken, activeSessionId.orEmpty())
    }

    /**
     * Every authenticated endpoint requires X-Session-Id. By default it is pulled from the tenant
     * SessionStore (the normal admin/staff flow); pass [sessionId] explicitly for a principal that
     * is not in that store, such as the in-memory master session (see [MasterAuthSession]).
     */
    suspend fun request(method: String, path: String, token: String? = null, body: JSONObject? = null, allowConflict: Boolean = false, sessionId: String? = null): JSONObject = withContext(Dispatchers.IO) {
        val base = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        require(base.startsWith("https://") || (BuildConfig.DEBUG && base.startsWith("http://"))) { "Backend server is not configured" }

        val isPublicAuthPath = path == "auth/refresh" || path == "auth/login" || path == "auth/master/login" ||
                path == "otp/send" || path == "otp/verify" || path == "auth/register" || path == "auth/password/reset"

        var currentToken = token?.trim()?.takeIf { it.isNotBlank() }
        var currentSessionId = sessionId ?: sessionStore.activeSession.first()?.sessionToken?.trim()?.takeIf { it.isNotBlank() }

        if (!isPublicAuthPath && path != "sessions/register") {
            if (currentToken.isNullOrBlank() || currentSessionId.isNullOrBlank()) {
                val recovered = autoRecoverSession(forceRefresh = false)
                if (recovered != null) {
                    if (currentToken.isNullOrBlank()) currentToken = recovered.first
                    if (currentSessionId.isNullOrBlank()) currentSessionId = recovered.second
                }
            }
        }

        fun buildRequest(tok: String?, sessId: String?): Request {
            val builder = Request.Builder().url("$base/api/v1/$path")
                .header("Accept", "application/json")
                .header("X-Request-Id", java.util.UUID.randomUUID().toString())
            if (!tok.isNullOrBlank()) builder.header("Authorization", "Bearer $tok")
            if (!sessId.isNullOrBlank() && !isPublicAuthPath && path != "sessions/register") {
                builder.header("X-Session-Id", sessId)
            }
            when (method) {
                "GET" -> builder.get()
                "DELETE" -> builder.delete((body ?: JSONObject()).toString().toRequestBody(jsonType))
                else -> builder.method(method, (body ?: JSONObject()).toString().toRequestBody(jsonType))
            }
            return builder.build()
        }

        var response = client.newCall(buildRequest(currentToken, currentSessionId)).execute()
        var bodyStr = response.body?.string().orEmpty()
        var parsed = runCatching { JSONObject(bodyStr.ifBlank { "{}" }) }.getOrElse { JSONObject() }

        // If backend returns 401 on an authenticated call, automatically recover token & session and retry once
        if (response.code == 401 && !isPublicAuthPath && path != "sessions/register") {
            response.close()
            val errorObj = parsed.optJSONObject("error")
            val errorCode = errorObj?.optString("code").orEmpty()
            val errorMessage = errorObj?.optString("message").orEmpty()

            if (errorCode == "SESSION_REVOKED" || errorCode == "SESSION_REQUIRED" || errorMessage.contains("signed out", ignoreCase = true) || errorMessage.contains("another device", ignoreCase = true)) {
                sessionStore.clear()
                throw BackendApiException(
                    code = "SESSION_REVOKED",
                    message = if (errorMessage.isNotBlank()) errorMessage else "Session signed out because account logged in on another device.",
                    retryable = false,
                    statusCode = 401
                )
            }

            val recovered = autoRecoverSession(forceRefresh = true)
            if (recovered != null) {
                val (freshToken, freshSessionId) = recovered
                val retryResp = client.newCall(buildRequest(freshToken, freshSessionId)).execute()
                val retryBody = retryResp.body?.string().orEmpty()
                response = retryResp
                parsed = runCatching { JSONObject(retryBody.ifBlank { "{}" }) }.getOrElse { JSONObject() }
            } else {
                sessionStore.clear()
            }
        }

        // Every response carries the server clock; offline edits are timestamped against it.
        response.headers.getDate("Date")?.let { com.kadaikutty.pos.core.common.TrustedClock.onServerTime(it.time) }
        response.use {
            if (!response.isSuccessful && !(allowConflict && response.code == 409 && parsed.has("results"))) {
                val error = parsed.optJSONObject("error")
                throw BackendApiException(
                    code = error?.optString("code")?.ifBlank { null } ?: "HTTP_${response.code}",
                    message = error?.optString("message")?.ifBlank { null } ?: "Backend request failed",
                    retryable = error?.optBoolean("retryable") ?: response.code in setOf(408, 429, 502, 503, 504),
                    statusCode = response.code,
                )
            }
        }
        parsed
    }

    private suspend fun putBinary(token: String, relativeUrl: String, requiredHeaders: JSONObject?, content: ByteArray) = withContext(Dispatchers.IO) {
        val url = resolveUrl(relativeUrl)
        val builder = Request.Builder().url(url)
        if (!relativeUrl.startsWith("http://") && !relativeUrl.startsWith("https://")) {
            builder.header("Authorization", "Bearer ${token.trim()}")
            sessionStore.activeSession.first()?.sessionToken?.takeIf { it.isNotBlank() }?.let { builder.header("X-Session-Id", it) }
        }
        requiredHeaders?.keys()?.forEach { name -> builder.header(name, requiredHeaders.getString(name)) }
        val request = builder.put(content.toRequestBody("application/octet-stream".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw parseBinaryError(response.code, response.body?.string())
        }
    }

    private suspend fun putFileBinary(token: String, relativeUrl: String, requiredHeaders: JSONObject?, file: java.io.File) = withContext(Dispatchers.IO) {
        val url = resolveUrl(relativeUrl)
        val builder = Request.Builder().url(url)
        if (!relativeUrl.startsWith("http://") && !relativeUrl.startsWith("https://")) {
            builder.header("Authorization", "Bearer ${token.trim()}")
            sessionStore.activeSession.first()?.sessionToken?.takeIf { it.isNotBlank() }?.let { builder.header("X-Session-Id", it) }
        }
        requiredHeaders?.keys()?.forEach { name -> builder.header(name, requiredHeaders.getString(name)) }
        val requestBody = object : okhttp3.RequestBody() {
            override fun contentType(): okhttp3.MediaType? = "application/octet-stream".toMediaType()
            override fun contentLength(): Long = file.length()
            override fun writeTo(sink: okio.BufferedSink) {
                file.inputStream().use { input ->
                    val buf = ByteArray(8192)
                    var read: Int
                    while (input.read(buf).also { read = it } > 0) {
                        sink.write(buf, 0, read)
                    }
                }
            }
        }
        val request = builder.put(requestBody).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw parseBinaryError(response.code, response.body?.string())
        }
    }

    private suspend fun getBinary(token: String, relativeUrl: String): ByteArray = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(resolveUrl(relativeUrl))
        if (!relativeUrl.startsWith("http://") && !relativeUrl.startsWith("https://")) {
            builder.header("Authorization", "Bearer ${token.trim()}")
            sessionStore.activeSession.first()?.sessionToken?.takeIf { it.isNotBlank() }?.let { builder.header("X-Session-Id", it) }
        }
        val request = builder.get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw parseBinaryError(response.code, response.body?.string())
            response.body?.bytes() ?: throw IOException("Backup download was empty")
        }
    }

    private fun resolveUrl(relativeUrl: String): String = if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) relativeUrl
        else "${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/${relativeUrl.trimStart('/')}"

    private fun parseBinaryError(status: Int, body: String?): BackendApiException {
        val error = runCatching { JSONObject(body.orEmpty()).optJSONObject("error") }.getOrNull()
        return BackendApiException(error?.optString("code") ?: "HTTP_$status", error?.optString("message") ?: "Backup transfer failed", error?.optBoolean("retryable") ?: status in setOf(408, 429, 502, 503, 504), status)
    }
}
