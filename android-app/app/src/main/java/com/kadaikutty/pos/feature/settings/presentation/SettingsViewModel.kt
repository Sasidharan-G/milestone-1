package com.kadaikutty.pos.feature.settings.presentation

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
    private val verifier: com.kadaikutty.pos.core.auth.OfflineCredentialVerifier,
    private val licenseManager: com.kadaikutty.pos.core.license.LicenseManager,
    private val sessionSecurityManager: com.kadaikutty.pos.core.auth.SessionSecurityManager,
    private val backendApi: com.kadaikutty.pos.core.network.BackendApiClient,
    private val tenantDatabaseManager: com.kadaikutty.pos.core.database.TenantDatabaseManager,
    private val liveBackupWriter: LiveBackupWriter,
    private val offlineCredentialStore: com.kadaikutty.pos.core.auth.OfflineCredentialStore,
    private val shareManager: ShareManager,
) : ViewModel() {

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
                    sessionSecurityManager.startListeningToSession(session.userId, session.sessionToken)
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
                status = obj.optString("status", "ACTIVE"),
                isCloudTier = obj.optBoolean("isCloudTier", true),
                cloudAccessGrantedUntilEpochMs = obj.optLong("cloudAccessGrantedUntilEpochMs", 0L).takeIf { it > 0L }
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
                        targetDb.userDao().deleteUserById(action.localId)
                    }
                    is com.kadaikutty.pos.core.auth.StaffReconciliationAction.Rekey -> {
                        targetDb.userDao().deleteUserById(action.local.id)
                        targetDb.userDao().insertUser(action.local.copy(
                            id = action.server.userId,
                            displayName = action.server.displayName,
                            permissions = action.server.permissions,
                            isCloudTier = action.server.isCloudTier,
                            cloudAccessGrantedUntilEpochMs = action.server.cloudAccessGrantedUntilEpochMs
                        ))
                        val existingCredential = offlineCredentialStore.getCredential(action.local.username).first()
                        if (existingCredential != null && existingCredential.userId == action.local.id) {
                            offlineCredentialStore.save(existingCredential.copy(userId = action.server.userId))
                        }
                    }
                    is com.kadaikutty.pos.core.auth.StaffReconciliationAction.UpdateFields -> {
                        targetDb.userDao().updateUser(action.local.copy(
                            displayName = action.server.displayName,
                            permissions = action.server.permissions,
                            isCloudTier = action.server.isCloudTier,
                            cloudAccessGrantedUntilEpochMs = action.server.cloudAccessGrantedUntilEpochMs
                        ))
                    }
                    is com.kadaikutty.pos.core.auth.StaffReconciliationAction.InsertMissing -> {
                        targetDb.userDao().insertUser(com.kadaikutty.pos.core.auth.UserEntity(
                            id = action.server.userId,
                            username = com.kadaikutty.pos.core.auth.StaffReconciler.normalizePhone(action.server.phone),
                            displayName = action.server.displayName,
                            salt = "",
                            verifier = "",
                            permissions = action.server.permissions,
                            companyId = action.companyId,
                            role = action.server.role,
                            lastOnlineVerifiedAt = 0L,
                            offlineValidUntil = 0L,
                            isCloudTier = action.server.isCloudTier,
                            cloudAccessGrantedUntilEpochMs = action.server.cloudAccessGrantedUntilEpochMs
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

    fun clearAllDatabase(clearCloudToo: Boolean, onResult: (Boolean) -> Unit) {
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

                    val session = sessionStore.activeSession.first()
                    val companyId = session?.companyId ?: "company_main"
                    val tenantDb = tenantDatabaseManager.getDatabase(companyId)
                    val dbsToClear = listOfNotNull(tenantDb, if (tenantDb != database) database else null)

                    val tables = listOf(
                        "draft_cart",
                        "sale_items",
                        "sales",
                        "purchase_items",
                        "purchases",
                        "stock_movements",
                        "customer_credits",
                        "supplier_credits",
                        "products",
                        "categories",
                        "customers",
                        "suppliers",
                        "expenses",
                        "sync_queue",
                        "sync_dead_letter",
                        "local_operations",
                        "audit_logs",
                        "shifts"
                    )

                    for (targetDb in dbsToClear) {
                        val sqlite = targetDb.openHelper.writableDatabase
                        sqlite.execSQL("PRAGMA foreign_keys = OFF")
                        sqlite.beginTransaction()
                        try {
                            for (table in tables) {
                                runCatching { sqlite.execSQL("DELETE FROM `$table`") }
                            }
                            runCatching { sqlite.execSQL("DELETE FROM `sqlite_sequence`") }
                            sqlite.setTransactionSuccessful()
                        } finally {
                            sqlite.endTransaction()
                            sqlite.execSQL("PRAGMA foreign_keys = ON")
                        }
                        runCatching { sqlite.execSQL("VACUUM") }
                    }

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
                            if (purgeResult.isFailure) {
                                val err = purgeResult.exceptionOrNull()?.message ?: "Unknown cloud error"
                                android.util.Log.e("SettingsViewModel", "Failed to purge cloud records: $err", purgeResult.exceptionOrNull())
                                throw Exception("Cloud database purge failed: $err")
                            }
                        } else {
                            throw Exception("No active session found to purge cloud database records.")
                        }

                        for (targetDb in dbsToClear) {
                            runCatching { targetDb.syncQueueDao().clearByCompany(companyId) }
                            runCatching { targetDb.syncDeadLetterDao().deleteAllForCompany(companyId) }
                        }
                        runCatching { appPreferences.clearShopDetails() }
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

    val layoutMode: StateFlow<String> = appPreferences.layoutMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "Grid"
    )

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

    // Checked only AFTER the company license lock passes (see BillingApp.kt) — this is the
    // per-user rule, independent of the whole-company license.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val currentUserCloudFields: StateFlow<com.kadaikutty.pos.core.auth.UserEntity?> = sessionStore.activeSession
        .flatMapLatest { session ->
            if (session == null) flowOf(null) else database.userDao().getUserByIdFlow(session.userId)
        }
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = null)

    val isCloudAccessLocked: StateFlow<Boolean> = currentUserCloudFields
        .map { user ->
            user != null && com.kadaikutty.pos.core.security.CloudAccessPolicy.isExpiredLockout(
                isCloudTier = user.isCloudTier,
                cloudAccessGrantedUntilEpochMs = user.cloudAccessGrantedUntilEpochMs,
                mustCheckInByEpochMs = user.mustCheckInByEpochMs
            )
        }
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = false)

    // For gating individual cloud-sync buttons (Settings screen), independent of the full lock screen.
    val hasCloudAccess: StateFlow<Boolean> = currentUserCloudFields
        .map { user ->
            user == null || com.kadaikutty.pos.core.security.CloudAccessPolicy.isAllowed(
                isCloudTier = user.isCloudTier,
                cloudAccessGrantedUntilEpochMs = user.cloudAccessGrantedUntilEpochMs,
                mustCheckInByEpochMs = user.mustCheckInByEpochMs
            )
        }
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = true)

    val cloudAccessDaysRemaining: StateFlow<Long?> = currentUserCloudFields
        .map { user ->
            user?.let {
                com.kadaikutty.pos.core.security.CloudAccessPolicy.daysRemaining(
                    isCloudTier = it.isCloudTier,
                    cloudAccessGrantedUntilEpochMs = it.cloudAccessGrantedUntilEpochMs,
                    mustCheckInByEpochMs = it.mustCheckInByEpochMs
                )
            }
        }
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = null)

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
            withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(true, null) }
        }
    }

    fun processAndSaveLogo(context: android.content.Context, uri: android.net.Uri): String? {
        return try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream) ?: return null
            inputStream.close()

            val width = originalBitmap.width
            val height = originalBitmap.height
            val maxSize = 512
            val scale = Math.min(maxSize.toFloat() / width, maxSize.toFloat() / height)

            val newWidth = (width * scale).toInt()
            val newHeight = (height * scale).toInt()
            val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)

            val dir = java.io.File(context.filesDir, "logos")
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, "shop_logo_${System.currentTimeMillis()}.png")

            java.io.FileOutputStream(file).use { out ->
                scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }

            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

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

    fun forceSyncNow() {
        sessionSecurityManager.resetSessionTermination()
        requireBiometricAuth {
            viewModelScope.launch {
                val session = sessionStore.activeSession.first()
                if (session != null) {
                    syncManager.enqueueAllDataForSync()
                }
            }
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

            val pType = when (typeStr) {
                "Usb" -> PrinterManager.PrinterType.Usb
                "Network" -> PrinterManager.PrinterType.Network
                else -> PrinterManager.PrinterType.Bluetooth
            }

            _printStatus.value = "Printing test receipt..."
            val testDoc = PrintDocument(
                title = "TEST RECEIPT",
                headers = listOf("Description", "Total"),
                lines = listOf(
                    PrintLine("Test Thermal Output", 1, "0.00", "0.00"),
                    PrintLine("EscPos Formatter Line", 2, "10.00", "20.00")
                ),
                totals = listOf(
                    "TOTAL AMOUNT" to "20.00"
                ),
                footer = "Thank you for verifying!"
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

    fun runCloudBackup(onFinished: (Boolean) -> Unit = {}) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isBackupRunning.value = true
            _backupStatus.value = "Creating backup package..."
            try {
                val token = resolveActiveToken()
                if (token.isNullOrBlank()) {
                    _backupStatus.value = "Backup failed: User not logged in."
                    withContext(kotlinx.coroutines.Dispatchers.Main) { onFinished(false) }
                    return@launch
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
                _isBackupRunning.value = false
            }
        }
    }

    fun runCloudRestore(onFinished: (Boolean) -> Unit) {
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
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isBackupRunning.value = true
            _backupStatus.value = "Setting up live local backup..."
            val result = liveBackupWriter.setup(treeUri)
            if (result.isSuccess) {
                _backupStatus.value = "Live local backup set up! Every change will now be mirrored to this folder automatically."
                // Live local backup alone still leaves a single-device risk (phone lost/damaged).
                // Closing that gap is not optional/opt-in: every setup also takes an immediate
                // cloud snapshot and registers a daily one going forward.
                syncScheduler.schedulePeriodicBackup()
                runCloudBackup()
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
                // writing the local offline-login cache once it succeeds, keyed by that same
                // server userId — is what keeps this device's local record and the cloud record
                // pointing at the same account; inventing a local ID first (as before) meant every
                // later edit/deactivate for that staff member silently 404'd against the backend.
                val created = backendApi.createStaff(token, cleanPhone, displayName, password.concatToString(), permissions.map { it.name })
                val serverUserId = created.getJSONObject("user").getString("userId")

                val credResult = createCredentials(cleanPhone, password, serverUserId, displayName)
                val userEntity = com.kadaikutty.pos.core.auth.UserEntity(
                    id = credResult.userId,
                    username = cleanPhone,
                    displayName = displayName,
                    salt = credResult.saltStr,
                    verifier = credResult.verifierStr,
                    permissions = permissions.joinToString(",") { it.name },
                    companyId = companyId,
                    role = role,
                    lastOnlineVerifiedAt = System.currentTimeMillis(),
                    offlineValidUntil = System.currentTimeMillis() + (30 * 24 * 60 * 60 * 1000L)
                )
                database.userDao().insertUser(userEntity)

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

                val updatedUser = if (newPassword != null && newPassword.isNotEmpty()) {
                    val credResult = createCredentials(existing.username, newPassword, existing.id, finalDisplayName)
                    existing.copy(
                        displayName = finalDisplayName,
                        role = finalRole,
                        salt = credResult.saltStr,
                        verifier = credResult.verifierStr,
                        permissions = permissions.joinToString(",") { it.name }
                    )
                } else {
                    existing.copy(
                        displayName = finalDisplayName,
                        role = finalRole,
                        permissions = permissions.joinToString(",") { it.name }
                    )
                }
                userDao.updateUser(updatedUser)

                onResult(true, "Staff account updated successfully!")
            } catch (e: Exception) {
                onResult(false, e.message ?: "Failed to update staff account")
            }
        }
    }

    fun deleteUser(userId: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
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

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private data class CredentialResult(val userId: String, val saltStr: String, val verifierStr: String)

    private fun createCredentials(username: String, password: CharArray, id: String, displayName: String): CredentialResult {
        val cred = verifier.create(username, password, id, displayName)
        val saltStr = java.util.Base64.getEncoder().encodeToString(cred.salt)
        val verifierStr = java.util.Base64.getEncoder().encodeToString(cred.verifier)
        return CredentialResult(cred.userId, saltStr, verifierStr)
    }
}
