package com.kadaikutty.pos.core.license

import android.content.Context
import android.content.SharedPreferences
import com.kadaikutty.pos.core.auth.SessionStore
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.network.BackendApiClient
import com.kadaikutty.pos.core.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LicenseManager @Inject constructor(
    @ApplicationContext context: Context,
    private val database: BillingDatabase,
    private val backendApi: BackendApiClient,
    private val sessionStore: SessionStore,
    private val appPreferences: AppPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs: SharedPreferences = context.getSharedPreferences("license_prefs", Context.MODE_PRIVATE)
    private val _currentLicense = MutableStateFlow<LicenseEntity?>(null)
    val currentLicense: StateFlow<LicenseEntity?> = _currentLicense.asStateFlow()
    private val _isClockTampered = MutableStateFlow(false)
    val isClockTampered: StateFlow<Boolean> = _isClockTampered.asStateFlow()
    private var refreshJob: Job? = null

    init {
        validateMonotonicClock()
        scope.launch {
            sessionStore.activeSession.collect { session ->
                refreshJob?.cancel()
                if (session == null) {
                    _currentLicense.value = null
                } else {
                    refreshJob = launch {
                        database.licenseDao().getLicenseFlow(session.companyId).collect { local ->
                            if (local != null) _currentLicense.value = local
                        }
                    }
                    launch {
                        while (true) {
                            refreshFromBackend(session.companyId, session.accessToken)
                            delay(15 * 60 * 1000L)
                        }
                    }
                }
            }
        }
    }

    private suspend fun refreshFromBackend(companyId: String, token: String?) {
        if (token.isNullOrBlank()) return
        runCatching {
            val raw = backendApi.currentLicense(token).getJSONObject("license")
            val existing = database.licenseDao().getLicense(companyId)
            val status = raw.optString("status", "EXPIRED")
            val now = System.currentTimeMillis()
            val entity = LicenseEntity(
                companyId = companyId,
                businessName = existing?.businessName.orEmpty(), ownerName = existing?.ownerName.orEmpty(),
                ownerMobile = raw.optString("ownerMobile", existing?.ownerMobile.orEmpty()),
                licenseStatus = if (status == "ACTIVE") "ACTIVE_PAID" else status,
                licenseType = existing?.licenseType ?: if (status == "TRIAL") "TRIAL_2_DAYS" else "YEARLY",
                yearsGranted = existing?.yearsGranted ?: 0, daysGranted = existing?.daysGranted ?: 0,
                activatedAtEpochMs = existing?.activatedAtEpochMs ?: now,
                validUntilEpochMs = raw.optLong("validUntilEpochMs"), lastVerifiedAtEpochMs = now,
                highestSeenClockEpochMs = maxOf(now, existing?.highestSeenClockEpochMs ?: 0L),
                renewalCount = existing?.renewalCount ?: 0, notes = existing?.notes.orEmpty(),
            )
            database.licenseDao().saveLicense(entity)
            _currentLicense.value = entity
            recordServerOrActivityTimestamp(raw.optLong("updatedAtEpochMs", now))
        }
    }

    private fun validateMonotonicClock() {
        val current = System.currentTimeMillis()
        val highest = prefs.getLong("highest_seen_clock_ms", 0L)
        _isClockTampered.value = highest > 0 && current < highest - 10 * 60 * 1000L
        if (!_isClockTampered.value && current > highest) prefs.edit().putLong("highest_seen_clock_ms", current).apply()
    }

    fun recordServerOrActivityTimestamp(timestampMs: Long) {
        if (timestampMs > prefs.getLong("highest_seen_clock_ms", 0L)) prefs.edit().putLong("highest_seen_clock_ms", timestampMs).apply()
        validateMonotonicClock()
    }

    fun startRealtimeLicenseSync(companyId: String, ownerMobile: String? = null) {
        scope.launch { refreshFromBackend(companyId, sessionStore.activeSession.valueOrNull()?.accessToken) }
    }

    fun stopRealtimeLicenseSync() { refreshJob?.cancel(); refreshJob = null }

    fun shouldShowDailyRenewalAlert(): Boolean {
        val license = _currentLicense.value ?: return false
        if (!license.isExpiringSoon) return false
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        return if (prefs.getString("last_alert_date", "") == today) prefs.getInt("alert_count_today", 0) < 2 else true
    }

    fun recordRenewalAlertShown() {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val count = if (prefs.getString("last_alert_date", "") == today) prefs.getInt("alert_count_today", 0) else 0
        prefs.edit().putString("last_alert_date", today).putInt("alert_count_today", count + 1).apply()
    }

    suspend fun isTerminalLocked(): Boolean = withContext(Dispatchers.IO) {
        validateMonotonicClock()
        _isClockTampered.value || (database.licenseDao().getActiveLicense()?.isExpired == true)
    }
}

private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.valueOrNull(): T? = firstOrNull()
