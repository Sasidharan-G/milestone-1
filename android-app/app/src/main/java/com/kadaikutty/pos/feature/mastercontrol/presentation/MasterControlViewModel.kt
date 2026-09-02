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

data class StaffApprovalRequest(
    val id: String = "",
    val username: String = "", // phone
    val displayName: String = "",
    val companyId: String = "",
    val businessName: String = "",
    val role: String = "CASHIER",
    val status: String = "PENDING_APPROVAL", // "PENDING_APPROVAL", "ACTIVE", "REJECTED", "INACTIVE"
    val permissions: String = "",
    val createdAt: Long = 0L
)

data class MasterControlUiState(
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val selectedFilter: String = "ALL", // "ALL", "PENDING", "TRIAL", "ACTIVE", "EXPIRING", "REVOKED"
    val currentTab: String = "LICENSES", // "LICENSES", "STAFF"
    val licenses: List<LicenseEntity> = emptyList(),
    val staffRequests: List<StaffApprovalRequest> = emptyList(),
    val pendingCount: Int = 0,
    val activeTrialCount: Int = 0,
    val activePaidCount: Int = 0,
    val expiredCount: Int = 0,
    val pendingStaffCount: Int = 0,
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
    private var companiesListener: ListenerRegistration? = null
    private var usersListener: ListenerRegistration? = null
    private var staffListener: ListenerRegistration? = null
    private var configListener: ListenerRegistration? = null

    private var rawLicenseDocs = listOf<com.google.firebase.firestore.DocumentSnapshot>()
    private var rawCompanyDocs = listOf<com.google.firebase.firestore.DocumentSnapshot>()
    private var rawUserDocs = listOf<com.google.firebase.firestore.DocumentSnapshot>()

    init {
        listenToLicenses()
        listenToStaffRequests()
        listenToMasterConfig()
    }

    fun refresh() {
        listenToLicenses()
        listenToStaffRequests()
    }

    fun deleteShopRecord(companyId: String, ownerMobile: String, businessName: String) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true)
                val cleanPhone = ownerMobile.replace("[^0-9]".toRegex(), "").takeLast(10)

                // 1. Delete from licenses collection
                firestore.collection("licenses").document(companyId).delete().await()
                if (cleanPhone.isNotBlank()) {
                    firestore.collection("licenses").document(cleanPhone).delete().await()
                    firestore.collection("licenses").document("+91$cleanPhone").delete().await()
                    
                    try {
                        val q1 = firestore.collection("licenses").whereEqualTo("ownerMobile", cleanPhone).get().await()
                        for (d in q1.documents) { d.reference.delete().await() }
                        val q2 = firestore.collection("licenses").whereEqualTo("ownerMobile", "+91$cleanPhone").get().await()
                        for (d in q2.documents) { d.reference.delete().await() }
                    } catch (_: Exception) {}

                    // 2. Delete from users collection
                    firestore.collection("users").document(cleanPhone).delete().await()
                    firestore.collection("users").document("+91$cleanPhone").delete().await()
                    try {
                        val u1 = firestore.collection("users").whereEqualTo("mobile", cleanPhone).get().await()
                        for (d in u1.documents) { d.reference.delete().await() }
                        val u2 = firestore.collection("users").whereEqualTo("company_id", companyId).get().await()
                        for (d in u2.documents) { d.reference.delete().await() }
                    } catch (_: Exception) {}
                }

                // 3. Delete from companies collection
                firestore.collection("companies").document(companyId).delete().await()

                // 4. Delete from company_users
                try {
                    val cu = firestore.collection("company_users").whereEqualTo("company_id", companyId).get().await()
                    for (d in cu.documents) { d.reference.delete().await() }
                } catch (_: Exception) {}

                // Immediate UI update
                val updatedList = _state.value.licenses.filter { it.companyId != companyId && (cleanPhone.isBlank() || !it.ownerMobile.contains(cleanPhone)) }
                _state.value = _state.value.copy(
                    isLoading = false,
                    licenses = updatedList,
                    successMessage = "Shop $businessName completely deleted from Cloud!"
                )
                reconcileLicenses()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, errorMessage = "Failed to delete: ${e.message}")
            }
        }
    }

    fun setTab(tab: String) {
        _state.value = _state.value.copy(currentTab = tab)
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
        companiesListener?.remove()
        usersListener?.remove()

        // 1. Live listener for licenses collection
        licensesListener = firestore.collection("licenses")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _state.value = _state.value.copy(isLoading = false, errorMessage = error.message)
                    return@addSnapshotListener
                }
                rawLicenseDocs = snapshot?.documents ?: emptyList()
                reconcileLicenses()
            }

        // 2. Live listener for companies collection
        companiesListener = firestore.collection("companies")
            .addSnapshotListener { snapshot, _ ->
                rawCompanyDocs = snapshot?.documents ?: emptyList()
                reconcileLicenses()
            }

        // 3. Live listener for admin users in users collection
        usersListener = firestore.collection("users")
            .whereEqualTo("role", "ADMIN")
            .addSnapshotListener { snapshot, _ ->
                rawUserDocs = snapshot?.documents ?: emptyList()
                reconcileLicenses()
            }
    }

    private fun reconcileLicenses() {
        val existingCompanyIds = rawCompanyDocs.map { it.id }.toSet()
        val existingUserPhones = rawUserDocs.map { doc ->
            (doc.getString("mobile") ?: doc.getString("username") ?: doc.id).replace("[^0-9]".toRegex(), "").takeLast(10)
        }.filter { it.isNotBlank() }.toSet()

        val validLicenses = mutableListOf<LicenseEntity>()
        val orphanDocIdsToDelete = mutableListOf<String>()

        for (doc in rawLicenseDocs) {
            val companyId = doc.getString("companyId") ?: doc.id
            val ownerMobile = (doc.getString("ownerMobile") ?: "").replace("[^0-9]".toRegex(), "").takeLast(10)

            // If the user deleted the admin or company in Firebase Console,
            // cross-verify so it immediately disappears in real time!
            val hasActiveCompany = existingCompanyIds.contains(companyId)
            val hasActiveUser = existingUserPhones.contains(ownerMobile)

            // If we have active data from Cloud and neither company nor admin user exists, it is deleted!
            if (rawCompanyDocs.isNotEmpty() && rawUserDocs.isNotEmpty() && !hasActiveCompany && !hasActiveUser) {
                orphanDocIdsToDelete.add(doc.id)
                continue
            }

            try {
                val entity = LicenseEntity(
                    companyId = companyId,
                    businessName = doc.getString("businessName") ?: "Shop #${doc.id.takeLast(4)}",
                    ownerName = doc.getString("ownerName") ?: "Store Owner",
                    ownerMobile = if (ownerMobile.isNotBlank()) ownerMobile else (doc.getString("ownerMobile") ?: ""),
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
                validLicenses.add(entity)
            } catch (_: Exception) {}
        }

        // Deduplicate by clean mobile number so duplicate entries for the same owner NEVER appear!
        val deduplicatedList = validLicenses
            .groupBy { it.ownerMobile.replace("[^0-9]".toRegex(), "").takeLast(10).ifBlank { it.companyId } }
            .values
            .map { group ->
                group.maxByOrNull { it.activatedAtEpochMs.coerceAtLeast(it.validUntilEpochMs) } ?: group.first()
            }
            .sortedByDescending { it.activatedAtEpochMs }

        val pending = deduplicatedList.count { it.licenseStatus == "PENDING_APPROVAL" }
        val trial = deduplicatedList.count { it.licenseStatus == "TRIAL" && !it.isExpired }
        val paid = deduplicatedList.count { it.licenseStatus == "ACTIVE_PAID" && !it.isExpired }
        val expired = deduplicatedList.count { it.isExpired || it.licenseStatus == "REVOKED" }

        _state.value = _state.value.copy(
            isLoading = false,
            licenses = deduplicatedList,
            pendingCount = pending,
            activeTrialCount = trial,
            activePaidCount = paid,
            expiredCount = expired
        )

        // Automatically purge any detected orphans from Cloud Firestore in background
        if (orphanDocIdsToDelete.isNotEmpty()) {
            viewModelScope.launch {
                for (orphanId in orphanDocIdsToDelete) {
                    try {
                        firestore.collection("licenses").document(orphanId).delete().await()
                    } catch (_: Exception) {}
                }
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
                    "licenseType" to "CUSTOM_DAYS",
                    "daysGranted" to days,
                    "yearsGranted" to 0,
                    "activatedAtEpochMs" to now,
                    "validUntilEpochMs" to validUntil,
                    "notes" to "Custom Access ($days Days) granted by Master Admin"
                )

                firestore.collection("licenses").document(companyId).set(map, com.google.firebase.firestore.SetOptions.merge()).await()
                _state.value = _state.value.copy(successMessage = "Custom $days Days access granted to $businessName!")
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = "Failed to grant access: ${e.message}")
            }
        }
    }

    /**
     * Instantly Revokes / Cuts Off Access. App immediately blocks the user.
     */
    fun revokeAccess(companyId: String, businessName: String) {
        viewModelScope.launch {
            try {
                val map = hashMapOf(
                    "companyId" to companyId,
                    "licenseStatus" to "REVOKED",
                    "validUntilEpochMs" to 0L,
                    "notes" to "Access manually REVOKED / CUT by Master Admin"
                )

                firestore.collection("licenses").document(companyId).set(map, com.google.firebase.firestore.SetOptions.merge()).await()
                _state.value = _state.value.copy(successMessage = "Access immediately REVOKED for $businessName!")
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = "Failed to revoke access: ${e.message}")
            }
        }
    }

    private fun listenToStaffRequests() {
        staffListener?.remove()

        staffListener = firestore.collectionGroup("staff")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                if (snapshot != null) {
                    val requests = snapshot.documents.mapNotNull { doc ->
                        try {
                            StaffApprovalRequest(
                                id = doc.id,
                                username = doc.getString("username") ?: "",
                                displayName = doc.getString("displayName") ?: doc.getString("name") ?: "Staff",
                                companyId = doc.getString("companyId") ?: doc.reference.parent.parent?.id ?: "",
                                businessName = doc.getString("businessName") ?: "Shop",
                                role = doc.getString("role") ?: "CASHIER",
                                status = doc.getString("status") ?: "PENDING_APPROVAL",
                                permissions = doc.getString("permissions") ?: "",
                                createdAt = doc.getLong("createdAt") ?: 0L
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }.sortedByDescending { it.createdAt }

                    val pendingCount = requests.count { it.status == "PENDING_APPROVAL" || it.permissions.contains("PENDING_MASTER_APPROVAL") }

                    _state.value = _state.value.copy(
                        staffRequests = requests,
                        pendingStaffCount = pendingCount
                    )
                }
            }
    }

    fun approveStaff(request: StaffApprovalRequest) {
        viewModelScope.launch {
            try {
                val cleanPerms = request.permissions.split(",")
                    .filter { it.isNotBlank() && it != "PENDING_MASTER_APPROVAL" && it != "ACCOUNT_INACTIVE" }
                    .joinToString(",")

                // 1. Update company staff sub-collection
                firestore.collection("users")
                    .document(request.companyId)
                    .collection("staff")
                    .document(request.id)
                    .set(mapOf(
                        "status" to "ACTIVE",
                        "permissions" to cleanPerms
                    ), com.google.firebase.firestore.SetOptions.merge()).await()

                // 2. Update root users collection for login
                if (request.username.isNotBlank()) {
                    val rootPerms = cleanPerms.split(",").filter { it.isNotBlank() }
                    firestore.collection("users")
                        .document(request.username)
                        .set(mapOf(
                            "status" to "ACTIVE",
                            "permissions" to rootPerms
                        ), com.google.firebase.firestore.SetOptions.merge()).await()
                }

                _state.value = _state.value.copy(successMessage = "Staff '${request.displayName}' for '${request.businessName}' approved and activated!")
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = "Failed to approve staff: ${e.message}")
            }
        }
    }

    fun rejectStaff(request: StaffApprovalRequest) {
        viewModelScope.launch {
            try {
                firestore.collection("users")
                    .document(request.companyId)
                    .collection("staff")
                    .document(request.id)
                    .set(mapOf("status" to "REJECTED"), com.google.firebase.firestore.SetOptions.merge()).await()

                if (request.username.isNotBlank()) {
                    firestore.collection("users")
                        .document(request.username)
                        .set(mapOf("status" to "REJECTED"), com.google.firebase.firestore.SetOptions.merge()).await()
                }

                _state.value = _state.value.copy(successMessage = "Staff '${request.displayName}' request rejected.")
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = "Failed to reject staff: ${e.message}")
            }
        }
    }

    fun revokeStaff(request: StaffApprovalRequest) {
        viewModelScope.launch {
            try {
                val revokedPerms = if (request.permissions.contains("ACCOUNT_INACTIVE")) request.permissions else "${request.permissions},ACCOUNT_INACTIVE"
                firestore.collection("users")
                    .document(request.companyId)
                    .collection("staff")
                    .document(request.id)
                    .set(mapOf(
                        "status" to "INACTIVE",
                        "permissions" to revokedPerms
                    ), com.google.firebase.firestore.SetOptions.merge()).await()

                if (request.username.isNotBlank()) {
                    val rootPerms = revokedPerms.split(",").filter { it.isNotBlank() }
                    firestore.collection("users")
                        .document(request.username)
                        .set(mapOf(
                            "status" to "INACTIVE",
                            "permissions" to rootPerms
                        ), com.google.firebase.firestore.SetOptions.merge()).await()
                }

                _state.value = _state.value.copy(successMessage = "Staff '${request.displayName}' access revoked.")
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = "Failed to revoke staff: ${e.message}")
            }
        }
    }

    fun deleteStaffPermanently(request: StaffApprovalRequest) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true)
                val cleanUsername = request.username.replace("[^0-9]".toRegex(), "").takeLast(10)

                // 1. Delete from company staff sub-collection
                if (request.companyId.isNotBlank() && request.id.isNotBlank()) {
                    firestore.collection("users")
                        .document(request.companyId)
                        .collection("staff")
                        .document(request.id)
                        .delete().await()
                }

                // 2. Delete from root users collection
                if (cleanUsername.isNotBlank()) {
                    firestore.collection("users").document(cleanUsername).delete().await()
                    firestore.collection("users").document("+91$cleanUsername").delete().await()
                    try {
                        val u = firestore.collection("users").whereEqualTo("mobile", cleanUsername).get().await()
                        for (d in u.documents) { d.reference.delete().await() }
                    } catch (_: Exception) {}
                }

                // 3. Delete from company_users collection
                try {
                    val cu1 = firestore.collection("company_users").whereEqualTo("user_id", request.id).get().await()
                    for (d in cu1.documents) { d.reference.delete().await() }
                    if (cleanUsername.isNotBlank()) {
                        val cu2 = firestore.collection("company_users").whereEqualTo("mobile", cleanUsername).get().await()
                        for (d in cu2.documents) { d.reference.delete().await() }
                    }
                } catch (_: Exception) {}

                val updatedStaff = _state.value.staffRequests.filter { it.id != request.id && (cleanUsername.isBlank() || !it.username.contains(cleanUsername)) }
                val pendingCount = updatedStaff.count { it.status == "PENDING_APPROVAL" || it.permissions.contains("PENDING_MASTER_APPROVAL") }

                _state.value = _state.value.copy(
                    isLoading = false,
                    staffRequests = updatedStaff,
                    pendingStaffCount = pendingCount,
                    successMessage = "Staff '${request.displayName}' permanently removed from Cloud!"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, errorMessage = "Failed to delete staff: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        licensesListener?.remove()
        companiesListener?.remove()
        usersListener?.remove()
        staffListener?.remove()
        configListener?.remove()
    }
}
