package com.company.billing.feature.settings.presentation

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.company.billing.core.preferences.AppPreferences
import com.company.billing.core.printer.data.PrinterManager
import com.company.billing.core.printer.domain.PrintDocument
import com.company.billing.core.printer.domain.PrintLine
import com.company.billing.core.printer.domain.PrinterResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.company.billing.core.backup.data.BackupManager
import com.company.billing.core.backup.domain.BackupResult
import com.company.billing.core.backup.data.GoogleDriveBackupManager
import com.company.billing.core.sync.SyncScheduler

data class BluetoothDeviceInfo(val name: String, val address: String)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val printerManager: PrinterManager,
    private val backupManager: BackupManager,
    private val googleDriveBackupManager: GoogleDriveBackupManager,
    private val syncScheduler: SyncScheduler,
    private val database: com.company.billing.core.database.BillingDatabase,
    private val sessionStore: com.company.billing.core.auth.SessionStore,
    private val verifier: com.company.billing.core.auth.OfflineCredentialVerifier
) : ViewModel() {

    val printerType: StateFlow<String?> = appPreferences.printerType.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
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
        initialValue = "Auto"
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

    fun saveLayoutMode(mode: String) {
        viewModelScope.launch {
            appPreferences.saveLayoutMode(mode)
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

            _printStatus.value = "Printing test document..."
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
                _restoreStatus.value = "Database restored successfully!"
                onFinished(true)
            } else {
                _restoreStatus.value = "Restore failed: Invalid checksum or corrupted backup file."
                onFinished(false)
            }
        }
    }

    private val _driveBackupStatus = MutableStateFlow<String?>(null)
    val driveBackupStatus: StateFlow<String?> = _driveBackupStatus.asStateFlow()

    fun linkGoogleAccount(email: String?) {
        viewModelScope.launch {
            appPreferences.saveGoogleAccount(email)
            if (email != null) {
                syncScheduler.schedulePeriodicGoogleDriveBackup()
            }
        }
    }

    fun backupToGoogleDrive() {
        viewModelScope.launch {
            _driveBackupStatus.value = "Uploading backup to Google Drive..."
            val success = googleDriveBackupManager.uploadBackupToDrive()
            if (success) {
                _driveBackupStatus.value = "Backup uploaded to Google Drive successfully!"
            } else {
                _driveBackupStatus.value = "Google Drive backup failed. Ensure you are signed in."
            }
        }
    }

    fun createUser(username: String, displayName: String, password: CharArray, permissions: Set<com.company.billing.core.security.Permission>) {
        viewModelScope.launch {
            val cred = verifier.create(username, password, java.util.UUID.randomUUID().toString(), displayName)
            val saltStr = android.util.Base64.encodeToString(cred.salt, android.util.Base64.NO_WRAP)
            val verifierStr = android.util.Base64.encodeToString(cred.verifier, android.util.Base64.NO_WRAP)

            val userEntity = com.company.billing.core.auth.UserEntity(
                id = cred.userId,
                username = username,
                displayName = displayName,
                salt = saltStr,
                verifier = verifierStr,
                permissions = permissions.joinToString(",") { it.name }
            )
            database.userDao().insertUser(userEntity)
        }
    }

    fun updateUserCredentials(userId: String, newPassword: CharArray?, permissions: Set<com.company.billing.core.security.Permission>) {
        viewModelScope.launch {
            val userDao = database.userDao()
            val existing = userDao.getUserById(userId) ?: return@launch

            val updatedUser = if (newPassword != null && newPassword.isNotEmpty()) {
                val cred = verifier.create(existing.username, newPassword, existing.id, existing.displayName)
                val saltStr = android.util.Base64.encodeToString(cred.salt, android.util.Base64.NO_WRAP)
                val verifierStr = android.util.Base64.encodeToString(cred.verifier, android.util.Base64.NO_WRAP)
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

    private val _driveBackupsList = MutableStateFlow<List<com.google.api.services.drive.model.File>>(emptyList())
    val driveBackupsList: StateFlow<List<com.google.api.services.drive.model.File>> = _driveBackupsList.asStateFlow()

    fun fetchDriveBackups() {
        viewModelScope.launch {
            _driveBackupStatus.value = "Fetching backups from Google Drive..."
            val files = googleDriveBackupManager.listBackupsFromDrive()
            // Sort by createdTime descending
            _driveBackupsList.value = files.sortedByDescending { it.createdTime?.value ?: 0L }
            if (files.isEmpty()) {
                _driveBackupStatus.value = "No backups found in Google Drive."
            } else {
                _driveBackupStatus.value = "Loaded ${files.size} backups."
            }
        }
    }

    fun restoreFromGoogleDrive(fileId: String, onFinished: (Boolean) -> Unit) {
        viewModelScope.launch {
            _driveBackupStatus.value = "Downloading database from Google Drive..."
            val bytes = googleDriveBackupManager.downloadBackupFromDrive(fileId)
            if (bytes == null) {
                _driveBackupStatus.value = "Download failed from Google Drive."
                onFinished(false)
                return@launch
            }
            _driveBackupStatus.value = "Restoring database backup..."
            val success = backupManager.restoreBackup(bytes)
            if (success) {
                _driveBackupStatus.value = "Database restored successfully from Google Drive!"
                onFinished(true)
            } else {
                _driveBackupStatus.value = "Restore failed: Invalid checksum or corrupted backup file."
                onFinished(false)
            }
        }
    }
}
