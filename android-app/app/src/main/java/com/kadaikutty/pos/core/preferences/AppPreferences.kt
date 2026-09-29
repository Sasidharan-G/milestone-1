package com.kadaikutty.pos.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.firstOrNull

data class SavedPrinter(val type: String, val deviceId: String, val paperWidth: Int)

class AppPreferences(private val dataStore: DataStore<Preferences>) {
    private val savedPrintersKey = stringPreferencesKey("saved_printer_profiles")
    val savedPrinters: Flow<List<SavedPrinter>> = dataStore.data.map { preferences ->
        decodePrinters(preferences[savedPrintersKey])
    }
    private fun decodePrinters(value: String?): List<SavedPrinter> = try {
        val array = org.json.JSONArray(value ?: "[]")
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            SavedPrinter(item.getString("type"), item.getString("deviceId"), item.getInt("paperWidth"))
        }
    } catch (_: org.json.JSONException) { emptyList() }

    private val printerTypeKey = stringPreferencesKey("printer_type")
    val printerType: Flow<String?> = dataStore.data.map { it[printerTypeKey] }

    private val printerDeviceIdKey = stringPreferencesKey("printer_device_id")
    val printerDeviceId: Flow<String?> = dataStore.data.map { it[printerDeviceIdKey] }

    private val printerPaperWidthKey = intPreferencesKey("printer_paper_width")
    val printerPaperWidth: Flow<Int> = dataStore.data.map { it[printerPaperWidthKey] ?: 32 }

    private val autoPrintReceiptKey = booleanPreferencesKey("auto_print_receipt")
    val autoPrintReceipt: Flow<Boolean> = dataStore.data.map { it[autoPrintReceiptKey] ?: false }

    suspend fun saveAutoPrintReceipt(enabled: Boolean) {
        dataStore.edit { it[autoPrintReceiptKey] = enabled }
    }

    suspend fun savePrinterSettings(type: String, deviceId: String, paperWidth: Int) {
        require(type in listOf("Bluetooth", "Usb", "Network")) { "Select a supported connection" }
        require(deviceId.isNotBlank()) { "Select a printer first" }
        require(paperWidth in listOf(32, 48)) { "Select 58 mm or 80 mm paper" }
        dataStore.edit {
            it[printerTypeKey] = type
            it[printerDeviceIdKey] = deviceId
            it[printerPaperWidthKey] = paperWidth
            val profiles = decodePrinters(it[savedPrintersKey]).filterNot { p -> p.type == type && p.deviceId == deviceId } +
                SavedPrinter(type, deviceId, paperWidth)
            val array = org.json.JSONArray()
            profiles.takeLast(30).forEach { p ->
                array.put(org.json.JSONObject().put("type", p.type).put("deviceId", p.deviceId).put("paperWidth", p.paperWidth))
            }
            it[savedPrintersKey] = array.toString()
        }
    }

    private val layoutModeKey = stringPreferencesKey("layout_mode")
    val layoutMode: Flow<String> = dataStore.data.map { it[layoutModeKey] ?: "Auto" }

    suspend fun saveLayoutMode(mode: String) {
        dataStore.edit {
            it[layoutModeKey] = mode
        }
    }

    private val themeModeKey = stringPreferencesKey("theme_mode")
    val themeMode: Flow<String> = dataStore.data.map { it[themeModeKey] ?: "Light" }

    suspend fun saveThemeMode(mode: String) {
        dataStore.edit {
            it[themeModeKey] = mode
        }
    }

    private val shopNameKey = stringPreferencesKey("shop_name")
    val shopName: Flow<String> = dataStore.data.map { it[shopNameKey] ?: "" }

    private val ownerNameKey = stringPreferencesKey("owner_name")
    val ownerName: Flow<String> = dataStore.data.map { it[ownerNameKey] ?: "" }

    private val gstNumberKey = stringPreferencesKey("gst_number")
    val gstNumber: Flow<String> = dataStore.data.map { it[gstNumberKey] ?: "" }

    private val shopAddressKey = stringPreferencesKey("shop_address")
    val shopAddress: Flow<String> = dataStore.data.map { it[shopAddressKey] ?: "" }

    private val shopPhoneKey = stringPreferencesKey("shop_phone")
    val shopPhone: Flow<String> = dataStore.data.map { it[shopPhoneKey] ?: "" }

    private val shopEmailKey = stringPreferencesKey("shop_email")
    val shopEmail: Flow<String> = dataStore.data.map { it[shopEmailKey] ?: "" }

    private val shopLogoPathKey = stringPreferencesKey("shop_logo_path")
    val shopLogoPath: Flow<String> = dataStore.data.map { it[shopLogoPathKey] ?: "" }

    suspend fun saveShopName(name: String) {
        dataStore.edit {
            it[shopNameKey] = name
        }
    }

    suspend fun saveOwnerName(owner: String) {
        dataStore.edit {
            it[ownerNameKey] = owner
        }
    }

    suspend fun saveShopDetails(name: String, owner: String, gst: String, address: String, phone: String, email: String, logoPath: String) {
        dataStore.edit {
            it[shopNameKey] = name
            it[ownerNameKey] = owner
            it[gstNumberKey] = gst
            it[shopAddressKey] = address
            it[shopPhoneKey] = phone
            it[shopEmailKey] = email
            it[shopLogoPathKey] = logoPath
        }
    }

    suspend fun clearShopDetails() {
        dataStore.edit {
            it.remove(shopNameKey)
            it.remove(ownerNameKey)
            it.remove(gstNumberKey)
            it.remove(shopAddressKey)
            it.remove(shopPhoneKey)
            it.remove(shopEmailKey)
            it.remove(shopLogoPathKey)
        }
    }

    private val installationDeviceIdKey = stringPreferencesKey("installation_device_id")

    suspend fun getOrCreateInstallationDeviceId(): String {
        var id = ""
        dataStore.edit {
            id = it[installationDeviceIdKey]?.takeIf(String::isNotBlank) ?: java.util.UUID.randomUUID().toString()
            it[installationDeviceIdKey] = id
        }
        return id
    }

    private val lastBackupAtEpochMsKey = longPreferencesKey("last_backup_at_epoch_ms")
    val lastBackupAtEpochMs: Flow<Long?> = dataStore.data.map { it[lastBackupAtEpochMsKey] }

    suspend fun saveLastBackupTimestamp(epochMs: Long) {
        dataStore.edit { it[lastBackupAtEpochMsKey] = epochMs }
    }

    private val liveBackupFolderUriKey = stringPreferencesKey("live_backup_folder_uri")
    val liveBackupFolderUri: Flow<String?> = dataStore.data.map { it[liveBackupFolderUriKey] }

    suspend fun saveLiveBackupFolderUri(uri: String?) {
        dataStore.edit {
            if (uri.isNullOrBlank()) it.remove(liveBackupFolderUriKey) else it[liveBackupFolderUriKey] = uri
        }
    }

    private val liveBackupLastWriteAtEpochMsKey = longPreferencesKey("live_backup_last_write_at_epoch_ms")
    val liveBackupLastWriteAtEpochMs: Flow<Long?> = dataStore.data.map { it[liveBackupLastWriteAtEpochMsKey] }

    suspend fun saveLiveBackupLastWriteTimestamp(epochMs: Long) {
        dataStore.edit { it[liveBackupLastWriteAtEpochMsKey] = epochMs }
    }

    fun getDeviceModelName(): String {
        val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val model = android.os.Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
    }
}

