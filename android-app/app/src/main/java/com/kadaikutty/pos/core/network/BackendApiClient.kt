package com.kadaikutty.pos.core.network

import com.kadaikutty.pos.BuildConfig
import com.kadaikutty.pos.core.auth.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
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
class BackendApiClient @Inject constructor(private val sessionStore: SessionStore) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun pushSync(token: String, companyId: String, operations: JSONArray): JSONObject =
        request("POST", "sync/push", token, JSONObject().put("companyId", companyId).put("operations", operations), allowConflict = true)

    suspend fun pullSync(token: String, companyId: String, cursor: String, limit: Int = 200): JSONObject =
        request("GET", "sync/pull?companyId=$companyId&cursor=$cursor&limit=$limit", token)

    suspend fun currentLicense(token: String): JSONObject = request("GET", "license/current", token)

    suspend fun purgeCloudData(token: String): JSONObject = request("POST", "sync/purge", token, JSONObject())

    suspend fun uploadBackup(token: String, fileName: String, schemaVersion: Int, content: ByteArray): JSONObject {
        val checksum = java.security.MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
        val intent = request("POST", "backups/upload-intent", token, JSONObject()
            .put("fileName", fileName).put("sizeBytes", content.size).put("checksumSha256", checksum).put("schemaVersion", schemaVersion))
        putBinary(token, intent.getString("uploadUrl"), intent.optJSONObject("requiredHeaders"), content)
        return request("POST", "backups/${intent.getString("backupId")}/complete", token, JSONObject())
    }

    suspend fun downloadLatestBackup(token: String): ByteArray {
        val list = request("GET", "backups", token).getJSONArray("backups")
        require(list.length() > 0) { "No cloud backups are available" }
        val backupId = list.getJSONObject(0).getString("backupId")
        val intent = request("POST", "backups/$backupId/download-intent", token, JSONObject())
        return getBinary(token, intent.getString("downloadUrl"))
    }

    suspend fun registerSession(token: String, deviceId: String): JSONObject =
        request("POST", "sessions/register", token, JSONObject().put("deviceId", deviceId))

    suspend fun heartbeat(token: String, sessionId: String): JSONObject =
        request("POST", "sessions/heartbeat", token, JSONObject().put("sessionId", sessionId))

    suspend fun revokeCurrentSession(token: String, sessionId: String) {
        request("DELETE", "sessions/current", token, JSONObject().put("sessionId", sessionId))
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

    suspend fun changeMasterPin(mobileNumber: String, pin: String, otpProof: String): JSONObject =
        request("POST", "auth/master/pin", body = JSONObject()
            .put("mobileNumber", mobileNumber).put("pin", pin).put("resetToken", otpProof))

    suspend fun createStaff(token: String, mobileNumber: String, displayName: String, password: String, permissions: Collection<String>): JSONObject =
        request("POST", "staff", token, JSONObject().put("mobileNumber", mobileNumber).put("displayName", displayName)
            .put("password", password).put("permissions", JSONArray(permissions)))

    suspend fun updateStaff(token: String, userId: String, displayName: String?, password: String?, permissions: Collection<String>): JSONObject =
        request("PATCH", "staff/$userId", token, JSONObject().put("permissions", JSONArray(permissions)).also {
            displayName?.let { name -> it.put("displayName", name) }; password?.let { value -> it.put("password", value) }
        })

    suspend fun deactivateStaff(token: String, userId: String): JSONObject = request("DELETE", "staff/$userId", token, JSONObject())

    suspend fun request(method: String, path: String, token: String? = null, body: JSONObject? = null, allowConflict: Boolean = false): JSONObject = withContext(Dispatchers.IO) {
        val base = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        require(base.startsWith("https://") || (BuildConfig.DEBUG && base.startsWith("http://"))) { "Backend server is not configured" }
        val builder = Request.Builder().url("$base/api/v1/$path")
            .header("Accept", "application/json")
            .header("X-Request-Id", java.util.UUID.randomUUID().toString())
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
        if (!token.isNullOrBlank()) sessionStore.activeSession.first()?.sessionToken?.takeIf { it.isNotBlank() }?.let { builder.header("X-Session-Id", it) }
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete((body ?: JSONObject()).toString().toRequestBody(jsonType))
            else -> builder.method(method, (body ?: JSONObject()).toString().toRequestBody(jsonType))
        }
        client.newCall(builder.build()).execute().use { response ->
            val parsed = runCatching { JSONObject(response.body?.string().orEmpty().ifBlank { "{}" }) }.getOrElse { JSONObject() }
            if (!response.isSuccessful && !(allowConflict && response.code == 409 && parsed.has("results"))) {
                val error = parsed.optJSONObject("error")
                throw BackendApiException(
                    code = error?.optString("code")?.ifBlank { null } ?: "HTTP_${response.code}",
                    message = error?.optString("message")?.ifBlank { null } ?: "Backend request failed",
                    retryable = error?.optBoolean("retryable") ?: response.code in setOf(408, 429, 502, 503, 504),
                    statusCode = response.code,
                )
            }
            parsed
        }
    }

    private suspend fun putBinary(token: String, relativeUrl: String, requiredHeaders: JSONObject?, content: ByteArray) = withContext(Dispatchers.IO) {
        val url = resolveUrl(relativeUrl)
        val builder = Request.Builder().url(url)
        if (!relativeUrl.startsWith("http://") && !relativeUrl.startsWith("https://")) {
            builder.header("Authorization", "Bearer $token")
            sessionStore.activeSession.first()?.sessionToken?.takeIf { it.isNotBlank() }?.let { builder.header("X-Session-Id", it) }
        }
        requiredHeaders?.keys()?.forEach { name -> builder.header(name, requiredHeaders.getString(name)) }
        val request = builder.put(content.toRequestBody("application/octet-stream".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw parseBinaryError(response.code, response.body?.string())
        }
    }

    private suspend fun getBinary(token: String, relativeUrl: String): ByteArray = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(resolveUrl(relativeUrl))
        if (!relativeUrl.startsWith("http://") && !relativeUrl.startsWith("https://")) {
            builder.header("Authorization", "Bearer $token")
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
