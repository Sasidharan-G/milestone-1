package com.kadaikutty.pos.core.auth

import android.app.Activity
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.database.TenantDatabaseManager
import com.kadaikutty.pos.core.network.BackendApiClient
import com.kadaikutty.pos.core.security.Permission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
            val response = backend.login(normalizePhone(username), password.concatToString())
            // The server accepted the password. What follows is one sign-in: the shop's records, the
            // offline credential and the device session belong together, so it must not be abandoned
            // half-way. The sign-in screen used to leave (and cancel this scope) the moment the session
            // appeared in the store, which cut this short before the credential was saved: the device
            // signed in fine and could then never sign in offline.
            withContext(NonCancellable) { completeSignIn(response, username, password, previousCompany) }
        } catch (e: Exception) {
            if (isServerRejection(e)) {
                // The server was reached and said no: wrong password, or the account was disabled
                // or removed. Whatever this device remembered for that number is now stale, and
                // keeping it would let the old password in the next time the network drops.
                credentials.remove(normalizePhone(username))
                LoginResult.Failure(e.message ?: "Invalid mobile number or password")
            } else {
                LoginResult.Failure(e.message ?: "Unable to sign in", canTryOffline = true)
            }
        }
    }

    private suspend fun completeSignIn(response: JSONObject, username: String, password: CharArray, previousCompany: String?): LoginResult {
        val session = persistOnlineSession(response, password)
        if (session.permissions.contains(Permission.ACCOUNT_INACTIVE)) {
            credentials.remove(normalizePhone(username))
            return LoginResult.Failure(DEACTIVATED_MESSAGE)
        }
        tenantDatabaseManager?.setActiveCompany(session.companyId)
        val targetDb = getDb(session.companyId)

        // Every authenticated call requires the device session issued here.
        return try {
            val remoteSessionId = sessionSecurityManager.registerSession(session.accessToken.orEmpty())
            val sessionWithToken = session.copy(sessionToken = remoteSessionId)
            // The one write that makes the sign-in visible. Whatever watches the session store (license
            // loading, sync, the socket) can therefore never see it without its device session, and
            // never registers a second one that revokes the first.
            sessions.save(sessionWithToken)
            runCatching { com.kadaikutty.pos.core.sync.LegacyTenantMigration.runOnce(targetDb, session.companyId) }
            syncScheduler?.requestPull()
            LoginResult.Success(sessionWithToken)
        } catch (e: Exception) {
            // The active company was switched above; put it back so a failed sign-in never
            // leaves the app pointed at this tenant's database.
            previousCompany?.let { tenantDatabaseManager?.setActiveCompany(it) }
            // The server did accept the password, so this is a connection problem and the
            // offline credential saved above may be used.
            LoginResult.Failure("Signed in, but could not establish a device session. Check your connection and try again.", canTryOffline = true)
        }
    }

    /**
     * Signs in against the verifier saved by the last successful online sign-in of this number on
     * this device. Only called when [loginOnline] failed for a reason other than the server
     * rejecting the credentials, so it can never outvote the server.
     */
    override suspend fun loginOffline(username: String, password: CharArray): LoginResult {
        sessionSecurityManager.resetSessionTermination()
        val normalized = normalizePhone(username)
        val credential = credentials.getCredential(normalized).first()
            ?: return LoginResult.Failure("No internet, and this number has not signed in on this device before. Check the mobile number, or connect to the internet and sign in once.")
        if (!verifier.matches(credential, password)) return LoginResult.Failure("Invalid mobile number or password")
        tenantDatabaseManager?.setActiveCompany(credential.companyId)
        val local = getDb(credential.companyId).userDao().getUserById(credential.userId)
            ?: return LoginResult.Failure("No internet. Sign in once with internet on this device to use it offline.")
        if (local.toPermissionsSet().contains(Permission.ACCOUNT_INACTIVE)) return LoginResult.Failure(DEACTIVATED_MESSAGE)
        // Keep this user's own tokens if they are still stored, so sync resumes on its own when the
        // network is back. Another user's tokens are never carried over.
        val existing = sessions.activeSession.first()?.takeIf { it.userId == local.id }
        val session = Session(
            userId = local.id,
            displayName = local.displayName,
            permissions = local.toPermissionsSet(),
            accessToken = existing?.accessToken,
            refreshToken = existing?.refreshToken,
            companyId = local.companyId,
            role = local.role,
            sessionToken = existing?.sessionToken
        )
        sessions.save(session)
        return LoginResult.Success(session)
    }

    // 401/403: bad password or disabled account. 404: the account no longer exists. Anything else,
    // including a timeout, a 5xx or a 429, says nothing about the credentials themselves.
    private fun isServerRejection(e: Exception): Boolean =
        e is com.kadaikutty.pos.core.network.BackendApiException && e.statusCode in setOf(401, 403, 404)

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

    override suspend fun verifyRegistrationOtpAndRegister(verificationId: String, otp: String, mobileNumber: String, password: CharArray, ownerName: String, businessName: String): RegisterResult = try {
        val phone = normalizePhone(mobileNumber)
        val proof = backend.verifyOtp(phone, otp, verificationId).getString("resetToken")
        val companyId = backend.registerMerchant(phone, ownerName, businessName, password.concatToString(), proof).getString("companyId")
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
        // Not "ADMIN": a response missing the field is malformed, and defaulting it to the
        // highest role turns that into a full permission set below.
        val role = user.optString("role", "")
        val parsedPerms = permissions(user.optJSONArray("permissions"))
        val perms = if (parsedPerms.contains(Permission.ACCOUNT_INACTIVE)) {
            // A deactivated account must not be promoted to a full permission set by role.
            parsedPerms
        } else if (role == "ADMIN" || role == "SUPER_ADMIN") {
            Permission.ALL_ACTIVE
        } else {
            // A cashier the owner gave no permissions has none. This used to hand such a cashier
            // every permission, owner screens included (SessionStore already refused to).
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
        // Not saved here: the session becomes visible only once the device session is registered (see completeSignIn).

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
        val existingLic = targetDb.licenseDao().getLicense(session.companyId)
        if (licenseObj != null || existingLic != null) {
            // Built on what was stored, with the sign-in response's license on top. Writing only
            // the name and status left validUntil at 0, which reads as expired: the shop was locked
            // out right after signing in, and stayed locked if the license refresh then failed.
            val base = existingLic ?: com.kadaikutty.pos.core.license.LicenseEntity(companyId = session.companyId)
            val licEntity = base.copy(
                businessName = bName ?: base.businessName,
                ownerName = oName ?: base.ownerName,
                ownerMobile = licenseObj?.optString("ownerMobile")?.ifBlank { null } ?: base.ownerMobile,
                licenseStatus = licenseObj?.optString("status")?.ifBlank { null } ?: base.licenseStatus,
                licenseType = licenseObj?.optString("licenseType")?.ifBlank { null } ?: base.licenseType,
                daysGranted = licenseObj?.optInt("daysGranted", base.daysGranted) ?: base.daysGranted,
                yearsGranted = licenseObj?.optInt("yearsGranted", base.yearsGranted) ?: base.yearsGranted,
                activatedAtEpochMs = licenseObj?.optLong("activatedAtEpochMs", base.activatedAtEpochMs) ?: base.activatedAtEpochMs,
                validUntilEpochMs = licenseObj?.optLong("validUntilEpochMs", base.validUntilEpochMs) ?: base.validUntilEpochMs,
                lastVerifiedAtEpochMs = if (licenseObj != null) System.currentTimeMillis() else base.lastVerifiedAtEpochMs,
            )
            targetDb.licenseDao().saveLicense(licEntity)
        }

        val local = UserEntity(
            id = session.userId,
            username = normalizePhone(user.optString("phone")),
            displayName = session.displayName,
            permissions = perms.joinToString(",") { it.name },
            companyId = session.companyId,
            role = session.role,
            lastOnlineVerifiedAt = System.currentTimeMillis()
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
