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

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reportService: ReportService,
    private val pdfExporter: PdfExporter,
    private val excelExporter: ExcelExporter
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
