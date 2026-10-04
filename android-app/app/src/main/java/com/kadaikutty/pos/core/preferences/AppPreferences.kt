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

    /** "A4" (full invoice, shared as a PDF) or "RECEIPT" (the thermal receipt at 58/80/112 mm, shared as a picture). */
    private val shareBillFormatKey = stringPreferencesKey("share_bill_format")
    val shareBillFormat: Flow<String> = dataStore.data.map { it[shareBillFormatKey] ?: "A4" }

    suspend fun saveShareBillFormat(format: String) {
        require(format in listOf("A4", "RECEIPT")) { "Unknown bill format" }
        dataStore.edit { it[shareBillFormatKey] = format }
    }

    suspend fun saveAutoPrintReceipt(enabled: Boolean) {
        dataStore.edit { it[autoPrintReceiptKey] = enabled }
    }

    /** Whether the shop logo is printed at the top of a thermal receipt (shared bills always carry it). On unless switched off. */
    private val printLogoOnReceiptKey = booleanPreferencesKey("print_logo_on_receipt")
    val printLogoOnReceipt: Flow<Boolean> = dataStore.data.map { it[printLogoOnReceiptKey] ?: true }

    suspend fun savePrintLogoOnReceipt(enabled: Boolean) {
        dataStore.edit { it[printLogoOnReceiptKey] = enabled }
    }

    suspend fun savePrinterSettings(type: String, deviceId: String, paperWidth: Int) {
        require(type in listOf("Bluetooth", "Usb", "Network")) { "Select a supported connection" }
        require(deviceId.isNotBlank()) { "Select a printer first" }
        require(paperWidth in com.kadaikutty.pos.core.printer.domain.PaperWidth.all) { "Select 58, 80 or 112 mm paper" }
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

    /**
     * Just the paper size, for a shop that has no printer set up but still shares receipt pictures:
     * the picture is cut to this width, and [savePrinterSettings] would refuse without a printer.
     */
    suspend fun savePaperWidth(paperWidth: Int) {
        require(paperWidth in com.kadaikutty.pos.core.printer.domain.PaperWidth.all) { "Select 58, 80 or 112 mm paper" }
        dataStore.edit { it[printerPaperWidthKey] = paperWidth }
    }

    private val layoutModeKey = stringPreferencesKey("layout_mode")
    val layoutMode: Flow<String> = dataStore.data.map { it[layoutModeKey] ?: "Auto" }

    suspend fun saveLayoutMode(mode: String) {
        dataStore.edit {
            it[layoutModeKey] = mode
        }
    }

    /** Product picker layout (Billing, Purchase, Product list): a plain list, or a 2-column photo
     *  grid for a shop where staff pick items by sight rather than by reading the name. */
    private val productGridViewKey = booleanPreferencesKey("product_grid_view")
    val productGridView: Flow<Boolean> = dataStore.data.map { it[productGridViewKey] ?: false }

    suspend fun saveProductGridView(enabled: Boolean) {
        dataStore.edit { it[productGridViewKey] = enabled }
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

    /** The logo file the cloud last received from this phone; a different [shopLogoPath] means a logo still to upload. */
    private val shopLogoUploadedPathKey = stringPreferencesKey("shop_logo_uploaded_path")
    val shopLogoUploadedPath: Flow<String> = dataStore.data.map { it[shopLogoUploadedPathKey] ?: "" }

    /** The cloud's logo version (server time) the saved logo file matches. */
    private val shopLogoVersionKey = longPreferencesKey("shop_logo_version")
    val shopLogoVersion: Flow<Long> = dataStore.data.map { it[shopLogoVersionKey] ?: 0L }

    suspend fun saveShopLogoSync(path: String, uploadedPath: String, version: Long) {
        dataStore.edit {
            it[shopLogoPathKey] = path
            it[shopLogoUploadedPathKey] = uploadedPath
            it[shopLogoVersionKey] = version
        }
    }

    suspend fun markShopLogoUploaded(path: String, version: Long) {
        dataStore.edit {
            it[shopLogoUploadedPathKey] = path
            it[shopLogoVersionKey] = version
        }
    }

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

    /** [logoPath] null leaves the logo as it is, so a background refresh can never overwrite a logo chosen or downloaded meanwhile. */
    suspend fun saveShopDetails(name: String, owner: String, gst: String, address: String, phone: String, email: String, logoPath: String?) {
        dataStore.edit {
            it[shopNameKey] = name
            it[ownerNameKey] = owner
            it[gstNumberKey] = gst
            it[shopAddressKey] = address
            it[shopPhoneKey] = phone
            it[shopEmailKey] = email
            if (logoPath != null) it[shopLogoPathKey] = logoPath
        }
    }

    /** The company the saved shop details (name, address, logo...) belong to. */
    private val shopDetailsCompanyKey = stringPreferencesKey("shop_details_company_id")

    private fun androidx.datastore.preferences.core.MutablePreferences.removeShopDetails() {
        remove(shopNameKey)
        remove(ownerNameKey)
        remove(gstNumberKey)
        remove(shopAddressKey)
        remove(shopPhoneKey)
        remove(shopEmailKey)
        remove(shopLogoPathKey)
        remove(shopLogoUploadedPathKey)
        remove(shopLogoVersionKey)
        remove(shopDetailsCompanyKey)
    }

    suspend fun clearShopDetails() {
        dataStore.edit { it.removeShopDetails() }
    }

    /**
     * Called when a company signs in (and each time the app starts with one signed in). The shop
     * details are wiped only if they belong to a *different* company, so one shop's name, GSTIN and
     * logo can never show on another shop's bills. They used to be wiped on every app start, which
     * erased the logo each time the app was reopened: the logo lives only on the phone, the server
     * profile has none to bring back. Details saved before this marker existed are taken as this
     * company's. Returns true when another company's details were removed.
     */
    suspend fun claimShopDetailsFor(companyId: String): Boolean {
        var removed = false
        dataStore.edit {
            val owner = it[shopDetailsCompanyKey]
            if (owner != null && owner != companyId) {
                it.removeShopDetails()
                removed = true
            }
            it[shopDetailsCompanyKey] = companyId
        }
        return removed
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

