package com.kadaikutty.pos.core.auth

import android.app.Activity
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.security.Permission
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.kadaikutty.pos.core.license.LicenseEntity
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.util.concurrent.TimeUnit

class DefaultAuthRepository(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val sessions: SessionStore,
    private val offlineCredentials: OfflineCredentialStore,
    private val verifier: OfflineCredentialVerifier,
    private val database: BillingDatabase,
) : AuthRepository {

    private fun normalizePhone(phone: String): String {
        val digits = phone.replace("[^0-9]".toRegex(), "")
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }

    private fun safeDecodeBase64(str: String): ByteArray {
        if (str.isBlank()) return ByteArray(0)
        return try {
            android.util.Base64.decode(str, android.util.Base64.DEFAULT)
        } catch (_: Exception) {
            try {
                java.util.Base64.getDecoder().decode(str)
            } catch (_: Exception) {
                ByteArray(0)
            }
        }
    }

    override suspend fun loginOnline(username: String, password: CharArray): LoginResult {
        val cleanPhone = normalizePhone(username)
        return try {
            var userDoc = firestore.collection("users").document(cleanPhone).get().await()
            if (!userDoc.exists()) {
                userDoc = firestore.collection("users").document("+91$cleanPhone").get().await()
            }
            if (!userDoc.exists()) {
                try {
                    val q1 = firestore.collection("users").whereEqualTo("mobile", cleanPhone).get().await()
                    if (!q1.isEmpty) userDoc = q1.documents.first()
                } catch (_: Exception) {}
            }
            if (!userDoc.exists()) {
                try {
                    val q2 = firestore.collection("users").whereEqualTo("username", cleanPhone).get().await()
                    if (!q2.isEmpty) userDoc = q2.documents.first()
                } catch (_: Exception) {}
            }

            if (userDoc.exists()) {
                val saltStr = userDoc.getString("salt") ?: ""
                val verifierStr = userDoc.getString("verifier") ?: ""
                val userId = userDoc.getString("user_id") ?: cleanPhone
                val displayName = userDoc.getString("full_name") ?: cleanPhone
                val companyId = userDoc.getString("company_id") ?: ""
                val role = userDoc.getString("role") ?: "ADMIN"
                val status = userDoc.getString("status") ?: "ACTIVE"
                val permsList = userDoc.get("permissions") as? List<*>
                val perms = permsList?.mapNotNull {
                    try { Permission.valueOf(it.toString()) } catch (_: Exception) { null }
                }?.filter {
                    if (role == "ADMIN") it != Permission.ACCOUNT_INACTIVE && it != Permission.PENDING_MASTER_APPROVAL else true
                }?.toSet() ?: Permission.ALL_ACTIVE

                if (role != "ADMIN") {
                    if (status.equals("PENDING_APPROVAL", ignoreCase = true) || perms.contains(Permission.PENDING_MASTER_APPROVAL)) {
                        password.fill('\u0000')
                        return LoginResult.Failure("Staff account is pending Master Admin approval. Please contact Super Master.")
                    }
                    if (status.equals("REJECTED", ignoreCase = true)) {
                        password.fill('\u0000')
                        return LoginResult.Failure("Staff account request was rejected by Master Admin.")
                    }
                    if (status.equals("INACTIVE", ignoreCase = true) || perms.contains(Permission.ACCOUNT_INACTIVE)) {
                        password.fill('\u0000')
                        return LoginResult.Failure("Your account has been deactivated. Contact Admin.")
                    }
                    if (perms.contains(Permission.REQUIRE_PASSWORD_CHANGE)) {
                        password.fill('\u0000')
                        return LoginResult.Failure("Password reset required. Please use 'Forgot Password / PIN' to set a new password.")
                    }
                }

                val saltBytes = safeDecodeBase64(saltStr)
                val verifierBytes = safeDecodeBase64(verifierStr)

                val cred = OfflineCredential(
                    username = cleanPhone,
                    userId = userId,
                    displayName = displayName,
                    salt = saltBytes,
                    verifier = verifierBytes
                )

                if (verifier.matches(cred, password)) {
                    val session = Session(
                        userId = userId,
                        displayName = displayName,
                        permissions = perms,
                        accessToken = userId,
                        companyId = companyId,
                        role = role
                    )
                    sessions.save(session)

                    val nowMs = System.currentTimeMillis()
                    val offlineValidityMs = 30 * 24 * 60 * 60 * 1000L
                    val userEntity = UserEntity(
                        id = userId,
                        username = cleanPhone,
                        displayName = displayName,
                        salt = saltStr,
                        verifier = verifierStr,
                        permissions = perms.joinToString(",") { it.name },
                        companyId = companyId,
                        role = role,
                        lastOnlineVerifiedAt = nowMs,
                        offlineValidUntil = nowMs + offlineValidityMs
                    )
                    database.userDao().insertUser(userEntity)

                    if ((role == "ADMIN" || role == "CASHIER" || role == "STAFF") && cleanPhone.isNotBlank()) {
                        try {
                            var targetLicDoc: com.google.firebase.firestore.DocumentSnapshot? = null
                            if (companyId.isNotBlank()) {
                                val doc = firestore.collection("licenses").document(companyId).get().await()
                                if (doc.exists()) targetLicDoc = doc
                            }
                            if (targetLicDoc == null || !targetLicDoc.exists()) {
                                var q = firestore.collection("licenses").whereEqualTo("ownerMobile", cleanPhone).get().await()
                                if (q.isEmpty) {
                                    q = firestore.collection("licenses").whereEqualTo("ownerMobile", "+91$cleanPhone").get().await()
                                }
                                if (!q.isEmpty) {
                                    targetLicDoc = q.documents.maxByOrNull { it.getLong("validUntilEpochMs") ?: 0L }
                                }
                            }

                            if (targetLicDoc != null && targetLicDoc.exists()) {
                                val actualCompanyId = targetLicDoc.getString("companyId") ?: targetLicDoc.id
                                val licEntity = LicenseEntity(
                                    companyId = if (companyId.isNotBlank()) companyId else actualCompanyId,
                                    businessName = targetLicDoc.getString("businessName") ?: userDoc.getString("business_name") ?: "My Shop",
                                    ownerName = targetLicDoc.getString("ownerName") ?: displayName,
                                    ownerMobile = targetLicDoc.getString("ownerMobile") ?: cleanPhone,
                                    licenseStatus = targetLicDoc.getString("licenseStatus") ?: "ACTIVE_PAID",
                                    licenseType = targetLicDoc.getString("licenseType") ?: "TRIAL_2_DAYS",
                                    yearsGranted = targetLicDoc.getLong("yearsGranted")?.toInt() ?: 0,
                                    daysGranted = targetLicDoc.getLong("daysGranted")?.toInt() ?: 0,
                                    activatedAtEpochMs = targetLicDoc.getLong("activatedAtEpochMs") ?: 0L,
                                    validUntilEpochMs = targetLicDoc.getLong("validUntilEpochMs") ?: 0L,
                                    lastVerifiedAtEpochMs = System.currentTimeMillis(),
                                    highestSeenClockEpochMs = System.currentTimeMillis(),
                                    renewalCount = targetLicDoc.getLong("renewalCount")?.toInt() ?: 0,
                                    notes = targetLicDoc.getString("notes") ?: ""
                                )
                                database.licenseDao().saveLicense(licEntity)
                                if (actualCompanyId != companyId && actualCompanyId.isNotBlank()) {
                                    database.licenseDao().saveLicense(licEntity.copy(companyId = actualCompanyId))
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    password.fill('\u0000')
                    LoginResult.Success(session)
                } else {
                    // Try fallback to local credential matching
                    val localResult = loginOffline(username, password)
                    if (localResult is LoginResult.Success) {
                        localResult
                    } else {
                        password.fill('\u0000')
                        LoginResult.Failure("Invalid mobile number or password.")
                    }
                }
            } else {
                // Try local offline login fallback
                val localResult = loginOffline(username, password)
                if (localResult is LoginResult.Success) {
                    localResult
                } else {
                    password.fill('\u0000')
                    LoginResult.Failure("Invalid mobile number or password.")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val localResult = loginOffline(username, password)
            if (localResult is LoginResult.Success) {
                localResult
            } else {
                password.fill('\u0000')
                LoginResult.Failure(if (e.message?.contains("network", ignoreCase = true) == true) "Network error. Please check your internet or switch to Offline mode." else "Invalid mobile number or password.")
            }
        }
    }

    override suspend fun loginOffline(username: String, password: CharArray): LoginResult {
        val cleanPhone = normalizePhone(username)
        val userDao = database.userDao()
        val userEntity = userDao.getUserByUsername(username, cleanPhone) ?: return LoginResult.Failure("Invalid mobile number or password")

        val permissions = if (userEntity.role == "ADMIN") {
            val activePerms = userEntity.toPermissionsSet().filter { 
                it != Permission.ACCOUNT_INACTIVE && it != Permission.REQUIRE_PASSWORD_CHANGE 
            }.toSet()
            if (activePerms != userEntity.toPermissionsSet()) {
                userDao.updateUser(userEntity.copy(permissions = activePerms.joinToString(",") { it.name }))
            }
            activePerms
        } else {
            userEntity.toPermissionsSet()
        }

        if (userEntity.role != "ADMIN") {
            if (permissions.contains(Permission.PENDING_MASTER_APPROVAL)) {
                password.fill('\u0000')
                return LoginResult.Failure("Staff account is pending Master Admin approval. Please contact Super Master.")
            }
            if (permissions.contains(Permission.ACCOUNT_INACTIVE)) {
                password.fill('\u0000')
                return LoginResult.Failure("Your account has been deactivated. Contact Admin.")
            }
            if (permissions.contains(Permission.REQUIRE_PASSWORD_CHANGE)) {
                password.fill('\u0000')
                return LoginResult.Failure("Password reset required. Please use 'Forgot Password / PIN' to set a new password.")
            }
        }

        val nowMs = System.currentTimeMillis()
        if (userEntity.offlineValidUntil in 1 until nowMs) {
            password.fill('\u0000')
            return LoginResult.Failure("Offline login expired. Please connect to the internet to sign in.")
        }

        val saltBytes = safeDecodeBase64(userEntity.salt)
        val verifierBytes = safeDecodeBase64(userEntity.verifier)

        val offlineCred = OfflineCredential(
            username = userEntity.username,
            userId = userEntity.id,
            displayName = userEntity.displayName,
            salt = saltBytes,
            verifier = verifierBytes
        )

        val result = if (verifier.matches(offlineCred, password)) {
            val session = Session(
                userId = userEntity.id,
                displayName = userEntity.displayName,
                permissions = permissions,
                companyId = userEntity.companyId,
                role = userEntity.role
            )
            sessions.save(session)
            LoginResult.Success(session)
        } else {
            LoginResult.Failure("Invalid mobile number or password")
        }
        password.fill('\u0000')
        return result
    }

    override suspend fun logout() {
        // We do NOT sign out of Firebase Auth here because this device acts as a POS terminal
        // and needs to continue background sync for secondary local users (Cashiers).
        sessions.clear()
    }

    override suspend fun registerMerchant(
        mobileNumber: String,
        password: CharArray,
        ownerName: String,
        businessName: String
    ): RegisterResult {
        return try {
            val cleanPhone = normalizePhone(mobileNumber)
            if (cleanPhone.length < 10) {
                return RegisterResult.Failure("Please enter a valid 10-digit mobile number")
            }

            // Check if phone already registered in Firestore
            try {
                val existingDoc = firestore.collection("users").document(cleanPhone).get().await()
                if (existingDoc.exists()) {
                    return RegisterResult.Failure("A shop with mobile number +91 $cleanPhone is already registered. Please Sign In.")
                }
            } catch (e: Exception) {
                // If offline, check local database
                val localExisting = database.userDao().getUserByUsername(cleanPhone, cleanPhone)
                if (localExisting != null) {
                    return RegisterResult.Failure("A shop with mobile number +91 $cleanPhone is already registered on this device. Please Sign In.")
                }
            }

            val userId = java.util.UUID.randomUUID().toString()
            val companyRef = firestore.collection("companies").document()
            val companyId = companyRef.id

            val offlineCred = verifier.create(cleanPhone, password, userId, ownerName)
            offlineCredentials.save(offlineCred)

            val saltStr = java.util.Base64.getEncoder().encodeToString(offlineCred.salt)
            val verifierStr = java.util.Base64.getEncoder().encodeToString(offlineCred.verifier)

            // Save Company, Profile, User, and Licenses to Firestore Cloud
            try {
                val licenseMap = hashMapOf(
                    "companyId" to companyId,
                    "businessName" to businessName,
                    "ownerName" to ownerName,
                    "ownerMobile" to cleanPhone,
                    "licenseStatus" to "PENDING_APPROVAL",
                    "licenseType" to "TRIAL_2_DAYS",
                    "yearsGranted" to 0,
                    "daysGranted" to 0,
                    "activatedAtEpochMs" to 0L,
                    "validUntilEpochMs" to 0L,
                    "notes" to "New Shop Registered. Waiting for Master Admin Approval."
                )

                companyRef.set(mapOf(
                    "id" to companyId,
                    "name" to businessName,
                    "owner_user_id" to userId,
                    "status" to "active",
                    "mobile" to cleanPhone,
                    "license_status" to "PENDING_APPROVAL",
                    "valid_until_epoch_ms" to 0L,
                    "licenses" to licenseMap
                )).await()

                firestore.collection("company_users").document().set(mapOf(
                    "company_id" to companyId,
                    "user_id" to userId,
                    "role" to "ADMIN",
                    "status" to "active",
                    "mobile" to cleanPhone,
                    "permissions" to Permission.ALL_ACTIVE.map { it.name }
                )).await()

                firestore.collection("profiles").document(userId).set(mapOf(
                    "full_name" to ownerName,
                    "business_name" to businessName,
                    "mobile" to cleanPhone
                )).await()

                firestore.collection("users").document(cleanPhone).set(mapOf(
                    "user_id" to userId,
                    "username" to cleanPhone,
                    "mobile" to cleanPhone,
                    "full_name" to ownerName,
                    "business_name" to businessName,
                    "company_id" to companyId,
                    "role" to "ADMIN",
                    "salt" to saltStr,
                    "verifier" to verifierStr,
                    "permissions" to Permission.ALL_ACTIVE.map { it.name },
                    "license_status" to "PENDING_APPROVAL",
                    "valid_until_epoch_ms" to 0L,
                    "licenses" to licenseMap
                )).await()

                firestore.collection("licenses").document(companyId).set(licenseMap, com.google.firebase.firestore.SetOptions.merge()).await()
            } catch (cloudErr: Exception) {
                cloudErr.printStackTrace()
            }

            val licenseEntity = LicenseEntity(
                companyId = companyId,
                businessName = businessName,
                ownerName = ownerName,
                ownerMobile = cleanPhone,
                licenseStatus = "PENDING_APPROVAL",
                licenseType = "TRIAL_2_DAYS"
            )
            database.licenseDao().saveLicense(licenseEntity)

            val nowMs = System.currentTimeMillis()
            val offlineValidityMs = 30 * 24 * 60 * 60 * 1000L
            val offlineValidUntil = nowMs + offlineValidityMs

            val session = Session(
                userId = userId,
                displayName = ownerName,
                permissions = Permission.ALL_ACTIVE,
                accessToken = userId,
                companyId = companyId,
                role = "ADMIN"
            )
            sessions.save(session)

            val userEntity = UserEntity(
                id = userId,
                username = cleanPhone,
                displayName = ownerName,
                salt = saltStr,
                verifier = verifierStr,
                permissions = Permission.ALL_ACTIVE.joinToString(",") { it.name },
                companyId = companyId,
                role = "ADMIN",
                lastOnlineVerifiedAt = nowMs,
                offlineValidUntil = offlineValidUntil
            )
            database.userDao().insertUser(userEntity)

            RegisterResult.Success(companyId)
        } catch (e: Exception) {
            RegisterResult.Failure(e.message ?: "Failed to register merchant account")
        }
    }

    override fun sendRegistrationOtp(
        mobileNumber: String,
        activity: Activity,
        onCodeSent: (String) -> Unit,
        onVerificationFailed: (String) -> Unit
    ) {
        val formattedNumber = if (mobileNumber.startsWith("+")) mobileNumber else "+91$mobileNumber"
        val options = PhoneAuthOptions.newBuilder(firebaseAuth)
            .setPhoneNumber(formattedNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {}
                override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                    onVerificationFailed(e.message ?: "Verification failed")
                }
                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    onCodeSent(verificationId)
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    override suspend fun verifyRegistrationOtpAndRegister(
        verificationId: String,
        otp: String,
        mobileNumber: String,
        password: CharArray,
        ownerName: String,
        businessName: String
    ): RegisterResult {
        return try {
            val credential = PhoneAuthProvider.getCredential(verificationId, otp)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user ?: return RegisterResult.Failure("Authentication failed")

            val cleanPhone = normalizePhone(mobileNumber)

            val companyRef = firestore.collection("companies").document()
            val companyId = companyRef.id
            val licenseMap = hashMapOf(
                "companyId" to companyId,
                "businessName" to businessName,
                "ownerName" to ownerName,
                "ownerMobile" to cleanPhone,
                "licenseStatus" to "PENDING_APPROVAL",
                "licenseType" to "TRIAL_2_DAYS",
                "yearsGranted" to 0,
                "daysGranted" to 0,
                "activatedAtEpochMs" to 0L,
                "validUntilEpochMs" to 0L,
                "notes" to "New Shop Registered. Waiting for Master Admin Approval."
            )

            companyRef.set(mapOf(
                "id" to companyId,
                "name" to businessName,
                "owner_user_id" to user.uid,
                "status" to "active",
                "mobile" to cleanPhone,
                "license_status" to "PENDING_APPROVAL",
                "valid_until_epoch_ms" to 0L,
                "licenses" to licenseMap
            )).await()

            firestore.collection("company_users").document().set(mapOf(
                "company_id" to companyId,
                "user_id" to user.uid,
                "role" to "ADMIN",
                "status" to "active",
                "mobile" to cleanPhone,
                "permissions" to Permission.ALL_ACTIVE.map { it.name }
            )).await()

            firestore.collection("profiles").document(user.uid).set(mapOf(
                "full_name" to ownerName,
                "business_name" to businessName,
                "mobile" to cleanPhone
            )).await()

            val offlineCred = verifier.create(cleanPhone, password, user.uid, ownerName)
            offlineCredentials.save(offlineCred)

            val saltStr = java.util.Base64.getEncoder().encodeToString(offlineCred.salt)
            val verifierStr = java.util.Base64.getEncoder().encodeToString(offlineCred.verifier)

            // Save to Firestore users collection for online multi-device authentication
            firestore.collection("users").document(cleanPhone).set(mapOf(
                "user_id" to user.uid,
                "username" to cleanPhone,
                "mobile" to cleanPhone,
                "full_name" to ownerName,
                "business_name" to businessName,
                "company_id" to companyId,
                "role" to "ADMIN",
                "salt" to saltStr,
                "verifier" to verifierStr,
                "permissions" to Permission.ALL_ACTIVE.map { it.name },
                "license_status" to "PENDING_APPROVAL",
                "valid_until_epoch_ms" to 0L,
                "licenses" to licenseMap
            )).await()

            try {
                firestore.collection("licenses").document(companyId).set(licenseMap, com.google.firebase.firestore.SetOptions.merge()).await()
            } catch (_: Exception) {}

            val licenseEntity = LicenseEntity(
                companyId = companyId,
                businessName = businessName,
                ownerName = ownerName,
                ownerMobile = cleanPhone,
                licenseStatus = "PENDING_APPROVAL",
                licenseType = "TRIAL_2_DAYS"
            )
            database.licenseDao().saveLicense(licenseEntity)

            val nowMs = System.currentTimeMillis()
            val offlineValidityMs = 30 * 24 * 60 * 60 * 1000L // 30 days
            val offlineValidUntil = nowMs + offlineValidityMs

            val session = Session(
                userId = user.uid,
                displayName = ownerName,
                permissions = Permission.ALL_ACTIVE,
                accessToken = user.uid,
                companyId = companyId,
                role = "ADMIN"
            )
            sessions.save(session)

            val userEntity = UserEntity(
                id = user.uid,
                username = cleanPhone,
                displayName = ownerName,
                salt = saltStr,
                verifier = verifierStr,
                permissions = Permission.ALL_ACTIVE.joinToString(",") { it.name },
                companyId = companyId,
                role = "ADMIN",
                lastOnlineVerifiedAt = nowMs,
                offlineValidUntil = offlineValidUntil
            )
            database.userDao().insertUser(userEntity)

            password.fill('\u0000')
            RegisterResult.Success(companyId)
        } catch (e: Exception) {
            password.fill('\u0000')
            RegisterResult.Failure(e.message ?: "Registration failed")
        }
    }

    override fun sendPasswordResetOtp(
        mobileNumber: String,
        activity: Activity,
        onCodeSent: (String) -> Unit,
        onVerificationFailed: (String) -> Unit
    ) {
        sendRegistrationOtp(mobileNumber, activity, onCodeSent, onVerificationFailed)
    }

    override suspend fun verifyOtpAndResetPassword(
        verificationId: String,
        otp: String,
        newPassword: CharArray
    ): RecoveryResult {
        return try {
            val credential = PhoneAuthProvider.getCredential(verificationId, otp)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user ?: return RecoveryResult.Failure("Phone authentication failed")
            val phone = user.phoneNumber ?: ""
            val cleanPhone = normalizePhone(phone)

            val offlineCred = verifier.create(cleanPhone, newPassword, user.uid, cleanPhone)
            val saltStr = java.util.Base64.getEncoder().encodeToString(offlineCred.salt)
            val verifierStr = java.util.Base64.getEncoder().encodeToString(offlineCred.verifier)

            var updatedAny = false

            // 1. Update in Local SQLite Database (Works for both Admin and Staff/Cashier)
            val localUser = database.userDao().getUserByUsername(cleanPhone, cleanPhone)
            if (localUser != null) {
                val perms = localUser.toPermissionsSet().toMutableSet()
                perms.remove(Permission.REQUIRE_PASSWORD_CHANGE)
                val newPermsStr = perms.joinToString(",") { it.name }
                
                database.userDao().updateUser(localUser.copy(salt = saltStr, verifier = verifierStr, permissions = newPermsStr))
                updatedAny = true

                // If Staff, sync to company's staff sub-collection in Firestore
                if (localUser.companyId.isNotBlank()) {
                    try {
                        firestore.collection("users")
                            .document(localUser.companyId)
                            .collection("staff")
                            .document(localUser.id)
                            .update(mapOf(
                                "salt" to saltStr,
                                "verifier" to verifierStr,
                                "permissions" to newPermsStr
                            )).await()
                    } catch (_: Exception) {}
                }
            }

            // 2. Update in Root Firestore 'users' collection (For Shop Owner / Admin accounts)
            try {
                val adminDocRef = firestore.collection("users").document(cleanPhone)
                val adminDoc = adminDocRef.get().await()
                if (adminDoc.exists()) {
                    val currentPerms = adminDoc.get("permissions") as? List<*> ?: emptyList<Any>()
                    val newPerms = currentPerms.filter { it.toString() != "REQUIRE_PASSWORD_CHANGE" }
                    adminDocRef.update(mapOf(
                        "salt" to saltStr,
                        "verifier" to verifierStr,
                        "permissions" to newPerms
                    )).await()
                    updatedAny = true
                }
            } catch (_: Exception) {}

            newPassword.fill('\u0000')

            if (updatedAny) {
                RecoveryResult.Success
            } else {
                RecoveryResult.Failure("No user found with mobile number +91 $cleanPhone. Please contact your Store Admin.")
            }
        } catch (e: Exception) {
            newPassword.fill('\u0000')
            RecoveryResult.Failure(e.message ?: "Failed to reset password")
        }
    }

    override suspend fun signInWithGoogle() {}
    override suspend fun handleGoogleSignInSuccess(): GoogleSignInResult = GoogleSignInResult.Failure("Google Sign In not supported with Firebase yet")
    override suspend fun completeGoogleRegistration(ownerName: String, businessName: String): RegisterResult = RegisterResult.Failure("Not supported")
}
