package com.kadaikutty.pos.feature.settings.presentation

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.kadaikutty.pos.core.backup.domain.BackupResult
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
    private val firestore: com.google.firebase.firestore.FirebaseFirestore,

    private val sampleDataGenerator: com.kadaikutty.pos.core.sample.SampleDataGenerator,
    private val licenseManager: com.kadaikutty.pos.core.license.LicenseManager
) : ViewModel() {

    fun loadDemoSampleData(onResult: (String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isBackupRunning.value = true
            _backupStatus.value = "Generating 100 demo retail records..."
            try {
                val session = sessionStore.activeSession.first()
                if (session != null) {
                    val count = sampleDataGenerator.insert100DemoRecords(session.companyId)
                    _backupStatus.value = "Successfully initialized $count demo items (Products, Sales, Customers, Stock)!"
                    withContext(kotlinx.coroutines.Dispatchers.Main) { onResult("Loaded $count demo records successfully!") }
                } else {
                    _backupStatus.value = "Failed: No active merchant session."
                    withContext(kotlinx.coroutines.Dispatchers.Main) { onResult("Error: Please log in first.") }
                }
            } catch (e: Exception) {
                _backupStatus.value = "Demo population failed: ${e.message}"
                withContext(kotlinx.coroutines.Dispatchers.Main) { onResult("Failed: ${e.message}") }
            } finally {
                _isBackupRunning.value = false
            }
        }
    }

    fun clearAllDatabase(clearCloudToo: Boolean, onResult: (Boolean) -> Unit) {
        requireBiometricAuth {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                _isRestoreRunning.value = true
                _restoreStatus.value = "Safely clearing database records..."
                try {
                    val session = sessionStore.activeSession.first()
                    val companyId = session?.companyId ?: ""
                    val success = sampleDataGenerator.clearAllData(companyId, clearCloudToo)
                    if (success) {
                        _restoreStatus.value = "All database records safely cleared."
                        withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(true) }
                    } else {
                        _restoreStatus.value = "Failed to clear database."
                        withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(false) }
                    }
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

    val usersList: StateFlow<List<com.kadaikutty.pos.core.auth.UserEntity>> = database.userDao().getAllUsersFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val currentLicense: StateFlow<com.kadaikutty.pos.core.license.LicenseEntity?> = licenseManager.currentLicense
    val isClockTampered: StateFlow<Boolean> = licenseManager.isClockTampered

    fun shouldShowRenewalAlert(): Boolean = licenseManager.shouldShowDailyRenewalAlert()
    fun markRenewalAlertShown() = licenseManager.recordRenewalAlertShown()
    fun refreshLicenseStatus() {
        activeSession.value?.companyId?.let { licenseManager.startRealtimeLicenseSync(it) }
    }

    fun logout(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            sessionStore.clear()
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                onComplete()
            }
        }
    }

    val shopName: StateFlow<String> = appPreferences.shopName.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "My Shop"
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

    fun saveShopDetails(name: String, owner: String, gst: String, address: String, phone: String, email: String, logoPath: String) {
        viewModelScope.launch {
            appPreferences.saveShopDetails(name, owner, gst, address, phone, email, logoPath)
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

    val googleAccount: StateFlow<String?> = appPreferences.googleAccount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

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

            val pType = if (typeStr == "Usb") PrinterManager.PrinterType.Usb else PrinterManager.PrinterType.Bluetooth
            printerManager.selectDriver(pType)

            _printStatus.value = "Connecting to printer..."
            val connResult = printerManager.connect(deviceId)
            if (connResult is PrinterResult.Failure) {
                onResult("Connection failed: ${connResult.error.message}")
                _printStatus.value = "Connection failed"
                return@launch
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

            val printResult = printerManager.print(testDoc)
            printerManager.disconnect()

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

    fun runBackup(uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isBackupRunning.value = true
            _backupStatus.value = "Creating backup package..."
            when (val result = backupManager.createBackup()) {
                is BackupResult.Success -> {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(result.zipBytes)
                        }
                        _backupStatus.value = "Backup created successfully!"
                    } catch (e: Exception) {
                        _backupStatus.value = "Backup failed to write: ${e.message}"
                    }
                }
                is BackupResult.Failure -> {
                    _backupStatus.value = "Backup failed: ${result.exception.message}"
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

    fun runRestoreFromCloud(onFinished: (Boolean) -> Unit) {
        requireBiometricAuth {
            viewModelScope.launch {
                _isRestoreRunning.value = true
                _restoreStatus.value = "Fetching data from cloud..."
                val session = sessionStore.activeSession.first()
                if (session == null) {
                    _restoreStatus.value = "Restore failed: User not logged in."
                    onFinished(false)
                    return@launch
                }
                when (val result = backupManager.restoreFromCloud(session.companyId, firestore)) {
                    is BackupResult.Success -> {
                        _restoreStatus.value = "Cloud restore completed successfully! App will restart."
                        _requireRestart.value = true
                        onFinished(true)
                    }
                    is BackupResult.Failure -> {
                        _restoreStatus.value = "Cloud restore failed: ${result.exception.message}"
                        onFinished(false)
                    }
                }
                _isRestoreRunning.value = false
            }
        }
    }

    val geminiApiKey: StateFlow<String?> = appPreferences.geminiApi.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun saveGeminiApiKey(key: String) {
        viewModelScope.launch {
            appPreferences.saveGeminiApiKey(if (key.isBlank()) null else key)
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
                val credResult = createCredentials(cleanPhone, password, java.util.UUID.randomUUID().toString(), displayName)
                
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

                // Sync to Firestore Cloud
                try {
                    val map = hashMapOf(
                        "id" to userEntity.id,
                        "username" to userEntity.username,
                        "displayName" to userEntity.displayName,
                        "salt" to userEntity.salt,
                        "verifier" to userEntity.verifier,
                        "permissions" to userEntity.permissions,
                        "companyId" to userEntity.companyId,
                        "role" to userEntity.role,
                        "lastOnlineVerifiedAt" to userEntity.lastOnlineVerifiedAt,
                        "offlineValidUntil" to userEntity.offlineValidUntil
                    )
                    firestore.collection("users").document(companyId).collection("staff").document(userEntity.id).set(map)
                    
                    val rootMap = hashMapOf(
                        "user_id" to userEntity.id,
                        "username" to userEntity.username,
                        "mobile" to userEntity.username,
                        "full_name" to userEntity.displayName,
                        "salt" to userEntity.salt,
                        "verifier" to userEntity.verifier,
                        "permissions" to permissions.map { it.name },
                        "company_id" to userEntity.companyId,
                        "role" to userEntity.role
                    )
                    firestore.collection("users").document(userEntity.username).set(rootMap)
                } catch (rpcEx: Exception) {
                    rpcEx.printStackTrace()
                }

                onResult(true, "Staff account for '$displayName' ($cleanPhone) created successfully!")
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

                // Sync to Firestore
                try {
                    val session = sessionStore.activeSession.first()
                    if (session != null) {
                        val map = hashMapOf(
                            "id" to updatedUser.id,
                            "username" to updatedUser.username,
                            "displayName" to updatedUser.displayName,
                            "salt" to updatedUser.salt,
                            "verifier" to updatedUser.verifier,
                            "permissions" to updatedUser.permissions,
                            "companyId" to updatedUser.companyId,
                            "role" to updatedUser.role,
                            "lastOnlineVerifiedAt" to updatedUser.lastOnlineVerifiedAt,
                            "offlineValidUntil" to updatedUser.offlineValidUntil
                        )
                        firestore.collection("users").document(session.companyId).collection("staff").document(updatedUser.id).set(map)
                        
                        val rootMap = hashMapOf(
                            "user_id" to updatedUser.id,
                            "username" to updatedUser.username,
                            "mobile" to updatedUser.username,
                            "full_name" to updatedUser.displayName,
                            "salt" to updatedUser.salt,
                            "verifier" to updatedUser.verifier,
                            "permissions" to permissions.map { it.name },
                            "company_id" to updatedUser.companyId,
                            "role" to updatedUser.role
                        )
                        firestore.collection("users").document(updatedUser.username).set(rootMap, com.google.firebase.firestore.SetOptions.merge())
                    }
                } catch (ignored: Exception) {}

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
                userDao.deleteUser(existing)

                try {
                    val session = sessionStore.activeSession.first()
                    if (session != null) {
                        firestore.collection("users").document(session.companyId).collection("staff").document(userId).delete()
                        firestore.collection("users").document(existing.username).delete()
                    }
                } catch (ignored: Exception) {}

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
