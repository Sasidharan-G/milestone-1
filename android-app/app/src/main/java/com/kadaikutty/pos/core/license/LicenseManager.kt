package com.kadaikutty.pos.core.license

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.kadaikutty.pos.core.auth.SessionStore
import com.kadaikutty.pos.core.database.BillingDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LicenseManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: BillingDatabase,
    private val firestore: FirebaseFirestore,
    private val sessionStore: SessionStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs: SharedPreferences = context.getSharedPreferences("license_prefs", Context.MODE_PRIVATE)

    private val _currentLicense = MutableStateFlow<LicenseEntity?>(null)
    val currentLicense: StateFlow<LicenseEntity?> = _currentLicense.asStateFlow()

    private val _isClockTampered = MutableStateFlow(false)
    val isClockTampered: StateFlow<Boolean> = _isClockTampered.asStateFlow()

    private var firestoreListener: ListenerRegistration? = null

    init {
        scope.launch {
            // Check Monotonic Clock integrity
            validateMonotonicClock()
        }

        // Start real-time Firestore sync & local flow whenever active session is ready
        scope.launch {
            sessionStore.activeSession.collect { session ->
                if (session != null) {
                    val targetCompanyId = session.companyId
                    val mobile = session.userId
                    startRealtimeLicenseSync(targetCompanyId, mobile)
                    if (targetCompanyId.isNotBlank()) {
                        database.licenseDao().getLicenseFlow(targetCompanyId).collect { license ->
                            if (license != null) {
                                _currentLicense.value = license
                            }
                        }
                    }
                } else {
                    stopRealtimeLicenseSync()
                    _currentLicense.value = null
                }
            }
        }
    }

    /**
     * Validates that the device clock has not been rolled backwards.
     */
    fun validateMonotonicClock(): Boolean {
        val now = System.currentTimeMillis()
        val highestClock = prefs.getLong("highest_seen_clock_ms", 0L)
        if (highestClock > 0 && now < (highestClock - 300000L)) { // 5-minute leeway
            _isClockTampered.value = true
            return false
        } else {
            if (now > highestClock) {
                prefs.edit().putLong("highest_seen_clock_ms", now).apply()
            }
            _isClockTampered.value = false
            return true
        }
    }

    /**
     * Records any high-water mark timestamp (e.g. from invoices or server response).
     */
    fun recordServerOrActivityTimestamp(timestampMs: Long) {
        val highestClock = prefs.getLong("highest_seen_clock_ms", 0L)
        if (timestampMs > highestClock) {
            prefs.edit().putLong("highest_seen_clock_ms", timestampMs).apply()
        }
        validateMonotonicClock()
    }

    private fun parseSnapshotToLicense(snapshot: com.google.firebase.firestore.DocumentSnapshot, fallbackCompanyId: String): LicenseEntity {
        val docCompanyId = snapshot.getString("companyId") ?: snapshot.id
        return LicenseEntity(
            companyId = if (docCompanyId.isNotBlank()) docCompanyId else fallbackCompanyId,
            businessName = snapshot.getString("businessName") ?: "My Shop",
            ownerName = snapshot.getString("ownerName") ?: "",
            ownerMobile = snapshot.getString("ownerMobile") ?: "",
            licenseStatus = snapshot.getString("licenseStatus") ?: "ACTIVE_PAID",
            licenseType = snapshot.getString("licenseType") ?: "TRIAL_2_DAYS",
            yearsGranted = snapshot.getLong("yearsGranted")?.toInt() ?: 0,
            daysGranted = snapshot.getLong("daysGranted")?.toInt() ?: 0,
            activatedAtEpochMs = snapshot.getLong("activatedAtEpochMs") ?: 0L,
            validUntilEpochMs = snapshot.getLong("validUntilEpochMs") ?: 0L,
            lastVerifiedAtEpochMs = System.currentTimeMillis(),
            highestSeenClockEpochMs = System.currentTimeMillis(),
            renewalCount = snapshot.getLong("renewalCount")?.toInt() ?: 0,
            notes = snapshot.getString("notes") ?: ""
        )
    }

    /**
     * Starts listening to Firestore license document for immediate remote grant/cut-off.
     */
    fun startRealtimeLicenseSync(companyId: String, ownerMobile: String? = null) {
        firestoreListener?.remove()

        if (companyId.isNotBlank()) {
            firestoreListener = firestore.collection("licenses").document(companyId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    if (snapshot != null && snapshot.exists()) {
                        val entity = parseSnapshotToLicense(snapshot, companyId)
                        scope.launch {
                            database.licenseDao().saveLicense(entity)
                            _currentLicense.value = entity
                        }
                    }
                }
        }

        // Secondary real-time check by owner mobile to handle multi-license / re-registration
        scope.launch {
            try {
                val cleanMobile = ownerMobile?.filter { it.isDigit() }?.takeLast(10)
                if (!cleanMobile.isNullOrBlank()) {
                    var docList = firestore.collection("licenses").whereEqualTo("ownerMobile", cleanMobile).get().await().documents
                    if (docList.isEmpty()) {
                        docList = firestore.collection("licenses").whereEqualTo("ownerMobile", "+91$cleanMobile").get().await().documents
                    }
                    val activeDoc = docList.maxByOrNull { it.getLong("validUntilEpochMs") ?: 0L }
                    if (activeDoc != null && activeDoc.exists()) {
                        val activeEntity = parseSnapshotToLicense(activeDoc, companyId)
                        database.licenseDao().saveLicense(activeEntity)
                        if (companyId.isNotBlank() && activeEntity.companyId != companyId) {
                            database.licenseDao().saveLicense(activeEntity.copy(companyId = companyId))
                        }
                        _currentLicense.value = activeEntity
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun stopRealtimeLicenseSync() {
        firestoreListener?.remove()
        firestoreListener = null
    }

    /**
     * Checks if the 7-day renewal popup alert should be shown on app launch.
     * Guaranteed MAX 2 times per day limit using SharedPreferences tracking.
     */
    fun shouldShowDailyRenewalAlert(): Boolean {
        val license = _currentLicense.value ?: return false
        if (!license.isExpiringSoon) return false

        val todayDate = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val lastDate = prefs.getString("last_alert_date", "") ?: ""
        val countToday = if (lastDate == todayDate) prefs.getInt("alert_count_today", 0) else 0

        return countToday < 2
    }

    /**
     * Increments the daily renewal popup counter.
     */
    fun recordRenewalAlertShown() {
        val todayDate = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val lastDate = prefs.getString("last_alert_date", "") ?: ""
        val countToday = if (lastDate == todayDate) prefs.getInt("alert_count_today", 0) else 0

        prefs.edit()
            .putString("last_alert_date", todayDate)
            .putInt("alert_count_today", countToday + 1)
            .apply()
    }

    /**
     * Synchronously/Locally verifies if the terminal is currently locked due to expiry or tampering.
     */
    suspend fun isTerminalLocked(): Boolean = withContext(Dispatchers.IO) {
        validateMonotonicClock()
        if (_isClockTampered.value) return@withContext true

        val license = database.licenseDao().getActiveLicense() ?: return@withContext false
        return@withContext license.isExpired
    }
}
