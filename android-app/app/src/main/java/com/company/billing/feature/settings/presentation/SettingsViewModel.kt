package com.company.billing.feature.settings.presentation

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.company.billing.core.preferences.AppPreferences
import com.company.billing.core.printer.data.PrinterManager
import com.company.billing.core.printer.domain.PrintDocument
import com.company.billing.core.printer.domain.PrintLine
import com.company.billing.core.printer.domain.PrinterResult
import com.company.billing.core.security.BiometricAuthenticator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.company.billing.core.backup.data.BackupManager
import com.company.billing.core.backup.domain.BackupResult
import com.company.billing.core.sync.SyncScheduler

data class BluetoothDeviceInfo(val name: String, val address: String)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val printerManager: PrinterManager,
    private val backupManager: BackupManager,
    private val syncScheduler: SyncScheduler,
    private val database: com.company.billing.core.database.BillingDatabase,
    private val sessionStore: com.company.billing.core.auth.SessionStore,
    private val verifier: com.company.billing.core.auth.OfflineCredentialVerifier,
    private val firestore: com.google.firebase.firestore.FirebaseFirestore
) : ViewModel() {

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

    val activeSession: StateFlow<com.company.billing.core.auth.Session?> = sessionStore.activeSession.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val usersList: StateFlow<List<com.company.billing.core.auth.UserEntity>> = database.userDao().getAllUsersFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

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
                    syncScheduler.request()
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

    fun runBackup(onBytesReady: (ByteArray) -> Unit) {
        viewModelScope.launch {
            _backupStatus.value = "Creating backup package..."
            when (val result = backupManager.createBackup()) {
                is BackupResult.Success -> {
                    onBytesReady(result.zipBytes)
                    _backupStatus.value = "Backup created successfully!"
                }
                is BackupResult.Failure -> {
                    _backupStatus.value = "Backup failed: ${result.exception.message}"
                }
            }
        }
    }

    fun runRestore(bytes: ByteArray, onFinished: (Boolean) -> Unit) {
        viewModelScope.launch {
            _restoreStatus.value = "Restoring database backup..."
            val success = backupManager.restoreBackup(bytes)
            if (success) {
                _restoreStatus.value = "Database restored successfully! All data recovered."
                onFinished(true)
            } else {
                _restoreStatus.value = "Restore failed: Invalid or empty backup archive. Please select a valid backup .zip file."
                onFinished(false)
            }
        }
    }

    fun runRestoreFromCloud(onFinished: (Boolean) -> Unit) {
        requireBiometricAuth {
            viewModelScope.launch {
                _restoreStatus.value = "Fetching data from cloud..."
                val session = sessionStore.activeSession.first()
                if (session == null) {
                    _restoreStatus.value = "Restore failed: User not logged in."
                    onFinished(false)
                    return@launch
                }
                when (val result = backupManager.restoreFromCloud(session.companyId, firestore)) {
                    is BackupResult.Success -> {
                        _restoreStatus.value = "Cloud restore completed successfully! All data recovered."
                        onFinished(true)
                    }
                    is BackupResult.Failure -> {
                        _restoreStatus.value = "Cloud restore failed: ${result.exception.message}"
                        onFinished(false)
                    }
                }
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

    private val _supabaseBackupStatus = MutableStateFlow<String?>(null)
    val supabaseBackupStatus: StateFlow<String?> = _supabaseBackupStatus.asStateFlow()

    private val _supabaseBackupsList = MutableStateFlow<List<Any>>(emptyList())
    val supabaseBackupsList: StateFlow<List<Any>> = _supabaseBackupsList.asStateFlow()

    fun backupToSupabase() {
        requireBiometricAuth {
            viewModelScope.launch {
                _supabaseBackupStatus.value = "Cloud syncing happens automatically in the background when connected."
            }
        }
    }

    fun restoreFromSupabase(fileName: String, onFinished: (Boolean) -> Unit) {
        requireBiometricAuth {
            viewModelScope.launch {
                _supabaseBackupStatus.value = "Firebase Restore is not implemented yet."
                onFinished(false)
            }
        }
    }

    fun fetchSupabaseBackups() {
        viewModelScope.launch {
            _supabaseBackupStatus.value = "Firebase Backups fetch is not implemented yet."
        }
    }

    fun createUser(username: String, displayName: String, password: CharArray, permissions: Set<com.company.billing.core.security.Permission>) {
        viewModelScope.launch {
            try {
                val session = sessionStore.activeSession.first() ?: return@launch
                val companyId = session.companyId
                val cred = verifier.create(username, password, java.util.UUID.randomUUID().toString(), displayName)
                val saltStr = java.util.Base64.getEncoder().encodeToString(cred.salt)
                val verifierStr = java.util.Base64.getEncoder().encodeToString(cred.verifier)

                val userEntity = com.company.billing.core.auth.UserEntity(
                    id = cred.userId,
                    username = username,
                    displayName = displayName,
                    salt = saltStr,
                    verifier = verifierStr,
                    permissions = permissions.joinToString(",") { it.name },
                    companyId = companyId,
                    role = "CASHIER",
                    lastOnlineVerifiedAt = System.currentTimeMillis(),
                    offlineValidUntil = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L)
                )
                database.userDao().insertUser(userEntity)

                // Call Firebase Cloud Function or similar logic
                try {
                    // TODO: Implement Firebase user creation
                } catch (rpcEx: Exception) {
                    rpcEx.printStackTrace()
                }
            } catch (e: Exception) {
                // Outer failure handling
            }
        }
    }

    fun updateUserCredentials(userId: String, newPassword: CharArray?, permissions: Set<com.company.billing.core.security.Permission>) {
        viewModelScope.launch {
            val userDao = database.userDao()
            val existing = userDao.getUserById(userId) ?: return@launch

            val updatedUser = if (newPassword != null && newPassword.isNotEmpty()) {
                val cred = verifier.create(existing.username, newPassword, existing.id, existing.displayName)
                val saltStr = java.util.Base64.getEncoder().encodeToString(cred.salt)
                val verifierStr = java.util.Base64.getEncoder().encodeToString(cred.verifier)
                existing.copy(
                    salt = saltStr,
                    verifier = verifierStr,
                    permissions = permissions.joinToString(",") { it.name }
                )
            } else {
                existing.copy(
                    permissions = permissions.joinToString(",") { it.name }
                )
            }
            userDao.updateUser(updatedUser)
        }
    }

    fun deleteUser(userId: String) {
        viewModelScope.launch {
            val userDao = database.userDao()
            val existing = userDao.getUserById(userId) ?: return@launch
            userDao.deleteUser(existing)
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
}
