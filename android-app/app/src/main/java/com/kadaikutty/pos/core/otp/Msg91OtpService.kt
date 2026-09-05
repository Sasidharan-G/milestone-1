package com.kadaikutty.pos.core.otp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface OtpSendResult {
    data class Success(val requestId: String, val message: String) : OtpSendResult
    data class Failure(val error: String) : OtpSendResult
}

sealed interface OtpVerifyResult {
    data object Success : OtpVerifyResult
    data class Failure(val error: String) : OtpVerifyResult
}

@Singleton
class Msg91OtpService @Inject constructor() {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val widgetId = "3669646c326b363432353531"
    private val tokenAuth = "567585Tl1cnXSjt6a9abddfP1"

    private fun normalizePhone(phone: String): String {
        val digits = phone.replace("[^0-9]".toRegex(), "")
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }

    suspend fun sendOtp(mobileNumber: String): OtpSendResult = withContext(Dispatchers.IO) {
        try {
            val cleanPhone = normalizePhone(mobileNumber)
            val fullPhoneWithCountry = if (cleanPhone.length == 10) "91$cleanPhone" else cleanPhone

            // MSG91 Widget sendOtp endpoint
            val url = "https://control.msg91.com/api/v5/widget/sendOtp"
            val jsonBody = JSONObject().apply {
                put("widgetId", widgetId)
                put("tokenAuth", tokenAuth)
                put("identifier", fullPhoneWithCountry)
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                // Fallback attempt with direct sendotp v5 if widget returns non-200
                val fallbackResult = sendOtpFallback(fullPhoneWithCountry)
                return@withContext fallbackResult
            }

            val json = try { JSONObject(responseBody) } catch (_: Exception) { JSONObject() }
            val type = json.optString("type", "")
            val message = json.optString("message", "OTP sent successfully")
            
            // MSG91 returns reqId in root or in 'message' field when successful
            val parsedReqId = when {
                json.has("reqId") -> json.optString("reqId")
                json.has("requestId") -> json.optString("requestId")
                json.has("request_id") -> json.optString("request_id")
                json.has("data") && json.optJSONObject("data")?.has("reqId") == true -> json.optJSONObject("data")?.optString("reqId") ?: ""
                json.has("data") && json.optJSONObject("data")?.has("requestId") == true -> json.optJSONObject("data")?.optString("requestId") ?: ""
                json.has("data") && json.optString("data").isNotBlank() && json.optJSONObject("data") == null -> json.optString("data")
                message.length >= 12 && !message.contains(" ") -> message // MSG91 sometimes returns the reqId directly as message string
                else -> ""
            }

            android.util.Log.d("Msg91OtpService", "sendOtp response: type=$type, message=$message, parsedReqId=$parsedReqId")

            if (type.equals("error", ignoreCase = true)) {
                OtpSendResult.Failure(message)
            } else {
                OtpSendResult.Success(
                    requestId = parsedReqId.ifBlank { fullPhoneWithCountry },
                    message = message
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            OtpSendResult.Failure(e.localizedMessage ?: "Failed to send OTP via MSG91")
        }
    }

    private fun sendOtpFallback(fullPhoneWithCountry: String): OtpSendResult {
        return try {
            val url = "https://control.msg91.com/api/v5/otp?template_id=&mobile=$fullPhoneWithCountry&authkey=$tokenAuth"
            val request = Request.Builder()
                .url(url)
                .post("{}".toRequestBody("application/json".toMediaType()))
                .addHeader("authkey", tokenAuth)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            val json = try { JSONObject(responseBody) } catch (_: Exception) { JSONObject() }
            val type = json.optString("type", "")
            val msg = json.optString("message", "OTP sent")

            if (type.equals("error", ignoreCase = true)) {
                OtpSendResult.Failure(msg)
            } else {
                OtpSendResult.Success(requestId = fullPhoneWithCountry, message = msg)
            }
        } catch (e: Exception) {
            OtpSendResult.Failure(e.localizedMessage ?: "Network error during OTP delivery")
        }
    }

    suspend fun verifyOtp(mobileNumber: String, otp: String, requestId: String? = null): OtpVerifyResult = withContext(Dispatchers.IO) {
        try {
            val cleanPhone = normalizePhone(mobileNumber)
            val fullPhoneWithCountry = if (cleanPhone.length == 10) "91$cleanPhone" else cleanPhone
            val cleanOtp = otp.trim()
            val hasValidReqId = !requestId.isNullOrBlank() && requestId != fullPhoneWithCountry && requestId != cleanPhone

            android.util.Log.d("Msg91OtpService", "Verifying OTP for phone=$fullPhoneWithCountry, hasValidReqId=$hasValidReqId, reqId=$requestId")

            var widgetMessage = ""

            // 1. First attempt: MSG91 Widget verifyOtp endpoint if we have a valid reqId
            if (hasValidReqId) {
                val widgetUrl = "https://control.msg91.com/api/v5/widget/verifyOtp"
                val jsonBody = JSONObject().apply {
                    put("widgetId", widgetId)
                    put("tokenAuth", tokenAuth)
                    put("otp", cleanOtp)
                    put("identifier", fullPhoneWithCountry)
                    put("reqId", requestId)
                    put("requestId", requestId)
                }

                val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(widgetUrl)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .addHeader("authkey", tokenAuth)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                android.util.Log.d("Msg91OtpService", "verifyOtp widget response code=${response.code}, body=$responseBody")

                val json = try { JSONObject(responseBody) } catch (_: Exception) { JSONObject() }
                val type = json.optString("type", "")
                widgetMessage = json.optString("message", "")

                val isSuccess = response.isSuccessful && (
                    type.equals("success", ignoreCase = true) ||
                    widgetMessage.contains("success", ignoreCase = true) ||
                    widgetMessage.contains("verified", ignoreCase = true)
                )

                if (isSuccess) {
                    return@withContext OtpVerifyResult.Success
                }
            }

            // 2. Direct otp/verify GET endpoint fallback
            android.util.Log.d("Msg91OtpService", "Calling direct otp/verify API for phone=$fullPhoneWithCountry")
            val fallbackResult = verifyOtpFallback(fullPhoneWithCountry, cleanOtp, if (hasValidReqId) requestId else null)
            if (fallbackResult is OtpVerifyResult.Success) {
                return@withContext OtpVerifyResult.Success
            }

            val fallbackError = (fallbackResult as? OtpVerifyResult.Failure)?.error
            val finalError = when {
                !fallbackError.isNullOrBlank() && !fallbackError.contains("reqId", ignoreCase = true) -> fallbackError
                widgetMessage.isNotBlank() && !widgetMessage.contains("reqId", ignoreCase = true) -> widgetMessage
                else -> "Invalid or expired OTP. Please check the code and try again."
            }
            OtpVerifyResult.Failure(finalError)
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("Msg91OtpService", "Error verifying OTP", e)
            OtpVerifyResult.Failure(e.localizedMessage ?: "Failed to verify OTP")
        }
    }

    private fun verifyOtpFallback(fullPhoneWithCountry: String, otp: String, requestId: String? = null): OtpVerifyResult {
        return try {
            val reqParam = if (!requestId.isNullOrBlank()) "&reqId=$requestId" else ""
            val url = "https://control.msg91.com/api/v5/otp/verify?otp=${otp.trim()}&mobile=$fullPhoneWithCountry&authkey=$tokenAuth$reqParam"
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("authkey", tokenAuth)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            android.util.Log.d("Msg91OtpService", "verifyOtp fallback response: $responseBody")
            val json = try { JSONObject(responseBody) } catch (_: Exception) { JSONObject() }
            val type = json.optString("type", "")
            val msg = json.optString("message", "")

            if (type.equals("success", ignoreCase = true) || msg.contains("verified", ignoreCase = true) || msg.contains("success", ignoreCase = true)) {
                OtpVerifyResult.Success
            } else {
                OtpVerifyResult.Failure(if (msg.isNotBlank()) msg else "Invalid OTP")
            }
        } catch (e: Exception) {
            OtpVerifyResult.Failure(e.localizedMessage ?: "Verification error")
        }
    }

    suspend fun retryOtp(mobileNumber: String, retryChannel: String = "sms"): OtpSendResult = withContext(Dispatchers.IO) {
        try {
            val cleanPhone = normalizePhone(mobileNumber)
            val fullPhoneWithCountry = if (cleanPhone.length == 10) "91$cleanPhone" else cleanPhone

            val url = "https://control.msg91.com/api/v5/widget/retryOtp"
            val jsonBody = JSONObject().apply {
                put("widgetId", widgetId)
                put("tokenAuth", tokenAuth)
                put("identifier", fullPhoneWithCountry)
                put("retryChannel", retryChannel)
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            val json = try { JSONObject(responseBody) } catch (_: Exception) { JSONObject() }
            val type = json.optString("type", "")
            val message = json.optString("message", "OTP resent successfully")

            if (type.equals("error", ignoreCase = true)) {
                OtpSendResult.Failure(message)
            } else {
                OtpSendResult.Success(requestId = fullPhoneWithCountry, message = message)
            }
        } catch (e: Exception) {
            OtpSendResult.Failure(e.localizedMessage ?: "Failed to resend OTP")
        }
    }
}
