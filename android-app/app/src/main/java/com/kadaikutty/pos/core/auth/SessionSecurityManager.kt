package com.kadaikutty.pos.core.auth

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.kadaikutty.pos.core.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionSecurityManager @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val appPreferences: AppPreferences
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isSessionTerminated = MutableStateFlow(false)
    val isSessionTerminated: StateFlow<Boolean> = _isSessionTerminated.asStateFlow()

    private val _terminationReason = MutableStateFlow<String?>(null)
    val terminationReason: StateFlow<String?> = _terminationReason.asStateFlow()

    private val _isMasterSessionTerminated = MutableStateFlow(false)
    val isMasterSessionTerminated: StateFlow<Boolean> = _isMasterSessionTerminated.asStateFlow()

    private val _masterTerminationReason = MutableStateFlow<String?>(null)
    val masterTerminationReason: StateFlow<String?> = _masterTerminationReason.asStateFlow()

    private var activeSessionListener: ListenerRegistration? = null
    private var masterSessionListener: ListenerRegistration? = null

    private var currentTrackingPhone: String? = null
    private var currentTrackingToken: String? = null
    private var currentMasterToken: String? = null

    private fun normalizePhone(phone: String): String =
        phone.replace("[^0-9]".toRegex(), "").takeLast(10)

    /**
     * Registers a new active session in Firestore for this user and device.
     * Takes ownership of the session, invalidating any previous device.
     */
    suspend fun registerSession(
        username: String,
        companyId: String,
        role: String,
        sessionToken: String
    ) {
        val cleanPhone = normalizePhone(username)
        if (cleanPhone.isBlank()) return

        val deviceId = appPreferences.getOrCreateInstallationDeviceId()
        val deviceName = appPreferences.getDeviceModelName()

        val sessionData = hashMapOf(
            "sessionToken" to sessionToken,
            "deviceId" to deviceId,
            "deviceName" to deviceName,
            "username" to cleanPhone,
            "companyId" to companyId,
            "role" to role,
            "updatedAt" to System.currentTimeMillis()
        )

        try {
            firestore.collection("active_sessions")
                .document(cleanPhone)
                .set(sessionData, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        startListeningToSession(cleanPhone, sessionToken)
    }

    /**
     * Listens for remote session changes on Firestore.
     * If another device logs in, its new sessionToken triggers invalidation here.
     */
    fun startListeningToSession(username: String, sessionToken: String) {
        val cleanPhone = normalizePhone(username)
        if (cleanPhone.isBlank()) return

        currentTrackingPhone = cleanPhone
        currentTrackingToken = sessionToken

        activeSessionListener?.remove()
        activeSessionListener = firestore.collection("active_sessions")
            .document(cleanPhone)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val remoteToken = snapshot.getString("sessionToken")
                    val remoteDevice = snapshot.getString("deviceName") ?: "Another device"

                    // If remote session has a different token, another device logged in!
                    if (!remoteToken.isNullOrBlank() && remoteToken != sessionToken) {
                        _isSessionTerminated.value = true
                        _terminationReason.value = "Your account was logged in on another device ($remoteDevice). This session has expired."
                    }
                }
            }
    }

    /**
     * Called on manual user logout.
     * Only clears the Firestore session if this device owns the active session.
     */
    fun clearSession(username: String) {
        val cleanPhone = normalizePhone(username)
        activeSessionListener?.remove()
        activeSessionListener = null
        currentTrackingPhone = null
        currentTrackingToken = null

        if (cleanPhone.isNotBlank()) {
            scope.launch {
                try {
                    val deviceId = appPreferences.getOrCreateInstallationDeviceId()
                    val doc = firestore.collection("active_sessions").document(cleanPhone).get().await()
                    if (doc.exists()) {
                        val activeDeviceId = doc.getString("deviceId")
                        if (activeDeviceId == deviceId) {
                            firestore.collection("active_sessions").document(cleanPhone).delete().await()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun resetSessionTermination() {
        _isSessionTerminated.value = false
        _terminationReason.value = null
    }

    /**
     * Registers a new Super Master session.
     * If another device opens Master Control, this device's Master Control will close.
     */
    suspend fun registerMasterSession(): String {
        val newToken = UUID.randomUUID().toString()
        currentMasterToken = newToken
        val deviceId = appPreferences.getOrCreateInstallationDeviceId()
        val deviceName = appPreferences.getDeviceModelName()

        val masterData = hashMapOf(
            "sessionToken" to newToken,
            "deviceId" to deviceId,
            "deviceName" to deviceName,
            "updatedAt" to System.currentTimeMillis()
        )

        try {
            firestore.collection("master_admin")
                .document("active_session")
                .set(masterData, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        listenToMasterSession(newToken)
        return newToken
    }

    private fun listenToMasterSession(token: String) {
        masterSessionListener?.remove()
        masterSessionListener = firestore.collection("master_admin")
            .document("active_session")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    val remoteToken = snapshot.getString("sessionToken")
                    val remoteDevice = snapshot.getString("deviceName") ?: "Another device"

                    if (!remoteToken.isNullOrBlank() && remoteToken != token) {
                        _isMasterSessionTerminated.value = true
                        _masterTerminationReason.value = "Master Control was opened on another device ($remoteDevice). This session has closed."
                    }
                }
            }
    }

    fun clearMasterSession() {
        masterSessionListener?.remove()
        masterSessionListener = null
        val token = currentMasterToken
        currentMasterToken = null

        scope.launch {
            try {
                if (token != null) {
                    val doc = firestore.collection("master_admin").document("active_session").get().await()
                    if (doc.exists() && doc.getString("sessionToken") == token) {
                        firestore.collection("master_admin").document("active_session").delete().await()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun resetMasterTermination() {
        _isMasterSessionTerminated.value = false
        _masterTerminationReason.value = null
    }
}
