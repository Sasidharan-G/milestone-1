package com.company.billing.feature.reports.domain

interface ReportService { suspend fun generate(query: ReportQuery): ReportData }
class DefaultReportService(private val repository: ReportRepository) : ReportService {
    override suspend fun generate(query: ReportQuery): ReportData = repository.query(query)
}
