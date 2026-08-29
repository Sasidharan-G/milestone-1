package com.kadaikutty.pos.feature.mastercontrol.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.kadaikutty.pos.core.license.LicenseEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class MasterControlUiState(
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val selectedFilter: String = "ALL", // "ALL", "PENDING", "TRIAL", "ACTIVE", "EXPIRING", "REVOKED"
    val licenses: List<LicenseEntity> = emptyList(),
    val pendingCount: Int = 0,
    val activeTrialCount: Int = 0,
    val activePaidCount: Int = 0,
    val expiredCount: Int = 0,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class MasterControlViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _state = MutableStateFlow(MasterControlUiState())
    val state: StateFlow<MasterControlUiState> = _state.asStateFlow()

    val masterMobile = MutableStateFlow("")
    val masterPin = MutableStateFlow("9840")

    private var licensesListener: ListenerRegistration? = null
    private var configListener: ListenerRegistration? = null

    init {
        listenToLicenses()
        listenToMasterConfig()
    }

    private fun listenToMasterConfig() {
        configListener?.remove()
        configListener = firestore.collection("master_admin").document("config")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    masterMobile.value = snapshot.getString("masterMobile") ?: ""
                    masterPin.value = snapshot.getString("masterPin") ?: "9840"
                }
            }
    }

    fun updateMasterProfile(newMobile: String, newPin: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true)
                val clean = newMobile.replace("[^0-9]".toRegex(), "").takeLast(10)
                firestore.collection("master_admin").document("config").set(mapOf(
                    "masterMobile" to clean,
                    "masterPin" to newPin.trim(),
                    "updatedAt" to System.currentTimeMillis()
                ), com.google.firebase.firestore.SetOptions.merge()).await()

                _state.value = _state.value.copy(isLoading = false, successMessage = "Master Mobile and PIN updated in Firebase!")
                onSuccess()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, errorMessage = e.message)
                onError(e.message ?: "Failed to update profile")
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun updateFilter(filter: String) {
        _state.value = _state.value.copy(selectedFilter = filter)
    }

    fun clearMessages() {
        _state.value = _state.value.copy(errorMessage = null, successMessage = null)
    }

    private fun listenToLicenses() {
        _state.value = _state.value.copy(isLoading = true)
        licensesListener?.remove()

        licensesListener = firestore.collection("licenses")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _state.value = _state.value.copy(isLoading = false, errorMessage = error.message)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            LicenseEntity(
                                companyId = doc.id,
                                businessName = doc.getString("businessName") ?: "Shop #${doc.id.takeLast(4)}",
                                ownerName = doc.getString("ownerName") ?: "Store Owner",
                                ownerMobile = doc.getString("ownerMobile") ?: "",
                                licenseStatus = doc.getString("licenseStatus") ?: "PENDING_APPROVAL",
                                licenseType = doc.getString("licenseType") ?: "TRIAL_2_DAYS",
                                yearsGranted = doc.getLong("yearsGranted")?.toInt() ?: 0,
                                daysGranted = doc.getLong("daysGranted")?.toInt() ?: 0,
                                activatedAtEpochMs = doc.getLong("activatedAtEpochMs") ?: 0L,
                                validUntilEpochMs = doc.getLong("validUntilEpochMs") ?: 0L,
                                lastVerifiedAtEpochMs = System.currentTimeMillis(),
                                highestSeenClockEpochMs = System.currentTimeMillis(),
                                renewalCount = doc.getLong("renewalCount")?.toInt() ?: 0,
                                notes = doc.getString("notes") ?: ""
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }.sortedByDescending { it.activatedAtEpochMs }

                    val pending = list.count { it.licenseStatus == "PENDING_APPROVAL" }
                    val trial = list.count { it.licenseStatus == "TRIAL" && !it.isExpired }
                    val paid = list.count { it.licenseStatus == "ACTIVE_PAID" && !it.isExpired }
                    val expired = list.count { it.isExpired || it.licenseStatus == "REVOKED" }

                    _state.value = _state.value.copy(
                        isLoading = false,
                        licenses = list,
                        pendingCount = pending,
                        activeTrialCount = trial,
                        activePaidCount = paid,
                        expiredCount = expired
                    )
                }
            }
    }

    /**
     * Approves 2-Day Free Trial (48 Hours) starting exactly from now.
     */
    fun approve2DayTrial(companyId: String, businessName: String) {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val validUntil = now + (2 * 24 * 60 * 60 * 1000L) // 48 Hours

                val map = hashMapOf(
                    "companyId" to companyId,
                    "licenseStatus" to "TRIAL",
                    "licenseType" to "TRIAL_2_DAYS",
                    "daysGranted" to 2,
                    "yearsGranted" to 0,
                    "activatedAtEpochMs" to now,
                    "validUntilEpochMs" to validUntil,
                    "notes" to "2-Day Free Trial approved by Master Admin"
                )

                firestore.collection("licenses").document(companyId).set(map, com.google.firebase.firestore.SetOptions.merge()).await()
                _state.value = _state.value.copy(successMessage = "2-Day Free Trial activated for $businessName!")
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = "Failed to grant trial: ${e.message}")
            }
        }
    }

    /**
     * Manually sets/renews license duration based on Years (e.g. 1 Year = 365 days, 2 Years = 730 days)
     * Calculated exactly from the date Master grants access.
     */
    fun grantYearlyLicense(companyId: String, businessName: String, years: Int) {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val totalDays = years * 365
                val validUntil = now + (totalDays.toLong() * 24 * 60 * 60 * 1000L)

                val map = hashMapOf(
                    "companyId" to companyId,
                    "licenseStatus" to "ACTIVE_PAID",
                    "licenseType" to "YEARLY",
                    "yearsGranted" to years,
                    "daysGranted" to totalDays,
                    "activatedAtEpochMs" to now,
                    "validUntilEpochMs" to validUntil,
                    "notes" to "Full Paid License ($years Year) granted by Master Admin"
                )

                firestore.collection("licenses").document(companyId).set(map, com.google.firebase.firestore.SetOptions.merge()).await()
                _state.value = _state.value.copy(successMessage = "Full $years Year ($totalDays Days) access granted to $businessName!")
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = "Failed to grant license: ${e.message}")
            }
        }
    }

    /**
     * Custom Days License Grant (e.g., 30 days, 90 days, 180 days).
     */
    fun grantCustomDaysLicense(companyId: String, businessName: String, days: Int) {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val validUntil = now + (days.toLong() * 24 * 60 * 60 * 1000L)

                val map = hashMapOf(
                    "companyId" to companyId,
                    "licenseStatus" to "ACTIVE_PAID",
                    "licenseType" to "CUSTOM",
                    "yearsGranted" to 0,
                    "daysGranted" to days,
                    "activatedAtEpochMs" to now,
                    "validUntilEpochMs" to validUntil,
                    "notes" to "$days Days Custom Access granted by Master Admin"
                )

                firestore.collection("licenses").document(companyId).set(map, com.google.firebase.firestore.SetOptions.merge()).await()
                _state.value = _state.value.copy(successMessage = "$days Days access granted to $businessName!")
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = "Failed to grant custom access: ${e.message}")
            }
        }
    }

    /**
     * Remote Kill Switch / Instant Access Revocation.
     */
    fun revokeAccess(companyId: String, businessName: String) {
        viewModelScope.launch {
            try {
                val map = hashMapOf(
                    "companyId" to companyId,
                    "licenseStatus" to "REVOKED",
                    "validUntilEpochMs" to 0L,
                    "notes" to "Access revoked by Master Admin"
                )

                firestore.collection("licenses").document(companyId).set(map, com.google.firebase.firestore.SetOptions.merge()).await()
                _state.value = _state.value.copy(successMessage = "Access successfully REVOKED for $businessName!")
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = "Failed to revoke access: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        licensesListener?.remove()
    }
}
