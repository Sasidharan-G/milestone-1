package com.kadaikutty.pos.feature.reports.domain

enum class ReportType { 
    SALES, 
    STOCK, 
    PROFIT, 
    PURCHASES 
}
data class ReportQuery(val type: ReportType, val fromEpochMs: Long?, val toEpochMs: Long?)
data class ReportData(val title: String, val columns: List<String>, val rows: List<List<String>>)
interface ReportRepository { suspend fun query(query: ReportQuery): ReportData }
