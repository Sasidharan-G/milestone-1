package com.company.billing.core.preferences

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

    suspend fun savePrinterSettings(type: String, deviceId: String, paperWidth: Int) {
        dataStore.edit {
            it[printerTypeKey] = type
            it[printerDeviceIdKey] = deviceId
            it[printerPaperWidthKey] = paperWidth
        }
    }
}

