package com.company.billing.core.export.domain

import com.company.billing.feature.reports.domain.ReportData

interface PdfExporter {
    fun export(data: ReportData): ByteArray
}

interface ExcelExporter {
    fun export(data: ReportData): ByteArray
}
