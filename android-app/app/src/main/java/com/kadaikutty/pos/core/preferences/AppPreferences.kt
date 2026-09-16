package com.kadaikutty.pos.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
    private val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
    val isOnboardingCompleted: Flow<Boolean> = dataStore.data.map { it[onboardingCompleted] ?: false }
    suspend fun markOnboardingCompleted() { dataStore.edit { it[onboardingCompleted] = true } }

    private val devicePrefixKey = stringPreferencesKey("device_prefix")
    val devicePrefix: Flow<String?> = dataStore.data.map { it[devicePrefixKey] }
    suspend fun saveDevicePrefix(prefix: String) { dataStore.edit { it[devicePrefixKey] = prefix } }

    private val printerTypeKey = stringPreferencesKey("printer_type")
    val printerType: Flow<String?> = dataStore.data.map { it[printerTypeKey] }

    private val printerDeviceIdKey = stringPreferencesKey("printer_device_id")
    val printerDeviceId: Flow<String?> = dataStore.data.map { it[printerDeviceIdKey] }

    private val printerPaperWidthKey = intPreferencesKey("printer_paper_width")
    val printerPaperWidth: Flow<Int> = dataStore.data.map { it[printerPaperWidthKey] ?: 32 }

    private val autoPrintReceiptKey = booleanPreferencesKey("auto_print_receipt")
    val autoPrintReceipt: Flow<Boolean> = dataStore.data.map { it[autoPrintReceiptKey] ?: false }

    private val allowNegativeStockKey = booleanPreferencesKey("allow_negative_stock")
    val allowNegativeStock: Flow<Boolean> = dataStore.data.map { it[allowNegativeStockKey] ?: true }

    suspend fun saveAutoPrintReceipt(enabled: Boolean) {
        dataStore.edit { it[autoPrintReceiptKey] = enabled }
    }

    suspend fun saveAllowNegativeStock(enabled: Boolean) {
        dataStore.edit { it[allowNegativeStockKey] = enabled }
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

    private val googleAccountKey = stringPreferencesKey("google_account")
    val googleAccount: Flow<String?> = dataStore.data.map { it[googleAccountKey] }

    suspend fun saveGoogleAccount(email: String?) {
        dataStore.edit {
            if (email != null) {
                it[googleAccountKey] = email
            } else {
                it.remove(googleAccountKey)
            }
        }
    }

    private val geminiApiKey = stringPreferencesKey("gemini_api_key")
    val geminiApi: Flow<String?> = dataStore.data.map { it[geminiApiKey] }

    suspend fun saveGeminiApiKey(key: String?) {
        dataStore.edit {
            if (key != null) {
                it[geminiApiKey] = key
            } else {
                it.remove(geminiApiKey)
            }
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

    private val installationDeviceIdKey = stringPreferencesKey("installation_device_id")
    val installationDeviceId: Flow<String?> = dataStore.data.map { it[installationDeviceIdKey] }

    suspend fun getOrCreateInstallationDeviceId(): String {
        var id = ""
        dataStore.edit {
            id = it[installationDeviceIdKey]?.takeIf(String::isNotBlank) ?: java.util.UUID.randomUUID().toString()
            it[installationDeviceIdKey] = id
        }
        return id
    }

    fun getDeviceModelName(): String {
        val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val model = android.os.Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
    }
}

