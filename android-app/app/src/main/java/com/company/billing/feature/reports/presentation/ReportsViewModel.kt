package com.company.billing.feature.reports.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.company.billing.feature.reports.domain.ReportData
import com.company.billing.feature.reports.domain.ReportQuery
import com.company.billing.feature.reports.domain.ReportService
import com.company.billing.feature.reports.domain.ReportType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.company.billing.core.export.domain.PdfExporter
import com.company.billing.core.export.domain.ExcelExporter

import com.company.billing.core.database.BillingDatabase

import com.company.billing.core.auth.SessionStore
import kotlinx.coroutines.flow.first

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reportService: ReportService,
    private val pdfExporter: PdfExporter,
    private val excelExporter: ExcelExporter,
    private val database: BillingDatabase,
    private val sessionStore: SessionStore
) : ViewModel() {

    private val _selectedType = MutableStateFlow(ReportType.SALE_AMOUNT)
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

    private val _netProfitSum = MutableStateFlow(0L)
    val netProfitSum: StateFlow<Long> = _netProfitSum.asStateFlow()

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

                _totalSalesSum.value = salesSum
                _purchaseCostSum.value = purchasesSum
                _netProfitSum.value = salesSum - purchasesSum - expensesSum

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
}
