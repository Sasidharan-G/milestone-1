package com.kadaikutty.pos.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppPreferences(private val dataStore: DataStore<Preferences>) {
    private val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
    val isOnboardingCompleted: Flow<Boolean> = dataStore.data.map { it[onboardingCompleted] ?: false }
    suspend fun markOnboardingCompleted() { dataStore.edit { it[onboardingCompleted] = true } }

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
        dataStore.edit {
            it[printerTypeKey] = type
            it[printerDeviceIdKey] = deviceId
            it[printerPaperWidthKey] = paperWidth
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
    val themeMode: Flow<String> = dataStore.data.map { it[themeModeKey] ?: "System" }

    suspend fun saveThemeMode(mode: String) {
        dataStore.edit {
            it[themeModeKey] = mode
        }
    }

    private val shopNameKey = stringPreferencesKey("shop_name")
    val shopName: Flow<String> = dataStore.data.map { it[shopNameKey] ?: "My Shop" }

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
}

