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

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reportService: ReportService,
    private val costingStrategy: com.kadaikutty.pos.feature.reports.domain.CostingStrategy,
    private val pdfExporter: PdfExporter,
    private val excelExporter: ExcelExporter,
    private val database: BillingDatabase,
    private val saleRepository: SaleRepository,
    private val sessionStore: SessionStore
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

    init {
        loadReport()
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
                    val cost = costingStrategy.getProductCost(item.productId, item.totalQty)
                    totalCogs += cost.minorUnits
                }

                _totalSalesSum.value = salesSum
                _purchaseCostSum.value = totalCogs
                _totalPurchasesSum.value = purchasesSum
                _expensesSum.value = expensesSum
                _netProfitSum.value = salesSum - totalCogs - expensesSum

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

    fun deleteSale(saleId: String, billNumber: String, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            when (val result = saleRepository.deleteSale(saleId, billNumber)) {
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
