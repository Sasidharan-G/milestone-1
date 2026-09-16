package com.kadaikutty.pos.feature.mastercontrol.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kadaikutty.pos.core.auth.MasterAuthSession
import com.kadaikutty.pos.core.license.LicenseEntity
import com.kadaikutty.pos.core.network.BackendApiClient
import com.kadaikutty.pos.core.auth.SessionSecurityManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class StaffApprovalRequest(
    val id: String = "", val username: String = "", val displayName: String = "", val companyId: String = "",
    val businessName: String = "", val role: String = "CASHIER", val status: String = "PENDING_APPROVAL",
    val permissions: String = "", val createdAt: Long = 0L,
)

data class MasterControlUiState(
    val isLoading: Boolean = false, val searchQuery: String = "", val selectedFilter: String = "ALL",
    val currentTab: String = "LICENSES", val licenses: List<LicenseEntity> = emptyList(),
    val staffRequests: List<StaffApprovalRequest> = emptyList(), val pendingCount: Int = 0,
    val activeTrialCount: Int = 0, val activePaidCount: Int = 0, val expiredCount: Int = 0,
    val pendingStaffCount: Int = 0, val errorMessage: String? = null, val successMessage: String? = null,
)

@HiltViewModel
class MasterControlViewModel @Inject constructor(
    private val backendApi: BackendApiClient,
    private val sessionSecurityManager: SessionSecurityManager,
) : ViewModel() {
    private val _state = MutableStateFlow(MasterControlUiState())
    val state: StateFlow<MasterControlUiState> = _state.asStateFlow()
    val isMasterSessionTerminated = sessionSecurityManager.isMasterSessionTerminated
    val masterTerminationReason = sessionSecurityManager.masterTerminationReason
    val masterMobile = MutableStateFlow("")
    val masterPin = MutableStateFlow("")
    private var allLicenses = emptyList<LicenseEntity>()
    private var allStaff = emptyList<StaffApprovalRequest>()

    init { registerMasterSession(); refresh() }

    fun registerMasterSession() { viewModelScope.launch { sessionSecurityManager.registerMasterSession() } }
    fun acknowledgeMasterTermination() { sessionSecurityManager.resetMasterTermination() }
    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                val response = backendApi.request("GET", "admin/overview", token())
                val config = response.optJSONObject("masterConfig")
                masterMobile.value = config?.optString("mobile").orEmpty()
                masterPin.value = config?.optString("pin").orEmpty()
                allLicenses = parseLicenses(response.optJSONArray("licenses") ?: JSONArray())
                allStaff = parseStaff(response.optJSONArray("users") ?: JSONArray())
                applyFilters()
            }.onFailure { _state.value = _state.value.copy(isLoading = false, errorMessage = it.message ?: "Unable to load platform data") }
        }
    }

    fun deleteShopRecord(companyId: String, ownerMobile: String, businessName: String) = mutate("DELETE", "admin/companies/$companyId", JSONObject(), "$businessName deleted")
    fun setTab(tab: String) { _state.value = _state.value.copy(currentTab = tab) }
    fun updateMasterProfile(newMobile: String, newPin: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { backendApi.request("PATCH", "admin/config", token(), JSONObject().put("mobile", newMobile).put("pin", newPin)) }
                .onSuccess { masterMobile.value = newMobile.filter(Char::isDigit).takeLast(10); masterPin.value = newPin; onSuccess() }
                .onFailure { onError(it.message ?: "Master profile update failed") }
        }
    }
    fun updateSearchQuery(query: String) { _state.value = _state.value.copy(searchQuery = query); applyFilters() }
    fun updateFilter(filter: String) { _state.value = _state.value.copy(selectedFilter = filter); applyFilters() }
    fun clearMessages() { _state.value = _state.value.copy(errorMessage = null, successMessage = null) }

    fun approve2DayTrial(companyId: String, businessName: String) = license(companyId, "TRIAL", 2, "$businessName trial activated")
    fun grantYearlyLicense(companyId: String, businessName: String, years: Int) = license(companyId, "ACTIVE", years * 365, "$businessName license activated")
    fun grantCustomDaysLicense(companyId: String, businessName: String, days: Int) = license(companyId, "ACTIVE", days, "$businessName license activated")
    fun revokeAccess(companyId: String, businessName: String) = license(companyId, "SUSPENDED", 0, "$businessName access revoked")
    fun approveStaff(request: StaffApprovalRequest) = staff(request, "ACTIVE", request.permissions, "Staff approved")
    fun rejectStaff(request: StaffApprovalRequest) = staff(request, "INACTIVE", request.permissions, "Staff rejected")
    fun revokeStaff(request: StaffApprovalRequest) = staff(request, "INACTIVE", "", "Staff access revoked")
    fun deleteStaffPermanently(request: StaffApprovalRequest) = mutate("DELETE", "admin/staff/${request.id}", JSONObject(), "Staff disabled")

    private fun license(companyId: String, status: String, days: Int, message: String) {
        val validUntil = if (days > 0) System.currentTimeMillis() + days * 86_400_000L else 0L
        mutate("PATCH", "admin/licenses/$companyId", JSONObject().put("status", status).put("validUntilEpochMs", validUntil), message)
    }

    private fun staff(request: StaffApprovalRequest, status: String, permissions: String, message: String) {
        val values = permissions.split(',').map(String::trim).filter(String::isNotBlank)
        mutate("PATCH", "admin/staff/${request.id}", JSONObject().put("status", status).put("permissions", JSONArray(values)), message)
    }

    private fun mutate(method: String, path: String, body: JSONObject, message: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            runCatching { backendApi.request(method, path, token(), body) }
                .onSuccess { _state.value = _state.value.copy(successMessage = message); refresh() }
                .onFailure { _state.value = _state.value.copy(isLoading = false, errorMessage = it.message ?: "Operation failed") }
        }
    }

    private fun parseLicenses(array: JSONArray): List<LicenseEntity> = (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        val status = when (item.optString("status")) { "ACTIVE" -> "ACTIVE_PAID"; "SUSPENDED" -> "REVOKED"; else -> item.optString("status") }
        LicenseEntity(companyId = item.getString("companyId"), businessName = item.optString("businessName"), ownerName = item.optString("ownerName"), ownerMobile = item.optString("ownerMobile"), licenseStatus = status, licenseType = if (status == "TRIAL") "TRIAL_2_DAYS" else "CUSTOM", validUntilEpochMs = item.optLong("validUntilEpochMs"), lastVerifiedAtEpochMs = item.optLong("updatedAtEpochMs"))
    }

    private fun parseStaff(array: JSONArray): List<StaffApprovalRequest> = (0 until array.length()).mapNotNull { index ->
        val item = array.getJSONObject(index)
        if (item.optString("role") != "CASHIER") null else StaffApprovalRequest(item.optString("userId"), item.optString("phone"), item.optString("displayName"), item.optString("companyId"), role = "CASHIER", status = item.optString("status"), permissions = item.optJSONArray("permissions")?.let { permissions -> (0 until permissions.length()).joinToString(",") { permissions.getString(it) } }.orEmpty(), createdAt = item.optLong("createdAtEpochMs"))
    }

    private fun applyFilters() {
        val query = _state.value.searchQuery.trim().lowercase()
        val filter = _state.value.selectedFilter
        val licenses = allLicenses.filter { license ->
            (query.isBlank() || license.businessName.lowercase().contains(query) || license.ownerMobile.contains(query)) &&
                (filter == "ALL" || when (filter) { "PENDING" -> license.licenseStatus == "PENDING_APPROVAL"; "TRIAL" -> license.licenseStatus == "TRIAL"; "ACTIVE" -> license.licenseStatus == "ACTIVE_PAID"; "REVOKED" -> license.licenseStatus == "REVOKED"; "EXPIRING" -> license.isExpiringSoon; else -> true })
        }
        val staff = allStaff.filter { query.isBlank() || it.displayName.lowercase().contains(query) || it.username.contains(query) }
        _state.value = _state.value.copy(isLoading = false, licenses = licenses, staffRequests = staff,
            pendingCount = allLicenses.count { it.licenseStatus == "PENDING_APPROVAL" }, activeTrialCount = allLicenses.count { it.licenseStatus == "TRIAL" },
            activePaidCount = allLicenses.count { it.licenseStatus == "ACTIVE_PAID" }, expiredCount = allLicenses.count { it.isExpired },
            pendingStaffCount = allStaff.count { it.status == "PENDING_APPROVAL" })
    }

    private fun token(): String = MasterAuthSession.accessToken ?: error("Master session expired. Verify Master PIN again")
    override fun onCleared() { MasterAuthSession.clear(); sessionSecurityManager.clearMasterSession(); super.onCleared() }
}
