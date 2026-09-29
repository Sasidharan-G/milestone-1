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
    val yesterdaySalesMinorUnits: Long = 0L,
    val yesterdayInvoicesCount: Int = 0,
    val weeklySalesMinorUnits: Long = 0L,
    val weeklyInvoicesCount: Int = 0,
    val monthlySalesMinorUnits: Long = 0L,
    val monthlyInvoicesCount: Int = 0,
    val lowStockCount: Int = 0,
    val recentSales: List<SaleEntity> = emptyList(),
    val pendingSyncCount: Int = 0,
    val isSyncing: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val database: BillingDatabase,
    private val syncScheduler: SyncScheduler,
    private val webSocketManager: WebSocketManager,
    private val backendApiClient: com.kadaikutty.pos.core.network.BackendApiClient
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
        // The socket's access token expired (it lives an hour). Refreshing it updates the session,
        // and the collector above then reopens the socket with the new one. At most once a minute,
        // so a server that keeps refusing is not hammered.
        viewModelScope.launch {
            var lastAttempt = 0L
            webSocketManager.tokenRejectedFlow.collect {
                val now = android.os.SystemClock.elapsedRealtime()
                if (lastAttempt != 0L && now - lastAttempt < 60_000L) return@collect
                lastAttempt = now
                runCatching { backendApiClient.autoRecoverSession(forceRefresh = true) }
            }
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

    /**
     * Products sold below zero. Offline billing never refuses a sale for stock another device may
     * have sold meanwhile, so after sync this is how an oversell becomes visible.
     */
    val negativeStockProducts: StateFlow<List<com.kadaikutty.pos.feature.masters.data.NegativeStockProduct>> = sessionStore.activeSession
        .flatMapLatest { session ->
            if (session == null) flowOf(emptyList()) else database.masterDao().negativeStockProducts(session.companyId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasStaleUnsyncedData: StateFlow<Boolean> = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            if (companyId.isEmpty()) flowOf(null) else database.syncQueueDao().oldestPendingCreatedAt(companyId)
        }
        .combine(staleCheckTicker) { oldestPendingCreatedAt, now ->
            StaleSyncDetector.isStale(oldestPendingCreatedAt, now)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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
                val startOfYesterday = Calendar.getInstance().apply { timeInMillis = startOfToday; add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
                // Rolling windows (last 7 / last 30 days, today inclusive) rather than calendar
                // week/month - avoids locale-dependent "first day of week" ambiguity for a simple
                // home-screen figure.
                val startOfWeek = Calendar.getInstance().apply { timeInMillis = startOfToday; add(Calendar.DAY_OF_YEAR, -6) }.timeInMillis
                val startOfMonth = Calendar.getInstance().apply { timeInMillis = startOfToday; add(Calendar.DAY_OF_YEAR, -29) }.timeInMillis

                val saleDao = database.saleDao()
                val periodStatsFlow = combine(
                    combine(saleDao.getSalesCountSince(companyId, startOfToday), saleDao.getSalesTotalSince(companyId, startOfToday)) { c, t -> c to (t ?: 0L) },
                    combine(saleDao.getSalesCountBetween(companyId, startOfYesterday, startOfToday), saleDao.getSalesTotalBetween(companyId, startOfYesterday, startOfToday)) { c, t -> c to (t ?: 0L) },
                    combine(saleDao.getSalesCountSince(companyId, startOfWeek), saleDao.getSalesTotalSince(companyId, startOfWeek)) { c, t -> c to (t ?: 0L) },
                    combine(saleDao.getSalesCountSince(companyId, startOfMonth), saleDao.getSalesTotalSince(companyId, startOfMonth)) { c, t -> c to (t ?: 0L) }
                ) { today, yesterday, weekly, monthly -> listOf(today, yesterday, weekly, monthly) }

                val stockFlow = database.purchaseDao().getStockBalances(companyId)
                val pendingFlow = database.syncQueueDao().pendingCount(companyId)
                val recentSalesFlow = database.saleDao().getRecentSales(companyId, 5)

                val aggregatedFlow = combine(
                    periodStatsFlow,
                    stockFlow,
                    pendingFlow,
                    recentSalesFlow
                ) { periods, stockList, pendingCount, recent ->
                    val (todayCount, todayTotal) = periods[0]
                    val (yesterdayCount, yesterdayTotal) = periods[1]
                    val (weeklyCount, weeklyTotal) = periods[2]
                    val (monthlyCount, monthlyTotal) = periods[3]
                    HomeDashboardUiState(
                        todaySalesMinorUnits = todayTotal,
                        todayInvoicesCount = todayCount,
                        yesterdaySalesMinorUnits = yesterdayTotal,
                        yesterdayInvoicesCount = yesterdayCount,
                        weeklySalesMinorUnits = weeklyTotal,
                        weeklyInvoicesCount = weeklyCount,
                        monthlySalesMinorUnits = monthlyTotal,
                        monthlyInvoicesCount = monthlyCount,
                        lowStockCount = stockList.count { com.kadaikutty.pos.feature.stock.domain.isLowStock(it.currentStock, it.minStockLevel, it.unitType) },
                        recentSales = recent,
                        pendingSyncCount = pendingCount,
                        isSyncing = false // Default, overwritten by combine below
                    )
                }

                val isOnline = session != null && !session.accessToken.isNullOrBlank()
                aggregatedFlow.combine(syncScheduler.isSyncingFlow) { state, isSyncing ->
                    val actualSyncing = isOnline && isSyncing
                    state.copy(isSyncing = actualSyncing)
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
