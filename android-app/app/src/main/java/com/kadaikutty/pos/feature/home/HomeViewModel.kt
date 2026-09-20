package com.kadaikutty.pos.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kadaikutty.pos.core.auth.Session
import com.kadaikutty.pos.core.auth.SessionStore
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.network.WebSocketManager
import com.kadaikutty.pos.core.sync.SyncScheduler
import com.kadaikutty.pos.feature.billing.data.SaleEntity
import com.kadaikutty.pos.feature.billing.data.ShiftEntity
import com.kadaikutty.pos.feature.reports.presentation.BillDetailData
import com.kadaikutty.pos.feature.reports.presentation.BillDetailItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
    private val syncScheduler: SyncScheduler,
    private val webSocketManager: WebSocketManager
) : ViewModel() {

    init {
        syncScheduler.schedulePeriodicSync()
        syncScheduler.schedulePeriodicLiveBackupCompaction()
        // Keep a realtime channel open while an online session exists; any data_changed
        // event from the backend schedules an immediate pull instead of waiting 15 minutes.
        viewModelScope.launch {
            sessionStore.activeSession.collect { session ->
                val token = session?.accessToken
                if (session != null && token != null && session.companyId.isNotBlank()) {
                    webSocketManager.connect(session.companyId, token, session.sessionToken)
                } else {
                    webSocketManager.disconnect()
                }
            }
        }
        viewModelScope.launch {
            webSocketManager.dataChangedFlow.collect { syncScheduler.requestPull() }
        }
    }

    override fun onCleared() {
        webSocketManager.disconnect()
        super.onCleared()
    }

    val activeSession: StateFlow<Session?> = sessionStore.activeSession
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Re-evaluates staleness periodically (not just on DB writes), so the banner still appears
    // for a device that's simply been offline with nothing new happening locally either.
    private val staleCheckTicker: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(15 * 60 * 1000L)
        }
    }

    val hasStaleUnsyncedData: StateFlow<Boolean> = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            if (companyId.isEmpty()) flowOf(null) else database.syncQueueDao().oldestPendingCreatedAt(companyId)
        }
        .combine(staleCheckTicker) { oldestPendingCreatedAt, now ->
            StaleSyncDetector.isStale(oldestPendingCreatedAt, now)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Null = unrestricted (offline-tier, or cloud-tier with no deadline set yet) — dashboard hides the ring.
    val cloudAccessDaysRemaining: StateFlow<Long?> = sessionStore.activeSession
        .flatMapLatest { session ->
            if (session == null) flowOf(null) else database.userDao().getUserByIdFlow(session.userId)
        }
        .map { user ->
            user?.let {
                com.kadaikutty.pos.core.security.CloudAccessPolicy.daysRemaining(
                    isCloudTier = it.isCloudTier,
                    cloudAccessGrantedUntilEpochMs = it.cloudAccessGrantedUntilEpochMs,
                    mustCheckInByEpochMs = it.mustCheckInByEpochMs
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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

                val salesCountFlow = database.saleDao().getSalesCountSince(companyId, startOfToday)
                val salesTotalFlow = database.saleDao().getSalesTotalSince(companyId, startOfToday)
                val stockFlow = database.purchaseDao().getStockBalances(companyId)
                val creditsFlow = database.masterDao().getTotalCustomerCreditsReceivable(companyId)
                val purchasesTotalFlow = database.purchaseDao().getPurchasesTotalSince(companyId, startOfToday)
                val pendingFlow = database.syncQueueDao().pendingCount(companyId)
                val recentSalesFlow = database.saleDao().getRecentSales(companyId, 5)

                val aggregatedFlow = combine(
                    combine(salesCountFlow, salesTotalFlow, purchasesTotalFlow) { count, sTotal, pTotal ->
                        Triple(count, sTotal ?: 0L, pTotal ?: 0L)
                    },
                    stockFlow,
                    creditsFlow,
                    pendingFlow,
                    recentSalesFlow
                ) { stats, stockList, customerCredits, pendingCount, recent ->
                    val customerDue = customerCredits ?: 0L
                    HomeDashboardUiState(
                        todaySalesMinorUnits = stats.second,
                        todayInvoicesCount = stats.first,
                        lowStockCount = stockList.count { it.minStockLevel > 0.0 && it.currentStock.toDouble() <= it.minStockLevel },
                        customerCreditDueMinorUnits = customerDue,
                        todayPurchasesMinorUnits = stats.third,
                        recentSales = recent,
                        pendingSyncCount = pendingCount,
                        isSyncing = false, // Default, overwritten by combine below
                        lastSyncMessage = "" // Default, overwritten by combine below
                    )
                }

                val isOnline = session != null && !session.accessToken.isNullOrBlank()
                aggregatedFlow.combine(syncScheduler.isSyncingFlow) { state, isSyncing ->
                    val actualSyncing = isOnline && isSyncing
                    state.copy(
                        isSyncing = actualSyncing,
                        lastSyncMessage = when {
                            !isOnline -> "Offline Mode (Local)"
                            actualSyncing -> "Sync in progress..."
                            state.pendingSyncCount == 0 -> "All data backed up to cloud"
                            else -> "${state.pendingSyncCount} items ready to sync"
                        }
                    )
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
            val session = sessionStore.activeSession.first() ?: return@launch
            runCatching { com.kadaikutty.pos.core.sync.LegacyTenantMigration.runOnce(database, session.companyId) }
            // Manual retry: ignore the dead-letter cap the background worker honours.
            database.syncQueueDao().retryFailed(session.companyId, System.currentTimeMillis(), Int.MAX_VALUE)
            syncScheduler.request(replaceExisting = true)
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
