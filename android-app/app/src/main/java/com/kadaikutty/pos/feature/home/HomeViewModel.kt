package com.kadaikutty.pos.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kadaikutty.pos.core.auth.Session
import com.kadaikutty.pos.core.auth.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.feature.billing.data.ShiftEntity

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val database: BillingDatabase
) : ViewModel() {
    val activeSession: StateFlow<Session?> = sessionStore.activeSession
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val shiftHistory: StateFlow<List<ShiftEntity>> = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            if (companyId.isNotEmpty()) database.shiftDao().getAllShifts(companyId)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
                    id = com.kadaikutty.pos.core.common.newRecordId(),
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
