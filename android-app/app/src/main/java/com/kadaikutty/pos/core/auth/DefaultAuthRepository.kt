package com.kadaikutty.pos.core.auth

import android.app.Activity
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.database.TenantDatabaseManager
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
    private val tenantDatabaseManager: TenantDatabaseManager?,
    private val backend: BackendApiClient,
    private val sessionSecurityManager: SessionSecurityManager,
    private val fallbackDatabase: BillingDatabase? = null,
    private val appPreferences: com.kadaikutty.pos.core.preferences.AppPreferences? = null,
    private val syncScheduler: com.kadaikutty.pos.core.sync.SyncScheduler? = null,
) : AuthRepository {
    private val scope = CoroutineScope(Dispatchers.IO)

    constructor(
        sessions: SessionStore,
        credentials: OfflineCredentialStore,
        verifier: OfflineCredentialVerifier,
        database: BillingDatabase,
        backend: BackendApiClient,
        sessionSecurityManager: SessionSecurityManager,
    ) : this(sessions, credentials, verifier, null, backend, sessionSecurityManager, database, null, null)

    constructor(
        sessions: SessionStore,
        credentials: OfflineCredentialStore,
        verifier: OfflineCredentialVerifier,
        tenantDatabaseManager: TenantDatabaseManager,
        backend: BackendApiClient,
        sessionSecurityManager: SessionSecurityManager,
        appPreferences: com.kadaikutty.pos.core.preferences.AppPreferences? = null,
        syncScheduler: com.kadaikutty.pos.core.sync.SyncScheduler? = null,
    ) : this(sessions, credentials, verifier, tenantDatabaseManager, backend, sessionSecurityManager, null, appPreferences, syncScheduler)

    private fun getDb(companyId: String? = null): BillingDatabase =
        tenantDatabaseManager?.getDatabase(companyId) ?: fallbackDatabase ?: error("No database available")

    override suspend fun loginOnline(username: String, password: CharArray): LoginResult {
        val previousCompany = tenantDatabaseManager?.getActiveCompany()
        return try {
            sessionSecurityManager.resetSessionTermination()
            val session = persistOnlineSession(backend.login(normalizePhone(username), password.concatToString()), password)
            if (session.permissions.contains(Permission.ACCOUNT_INACTIVE)) {
                sessions.clear()
                return LoginResult.Failure(DEACTIVATED_MESSAGE)
            }
            tenantDatabaseManager?.setActiveCompany(session.companyId)
            val targetDb = getDb(session.companyId)

            // Every authenticated call requires the device session issued here
            try {
                val remoteSessionId = sessionSecurityManager.registerSession(session.userId, session.companyId, session.role, "")
                val sessionWithToken = session.copy(sessionToken = remoteSessionId)
                sessions.save(sessionWithToken)
                runCatching { targetDb.syncQueueDao().migrateTenantData("company_main", session.companyId) }
                syncScheduler?.requestPull()
                LoginResult.Success(sessionWithToken)
            } catch (e: Exception) {
                sessions.clear()
                // The active company was switched above; put it back so a failed sign-in never
                // leaves the app pointed at this tenant's database.
                previousCompany?.let { tenantDatabaseManager?.setActiveCompany(it) }
                LoginResult.Failure("Signed in, but could not establish a device session. Check your connection and try again.")
            }
        } catch (e: Exception) { LoginResult.Failure(e.message ?: "Unable to sign in") }
    }

    override suspend fun loginOffline(username: String, password: CharArray): LoginResult {
        sessionSecurityManager.resetSessionTermination()
        val normalized = normalizePhone(username)
        val credential = credentials.getCredential(normalized).first()
            ?: credentials.credential.first()
            ?: return LoginResult.Failure("No verified offline sign-in is available on this device")
        if (credential.username != normalized || !verifier.matches(credential, password)) return LoginResult.Failure("Invalid mobile number or password")
        val companyId = credential.companyId
        tenantDatabaseManager?.setActiveCompany(companyId)
        val targetDb = getDb(companyId)
        val local = targetDb.userDao().getUserById(credential.userId) ?: return LoginResult.Failure("Offline account data is unavailable")
        if (local.toPermissionsSet().contains(Permission.ACCOUNT_INACTIVE)) return LoginResult.Failure(DEACTIVATED_MESSAGE)
        val offlineValidUntil = if (local.offlineValidUntil == 0L) System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000 else local.offlineValidUntil
        if (System.currentTimeMillis() >= offlineValidUntil) return LoginResult.Failure("Offline sign-in expired. Connect to the internet.")
        val existingSession = sessions.activeSession.first()
        val preservedAccess = if (existingSession?.userId == local.id) existingSession.accessToken else null
        val preservedRefresh = if (existingSession?.userId == local.id) existingSession.refreshToken else null
        val preservedSessionToken = if (existingSession?.userId == local.id) existingSession.sessionToken else null
        val session = Session(
            userId = local.id,
            displayName = local.displayName,
            permissions = local.toPermissionsSet(),
            accessToken = preservedAccess,
            refreshToken = preservedRefresh,
            companyId = local.companyId,
            role = local.role,
            sessionToken = preservedSessionToken
        )
        sessions.save(session)
        return LoginResult.Success(session)
    }

    override suspend fun logout() {
        sessionSecurityManager.resetSessionTermination()
        // clearSession reads the tokens out of the store, so revoking has to happen before the
        // local clear below - otherwise the device session stays alive on the backend.
        sessionSecurityManager.clearSession()
        sessions.clear()
        appPreferences?.clearShopDetails()
        tenantDatabaseManager?.setActiveCompany("company_main")
    }
    override suspend fun registerMerchant(mobileNumber: String, password: CharArray, ownerName: String, businessName: String) = RegisterResult.Failure("Mobile OTP verification is required before registration")
    override fun sendRegistrationOtp(mobileNumber: String, activity: Activity, onCodeSent: (String) -> Unit, onVerificationFailed: (String) -> Unit) = sendOtp(mobileNumber, onCodeSent, onVerificationFailed)

    override suspend fun verifyRegistrationOtpAndRegister(verificationId: String, otp: String, mobileNumber: String, password: CharArray, ownerName: String, businessName: String, isCloudTier: Boolean): RegisterResult = try {
        val phone = normalizePhone(mobileNumber)
        val proof = backend.verifyOtp(phone, otp, verificationId).getString("resetToken")
        val companyId = backend.registerMerchant(phone, ownerName, businessName, password.concatToString(), proof, isCloudTier).getString("companyId")
        val loginRes = loginOnline(phone, password)
        if (loginRes is LoginResult.Success) {
            RegisterResult.Success(companyId)
        } else if (loginRes is LoginResult.Failure) {
            RegisterResult.Failure("Registration complete, but sign-in failed: ${loginRes.message}")
        } else {
            RegisterResult.Success(companyId)
        }
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
        val role = user.optString("role", "ADMIN")
        val parsedPerms = permissions(user.optJSONArray("permissions"))
        val perms = if (parsedPerms.contains(Permission.ACCOUNT_INACTIVE)) {
            // A deactivated account must not be promoted to a full permission set by role.
            parsedPerms
        } else if (role == "ADMIN" || role == "SUPER_ADMIN" || parsedPerms.isEmpty()) {
            Permission.ALL_ACTIVE
        } else {
            parsedPerms
        }
        val tokensObj = response.optJSONObject("tokens")
        val accessToken = tokensObj?.optString("accessToken").orEmpty()
        val refreshToken = tokensObj?.optString("refreshToken")?.takeIf { it.isNotBlank() }
        val session = Session(
            userId = user.getString("userId"),
            displayName = user.optString("displayName", "User"),
            permissions = perms,
            accessToken = accessToken,
            refreshToken = refreshToken,
            companyId = user.getString("companyId"),
            role = role
        )
        sessions.save(session)

        val licenseObj = response.optJSONObject("license")
        val bName = licenseObj?.optString("businessName")?.ifBlank { null } ?: user.optString("businessName").ifBlank { null }
        val oName = licenseObj?.optString("ownerName")?.ifBlank { null } ?: user.optString("displayName").ifBlank { null }
        if (!bName.isNullOrBlank()) {
            appPreferences?.saveShopName(bName)
        }
        if (!oName.isNullOrBlank()) {
            appPreferences?.saveOwnerName(oName)
        }

        val targetDb = getDb(session.companyId)
        if (!bName.isNullOrBlank()) {
            val existingLic = targetDb.licenseDao().getLicense(session.companyId)
            val licEntity = com.kadaikutty.pos.core.license.LicenseEntity(
                companyId = session.companyId,
                businessName = bName,
                ownerName = oName.orEmpty(),
                licenseStatus = licenseObj?.optString("status") ?: existingLic?.licenseStatus ?: "ACTIVE_PAID"
            )
            targetDb.licenseDao().saveLicense(licEntity)
        }

        val local = UserEntity(
            session.userId,
            normalizePhone(user.optString("phone")),
            session.displayName,
            "", "",
            perms.joinToString(",") { it.name },
            session.companyId,
            session.role,
            System.currentTimeMillis(),
            System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
        )
        targetDb.userDao().insertUser(local)
        credentials.save(verifier.create(local.username, password, session.userId, session.displayName, session.companyId))
        return session
    }
    private companion object {
        const val DEACTIVATED_MESSAGE = "This account has been deactivated. Contact your shop owner."
    }

    private fun permissions(values: JSONArray?): Set<Permission> = buildSet { values?.let { a -> for (i in 0 until a.length()) runCatching { Permission.valueOf(a.getString(i)) }.getOrNull()?.let(::add) } }
    private fun normalizePhone(value: String) = value.filter(Char::isDigit).takeLast(10)
}
