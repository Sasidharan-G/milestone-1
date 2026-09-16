package com.kadaikutty.pos.feature.mastercontrol.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kadaikutty.pos.core.auth.MasterAuthSession
import com.kadaikutty.pos.core.auth.SessionSecurityManager
import com.kadaikutty.pos.core.license.LicenseEntity
import com.kadaikutty.pos.core.network.BackendApiClient
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
    private var allLicenses = emptyList<LicenseEntity>()
    private var allStaff = emptyList<StaffApprovalRequest>()

    init { ensureMasterSession(); refresh() }

    /** LoginViewModel already registers the session on PIN entry; this only self-heals a missing one. */
    private fun ensureMasterSession() {
        if (MasterAuthSession.sessionId != null) return
        viewModelScope.launch { sessionSecurityManager.registerMasterSession() }
    }

    fun acknowledgeMasterTermination() { sessionSecurityManager.resetMasterTermination() }
    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                val response = backendApi.request("GET", "admin/overview", token(), sessionId = sessionId())
                val config = response.optJSONObject("masterConfig")
                masterMobile.value = config?.optString("mobile").orEmpty()
                allLicenses = parseLicenses(response.optJSONArray("licenses") ?: JSONArray())
                allStaff = parseStaff(response.optJSONArray("users") ?: JSONArray())
                applyFilters()
            }.onFailure { _state.value = _state.value.copy(isLoading = false, errorMessage = it.message ?: "Unable to load platform data") }
        }
    }

    fun deleteShopRecord(companyId: String, ownerMobile: String, businessName: String) = mutate("DELETE", "admin/companies/$companyId", JSONObject(), "$businessName deleted")
    fun setTab(tab: String) { _state.value = _state.value.copy(currentTab = tab) }

    private var masterProfileOtpRequestId: String? = null

    /**
     * Changing the master's own mobile or PIN is OTP-gated (same requirement as every other
     * credential change): an OTP is sent to the *current* master mobile before anything changes.
     * There is no direct "set PIN" endpoint — only POST /auth/master/pin, which demands the proof.
     */
    fun sendMasterProfileOtp(onSent: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { backendApi.sendOtp(masterMobile.value) }
                .onSuccess { masterProfileOtpRequestId = it.getString("requestId"); onSent() }
                .onFailure { onError(it.message ?: "Unable to send OTP") }
        }
    }

    fun confirmMasterProfileUpdate(otp: String, newMobile: String, newPin: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val requestId = masterProfileOtpRequestId ?: error("Request an OTP first")
                val resetToken = backendApi.verifyOtp(masterMobile.value, otp, requestId).getString("resetToken")
                val nextMobile = newMobile.filter(Char::isDigit).takeLast(10)
                backendApi.changeMasterPin(masterMobile.value, newPin, resetToken, newMobileNumber = nextMobile.takeIf { it != masterMobile.value })
                nextMobile
            }.onSuccess { nextMobile ->
                masterMobile.value = nextMobile
                masterProfileOtpRequestId = null
                onSuccess()
            }.onFailure { onError(it.message ?: "Master profile update failed") }
        }
    }
    fun updateSearchQuery(query: String) { _state.value = _state.value.copy(searchQuery = query); applyFilters() }
    fun updateFilter(filter: String) { _state.value = _state.value.copy(selectedFilter = filter); applyFilters() }
    fun clearMessages() { _state.value = _state.value.copy(errorMessage = null, successMessage = null) }

    // Subscription control: exact-expiry TRIAL/day-grant/year-grant/extension/revoke. Server enforces
    // zero grace period itself; the app only ever describes the *action*, never computes validUntil.
    fun approve2DayTrial(companyId: String, businessName: String) =
        licenseAction(companyId, JSONObject().put("action", "TRIAL"), "$businessName trial activated")
    fun grantYearlyLicense(companyId: String, businessName: String, years: Int) =
        licenseAction(companyId, JSONObject().put("action", "GRANT_YEARS").put("years", years), "$businessName license activated")
    fun grantCustomDaysLicense(companyId: String, businessName: String, days: Int) =
        licenseAction(companyId, JSONObject().put("action", "GRANT_DAYS").put("days", days), "$businessName license activated")
    fun extendLicense(companyId: String, businessName: String, days: Int) =
        licenseAction(companyId, JSONObject().put("action", "EXTEND_DAYS").put("days", days), "$businessName license extended")
    fun revokeAccess(companyId: String, businessName: String) =
        licenseAction(companyId, JSONObject().put("action", "REVOKE"), "$businessName access revoked")

    fun approveStaff(request: StaffApprovalRequest) = staff(request, "ACTIVE", request.permissions, "Staff approved")
    fun rejectStaff(request: StaffApprovalRequest) = staff(request, "REJECTED", "", "Staff request rejected")
    fun revokeStaff(request: StaffApprovalRequest) = staff(request, "INACTIVE", "", "Staff access revoked")
    fun deleteStaffPermanently(request: StaffApprovalRequest) = mutate("DELETE", "admin/staff/${request.id}", JSONObject(), "Staff disabled")

    private fun licenseAction(companyId: String, body: JSONObject, message: String) =
        mutate("PATCH", "admin/licenses/$companyId", body, message)

    private fun staff(request: StaffApprovalRequest, status: String, permissions: String, message: String) {
        val values = permissions.split(',').map(String::trim).filter(String::isNotBlank)
        mutate("PATCH", "admin/staff/${request.id}", JSONObject().put("status", status).put("permissions", JSONArray(values)), message)
    }

    private fun mutate(method: String, path: String, body: JSONObject, message: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            runCatching { backendApi.request(method, path, token(), body, sessionId = sessionId()) }
                .onSuccess { _state.value = _state.value.copy(successMessage = message); refresh() }
                .onFailure { _state.value = _state.value.copy(isLoading = false, errorMessage = it.message ?: "Operation failed") }
        }
    }

    private fun parseLicenses(array: JSONArray): List<LicenseEntity> = (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        LicenseEntity(
            companyId = item.getString("companyId"), businessName = item.optString("businessName"), ownerName = item.optString("ownerName"),
            ownerMobile = item.optString("ownerMobile"), licenseStatus = item.optString("status", "EXPIRED"), licenseType = item.optString("licenseType", "TRIAL_2_DAYS"),
            yearsGranted = item.optInt("yearsGranted"), daysGranted = item.optInt("daysGranted"), activatedAtEpochMs = item.optLong("activatedAtEpochMs"),
            validUntilEpochMs = item.optLong("validUntilEpochMs"), lastVerifiedAtEpochMs = item.optLong("updatedAtEpochMs"), notes = item.optString("notes"),
        )
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
    private fun sessionId(): String = MasterAuthSession.sessionId ?: error("Master session expired. Verify Master PIN again")
    override fun onCleared() { sessionSecurityManager.clearMasterSession(); super.onCleared() }
}
