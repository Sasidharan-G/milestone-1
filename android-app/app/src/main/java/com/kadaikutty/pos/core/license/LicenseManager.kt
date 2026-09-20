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

    /**
     * Highest wall-clock time this install has ever seen, from the device clock or a server
     * timestamp. isClockTampered above only trips past a 10-minute grace and only locks the app
     * for non-SUPER_ADMIN users; CloudAccessPolicy takes this value instead and simply refuses to
     * read time as earlier than it, so a rollback cannot push a cloud-access deadline away.
     */
    private val _highestSeenClockMs = MutableStateFlow<Long?>(null)
    val highestSeenClockMs: StateFlow<Long?> = _highestSeenClockMs.asStateFlow()
    private var refreshJob: Job? = null

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
            sessionStore.activeSession.collect { session ->
                refreshJob?.cancel()
                if (session == null) {
                    _currentLicense.value = null
                    _isLicenseLoaded.value = false
                    lastCompanyId = null
                    appPreferences.clearShopDetails()
                } else {
                    if (lastCompanyId != session.companyId) {
                        lastCompanyId = session.companyId
                        appPreferences.clearShopDetails()
                    }
                    refreshJob = launch {
                        tenantDatabaseManager.getDatabase(session.companyId).licenseDao().getLicenseFlow(session.companyId).collect { local ->
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
                            refreshFromBackend(session.companyId, session.userId, session.accessToken)
                            delay(15 * 60 * 1000L)
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
            recordServerOrActivityTimestamp(raw.optLong("updatedAtEpochMs", now))

            // This poll succeeding IS the "online check-in" — push the rolling 7-day connectivity
            // deadline forward and mirror Master's granted cloud-access date, all purely local so
            // CloudAccessPolicy can keep enforcing it even the next time this device is offline.
            val cloudAccess = response.optJSONObject("cloudAccess")
            if (cloudAccess != null) {
                val userDao = targetDb.userDao()
                val localUser = userDao.getUserById(userId)
                if (localUser != null) {
                    userDao.updateUser(localUser.copy(
                        isCloudTier = cloudAccess.optBoolean("isCloudTier", true),
                        cloudAccessGrantedUntilEpochMs = cloudAccess.optLong("grantedUntilEpochMs", 0L).takeIf { it > 0L },
                        mustCheckInByEpochMs = com.kadaikutty.pos.core.security.CloudAccessPolicy.nextCheckInDeadline(now)
                    ))
                }
            }
        }
    }

    private fun validateMonotonicClock() {
        val current = System.currentTimeMillis()
        val highest = prefs.getLong("highest_seen_clock_ms", 0L)
        _isClockTampered.value = highest > 0 && current < highest - 10 * 60 * 1000L
        if (!_isClockTampered.value && current > highest) prefs.edit().putLong("highest_seen_clock_ms", current).apply()
        _highestSeenClockMs.value = prefs.getLong("highest_seen_clock_ms", 0L).takeIf { it > 0L }
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

}

private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.valueOrNull(): T? = firstOrNull()
