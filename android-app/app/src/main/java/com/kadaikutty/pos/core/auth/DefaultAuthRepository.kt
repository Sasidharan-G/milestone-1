package com.kadaikutty.pos.core.auth

import android.app.Activity
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.network.BackendApiClient
import com.kadaikutty.pos.core.security.Permission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** AWS-only identity implementation. The app contacts only the POS backend. */
class DefaultAuthRepository(
    private val sessions: SessionStore,
    private val credentials: OfflineCredentialStore,
    private val verifier: OfflineCredentialVerifier,
    private val database: BillingDatabase,
    private val backend: BackendApiClient,
    private val sessionSecurityManager: SessionSecurityManager,
) : AuthRepository {
    private val scope = CoroutineScope(Dispatchers.IO)

    override suspend fun loginOnline(username: String, password: CharArray): LoginResult = try {
        val session = persistOnlineSession(backend.login(normalizePhone(username), password.concatToString()), password)
        runCatching { sessionSecurityManager.registerSession(session.userId, session.companyId, session.role, "") }
        LoginResult.Success(session)
    } catch (e: Exception) { LoginResult.Failure(e.message ?: "Unable to sign in") }

    override suspend fun loginOffline(username: String, password: CharArray): LoginResult {
        val credential = credentials.credential.first() ?: return LoginResult.Failure("No verified offline sign-in is available on this device")
        if (credential.username != normalizePhone(username) || !verifier.matches(credential, password)) return LoginResult.Failure("Invalid mobile number or password")
        val local = database.userDao().getUserById(credential.userId) ?: return LoginResult.Failure("Offline account data is unavailable")
        if (System.currentTimeMillis() >= local.offlineValidUntil) return LoginResult.Failure("Offline sign-in expired. Connect to the internet.")
        return LoginResult.Success(Session(local.id, local.displayName, local.toPermissionsSet(), companyId = local.companyId, role = local.role).also { sessions.save(it) })
    }

    override suspend fun logout() { sessions.clear() }
    override suspend fun registerMerchant(mobileNumber: String, password: CharArray, ownerName: String, businessName: String) = RegisterResult.Failure("Mobile OTP verification is required before registration")
    override fun sendRegistrationOtp(mobileNumber: String, activity: Activity, onCodeSent: (String) -> Unit, onVerificationFailed: (String) -> Unit) = sendOtp(mobileNumber, onCodeSent, onVerificationFailed)

    override suspend fun verifyRegistrationOtpAndRegister(verificationId: String, otp: String, mobileNumber: String, password: CharArray, ownerName: String, businessName: String): RegisterResult = try {
        val phone = normalizePhone(mobileNumber)
        val proof = backend.verifyOtp(phone, otp, verificationId).getString("resetToken")
        RegisterResult.Success(backend.registerMerchant(phone, ownerName, businessName, password.concatToString(), proof).getString("companyId"))
    } catch (e: Exception) { RegisterResult.Failure(e.message ?: "Registration failed") }

    override fun sendPasswordResetOtp(mobileNumber: String, activity: Activity, onCodeSent: (String) -> Unit, onVerificationFailed: (String) -> Unit) = sendOtp(mobileNumber, onCodeSent, onVerificationFailed)
    override suspend fun verifyOtpAndResetPassword(verificationId: String, otp: String, newPassword: CharArray, mobileNumber: String): RecoveryResult = try {
        val phone = normalizePhone(mobileNumber)
        backend.resetPassword(phone, newPassword.concatToString(), backend.verifyOtp(phone, otp, verificationId).getString("resetToken"))
        RecoveryResult.Success
    } catch (e: Exception) { RecoveryResult.Failure(e.message ?: "Password reset failed") }
    override suspend fun changeMasterPin(verificationId: String, otp: String, newPin: CharArray, mobileNumber: String): RecoveryResult = try {
        val phone = normalizePhone(mobileNumber)
        backend.changeMasterPin(phone, newPin.concatToString(), backend.verifyOtp(phone, otp, verificationId).getString("resetToken"))
        RecoveryResult.Success
    } catch (e: Exception) { RecoveryResult.Failure(e.message ?: "Master PIN update failed") }

    private fun sendOtp(mobile: String, ok: (String) -> Unit, failed: (String) -> Unit) {
        scope.launch { runCatching { backend.sendOtp(normalizePhone(mobile)).getString("requestId") }.onSuccess(ok).onFailure { failed(it.message ?: "Unable to send OTP") } }
    }

    private suspend fun persistOnlineSession(response: JSONObject, password: CharArray): Session {
        val user = response.getJSONObject("user")
        val perms = permissions(user.optJSONArray("permissions"))
        val session = Session(user.getString("userId"), user.optString("displayName", "User"), perms, response.getJSONObject("tokens").getString("accessToken"), user.getString("companyId"), user.optString("role", "CASHIER"))
        sessions.save(session)
        val local = UserEntity(session.userId, normalizePhone(user.optString("phone")), session.displayName, "", "", perms.joinToString(",") { it.name }, session.companyId, session.role, System.currentTimeMillis(), System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)
        database.userDao().insertUser(local)
        credentials.save(verifier.create(local.username, password, session.userId, session.displayName))
        return session
    }
    private fun permissions(values: JSONArray?): Set<Permission> = buildSet { values?.let { a -> for (i in 0 until a.length()) runCatching { Permission.valueOf(a.getString(i)) }.getOrNull()?.let(::add) } }
    private fun normalizePhone(value: String) = value.filter(Char::isDigit).takeLast(10)
}
