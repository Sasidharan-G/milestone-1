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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.coroutineScope
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
    private val tenantDatabaseManager: com.kadaikutty.pos.core.database.TenantDatabaseManager,
    private val backendApi: BackendApiClient,
    private val sessionStore: SessionStore,
    private val appPreferences: AppPreferences,
    private val webSocketManager: com.kadaikutty.pos.core.network.WebSocketManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs: SharedPreferences = context.getSharedPreferences("license_prefs", Context.MODE_PRIVATE)
    private val _currentLicense = MutableStateFlow<LicenseEntity?>(null)
    val currentLicense: StateFlow<LicenseEntity?> = _currentLicense.asStateFlow()
    private val _isLicenseLoaded = MutableStateFlow(false)
    val isLicenseLoaded: StateFlow<Boolean> = _isLicenseLoaded.asStateFlow()
    private val _isClockTampered = MutableStateFlow(false)
    val isClockTampered: StateFlow<Boolean> = _isClockTampered.asStateFlow()

    init {
        validateMonotonicClock()
        // Profile updates are not part of the product/customer sync queue. Refresh the canonical
        // tenant profile immediately when the authenticated company room notifies a change.
        scope.launch {
            webSocketManager.dataChangedFlow.collect {
                val session = sessionStore.activeSession.valueOrNull() ?: return@collect
                refreshFromBackend(session.companyId, session.userId, session.accessToken)
            }
        }
        scope.launch {
            var lastCompanyId: String? = null
            // Keyed on who is signed in, not on every session write. The session is rewritten on
            // each token refresh, and each write used to start another endless 15-minute refresh
            // loop that was never cancelled, each holding a token that had since expired.
            sessionStore.activeSession
                .map { session -> session?.let { it.companyId to it.userId } }
                .distinctUntilChanged()
                .collectLatest { identity ->
                if (identity == null) {
                    _currentLicense.value = null
                    _isLicenseLoaded.value = false
                    lastCompanyId = null
                    appPreferences.clearShopDetails()
                } else {
                    val (companyId, userId) = identity
                    if (lastCompanyId != companyId) {
                        lastCompanyId = companyId
                        appPreferences.clearShopDetails()
                    }
                    coroutineScope {
                    launch {
                        tenantDatabaseManager.getDatabase(companyId).licenseDao().getLicenseFlow(companyId).collect { local ->
                            _currentLicense.value = local
                            _isLicenseLoaded.value = true
                            if (local != null) {
                                if (local.businessName.isNotBlank() && appPreferences.shopName.firstOrNull().isNullOrBlank()) {
                                    appPreferences.saveShopName(local.businessName)
                                }
                                if (local.ownerName.isNotBlank() && appPreferences.ownerName.firstOrNull().isNullOrBlank()) {
                                    appPreferences.saveOwnerName(local.ownerName)
                                }
                            }
                        }
                    }
                    launch {
                        while (true) {
                            // The current token each time, not the one from when the loop started.
                            refreshFromBackend(companyId, userId, sessionStore.activeSession.valueOrNull()?.accessToken)
                            delay(15 * 60 * 1000L)
                        }
                    }
                    }
                }
            }
        }
    }

    private suspend fun refreshFromBackend(companyId: String, userId: String, token: String?) {
        if (token.isNullOrBlank()) return
        runCatching {
            val response = backendApi.currentLicense(token)
            val raw = response.getJSONObject("license")
            val profile = response.optJSONObject("shopProfile")
            val targetDb = tenantDatabaseManager.getDatabase(companyId)
            val existing = targetDb.licenseDao().getLicense(companyId)
            val status = raw.optString("status", "EXPIRED")
            val now = System.currentTimeMillis()
            // ShopProfile is the canonical tenant record. License values are only a migration fallback.
            val serverBusinessName = profile?.optString("shopName").orEmpty().ifBlank { raw.optString("businessName").ifBlank { existing?.businessName.orEmpty() } }
            val serverOwnerName = profile?.optString("ownerName").orEmpty().ifBlank { raw.optString("ownerName").ifBlank { existing?.ownerName.orEmpty() } }
            val businessName = serverBusinessName.ifBlank { appPreferences.shopName.firstOrNull().orEmpty() }
            val ownerName = serverOwnerName.ifBlank { appPreferences.ownerName.firstOrNull().orEmpty() }
            val entity = LicenseEntity(
                companyId = companyId,
                businessName = businessName,
                ownerName = ownerName,
                ownerMobile = raw.optString("ownerMobile", existing?.ownerMobile.orEmpty()),
                licenseStatus = if (status == "ACTIVE") "ACTIVE_PAID" else status,
                licenseType = existing?.licenseType ?: if (status == "TRIAL") "TRIAL_2_DAYS" else "YEARLY",
                yearsGranted = existing?.yearsGranted ?: 0, daysGranted = existing?.daysGranted ?: 0,
                activatedAtEpochMs = existing?.activatedAtEpochMs ?: now,
                validUntilEpochMs = raw.optLong("validUntilEpochMs"), lastVerifiedAtEpochMs = now,
                highestSeenClockEpochMs = maxOf(now, existing?.highestSeenClockEpochMs ?: 0L),
                renewalCount = existing?.renewalCount ?: 0, notes = existing?.notes.orEmpty(),
            )
            targetDb.licenseDao().saveLicense(entity)
            _currentLicense.value = entity
            if (businessName.isNotBlank()) {
                appPreferences.saveShopName(businessName)
            }
            if (ownerName.isNotBlank()) {
                appPreferences.saveOwnerName(ownerName)
            }
            if (profile != null) {
                appPreferences.saveShopDetails(
                    serverBusinessName,
                    serverOwnerName,
                    profile.optString("gstNumber"),
                    profile.optString("address"),
                    profile.optString("phone"),
                    profile.optString("email"),
                    appPreferences.shopLogoPath.firstOrNull().orEmpty()
                )
            }
            // Only this phone's own clock goes into the rollback check below. Feeding it the server's
            // time locked any shop whose phone ran more than ten minutes slow as "clock tampered".
            recordServerOrActivityTimestamp(now)
        }
    }

    /**
     * Catches the phone's date being moved back to stretch an expired license while offline.
     * Expiry itself is judged on server-corrected time (TrustedClock), so this only matters after
     * a restart; a day of slack keeps an ordinary clock correction from locking the shop.
     */
    private fun validateMonotonicClock() {
        val current = System.currentTimeMillis()
        val highest = prefs.getLong("highest_seen_clock_ms", 0L)
        _isClockTampered.value = highest > 0 && current < highest - CLOCK_ROLLBACK_TOLERANCE_MS
        if (!_isClockTampered.value && current > highest) prefs.edit().putLong("highest_seen_clock_ms", current).apply()
    }

    fun recordServerOrActivityTimestamp(timestampMs: Long) {
        if (timestampMs > prefs.getLong("highest_seen_clock_ms", 0L)) prefs.edit().putLong("highest_seen_clock_ms", timestampMs).apply()
        validateMonotonicClock()
    }

    fun startRealtimeLicenseSync(companyId: String, ownerMobile: String? = null) {
        scope.launch {
            val session = sessionStore.activeSession.valueOrNull() ?: return@launch
            refreshFromBackend(companyId, session.userId, session.accessToken)
        }
    }

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

    private companion object {
        const val CLOCK_ROLLBACK_TOLERANCE_MS = 24 * 60 * 60 * 1000L
    }
}

private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.valueOrNull(): T? = firstOrNull()
