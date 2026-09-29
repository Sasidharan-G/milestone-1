package com.kadaikutty.pos.feature.reports.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kadaikutty.pos.feature.reports.domain.ReportData
import com.kadaikutty.pos.feature.reports.domain.ReportQuery
import com.kadaikutty.pos.feature.reports.domain.ReportService
import com.kadaikutty.pos.feature.reports.domain.ReportType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.kadaikutty.pos.core.export.domain.PdfExporter
import com.kadaikutty.pos.core.export.domain.ExcelExporter

import com.kadaikutty.pos.feature.billing.domain.SaleRepository
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.auth.SessionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import androidx.room.InvalidationTracker
import com.kadaikutty.pos.core.sharing.ShareManager

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reportService: ReportService,
    private val costingStrategy: com.kadaikutty.pos.feature.reports.domain.CostingStrategy,
    private val pdfExporter: PdfExporter,
    private val excelExporter: ExcelExporter,
    private val database: BillingDatabase,
    private val saleRepository: SaleRepository,
    private val sessionStore: SessionStore,
    private val shareManager: ShareManager
) : ViewModel() {

    private val _selectedType = MutableStateFlow(ReportType.SALES)
    val selectedType: StateFlow<ReportType> = _selectedType.asStateFlow()

    private val _fromEpochMs = MutableStateFlow<Long?>(null)
    val fromEpochMs: StateFlow<Long?> = _fromEpochMs.asStateFlow()

    private val _toEpochMs = MutableStateFlow<Long?>(null)
    val toEpochMs: StateFlow<Long?> = _toEpochMs.asStateFlow()

    private val _reportData = MutableStateFlow<ReportData?>(null)
    val reportData: StateFlow<ReportData?> = _reportData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _totalSalesSum = MutableStateFlow(0L)
    val totalSalesSum: StateFlow<Long> = _totalSalesSum.asStateFlow()

    private val _purchaseCostSum = MutableStateFlow(0L)
    val purchaseCostSum: StateFlow<Long> = _purchaseCostSum.asStateFlow()

    private val _totalPurchasesSum = MutableStateFlow(0L)
    val totalPurchasesSum: StateFlow<Long> = _totalPurchasesSum.asStateFlow()

    private val _netProfitSum = MutableStateFlow(0L)
    val netProfitSum: StateFlow<Long> = _netProfitSum.asStateFlow()

    private val _expensesSum = MutableStateFlow(0L)
    val expensesSum: StateFlow<Long> = _expensesSum.asStateFlow()

    private val _totalStockValue = MutableStateFlow(0L)
    val totalStockValue: StateFlow<Long> = _totalStockValue.asStateFlow()

    // Quantity totals are per unit ("12 Pcs · 2.500 Kg"): adding kilograms to pieces gave a number
    // that meant nothing.
    private val _totalStockInward = MutableStateFlow("0")
    val totalStockInward: StateFlow<String> = _totalStockInward.asStateFlow()

    private val _totalStockOutward = MutableStateFlow("0")
    val totalStockOutward: StateFlow<String> = _totalStockOutward.asStateFlow()

    private val _totalStockUnits = MutableStateFlow("0")
    val totalStockUnits: StateFlow<String> = _totalStockUnits.asStateFlow()



    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val auditLogs: StateFlow<List<com.kadaikutty.pos.feature.billing.data.AuditLogEntity>> = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            if (companyId.isBlank()) flowOf(emptyList())
            else database.auditLogDao().getAuditLogs(companyId)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val tableObserver = object : InvalidationTracker.Observer(
        "sales", "sale_items", "purchases", "purchase_items", "expenses", 
        "stock_movements", "products", "categories", "customers", "suppliers"
    ) {
        override fun onInvalidated(tables: Set<String>) {
            loadReport()
        }
    }

    init {
        database.invalidationTracker.addObserver(tableObserver)
        loadReport()
    }

    override fun onCleared() {
        super.onCleared()
        database.invalidationTracker.removeObserver(tableObserver)
    }

    fun setReportType(type: ReportType) {
        _selectedType.value = type
        loadReport()
    }

    fun setDateFilter(from: Long?, to: Long?) {
        _fromEpochMs.value = from
        _toEpochMs.value = to
        loadReport()
    }

    fun loadReport() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
                val companyId = session.companyId
                
                // Query live KPI sums
                val salesSum = database.reportDao().getTotalSalesSum(companyId, _fromEpochMs.value, _toEpochMs.value) ?: 0L
                val purchasesSum = database.reportDao().getTotalPurchasesSum(companyId, _fromEpochMs.value, _toEpochMs.value) ?: 0L
                val expensesSum = database.reportDao().getTotalExpensesSum(companyId, _fromEpochMs.value, _toEpochMs.value) ?: 0L
                val profitRaw = database.reportDao().getProfitReportRaw(companyId, _fromEpochMs.value, _toEpochMs.value)
                
                var totalCogs = 0L
                for (item in profitRaw) {
                    // Same rule as the Profit report: the cost saved on each sale line first, so the dashboard
                    // and the report agree after purchase prices change.
                    val cost = item.recordedCost?.let { com.kadaikutty.pos.core.common.Money(it) } ?: costingStrategy.getProductCost(item.productId, item.totalQty)
                    totalCogs += cost.minorUnits
                }

                _totalSalesSum.value = salesSum
                _purchaseCostSum.value = totalCogs
                _totalPurchasesSum.value = purchasesSum
                _expensesSum.value = expensesSum
                _netProfitSum.value = salesSum - totalCogs - expensesSum

                // Compute period-filtered stock KPIs
                val stockData = database.reportDao().getStockReport(companyId, _fromEpochMs.value, _toEpochMs.value)
                var stockValSum = 0L
                // Summed per display unit in storage units (g / ml / pcs), formatted at the end.
                val unitOf: (String) -> String = { u -> if (u == "KG" || u == "LITER") u else "PIECE" }
                val inSum = linkedMapOf<String, Long>()
                val outSum = linkedMapOf<String, Long>()
                val closingSum = linkedMapOf<String, Long>()

                for (item in stockData) {
                    val isWeighted = item.unitType == "KG" || item.unitType == "LITER"
                    val u = unitOf(item.unitType)
                    inSum.merge(u, item.inwardQty, Long::plus)
                    outSum.merge(u, item.outwardQty, Long::plus)
                    closingSum.merge(u, item.currentStock, Long::plus)

                    val v = if (isWeighted) {
                        Math.round((item.purchasePrice * item.currentStock) / 1000.0)
                    } else {
                        item.purchasePrice * item.currentStock
                    }
                    stockValSum += v
                }

                val perUnit: (Map<String, Long>) -> String = { sums ->
                    sums.filterValues { it != 0L }.entries
                        .joinToString(" · ") { (u, q) -> com.kadaikutty.pos.feature.stock.domain.formatQuantity(q, u) }
                        .ifBlank { "0" }
                }
                _totalStockValue.value = stockValSum
                _totalStockInward.value = perUnit(inSum)
                _totalStockOutward.value = perUnit(outSum)
                _totalStockUnits.value = perUnit(closingSum)

                val query = ReportQuery(
                    type = _selectedType.value,
                    fromEpochMs = _fromEpochMs.value,
                    toEpochMs = _toEpochMs.value
                )
                val data = reportService.generate(query)
                _reportData.value = data
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to generate report"
                _reportData.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun exportPdf(): ByteArray {
        val data = _reportData.value ?: throw java.lang.IllegalStateException("No report data loaded to export")
        return pdfExporter.export(data)
    }

    fun shareReportPdf() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val pdfBytes = exportPdf()
                val filename = "${_selectedType.value.name.lowercase()}_report.pdf"
                shareManager.shareFile(pdfBytes, filename, "application/pdf")
            } catch (e: Exception) {
                _error.value = "Share failed: ${e.message}"
            }
        }
    }

    fun exportExcel(): ByteArray {
        val data = _reportData.value ?: throw java.lang.IllegalStateException("No report data loaded to export")
        return excelExporter.export(data)
    }

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
                productName = item.productName ?: product?.name ?: "Item #${item.productId.take(6)}",
                unitType = item.unitType ?: product?.unitType ?: "PIECE",
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

    fun deleteSale(saleId: String, billNumber: String, reason: String = "Cancelled in Reports", onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            val session = sessionStore.activeSession.first()
            val isAdminOrManager = session?.role in listOf("ADMIN", "SUPER_ADMIN", "OWNER") || session?.permissions?.contains(com.kadaikutty.pos.core.security.Permission.USER_MANAGE) == true
            if (session == null || !isAdminOrManager) {
                onError(Exception("Only admin or manager can delete sales."))
                return@launch
            }
            // The repository writes the BILL_CANCEL audit row inside the delete transaction.
            when (val result = saleRepository.deleteSale(saleId, billNumber, reason.ifBlank { "Bill deleted from Reports" })) {
                is com.kadaikutty.pos.core.common.AppResult.Success -> {
                    loadReport()
                    onSuccess()
                }
                is com.kadaikutty.pos.core.common.AppResult.Failure -> {
                    onError(Exception(result.error.userMessage))
                }
            }
        }
    }

    /** Deletes several bills one by one; each gets its own transaction and audit row. */
    fun deleteSales(billNumbers: List<String>, reason: String, onDone: (deleted: Int, failures: List<String>) -> Unit) {
        viewModelScope.launch {
            val session = sessionStore.activeSession.first()
            val isAdminOrManager = session?.role in listOf("ADMIN", "SUPER_ADMIN", "OWNER") || session?.permissions?.contains(com.kadaikutty.pos.core.security.Permission.USER_MANAGE) == true
            if (session == null || !isAdminOrManager) {
                onDone(0, listOf("Only admin or manager can delete sales."))
                return@launch
            }
            var deleted = 0
            val failures = mutableListOf<String>()
            for (billNumber in billNumbers.distinct()) {
                when (val result = saleRepository.deleteSale(billNumber, billNumber, reason.ifBlank { "Bill deleted from Reports" })) {
                    is com.kadaikutty.pos.core.common.AppResult.Success -> deleted++
                    is com.kadaikutty.pos.core.common.AppResult.Failure -> failures += "#$billNumber: ${result.error.userMessage}"
                }
            }
            loadReport()
            onDone(deleted, failures)
        }
    }
}

data class BillDetailItem(
    val productName: String,
    val unitType: String,
    val quantity: Long,
    val unitPriceMinorUnits: Long,
    val lineTotalMinorUnits: Long
)

data class BillDetailData(
    val sale: com.kadaikutty.pos.feature.billing.data.SaleEntity,
    val customerName: String,
    val customerPhone: String,
    val items: List<BillDetailItem>
)
