package com.kadaikutty.pos.feature.reports.data

import com.kadaikutty.pos.core.common.Money
import com.kadaikutty.pos.core.database.ReportDao
import com.kadaikutty.pos.feature.reports.domain.CostingStrategy
import com.kadaikutty.pos.feature.reports.domain.ReportData
import com.kadaikutty.pos.feature.reports.domain.ReportQuery
import com.kadaikutty.pos.feature.reports.domain.ReportRepository
import com.kadaikutty.pos.feature.reports.domain.ReportType
import com.kadaikutty.pos.core.auth.SessionStore
import kotlinx.coroutines.flow.first

class ReportRepositoryImpl(
    private val tenantDatabaseManager: com.kadaikutty.pos.core.database.TenantDatabaseManager?,
    private val costingStrategy: CostingStrategy,
    private val sessionStore: SessionStore,
    private val fallbackReportDao: ReportDao? = null,
) : ReportRepository {
    constructor(
        tenantDatabaseManager: com.kadaikutty.pos.core.database.TenantDatabaseManager,
        costingStrategy: CostingStrategy,
        sessionStore: SessionStore,
    ) : this(tenantDatabaseManager, costingStrategy, sessionStore, null)

    constructor(
        reportDao: ReportDao,
        costingStrategy: CostingStrategy,
        sessionStore: SessionStore,
    ) : this(null, costingStrategy, sessionStore, reportDao)

    private val reportDao: ReportDao
        get() = tenantDatabaseManager?.getDatabase()?.reportDao() ?: fallbackReportDao ?: error("No ReportDao available")

    override suspend fun query(query: ReportQuery): ReportData {
        val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
        val companyId = session.companyId
        val fromMs = query.fromEpochMs
        val toMs = query.toEpochMs

        return when (query.type) {
            ReportType.SALES -> {
                val data = reportDao.getSaleBillReport(companyId, fromMs, toMs)
                val rows = mutableListOf<List<String>>()
                data.forEachIndexed { index, it ->
                    val cleanBillNum = if (it.billNumber.startsWith("Bill #")) it.billNumber.removePrefix("Bill #") else it.billNumber
                    rows.add(listOf("${index + 1}", cleanBillNum, it.date, it.customerName, Money(it.totalAmount).toString()))
                }
                if (data.isNotEmpty()) {
                    val total = data.sumOf { it.totalAmount }
                    rows.add(listOf("", "TOTAL", "", "${data.size} Bills", Money(total).toString()))
                }
                ReportData(
                    title = "Sales & Bills Summary",
                    columns = listOf("S.No", "Bill Number", "Date & Time", "Customer", "Amount"),
                    rows = rows,
                    fromEpochMs = fromMs,
                    toEpochMs = toMs
                )
            }
            ReportType.STOCK -> {
                val data = reportDao.getStockReport(companyId, fromMs, toMs)
                val rows = mutableListOf<List<String>>()
                var totalStockValue = 0L
                val timeFormat = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())

                data.forEachIndexed { index, item ->
                    fun formatUnit(qty: Long): String {
                        return if (item.unitType == "KG" || item.unitType == "LITER") {
                            String.format(java.util.Locale.US, "%.3f", qty / 1000.0)
                        } else {
                            qty.toString()
                        }
                    }

                    val qtyFormatted = formatUnit(item.currentStock)
                    val stockVal = if (item.unitType == "KG" || item.unitType == "LITER") {
                        ((item.purchasePrice * item.currentStock) / 1000.0).toLong()
                    } else {
                        item.purchasePrice * item.currentStock
                    }
                    totalStockValue += stockVal

                    val inwardFormatted = if (item.inwardQty > 0) formatUnit(item.inwardQty) else ""
                    val outwardFormatted = if (item.outwardQty > 0) formatUnit(item.outwardQty) else ""
                    val openingFormatted = if (fromMs != null) formatUnit(item.openingStock) else ""
                    val lastActivity = if (item.lastUpdatedEpochMs > 0) timeFormat.format(java.util.Date(item.lastUpdatedEpochMs)) else "-"

                    rows.add(listOf(
                        "${index + 1}",
                        item.productName,
                        item.categoryName,
                        item.unitType,
                        qtyFormatted,
                        Money(item.purchasePrice).toString(),
                        Money(stockVal).toString(),
                        inwardFormatted,
                        outwardFormatted,
                        openingFormatted,
                        lastActivity
                    ))
                }
                if (data.isNotEmpty()) {
                    rows.add(listOf("", "TOTAL INVENTORY VALUE", "", "", "", "", Money(totalStockValue).toString(), "", "", "", ""))
                }
                ReportData(
                    title = if (fromMs != null || toMs != null) "Stock Inventory Valuation (Period Filtered)" else "Stock Inventory & Valuation Report",
                    columns = listOf("S.No", "Product", "Category", "Unit", "Stock", "Purchase Price", "Stock Value (Cost)", "Inward", "Outward", "Opening", "Last Activity"),
                    rows = rows,
                    fromEpochMs = fromMs,
                    toEpochMs = toMs
                )
            }
            ReportType.PROFIT -> {
                val raw = reportDao.getProfitReportRaw(companyId, fromMs, toMs)
                val expenses = reportDao.getExpensesReport(companyId, fromMs, toMs)
                val totalExpenses = expenses.sumOf { it.amount }

                val rows = mutableListOf<List<String>>()
                var grandTotalRevenue = Money.Zero
                var grandTotalCost = Money.Zero

                raw.forEachIndexed { index, item ->
                    val revenue = Money(item.totalRevenue)
                    val cost = item.recordedCost?.let { Money(it) } ?: costingStrategy.getProductCost(item.productId, item.totalQty)
                    val profit = revenue - cost

                    grandTotalRevenue += revenue
                    grandTotalCost += cost

                    rows.add(listOf(
                        "${index + 1}",
                        item.productName,
                        com.kadaikutty.pos.feature.stock.domain.formatQuantity(item.totalQty, item.unitType),
                        revenue.toString(),
                        cost.toString(),
                        profit.toString()
                    ))
                }

                if (expenses.isNotEmpty()) {
                    rows.add(listOf("", "---", "---", "---", "---", "---"))
                    rows.add(listOf("", "OPERATING EXPENSES BREAKDOWN", "", "", "", ""))
                    expenses.forEach { exp ->
                        rows.add(listOf(
                            "",
                            "   - ${exp.description} (${exp.date})",
                            "",
                            "",
                            "",
                            "- " + Money(exp.amount).toString()
                        ))
                    }
                }

                val grossProfit = grandTotalRevenue - grandTotalCost
                val netProfit = grossProfit - Money(totalExpenses)

                if (rows.isNotEmpty() || totalExpenses > 0) {
                    rows.add(listOf("", "---", "---", "---", "---", "---"))
                    rows.add(listOf("", "1. TOTAL SALES REVENUE", "", grandTotalRevenue.toString(), "", ""))
                    rows.add(listOf("", "2. COST OF GOODS SOLD (COGS)", "", "", grandTotalCost.toString(), ""))
                    rows.add(listOf("", "3. GROSS PROFIT (Sales - Cost)", "", "", "", grossProfit.toString()))
                    rows.add(listOf("", "4. TOTAL OPERATING EXPENSES", "", "", "", "- " + Money(totalExpenses).toString()))
                    rows.add(listOf("", "5. NET PROFIT / LOSS", "", "", "", netProfit.toString()))
                }

                ReportData(
                    title = if (raw.any { it.recordedCost == null }) "Profit & Loss (legacy costs estimated)" else "Profit & Loss Statement",
                    columns = listOf("S.No", "Item / Description", "Qty Sold", "Sales Revenue", "Purchase Cost", "Profit"),
                    rows = rows,
                    fromEpochMs = fromMs,
                    toEpochMs = toMs
                )
            }
            ReportType.PURCHASES -> {
                val data = reportDao.getPurchaseReport(companyId, fromMs, toMs)
                val rows = mutableListOf<List<String>>()
                data.forEachIndexed { index, it ->
                    val invDisplay = when {
                        !it.invoiceNumber.isNullOrBlank() -> it.invoiceNumber
                        !it.orderNumber.isNullOrBlank() -> "Order #${it.orderNumber}"
                        else -> "Order #${String.format(java.util.Locale.US, "%02d", index + 1)}"
                    }
                    rows.add(listOf("${index + 1}", invDisplay, it.date, it.supplierName, it.paymentMode, Money(it.totalAmount).toString()))
                }
                if (data.isNotEmpty()) {
                    val total = data.sumOf { it.totalAmount }
                    rows.add(listOf("", "TOTAL PURCHASES", "", "${data.size} Orders", "", Money(total).toString()))
                }
                ReportData(
                    title = "Purchases & Supplier Bills Report",
                    columns = listOf("S.No", "Supplier Inv / ID", "Date & Time", "Supplier", "Payment Mode", "Total Amount"),
                    rows = rows,
                    fromEpochMs = fromMs,
                    toEpochMs = toMs
                )
            }
        }
    }
}
