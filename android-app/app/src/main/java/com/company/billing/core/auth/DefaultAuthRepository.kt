package com.company.billing.core.auth

import android.app.Activity
import com.company.billing.core.database.BillingDatabase
import com.company.billing.core.security.Permission
import com.company.billing.core.sync.*
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

    private fun getFakeEmail(mobileNumber: String): String {
        val cleanPhone = mobileNumber.trim().replace(" ", "").replace("-", "").replace("+", "")
        return "$cleanPhone@pos-app.com"
    }

    override suspend fun loginOnline(username: String, password: CharArray): LoginResult = try {
        val email = getFakeEmail(username)
        val authResult = firebaseAuth.signInWithEmailAndPassword(email, String(password)).await()
        val user = authResult.user ?: throw IOException("Authentication failed: user not established")

        // Resolve company membership
        val membershipsSnapshot = firestore.collection("company_users")
            .whereEqualTo("user_id", user.uid)
            .whereEqualTo("status", "active")
            .get()
            .await()

        if (membershipsSnapshot.isEmpty) {
            throw IOException("NO_COMPANY_MEMBERSHIP")
        }

        val activeMembership = membershipsSnapshot.documents.first()
        val companyId = activeMembership.getString("company_id") ?: ""
        val role = activeMembership.getString("role") ?: "USER"
        val permissionsList = activeMembership.get("permissions") as? List<*>
        val perms = permissionsList?.mapNotNull {
            try { Permission.valueOf(it.toString()) } catch (e: Exception) { null }
        }?.toSet() ?: emptySet()

        // Verify company status
        val companyDoc = firestore.collection("companies").document(companyId).get().await()
        if (companyDoc.getString("status") == "suspended") {
            throw IOException("COMPANY_SUSPENDED")
        }

        // Resolve profile display name
        val profileDoc = firestore.collection("profiles").document(user.uid).get().await()
        val displayName = profileDoc.getString("full_name") ?: username

        val session = Session(
            userId = user.uid,
            displayName = displayName,
            permissions = perms,
            accessToken = user.uid, // Dummy for offline sync marker
            companyId = companyId,
            role = role
        )
        sessions.save(session)

        val nowMs = System.currentTimeMillis()
        val offlineValidityMs = 7 * 24 * 60 * 60 * 1000L // 7 days
        val offlineValidUntil = nowMs + offlineValidityMs

        val offlineCred = verifier.create(username, password, session.userId, session.displayName)
        offlineCredentials.save(offlineCred)

        val saltStr = java.util.Base64.getEncoder().encodeToString(offlineCred.salt)
        val verifierStr = java.util.Base64.getEncoder().encodeToString(offlineCred.verifier)
        val userEntity = UserEntity(
            id = session.userId,
            username = username,
            displayName = session.displayName,
            salt = saltStr,
            verifier = verifierStr,
            permissions = perms.joinToString(",") { it.name },
            companyId = session.companyId,
            role = session.role,
            lastOnlineVerifiedAt = nowMs,
            offlineValidUntil = offlineValidUntil
        )
        database.userDao().insertUser(userEntity)

        try {
            // pullAllDataFromCloud(firestore, database, companyId)
        } catch (syncError: Exception) {
            syncError.printStackTrace()
        }

        password.fill('\u0000')
        LoginResult.Success(session)
    } catch (e: Exception) {
        val msg = e.message ?: ""
        if (msg.contains("NO_COMPANY_MEMBERSHIP") || msg.contains("COMPANY_SUSPENDED")) {
            password.fill('\u0000')
            LoginResult.Failure(if (msg == "COMPANY_SUSPENDED") "Your company account has been suspended." else "No active company membership found.")
        } else {
            val localResult = loginOffline(username, password)
            if (localResult is LoginResult.Success) {
                localResult
            } else {
                password.fill('\u0000')
                LoginResult.Failure(if (msg.contains("network", ignoreCase = true)) "Network unreachable. Cached login not found or expired." else "Invalid mobile number or password.")
            }
        }
    }

    override suspend fun loginOffline(username: String, password: CharArray): LoginResult {
        val userDao = database.userDao()
        val userEntity = userDao.getUserByUsername(username) ?: return LoginResult.Failure("Invalid username or password")

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
            LoginResult.Failure("Invalid username or password")
        }
        password.fill('\u0000')
        return result
    }

    override suspend fun logout() {
        firebaseAuth.signOut()
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

            val email = getFakeEmail(mobileNumber)
            val emailCredential = EmailAuthProvider.getCredential(email, String(password))
            
            try {
                user.linkWithCredential(emailCredential).await()
            } catch (e: Exception) {
                // If linking fails (e.g., email already in use), try creating/updating
            }

            val companyRef = firestore.collection("companies").document()
            val companyId = companyRef.id
            companyRef.set(mapOf(
                "name" to businessName,
                "owner_user_id" to user.uid,
                "status" to "active"
            )).await()

            firestore.collection("company_users").document().set(mapOf(
                "company_id" to companyId,
                "user_id" to user.uid,
                "role" to "ADMIN",
                "status" to "active",
                "permissions" to Permission.entries.map { it.name }
            )).await()

            firestore.collection("profiles").document(user.uid).set(mapOf(
                "full_name" to ownerName,
                "business_name" to businessName
            )).await()

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

            user.updatePassword(String(newPassword)).await()
            
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
