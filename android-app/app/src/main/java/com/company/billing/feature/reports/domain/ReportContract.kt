package com.company.billing.feature.reports.domain

enum class ReportType { SALE_AMOUNT, SALE_BILL, ITEM_WISE, STOCK, PROFIT, PURCHASE, CUSTOMER, SUPPLIER, EXPENSES }
data class ReportQuery(val type: ReportType, val fromEpochMs: Long?, val toEpochMs: Long?)
data class ReportData(val title: String, val columns: List<String>, val rows: List<List<String>>)
interface ReportRepository { suspend fun query(query: ReportQuery): ReportData }
/** REQUIRES_CLIENT_CONFIRMATION: PROFIT_COSTING_METHOD and final report columns/formulas. */
