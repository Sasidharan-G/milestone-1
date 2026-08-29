package com.kadaikutty.pos.core.auth

import android.app.Activity
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.security.Permission
import com.kadaikutty.pos.core.sync.*
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.util.concurrent.TimeUnit

class DefaultAuthRepository(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val sessions: SessionStore,
    private val offlineCredentials: OfflineCredentialStore,
    private val verifier: OfflineCredentialVerifier,
    private val database: BillingDatabase
) : AuthRepository {

    private fun normalizePhone(phone: String): String {
        val digits = phone.replace("[^0-9]".toRegex(), "")
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }

    override suspend fun loginOnline(username: String, password: CharArray): LoginResult {
        val cleanPhone = normalizePhone(username)
        return try {
            val userDoc = firestore.collection("users").document(cleanPhone).get().await()
            if (userDoc.exists()) {
                val saltStr = userDoc.getString("salt") ?: ""
                val verifierStr = userDoc.getString("verifier") ?: ""
                val userId = userDoc.getString("user_id") ?: cleanPhone
                val displayName = userDoc.getString("full_name") ?: cleanPhone
                val companyId = userDoc.getString("company_id") ?: ""
                val role = userDoc.getString("role") ?: "ADMIN"
                val permsList = userDoc.get("permissions") as? List<*>
                val perms = permsList?.mapNotNull {
                    try { Permission.valueOf(it.toString()) } catch (e: Exception) { null }
                }?.toSet() ?: Permission.entries.toSet()

                val saltBytes = java.util.Base64.getDecoder().decode(saltStr)
                val verifierBytes = java.util.Base64.getDecoder().decode(verifierStr)

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

                    password.fill('\u0000')
                    LoginResult.Success(session)
                } else {
                    password.fill('\u0000')
                    LoginResult.Failure("Invalid mobile number or password.")
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

        val nowMs = System.currentTimeMillis()
        if (userEntity.offlineValidUntil > 0 && nowMs > userEntity.offlineValidUntil) {
            password.fill('\u0000')
            return LoginResult.Failure("Offline login expired. Please connect to the internet to sign in.")
        }

        val saltBytes = java.util.Base64.getDecoder().decode(userEntity.salt)
        val verifierBytes = java.util.Base64.getDecoder().decode(userEntity.verifier)

        val offlineCred = OfflineCredential(
            username = userEntity.username,
            userId = userEntity.id,
            displayName = userEntity.displayName,
            salt = saltBytes,
            verifier = verifierBytes
        )

        val result = if (verifier.matches(offlineCred, password)) {
            val permissions = userEntity.toPermissionsSet()
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
            companyRef.set(mapOf(
                "name" to businessName,
                "owner_user_id" to user.uid,
                "status" to "active",
                "mobile" to cleanPhone
            )).await()

            firestore.collection("company_users").document().set(mapOf(
                "company_id" to companyId,
                "user_id" to user.uid,
                "role" to "ADMIN",
                "status" to "active",
                "mobile" to cleanPhone,
                "permissions" to Permission.entries.map { it.name }
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
                "permissions" to Permission.entries.map { it.name }
            )).await()

            val nowMs = System.currentTimeMillis()
            val offlineValidityMs = 30 * 24 * 60 * 60 * 1000L // 30 days
            val offlineValidUntil = nowMs + offlineValidityMs

            val session = Session(
                userId = user.uid,
                displayName = ownerName,
                permissions = Permission.entries.toSet(),
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
                permissions = Permission.entries.joinToString(",") { it.name },
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
            val user = authResult.user ?: return RecoveryResult.Failure("Authentication failed")
            val phone = user.phoneNumber ?: ""
            val cleanPhone = normalizePhone(phone)

            val offlineCred = verifier.create(cleanPhone, newPassword, user.uid, cleanPhone)
            val saltStr = java.util.Base64.getEncoder().encodeToString(offlineCred.salt)
            val verifierStr = java.util.Base64.getEncoder().encodeToString(offlineCred.verifier)

            if (cleanPhone.isNotBlank()) {
                firestore.collection("users").document(cleanPhone).update(mapOf(
                    "salt" to saltStr,
                    "verifier" to verifierStr
                )).await()

                val localUser = database.userDao().getUserByUsername(cleanPhone)
                if (localUser != null) {
                    database.userDao().updateUser(localUser.copy(salt = saltStr, verifier = verifierStr))
                }
            }

            newPassword.fill('\u0000')
            RecoveryResult.Success
        } catch (e: Exception) {
            newPassword.fill('\u0000')
            RecoveryResult.Failure(e.message ?: "Failed to reset password")
        }
    }

    override suspend fun signInWithGoogle() {}
    override suspend fun handleGoogleSignInSuccess(): GoogleSignInResult = GoogleSignInResult.Failure("Google Sign In not supported with Firebase yet")
    override suspend fun completeGoogleRegistration(ownerName: String, businessName: String): RegisterResult = RegisterResult.Failure("Not supported")
}
