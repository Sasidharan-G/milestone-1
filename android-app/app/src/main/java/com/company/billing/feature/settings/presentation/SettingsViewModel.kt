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

data class BluetoothDeviceInfo(val name: String, val address: String)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val printerManager: PrinterManager,
    private val backupManager: BackupManager
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

    fun saveLayoutMode(mode: String) {
        viewModelScope.launch {
            appPreferences.saveLayoutMode(mode)
        }
    }

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
}
