package com.kadaikutty.pos.core.export.domain

import com.kadaikutty.pos.feature.reports.domain.ReportData

interface PdfExporter {
    fun export(data: ReportData): ByteArray
}

interface ExcelExporter {
    fun export(data: ReportData): ByteArray
}
