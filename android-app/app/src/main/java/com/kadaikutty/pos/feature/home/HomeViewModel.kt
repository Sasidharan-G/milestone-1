package com.kadaikutty.pos.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kadaikutty.pos.core.auth.Session
import com.kadaikutty.pos.core.auth.SessionStore
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.sync.SyncScheduler
import com.kadaikutty.pos.feature.billing.data.SaleEntity
import com.kadaikutty.pos.feature.billing.data.ShiftEntity
import com.kadaikutty.pos.feature.reports.presentation.BillDetailData
import com.kadaikutty.pos.feature.reports.presentation.BillDetailItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class HomeDashboardUiState(
    val todaySalesMinorUnits: Long = 0L,
    val todayInvoicesCount: Int = 0,
    val lowStockCount: Int = 0,
    val customerCreditDueMinorUnits: Long = 0L,
    val todayPurchasesMinorUnits: Long = 0L,
    val recentSales: List<SaleEntity> = emptyList(),
    val pendingSyncCount: Int = 0,
    val isSyncing: Boolean = false,
    val lastSyncMessage: String = "Cloud Backup Active"
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val database: BillingDatabase,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    val activeSession: StateFlow<Session?> = sessionStore.activeSession
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isSyncing = MutableStateFlow(false)

    val dashboardState: StateFlow<HomeDashboardUiState> = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            if (companyId.isEmpty()) {
                flowOf(HomeDashboardUiState())
            } else {
                val startOfToday = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val salesFlow = database.saleDao().getSales(companyId)
                val stockFlow = database.purchaseDao().getStockBalances(companyId)
                val creditsFlow = database.masterDao().getTotalCustomerCreditsReceivable(companyId)
                val purchasesFlow = database.purchaseDao().getPurchases(companyId)
                val pendingFlow = database.syncQueueDao().pendingCount(companyId)

                combine(salesFlow, stockFlow, creditsFlow, purchasesFlow, pendingFlow) { sales, stockList, customerCredits, purchases, pendingCount ->
                    val todaySales = sales.filter { it.createdAtEpochMs >= startOfToday }
                    val todaySalesTotal = todaySales.sumOf { it.totalMinorUnits }
                    val todayInvoices = todaySales.size

                    val lowStockItems = stockList.count { it.currentStock <= 5 }
                    val customerDue = customerCredits ?: 0L

                    val todayPurchases = purchases.filter { it.createdAtEpochMs >= startOfToday }
                        .sumOf { it.totalMinorUnits }

                    val recent = sales.take(5)

                    HomeDashboardUiState(
                        todaySalesMinorUnits = todaySalesTotal,
                        todayInvoicesCount = todayInvoices,
                        lowStockCount = lowStockItems,
                        customerCreditDueMinorUnits = customerDue,
                        todayPurchasesMinorUnits = todayPurchases,
                        recentSales = recent,
                        pendingSyncCount = pendingCount,
                        isSyncing = _isSyncing.value,
                        lastSyncMessage = if (pendingCount == 0) "All data backed up to cloud" else "$pendingCount items ready to sync"
                    )
                }.combine(_isSyncing) { state, isSyncing ->
                    state.copy(isSyncing = isSyncing)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeDashboardUiState())

    val shiftHistory: StateFlow<List<ShiftEntity>> = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            if (companyId.isNotEmpty()) database.shiftDao().getAllShifts(companyId)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun getBillDetails(billNumber: String): BillDetailData? {
        val session = sessionStore.activeSession.first() ?: return null
        val companyId = session.companyId
        val sale = database.saleDao().getSaleByBillNumber(companyId, billNumber) ?: return null
        val rawItems = database.saleDao().getSaleItemsList(companyId, sale.id)
        val customer = if (!sale.customerId.isNullOrBlank()) {
            database.masterDao().getCustomerById(companyId, sale.customerId)
        } else null

        val items = rawItems.map { item ->
            val product = database.masterDao().getProductById(companyId, item.productId)
            BillDetailItem(
                productName = product?.name ?: "Item #${item.productId.take(6)}",
                unitType = product?.unitType ?: "PIECE",
                quantity = item.quantity,
                unitPriceMinorUnits = item.unitPriceMinorUnits,
                lineTotalMinorUnits = item.lineTotalMinorUnits
            )
        }

        return BillDetailData(
            sale = sale,
            customerName = customer?.name ?: "Walk-in Customer",
            customerPhone = customer?.phone ?: "",
            items = items
        )
    }

    fun triggerCloudSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            syncScheduler.request()
            kotlinx.coroutines.delay(2000)
            _isSyncing.value = false
        }
    }

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
