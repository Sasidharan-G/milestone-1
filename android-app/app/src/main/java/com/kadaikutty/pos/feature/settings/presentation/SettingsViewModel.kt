package com.kadaikutty.pos.feature.settings.presentation

import kotlinx.coroutines.sync.withLock
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.kadaikutty.pos.core.preferences.AppPreferences
import com.kadaikutty.pos.core.printer.data.PrinterManager
import com.kadaikutty.pos.core.printer.domain.PrintDocument
import com.kadaikutty.pos.core.printer.domain.PrintLine
import com.kadaikutty.pos.core.printer.domain.PrinterResult
import com.kadaikutty.pos.core.security.BiometricAuthenticator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.kadaikutty.pos.core.backup.data.BackupManager
import com.kadaikutty.pos.core.backup.data.LiveBackupWriter
import com.kadaikutty.pos.core.backup.domain.BackupResult
import com.kadaikutty.pos.core.sharing.ShareManager
import com.kadaikutty.pos.core.sync.SyncScheduler

data class BluetoothDeviceInfo(val name: String, val address: String)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val printerManager: PrinterManager,
    private val backupManager: BackupManager,
    private val syncScheduler: SyncScheduler,
    private val syncManager: com.kadaikutty.pos.core.sync.SyncManager,
    private val database: com.kadaikutty.pos.core.database.BillingDatabase,
    private val sessionStore: com.kadaikutty.pos.core.auth.SessionStore,
    private val licenseManager: com.kadaikutty.pos.core.license.LicenseManager,
    private val sessionSecurityManager: com.kadaikutty.pos.core.auth.SessionSecurityManager,
    private val backendApi: com.kadaikutty.pos.core.network.BackendApiClient,
    private val tenantDatabaseManager: com.kadaikutty.pos.core.database.TenantDatabaseManager,
    private val liveBackupWriter: LiveBackupWriter,
    private val offlineCredentialStore: com.kadaikutty.pos.core.auth.OfflineCredentialStore,
    private val shareManager: ShareManager,
    private val webSocketManager: com.kadaikutty.pos.core.network.WebSocketManager,
    connectivityMonitor: com.kadaikutty.pos.core.network.ConnectivityMonitor,
    private val shopLogoSyncer: com.kadaikutty.pos.core.branding.ShopLogoSyncer,
) : ViewModel() {

    /** Server reachable right now. Billing works either way; this gates the online-only actions. */
    val isOnline: StateFlow<Boolean> = connectivityMonitor.isOnline

    val isSessionTerminated: StateFlow<Boolean> = sessionSecurityManager.isSessionTerminated
    val terminationReason: StateFlow<String?> = sessionSecurityManager.terminationReason

    val syncNotificationState: StateFlow<com.kadaikutty.pos.core.sync.SyncNotificationState> = syncScheduler.syncNotificationFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = com.kadaikutty.pos.core.sync.SyncNotificationState.Idle
        )

    fun dismissSyncNotification() {
        syncScheduler.dismissNotification()
    }

    fun retrySync() {
        viewModelScope.launch {
            val session = sessionStore.activeSession.first()
            if (session != null) {
                // Manual retry: ignore the dead-letter cap the background worker honours.
                database.syncQueueDao().retryFailed(session.companyId, System.currentTimeMillis(), Int.MAX_VALUE)
            }
            syncScheduler.request(replaceExisting = true)
        }
    }

    init {
        viewModelScope.launch {
            sessionStore.activeSession.collectLatest { session ->
                if (session != null && !session.sessionToken.isNullOrBlank()) {
                    sessionSecurityManager.startListeningToSession()
                }
                if (session != null && (session.role == "ADMIN" || session.role == "SUPER_ADMIN")) {
                    runCatching { reconcileStaffAccounts(session) }
                }
            }
        }
    }

    // Self-heals staff accounts that diverged from the server (e.g. devices that created a staff
    // member back when local Room IDs and server IDs weren't guaranteed to match). Runs whenever
    // an admin's session becomes active; safe to call repeatedly and a no-op when nothing diverged.
    // See server GET /staff (admin-only, company-scoped) for the authoritative staff list.
    private suspend fun reconcileStaffAccounts(session: com.kadaikutty.pos.core.auth.Session) {
        val token = resolveActiveToken() ?: return
        val companyId = session.companyId
        val serverStaffJson = runCatching { backendApi.listStaff(token) }.getOrNull() ?: return

        val serverStaff = (0 until serverStaffJson.length()).map { index ->
            val obj = serverStaffJson.getJSONObject(index)
            val permsArray = obj.optJSONArray("permissions")
            val perms = if (permsArray != null) (0 until permsArray.length()).joinToString(",") { permsArray.getString(it) } else ""
            com.kadaikutty.pos.core.auth.ServerStaffRecord(
                userId = obj.getString("userId"),
                phone = obj.optString("phone"),
                displayName = obj.optString("displayName"),
                role = obj.optString("role", "CASHIER"),
                permissions = perms,
                status = obj.optString("status", "ACTIVE")
            )
        }

        val targetDb = tenantDatabaseManager.getDatabase(companyId)
        val localUsers = targetDb.userDao().getUsersByCompany(companyId)
        val actions = com.kadaikutty.pos.core.auth.StaffReconciler.plan(localUsers, serverStaff, session.userId, companyId)
        if (actions.isEmpty()) return

        targetDb.withTransaction {
            for (action in actions) {
                when (action) {
                    is com.kadaikutty.pos.core.auth.StaffReconciliationAction.Delete -> {
                        targetDb.userDao().getUserById(action.localId)?.let { offlineCredentialStore.remove(it.username) }
                        targetDb.userDao().deleteUserById(action.localId)
                    }
                    is com.kadaikutty.pos.core.auth.StaffReconciliationAction.Rekey -> {
                        targetDb.userDao().deleteUserById(action.local.id)
                        targetDb.userDao().insertUser(action.local.copy(
                            id = action.server.userId,
                            displayName = action.server.displayName,
                            permissions = action.server.permissions
                        ))
                        offlineCredentialStore.rekey(action.local.username, action.local.id, action.server.userId)
                    }
                    is com.kadaikutty.pos.core.auth.StaffReconciliationAction.UpdateFields -> {
                        targetDb.userDao().updateUser(action.local.copy(
                            displayName = action.server.displayName,
                            permissions = action.server.permissions
                        ))
                    }
                    is com.kadaikutty.pos.core.auth.StaffReconciliationAction.InsertMissing -> {
                        targetDb.userDao().insertUser(com.kadaikutty.pos.core.auth.UserEntity(
                            id = action.server.userId,
                            username = com.kadaikutty.pos.core.auth.StaffReconciler.normalizePhone(action.server.phone),
                            displayName = action.server.displayName,
                            permissions = action.server.permissions,
                            companyId = action.companyId,
                            role = action.server.role,
                            lastOnlineVerifiedAt = 0L
                        ))
                    }
                }
            }
        }
    }

    fun acknowledgeSessionTermination() {
        sessionSecurityManager.resetSessionTermination()
        viewModelScope.launch {
            sessionStore.clear()
        }
    }

    private val _isDeletingAccount = MutableStateFlow(false)
    val isDeletingAccount: StateFlow<Boolean> = _isDeletingAccount.asStateFlow()

    /**
     * Deletes the owner's account and the whole shop: first in the cloud (which also signs every
     * other device out), then everything on this phone. Cloud first, so a failure - no internet,
     * wrong PIN - leaves this phone exactly as it was. [onResult] gets null on success, otherwise
     * a message to show. Google Play requires this to be possible from inside the app.
     */
    fun deleteAccount(pin: String, onResult: (String?) -> Unit) {
        if (_isDeletingAccount.value) return
        _isDeletingAccount.value = true
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var failure: String? = null
            try {
                val session = sessionStore.activeSession.first() ?: throw IllegalStateException("You are signed out. Sign in again.")
                var token = session.accessToken?.trim()?.takeIf { it.isNotBlank() }
                if (token == null) token = backendApi.autoRecoverSession(forceRefresh = false)?.first
                if (token.isNullOrBlank()) throw IllegalStateException("Connect to the internet and sign in again to delete the account.")

                // The server signs this device out as part of the deletion; without closing the
                // socket first that "signed in elsewhere" notice would greet the login screen.
                webSocketManager.disconnect()
                backendApi.deleteAccount(token, pin)

                // The cloud side is gone and every device is signed out. Nothing may sync now, and
                // whatever is on this phone has to go too, sign-in included.
                syncScheduler.cancelAllWork()
                com.kadaikutty.pos.core.sync.SyncLock.mutex.withLock {
                    val tenantDb = tenantDatabaseManager.getDatabase(session.companyId)
                    for (target in listOfNotNull(tenantDb, if (tenantDb != database) database else null)) {
                        com.kadaikutty.pos.core.sync.ShopDataWiper.wipe(target, includeAccount = true)
                    }
                }
                runCatching { offlineCredentialStore.removeByCompanyId(session.companyId) }
                runCatching { offlineCredentialStore.removeByUserId(session.userId) }
                runCatching { appPreferences.clearShopDetails() }
                sessionStore.clear()
                sessionSecurityManager.resetSessionTermination()
            } catch (e: com.kadaikutty.pos.core.network.BackendApiException) {
                failure = when (e.code) {
                    "ACCOUNT_DELETE_PIN_INVALID" -> "The PIN is incorrect."
                    "STAFF_ADMIN_REQUIRED" -> "Only the shop owner can delete the account."
                    else -> e.message
                }
            } catch (e: Exception) {
                failure = e.message ?: "Could not delete the account."
            } finally {
                _isDeletingAccount.value = false
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(failure) }
        }
    }

    fun clearAllDatabase(clearCloudToo: Boolean, onResult: (Boolean) -> Unit) {
        // Unlike its sibling backup/restore operations (all sharing this same _isRestoreRunning
        // flag), this one had no reentrancy guard at all - a fast double-tap on "Yes, Clear Data"
        // could fire two concurrent wipes of the local (and possibly cloud) database.
        if (_isRestoreRunning.value || _isBackupRunning.value) return
        requireBiometricAuth {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                _isRestoreRunning.value = true
                _restoreStatus.value = "Safely clearing database records..."
                try {
                    // Defense in depth: re-check here (not just at the UI layer) so this guard
                    // can't be bypassed by any future or alternate caller of this function — and
                    // check it before anything destructive runs, not after the local DB is wiped.
                    if (clearCloudToo && !hasRecentSafetyBackup()) {
                        throw Exception("Refusing to clear cloud data without a recent backup")
                    }

                    // Cancel any active sync workers so they don't upload/download stale data during reset
                    syncScheduler.cancelAllWork()

                    // A push or pull already running is only cancelled cooperatively; holding the
                    // sync lock makes sure none is still writing while the tables are emptied.
                    var purgedEpoch: Long? = null
                    com.kadaikutty.pos.core.sync.SyncLock.mutex.withLock {
                    val session = sessionStore.activeSession.first()
                    val companyId = session?.companyId ?: "company_main"
                    val tenantDb = tenantDatabaseManager.getDatabase(companyId)
                    val dbsToClear = listOfNotNull(tenantDb, if (tenantDb != database) database else null)

                    // Cloud first. If the purge fails nothing local has been touched yet; wiping
                    // first left an empty device that the next pull silently refilled from the
                    // cloud copy that was supposed to be gone.
                    if (clearCloudToo) {
                        _restoreStatus.value = "Purging cloud database records..."
                        var activeToken = session?.accessToken?.trim()?.takeIf { it.isNotBlank() }
                        if (activeToken.isNullOrBlank()) {
                            val recovered = backendApi.autoRecoverSession(forceRefresh = false)
                            activeToken = recovered?.first
                        }

                        if (!activeToken.isNullOrBlank()) {
                            val purgeResult = runCatching {
                                backendApi.purgeCloudData(activeToken)
                            }
                            purgedEpoch = purgeResult.getOrNull()?.optLong("epoch", 0L)
                            if (purgeResult.isFailure) {
                                val err = purgeResult.exceptionOrNull()?.message ?: "Unknown cloud error"
                                android.util.Log.e("SettingsViewModel", "Failed to purge cloud records: $err", purgeResult.exceptionOrNull())
                                throw Exception("Cloud database purge failed: $err")
                            }
                        } else {
                            throw Exception("No active session found to purge cloud database records.")
                        }

                    }

                    for (targetDb in dbsToClear) com.kadaikutty.pos.core.sync.ShopDataWiper.wipe(targetDb)
                    // This device is already at the new data generation; without this its next
                    // pull would see a newer epoch than it knows and wipe itself a second time.
                    purgedEpoch?.let { com.kadaikutty.pos.core.sync.PullWorker.rememberEpoch(tenantDb, companyId, it) }

                    if (clearCloudToo) runCatching { appPreferences.clearShopDetails() }
                    }

                    _restoreStatus.value = if (clearCloudToo) {
                        "Database and cloud records cleared successfully. You can now start fresh."
                    } else {
                        "Database cleared successfully. You can now start fresh."
                    }
                    withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(true) }
                } catch (e: Exception) {
                    _restoreStatus.value = "Clear error: ${e.message}"
                    withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(false) }
                } finally {
                    // cancelAllWork() above also stopped the periodic sync, pull and daily cloud
                    // backup; nothing rescheduled them until the app happened to restart.
                    runCatching {
                        syncScheduler.schedulePeriodicSync()
                        syncScheduler.schedulePeriodicLiveBackupCompaction()
                        if (!appPreferences.liveBackupFolderUri.first().isNullOrBlank()) syncScheduler.schedulePeriodicBackup()
                    }
                    _isRestoreRunning.value = false
                }
            }
        }
    }



    // Biometric authentication state
    private val _biometricAuthPending = MutableStateFlow<(() -> Unit)?>(null)
    val biometricAuthPending: StateFlow<(() -> Unit)?> = _biometricAuthPending

    fun requireBiometricAuth(onAuthenticated: () -> Unit) {
        _biometricAuthPending.value = onAuthenticated
    }

    fun clearBiometricAuthPending() {
        _biometricAuthPending.value = null
    }

    fun onBiometricAuthFailed(error: String) {
        _restoreStatus.value = "Authentication failed: $error"
    }

    val isLoggedIn: StateFlow<Boolean?> = sessionStore.activeSession
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val printerType: StateFlow<String?> = appPreferences.printerType.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "Bluetooth"
    )
    val savedPrinters: StateFlow<List<com.kadaikutty.pos.core.preferences.SavedPrinter>> = appPreferences.savedPrinters.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val printerDeviceId: StateFlow<String?> = appPreferences.printerDeviceId.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val printerPaperWidth: StateFlow<Int> = appPreferences.printerPaperWidth.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 32
    )

    val autoPrintReceipt: StateFlow<Boolean> = appPreferences.autoPrintReceipt.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val shareBillFormat: StateFlow<String> = appPreferences.shareBillFormat.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "A4"
    )

    fun setShareBillFormat(format: String) {
        viewModelScope.launch { appPreferences.saveShareBillFormat(format) }
    }

    fun setAutoPrintReceipt(enabled: Boolean) {
        viewModelScope.launch { appPreferences.saveAutoPrintReceipt(enabled) }
    }

    val printLogoOnReceipt: StateFlow<Boolean> = appPreferences.printLogoOnReceipt.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    fun setPrintLogoOnReceipt(enabled: Boolean) {
        viewModelScope.launch { appPreferences.savePrintLogoOnReceipt(enabled) }
    }

    val layoutMode: StateFlow<String> = appPreferences.layoutMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "Grid"
    )

    val productGridView: StateFlow<Boolean> = appPreferences.productGridView.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    fun setProductGridView(enabled: Boolean) {
        viewModelScope.launch { appPreferences.saveProductGridView(enabled) }
    }

    val activeSession: StateFlow<com.kadaikutty.pos.core.auth.Session?> = sessionStore.activeSession.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val usersList: StateFlow<List<com.kadaikutty.pos.core.auth.UserEntity>> = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId.orEmpty()
            if (companyId.isBlank()) {
                flowOf(emptyList())
            } else {
                database.userDao().getUsersFlowByCompany(companyId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val currentLicense: StateFlow<com.kadaikutty.pos.core.license.LicenseEntity?> = licenseManager.currentLicense
    val isLicenseLoaded: StateFlow<Boolean> = licenseManager.isLicenseLoaded
    val isClockTampered: StateFlow<Boolean> = licenseManager.isClockTampered

    private var lastLicenseRefreshMs = 0L

    fun shouldShowRenewalAlert(): Boolean = licenseManager.shouldShowDailyRenewalAlert()
    fun markRenewalAlertShown() = licenseManager.recordRenewalAlertShown()
    fun refreshLicenseStatus() {
        val now = System.currentTimeMillis()
        if (now - lastLicenseRefreshMs < 120_000L) return
        lastLicenseRefreshMs = now
        val session = activeSession.value
        if (session != null) {
            licenseManager.startRealtimeLicenseSync(session.companyId, session.userId)
        }
    }

    fun logout(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            sessionStore.clear()
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                onComplete()
            }
        }
    }

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val companyId = sessionStore.activeSession.first()?.companyId ?: "company_main"
            val targetDb = tenantDatabaseManager.getDatabase(companyId)
            val lic = targetDb.licenseDao().getLicense(companyId) ?: database.licenseDao().getActiveLicense()
            if (lic != null && lic.businessName.isNotBlank()) {
                val currentPref = appPreferences.shopName.first()
                if (currentPref.isBlank()) {
                    appPreferences.saveShopName(lic.businessName)
                    if (lic.ownerName.isNotBlank()) {
                        appPreferences.saveOwnerName(lic.ownerName)
                    }
                }
            }
        }
    }

    val shopName: StateFlow<String> = appPreferences.shopName.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val ownerName: StateFlow<String> = appPreferences.ownerName.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val gstNumber: StateFlow<String> = appPreferences.gstNumber.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val shopAddress: StateFlow<String> = appPreferences.shopAddress.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val shopPhone: StateFlow<String> = appPreferences.shopPhone.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val shopEmail: StateFlow<String> = appPreferences.shopEmail.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val shopLogoPath: StateFlow<String> = appPreferences.shopLogoPath.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    fun saveShopDetails(name: String, owner: String, gst: String, address: String, phone: String, email: String, logoPath: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val session = sessionStore.activeSession.first()
            val token = session?.accessToken
            if (token.isNullOrBlank()) {
                withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(false, "Please sign in again before saving shop details") }
                return@launch
            }
            val response = runCatching { backendApi.updateShopProfile(token, name, owner, gst, address, phone, email) }
            if (response.isFailure) {
                withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(false, response.exceptionOrNull()?.message ?: "Cloud sync failed") }
                return@launch
            }
            val profile = response.getOrThrow().optJSONObject("shopProfile")
            val savedName = profile?.optString("shopName").orEmpty().ifBlank { name }
            val savedOwner = profile?.optString("ownerName").orEmpty().ifBlank { owner }
            appPreferences.saveShopDetails(savedName, savedOwner, profile?.optString("gstNumber") ?: gst, profile?.optString("address") ?: address, profile?.optString("phone") ?: phone, profile?.optString("email") ?: email, logoPath)
            // The logo is kept in the cloud as well (a new one is uploaded, a removed one is removed). If that cannot
            // happen right now the details are still saved; the logo stays pending and is retried on the next refresh.
            val logoWarning = try {
                shopLogoSyncer.sync(profile)
                null
            } catch (e: Exception) {
                "Saved. The logo could not reach the cloud yet (${e.message ?: "no connection"}); it will retry by itself."
            }
            val companyId = session?.companyId ?: "company_main"
            val targetDb = tenantDatabaseManager.getDatabase(companyId)
            val lic = targetDb.licenseDao().getLicense(companyId) ?: database.licenseDao().getActiveLicense()
            if (lic != null) {
                val updated = lic.copy(businessName = savedName, ownerName = savedOwner)
                targetDb.licenseDao().saveLicense(updated)
                database.licenseDao().saveLicense(updated)
            } else {
                val newLic = com.kadaikutty.pos.core.license.LicenseEntity(
                    companyId = companyId,
                    businessName = savedName,
                    ownerName = savedOwner,
                    licenseStatus = "ACTIVE_PAID"
                )
                targetDb.licenseDao().saveLicense(newLic)
                database.licenseDao().saveLicense(newLic)
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(true, logoWarning) }
        }
    }

    fun processAndSaveLogo(context: android.content.Context, uri: android.net.Uri): String? =
        com.kadaikutty.pos.core.branding.LogoImages.saveScaled(context, uri)

    private val _bluetoothDevices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    val bluetoothDevices: StateFlow<List<BluetoothDeviceInfo>> = _bluetoothDevices.asStateFlow()

    private val _printStatus = MutableStateFlow<String?>(null)
    val printStatus: StateFlow<String?> = _printStatus.asStateFlow()

    init {
        loadPairedBluetoothDevices()
        viewModelScope.launch {
            sessionStore.activeSession.collect { session ->
                if (session != null && session.userId != "admin-user") {
                    syncScheduler.schedulePeriodicSync()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun loadPairedBluetoothDevices() {
        viewModelScope.launch {
            try {
                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val adapter = bluetoothManager?.adapter
                if (adapter != null && adapter.isEnabled) {
                    val bonded = adapter.bondedDevices
                    val list = bonded.map { BluetoothDeviceInfo(it.name ?: "Unknown Device", it.address) }
                    _bluetoothDevices.value = list
                }
            } catch (e: SecurityException) {
                _bluetoothDevices.value = emptyList()
            } catch (e: Exception) {
                _bluetoothDevices.value = emptyList()
            }
        }
    }

    fun saveSettings(type: String, deviceId: String, paperWidth: Int) {
        viewModelScope.launch {
            appPreferences.savePrinterSettings(type, deviceId, paperWidth)
        }
    }

    /** The paper size alone, when no printer has been chosen yet. */
    fun savePaperWidth(paperWidth: Int) {
        viewModelScope.launch {
            appPreferences.savePaperWidth(paperWidth)
        }
    }

    fun clearPrintStatus() {
        _printStatus.value = null
    }

    fun clearBackupRestoreStatus() {
        _backupStatus.value = null
        _restoreStatus.value = null
    }

    fun saveLayoutMode(mode: String) {
        viewModelScope.launch {
            appPreferences.saveLayoutMode(mode)
        }
    }

    fun printTestReceipt(onResult: (String) -> Unit) {
        viewModelScope.launch {
            _printStatus.value = "Preparing print job..."
            
            val typeStr = printerType.value ?: "Bluetooth"
            val deviceId = printerDeviceId.value
            if (deviceId.isNullOrBlank()) {
                onResult("Error: No printer device selected in settings")
                _printStatus.value = "Print failed: Device not selected"
                return@launch
            }

            val pType = PrinterManager.PrinterType.fromSetting(typeStr)

            _printStatus.value = "Printing test receipt..."
            // The saved paper size and logo, exactly as a real bill uses them: a test page that ignored the
            // paper size printed a narrow strip on an 80 mm printer and proved nothing about the real bill.
            val paperWidth = appPreferences.printerPaperWidth.first()
            val logoPath = if (appPreferences.printLogoOnReceipt.first()) appPreferences.shopLogoPath.first() else ""
            val testDoc = PrintDocument(
                title = appPreferences.shopName.first().ifBlank { "TEST RECEIPT" },
                headers = listOf("TEST RECEIPT", "Paper: ${com.kadaikutty.pos.core.printer.domain.PaperWidth.label(paperWidth)}"),
                lines = listOf(
                    PrintLine("Test Thermal Output", 1, "₹0.00", "₹0.00"),
                    PrintLine("A long product name to check that long lines wrap neatly on this paper", 2, "₹10.00", "₹20.00")
                ),
                totals = listOf(
                    "TOTAL AMOUNT" to "₹20.00"
                ),
                footer = "Thank you for verifying!",
                paperWidth = paperWidth,
                logoPath = logoPath
            )

            // printJob holds PrinterJobLock and always disconnects in its finally block. The old
            // unlocked connect/print/disconnect here raced printJob, so a second tap could close
            // the first job's socket mid-write.
            val printResult = printerManager.printJob(pType, deviceId, testDoc)

            if (printResult is PrinterResult.Success) {
                onResult("Printed successfully!")
                _printStatus.value = "Success"
            } else {
                val err = (printResult as PrinterResult.Failure).error
                onResult("Print failed: ${err.message}")
                _printStatus.value = "Failed"
            }
        }
    }

    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus: StateFlow<String?> = _backupStatus.asStateFlow()

    private val _restoreStatus = MutableStateFlow<String?>(null)
    val restoreStatus: StateFlow<String?> = _restoreStatus.asStateFlow()

    private val _isBackupRunning = MutableStateFlow(false)
    val isBackupRunning: StateFlow<Boolean> = _isBackupRunning.asStateFlow()

    private val _isRestoreRunning = MutableStateFlow(false)
    val isRestoreRunning: StateFlow<Boolean> = _isRestoreRunning.asStateFlow()

    private val preRestoreSafetyFile: java.io.File
        get() = java.io.File(context.filesDir, "pre_restore_safety.zip")

    private val _canUndoLastRestore = MutableStateFlow(false)
    val canUndoLastRestore: StateFlow<Boolean> = _canUndoLastRestore.asStateFlow()

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _canUndoLastRestore.value = preRestoreSafetyFile.exists()
        }
    }

    // Best-effort: a failure here must never block the restore itself. Worst case, "Undo Last
    // Restore" is unavailable afterward, which is strictly better than the old "no undo at all".
    /**
     * A restore puts back records that sync may never have seen: ones made offline and never sent,
     * and the ones a replayed Auto Backup change history writes straight into the tables. So every
     * record is queued once; the server acknowledges the ones it already has and settles real
     * differences by edit time, like any other edit.
     *
     * The pull position and data epoch are forgotten too. A backup taken before the cloud copy was
     * cleared carries the old epoch, and the next pull would otherwise wipe the data just restored.
     * Without either, the pull adopts the current epoch and reads the cloud again from the start.
     */
    private suspend fun afterRestore() {
        // The restore itself has already succeeded; failing here must not report it as failed.
        // Anything left unqueued is still sent the next time that record is edited.
        runCatching {
            val companyId = sessionStore.activeSession.first()?.companyId?.takeIf { it.isNotBlank() } ?: return
            val dao = tenantDatabaseManager.getDatabase(companyId).localOperationDao()
            dao.delete(companyId, com.kadaikutty.pos.core.sync.PullWorker.EPOCH_KEY)
            dao.delete(companyId, com.kadaikutty.pos.core.sync.PullWorker.CURSOR_KEY)
            syncManager.enqueueAllDataForSync()
        }
    }

    private suspend fun writePreRestoreSafetySnapshot() {
        runCatching {
            when (val result = backupManager.createBackup()) {
                is BackupResult.Success -> {
                    preRestoreSafetyFile.writeBytes(result.zipBytes)
                    _canUndoLastRestore.value = true
                }
                is BackupResult.Failure -> Unit
            }
        }
    }

    // Makes a fresh snapshot and hands it straight to Android's share sheet, so it can go to
    // WhatsApp/USB/Drive/email etc. with no folder setup and no internet dependency — the one
    // backup path that works standalone, independent of Auto Backup or the cloud.
    fun exportBackupNow(onFinished: (Boolean) -> Unit = {}) {
        if (_isBackupRunning.value || _isRestoreRunning.value) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isBackupRunning.value = true
            _backupStatus.value = "Preparing backup to share..."
            when (val result = backupManager.createBackup()) {
                is BackupResult.Success -> {
                    val filename = "billing_backup_${System.currentTimeMillis()}.zip"
                    val shared = shareManager.shareFile(result.zipBytes, filename, "application/zip")
                    if (shared) {
                        appPreferences.saveLastBackupTimestamp(System.currentTimeMillis())
                        _backupStatus.value = "Backup ready — choose where to save or send it."
                    } else {
                        _backupStatus.value = "Could not open the share screen. Please try again."
                    }
                    withContext(kotlinx.coroutines.Dispatchers.Main) { onFinished(shared) }
                }
                is BackupResult.Failure -> {
                    _backupStatus.value = "Backup failed: ${result.exception.message}"
                    withContext(kotlinx.coroutines.Dispatchers.Main) { onFinished(false) }
                }
            }
            _isBackupRunning.value = false
        }
    }

    private val _requireRestart = MutableStateFlow(false)
    val requireRestart: StateFlow<Boolean> = _requireRestart.asStateFlow()

    fun runRestore(uri: android.net.Uri, onFinished: (Boolean) -> Unit) {
        if (_isRestoreRunning.value || _isBackupRunning.value) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isRestoreRunning.value = true
            _restoreStatus.value = "Restoring database backup..."
            try {
                var bytes: ByteArray? = null
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    bytes = inputStream.readBytes()
                }
                if (bytes != null) {
                    writePreRestoreSafetySnapshot()
                    val success = backupManager.restoreBackup(bytes!!)
                    if (success) {
                        afterRestore()
                        _restoreStatus.value = "Database restored successfully! App will restart."
                        _requireRestart.value = true
                        withContext(kotlinx.coroutines.Dispatchers.Main) { onFinished(true) }
                    } else {
                        _restoreStatus.value = "Restore failed: Invalid or empty backup archive. Please select a valid backup .zip file."
                        withContext(kotlinx.coroutines.Dispatchers.Main) { onFinished(false) }
                    }
                } else {
                    _restoreStatus.value = "Restore failed: Could not read file."
                    withContext(kotlinx.coroutines.Dispatchers.Main) { onFinished(false) }
                }
            } catch (e: Exception) {
                _restoreStatus.value = "Restore failed: ${e.message}"
                withContext(kotlinx.coroutines.Dispatchers.Main) { onFinished(false) }
            } finally {
                _isRestoreRunning.value = false
            }
        }
    }

    val lastBackupAtEpochMs: StateFlow<Long?> = appPreferences.lastBackupAtEpochMs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private suspend fun resolveActiveToken(): String? {
        val session = sessionStore.activeSession.first()
        val direct = session?.accessToken?.trim()?.takeIf { it.isNotBlank() }
        if (!direct.isNullOrBlank()) return direct
        return runCatching { backendApi.autoRecoverSession(forceRefresh = false)?.first }.getOrNull()
    }

    companion object {
        // How fresh a backup must be to satisfy the "Clear Cloud Data" hard guard (Phase 5).
        const val SAFETY_BACKUP_FRESHNESS_MS = 3 * 24 * 60 * 60 * 1000L
    }

    // True if any of: a manual local/cloud backup, an actively-mirroring live local backup, or a
    // real cloud snapshot is fresher than SAFETY_BACKUP_FRESHNESS_MS. Used to hard-block "Clear
    // Cloud Data" (see clearAllDatabase) so that action can never run without a recent recovery
    // path already in place.
    suspend fun hasRecentSafetyBackup(): Boolean {
        val cutoff = System.currentTimeMillis() - SAFETY_BACKUP_FRESHNESS_MS

        val lastManualBackup = appPreferences.lastBackupAtEpochMs.first()
        if (lastManualBackup != null && lastManualBackup >= cutoff) return true

        val lastLiveBackupWrite = appPreferences.liveBackupLastWriteAtEpochMs.first()
        if (lastLiveBackupWrite != null && lastLiveBackupWrite >= cutoff) return true

        val token = resolveActiveToken()
        if (!token.isNullOrBlank()) {
            val newestCloudBackupAt = runCatching {
                val backups = backendApi.listBackups(token)
                if (backups.length() > 0) backups.getJSONObject(0).optLong("createdAtEpochMs") else null
            }.getOrNull()
            if (newestCloudBackupAt != null && newestCloudBackupAt >= cutoff) return true
        }

        return false
    }

    // Guards the actual cloud upload specifically (distinct from _isBackupRunning, which also covers
    // the local-setup step of setupLiveBackup) so the upload itself can't run twice concurrently no
    // matter which caller triggers it.
    private val _isCloudUploadRunning = MutableStateFlow(false)

    fun runCloudBackup(onFinished: (Boolean) -> Unit = {}) {
        if (_isBackupRunning.value || _isRestoreRunning.value) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isBackupRunning.value = true
            try {
                runCloudBackupBody(onFinished)
            } finally {
                _isBackupRunning.value = false
            }
        }
    }

    // The actual upload. setupLiveBackup() fires this as a separate fire-and-forget launch (matching
    // its original non-blocking behavior) rather than through the guarded runCloudBackup() above, so
    // this has its own _isCloudUploadRunning guard to still prevent two concurrent uploads.
    private suspend fun runCloudBackupBody(onFinished: (Boolean) -> Unit) {
        if (_isCloudUploadRunning.value) {
            withContext(kotlinx.coroutines.Dispatchers.Main) { onFinished(false) }
            return
        }
        _isCloudUploadRunning.value = true
        _backupStatus.value = "Creating backup package..."
        try {
            val token = resolveActiveToken()
            if (token.isNullOrBlank()) {
                _backupStatus.value = "Backup failed: User not logged in."
                withContext(kotlinx.coroutines.Dispatchers.Main) { onFinished(false) }
                return
            }
            when (val result = backupManager.createBackup()) {
                is BackupResult.Success -> {
                    _backupStatus.value = "Uploading backup to cloud..."
                    backendApi.uploadBackup(token, "billing_backup_${System.currentTimeMillis()}.zip", result.schemaVersion, result.zipBytes)
                    appPreferences.saveLastBackupTimestamp(System.currentTimeMillis())
                    _backupStatus.value = "Cloud backup created successfully!"
                    withContext(kotlinx.coroutines.Dispatchers.Main) { onFinished(true) }
                }
                is BackupResult.Failure -> {
                    _backupStatus.value = "Backup failed: ${result.exception.message}"
                    withContext(kotlinx.coroutines.Dispatchers.Main) { onFinished(false) }
                }
            }
        } catch (e: Exception) {
            _backupStatus.value = "Cloud backup failed: ${e.message}"
            withContext(kotlinx.coroutines.Dispatchers.Main) { onFinished(false) }
        } finally {
            _isCloudUploadRunning.value = false
        }
    }

    fun runCloudRestore(onFinished: (Boolean) -> Unit) {
        if (_isRestoreRunning.value || _isBackupRunning.value) return
        requireBiometricAuth {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                _isRestoreRunning.value = true
                _restoreStatus.value = "Downloading latest cloud backup..."
                try {
                    val token = resolveActiveToken()
                    if (token.isNullOrBlank()) {
                        _restoreStatus.value = "Restore failed: User not logged in."
                        withContext(kotlinx.coroutines.Dispatchers.Main) { onFinished(false) }
                        return@launch
                    }
                    val bytes = backendApi.downloadLatestBackup(token)
                    _restoreStatus.value = "Restoring database from cloud backup..."
                    writePreRestoreSafetySnapshot()
                    val success = backupManager.restoreBackup(bytes)
                    if (success) {
                        afterRestore()
                        _restoreStatus.value = "Database restored from cloud successfully! App will restart."
                        _requireRestart.value = true
                        withContext(kotlinx.coroutines.Dispatchers.Main) { onFinished(true) }
                    } else {
                        _restoreStatus.value = "Restore failed: Invalid or corrupted cloud backup."
                        withContext(kotlinx.coroutines.Dispatchers.Main) { onFinished(false) }
                    }
                } catch (e: IllegalArgumentException) {
                    _restoreStatus.value = "No cloud backup found yet. Back up to cloud first."
                    withContext(kotlinx.coroutines.Dispatchers.Main) { onFinished(false) }
                } catch (e: Exception) {
                    _restoreStatus.value = "Cloud restore failed: ${e.message}"
                    withContext(kotlinx.coroutines.Dispatchers.Main) { onFinished(false) }
                } finally {
                    _isRestoreRunning.value = false
                }
            }
        }
    }

    val liveBackupFolderUri: StateFlow<String?> = appPreferences.liveBackupFolderUri.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val liveBackupLastWriteAtEpochMs: StateFlow<Long?> = appPreferences.liveBackupLastWriteAtEpochMs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun setupLiveBackup(treeUri: android.net.Uri, onFinished: (Boolean) -> Unit) {
        if (_isBackupRunning.value || _isRestoreRunning.value) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isBackupRunning.value = true
            _backupStatus.value = "Setting up live local backup..."
            val result = liveBackupWriter.setup(treeUri)
            if (result.isSuccess) {
                _backupStatus.value = "Live local backup set up! Every change will now be mirrored to this folder automatically."
                // Live local backup alone still leaves a single-device risk (phone lost/damaged), so
                // every setup also takes an immediate cloud snapshot and registers a daily one.
                syncScheduler.schedulePeriodicBackup()
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { runCloudBackupBody {} }
            } else {
                _backupStatus.value = "Live local backup setup failed: ${result.exceptionOrNull()?.message}"
            }
            _isBackupRunning.value = false
            withContext(kotlinx.coroutines.Dispatchers.Main) { onFinished(result.isSuccess) }
        }
    }

    // folderUri lets this restore from a folder that isn't (yet) this device's configured Auto
    // Backup folder — e.g. one shared from another phone — without ever adopting or overwriting it.
    // Omit it to restore from the folder this device already has set up.
    fun restoreFromLiveBackup(folderUri: android.net.Uri? = null, onFinished: (Boolean) -> Unit) {
        if (_isRestoreRunning.value || _isBackupRunning.value) return
        requireBiometricAuth {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                _isRestoreRunning.value = true
                _restoreStatus.value = "Restoring from local backup..."
                try {
                    val companyId = sessionStore.activeSession.first()?.companyId
                    if (companyId.isNullOrBlank()) {
                        _restoreStatus.value = "Restore failed: User not logged in."
                        withContext(kotlinx.coroutines.Dispatchers.Main) { onFinished(false) }
                        return@launch
                    }
                    writePreRestoreSafetySnapshot()
                    val result = liveBackupWriter.restoreFrom(companyId, folderUri?.toString())
                    if (result.isSuccess) {
                        afterRestore()
                        _restoreStatus.value = "Database restored! App will restart."
                        _requireRestart.value = true
                        withContext(kotlinx.coroutines.Dispatchers.Main) { onFinished(true) }
                    } else {
                        _restoreStatus.value = "Restore failed: ${result.exceptionOrNull()?.message}"
                        withContext(kotlinx.coroutines.Dispatchers.Main) { onFinished(false) }
                    }
                } catch (e: Exception) {
                    _restoreStatus.value = "Restore failed: ${e.message}"
                    withContext(kotlinx.coroutines.Dispatchers.Main) { onFinished(false) }
                } finally {
                    _isRestoreRunning.value = false
                }
            }
        }
    }

    fun undoLastRestore(onFinished: (Boolean) -> Unit) {
        if (_isRestoreRunning.value || _isBackupRunning.value) return
        requireBiometricAuth {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                _isRestoreRunning.value = true
                _restoreStatus.value = "Undoing last restore..."
                try {
                    if (!preRestoreSafetyFile.exists()) {
                        _restoreStatus.value = "Nothing to undo."
                        withContext(kotlinx.coroutines.Dispatchers.Main) { onFinished(false) }
                        return@launch
                    }
                    val bytes = preRestoreSafetyFile.readBytes()
                    val success = backupManager.restoreBackup(bytes)
                    if (success) {
                        afterRestore()
                        _restoreStatus.value = "Undo complete! App will restart."
                        _requireRestart.value = true
                        withContext(kotlinx.coroutines.Dispatchers.Main) { onFinished(true) }
                    } else {
                        _restoreStatus.value = "Undo failed: The safety snapshot is invalid."
                        withContext(kotlinx.coroutines.Dispatchers.Main) { onFinished(false) }
                    }
                } catch (e: Exception) {
                    _restoreStatus.value = "Undo failed: ${e.message}"
                    withContext(kotlinx.coroutines.Dispatchers.Main) { onFinished(false) }
                } finally {
                    _isRestoreRunning.value = false
                }
            }
        }
    }

    private val _cloudSyncStatus = MutableStateFlow<String?>(null)
    val cloudSyncStatus: StateFlow<String?> = _cloudSyncStatus.asStateFlow()


    fun createUser(
        phone: String,
        displayName: String,
        password: CharArray,
        role: String = "CASHIER",
        permissions: Set<com.kadaikutty.pos.core.security.Permission>,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (!isOnline.value) {
                    onResult(false, "Staff changes need internet. Connect and try again.")
                    return@launch
                }
                val session = sessionStore.activeSession.first() ?: run {
                    onResult(false, "No active session")
                    return@launch
                }
                if (!session.permissions.contains(com.kadaikutty.pos.core.security.Permission.USER_MANAGE)) {
                    onResult(false, "You do not have permission to manage users.")
                    return@launch
                }
                val cleanPhone = phone.replace("[^0-9]".toRegex(), "").takeLast(10)
                if (cleanPhone.length < 10) {
                    onResult(false, "Please enter a valid 10-digit mobile number")
                    return@launch
                }
                val existing = database.userDao().getUserByUsername(cleanPhone, cleanPhone)
                if (existing != null) {
                    onResult(false, "A staff user with mobile number $cleanPhone already exists!")
                    return@launch
                }

                val companyId = session.companyId
                val token = session.accessToken ?: error("Online session token is missing")

                // The backend is the source of truth for the account's identity: it creates the
                // account ACTIVE immediately (the shop admin owns staff onboarding, no separate
                // master approval step) and assigns the real userId. Calling it first — and only
                // writing the local user row once it succeeds, keyed by that same
                // server userId — is what keeps this device's local record and the cloud record
                // pointing at the same account; inventing a local ID first (as before) meant every
                // later edit/deactivate for that staff member silently 404'd against the backend.
                val created = backendApi.createStaff(token, cleanPhone, displayName, password.concatToString(), permissions.map { it.name })
                val serverUserId = created.getJSONObject("user").getString("userId")

                val userEntity = com.kadaikutty.pos.core.auth.UserEntity(
                    id = serverUserId,
                    username = cleanPhone,
                    displayName = displayName,
                    permissions = permissions.joinToString(",") { it.name },
                    companyId = companyId,
                    role = role,
                    lastOnlineVerifiedAt = System.currentTimeMillis()
                )
                database.userDao().insertUser(userEntity)
                database.auditLogDao().insertAuditLog(
                    com.kadaikutty.pos.feature.billing.data.AuditLogEntity(
                        id = com.kadaikutty.pos.core.common.newRecordId(),
                        companyId = companyId,
                        action = "STAFF_CREATE",
                        billNumber = "$displayName ($cleanPhone)",
                        amountMinorUnits = 0,
                        reason = "Role: $role. Permissions: ${permissions.joinToString(", ") { it.name }.ifBlank { "none" }}",
                        performedByUserId = session.userId,
                        performedByUserName = session.displayName,
                        timestampEpochMs = System.currentTimeMillis()
                    )
                )

                onResult(true, "Staff account for '$displayName' ($cleanPhone) created and active.")
            } catch (e: Exception) {
                onResult(false, e.message ?: "Failed to create staff account")
            }
        }
    }

    fun updateUserCredentials(
        userId: String,
        displayName: String?,
        role: String?,
        newPassword: CharArray?,
        permissions: Set<com.kadaikutty.pos.core.security.Permission>,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            try {
                if (!isOnline.value) {
                    onResult(false, "Staff changes need internet. Connect and try again.")
                    return@launch
                }
                val session = sessionStore.activeSession.first()
                if (session == null || !session.permissions.contains(com.kadaikutty.pos.core.security.Permission.USER_MANAGE)) {
                    onResult(false, "You do not have permission to manage users.")
                    return@launch
                }
                val userDao = database.userDao()
                val existing = userDao.getUserById(userId) ?: run {
                    onResult(false, "User not found")
                    return@launch
                }

                val finalDisplayName = displayName?.ifBlank { null } ?: existing.displayName
                val finalRole = role ?: existing.role

                // Backend first: if this fails (network, permission, stale userId), nothing local
                // changes, so this device's cache never diverges from what the cloud actually has.
                val token = session.accessToken ?: error("Online session token is missing")
                backendApi.updateStaff(token, existing.id, finalDisplayName, newPassword?.concatToString(), permissions.map { it.name })
                // The old password must stop working offline on this device too; the staff member
                // gets a fresh offline credential on their next online sign-in.
                if (newPassword != null && newPassword.isNotEmpty()) offlineCredentialStore.remove(existing.username)

                val newPermissionsCsv = permissions.joinToString(",") { it.name }
                val changes = mutableListOf<String>()
                if (finalDisplayName != existing.displayName) changes += "name '${existing.displayName}' -> '$finalDisplayName'"
                if (finalRole != existing.role) changes += "role ${existing.role} -> $finalRole"
                if (newPermissionsCsv != existing.permissions) changes += "permissions changed"
                if (newPassword != null && newPassword.isNotEmpty()) changes += "password reset"

                val updatedUser = existing.copy(
                    displayName = finalDisplayName,
                    role = finalRole,
                    permissions = newPermissionsCsv
                )
                userDao.updateUser(updatedUser)
                database.auditLogDao().insertAuditLog(
                    com.kadaikutty.pos.feature.billing.data.AuditLogEntity(
                        id = com.kadaikutty.pos.core.common.newRecordId(),
                        companyId = session.companyId,
                        action = "STAFF_UPDATE",
                        billNumber = "$finalDisplayName (${existing.username})",
                        amountMinorUnits = 0,
                        reason = changes.joinToString("; ").ifBlank { "No fields changed" },
                        performedByUserId = session.userId,
                        performedByUserName = session.displayName,
                        timestampEpochMs = System.currentTimeMillis()
                    )
                )

                onResult(true, "Staff account updated successfully!")
            } catch (e: Exception) {
                onResult(false, e.message ?: "Failed to update staff account")
            }
        }
    }

    fun deleteUser(userId: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                if (!isOnline.value) {
                    onResult(false, "Staff changes need internet. Connect and try again.")
                    return@launch
                }
                val session = sessionStore.activeSession.first()
                if (session == null || !session.permissions.contains(com.kadaikutty.pos.core.security.Permission.USER_MANAGE)) {
                    onResult(false, "You do not have permission to manage users.")
                    return@launch
                }
                val userDao = database.userDao()
                val existing = userDao.getUserById(userId) ?: run {
                    onResult(false, "User not found")
                    return@launch
                }

                // Backend first: deactivate the real account before dropping the local cache row.
                // The old order deleted locally first, so a failed/offline backend call silently
                // left the staff member's cloud account fully active and able to log in on another
                // device — while this device's admin saw the entry gone and believed it was removed.
                val token = session.accessToken ?: error("Online session token is missing")
                backendApi.deactivateStaff(token, userId)
                userDao.deleteUser(existing)
                offlineCredentialStore.remove(existing.username)
                database.auditLogDao().insertAuditLog(
                    com.kadaikutty.pos.feature.billing.data.AuditLogEntity(
                        id = com.kadaikutty.pos.core.common.newRecordId(),
                        companyId = session.companyId,
                        action = "STAFF_DELETE",
                        billNumber = "${existing.displayName} (${existing.username})",
                        amountMinorUnits = 0,
                        reason = "Staff account removed",
                        performedByUserId = session.userId,
                        performedByUserName = session.displayName,
                        timestampEpochMs = System.currentTimeMillis()
                    )
                )

                onResult(true, "Staff user deleted successfully!")
            } catch (e: Exception) {
                onResult(false, e.message ?: "Failed to delete user")
            }
        }
    }

    val themeMode: StateFlow<String> = appPreferences.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "System"
    )

    fun saveThemeMode(mode: String) {
        viewModelScope.launch {
            appPreferences.saveThemeMode(mode)
        }
    }

}
