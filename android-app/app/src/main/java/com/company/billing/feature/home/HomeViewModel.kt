package com.company.billing.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.company.billing.core.auth.Session
import com.company.billing.core.auth.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.company.billing.core.database.BillingDatabase
import com.company.billing.feature.billing.data.ShiftEntity
import kotlinx.coroutines.flow.first

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val database: BillingDatabase
) : ViewModel() {
    val activeSession: StateFlow<Session?> = kotlinx.coroutines.flow.MutableStateFlow(
        Session(
            userId = "dummy_user",
            displayName = "Admin (Dev Mode)",
            permissions = com.company.billing.core.security.Permission.entries.toSet(),
            companyId = "dummy_company",
            role = "ADMIN"
        )
    )

    fun logout() {
        viewModelScope.launch {
            sessionStore.clear()
        }
    }

    fun closeShift(declaredCashMinorUnits: Long, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val session = sessionStore.activeSession.first()
                if (session == null) {
                    onError("No active session")
                    return@launch
                }
                val companyId = session.companyId
                val lastShift = database.shiftDao().getLastShift(companyId).first()
                val sinceEpochMs = lastShift?.closedAtEpochMs ?: 0L
                val expectedCash = database.saleDao().getCashSalesSumSince(companyId, sinceEpochMs) ?: 0L
                
                val discrepancy = declaredCashMinorUnits - expectedCash
                
                val shift = ShiftEntity(
                    id = com.company.billing.core.common.newRecordId(),
                    companyId = companyId,
                    closedAtEpochMs = System.currentTimeMillis(),
                    expectedCashMinorUnits = expectedCash,
                    declaredCashMinorUnits = declaredCashMinorUnits,
                    discrepancyMinorUnits = discrepancy,
                    closedByUserId = session.userId
                )
                database.shiftDao().insertShift(shift)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Unknown error")
            }
        }
    }
}
