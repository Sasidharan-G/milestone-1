package com.company.billing.feature.reports.data

import com.company.billing.core.common.Money
import com.company.billing.core.database.ReportDao
import com.company.billing.feature.reports.domain.CostingStrategy
import com.company.billing.feature.reports.domain.ReportData
import com.company.billing.feature.reports.domain.ReportQuery
import com.company.billing.feature.reports.domain.ReportRepository
import com.company.billing.feature.reports.domain.ReportType
import com.company.billing.core.auth.SessionStore
import kotlinx.coroutines.flow.first

class ReportRepositoryImpl(
    private val reportDao: ReportDao,
    private val costingStrategy: CostingStrategy,
    private val sessionStore: SessionStore
) : ReportRepository {

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
                    rows = rows
                )
            }
            ReportType.STOCK -> {
                val data = reportDao.getStockReport(companyId)
                val rows = mutableListOf<List<String>>()
                var totalStockValue = 0L
                data.forEachIndexed { index, item ->
                    val qtyFormatted = if (item.unitType == "KG" || item.unitType == "LITER") {
                        String.format(java.util.Locale.US, "%.3f", item.currentStock / 1000.0)
                    } else {
                        item.currentStock.toString()
                    }
                    val stockVal = if (item.unitType == "KG" || item.unitType == "LITER") {
                        ((item.purchasePrice * item.currentStock) / 1000.0).toLong()
                    } else {
                        item.purchasePrice * item.currentStock
                    }
                    totalStockValue += stockVal
                    rows.add(listOf(
                        "${index + 1}",
                        item.productName,
                        item.categoryName,
                        item.unitType,
                        qtyFormatted,
                        Money(item.purchasePrice).toString(),
                        Money(stockVal).toString()
                    ))
                }
                if (data.isNotEmpty()) {
                    rows.add(listOf("", "TOTAL INVENTORY VALUE", "", "", "", "", Money(totalStockValue).toString()))
                }
                ReportData(
                    title = "Stock Inventory & Valuation Report",
                    columns = listOf("S.No", "Product", "Category", "Unit", "Current Stock", "Purchase Price", "Stock Value (Cost)"),
                    rows = rows
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
                    val cost = costingStrategy.getProductCost(item.productId, item.totalQty)
                    val profit = revenue - cost

                    grandTotalRevenue += revenue
                    grandTotalCost += cost

                    rows.add(listOf(
                        "${index + 1}",
                        item.productName,
                        item.totalQty.toString(),
                        revenue.toString(),
                        cost.toString(),
                        profit.toString()
                    ))
                }

                val grossProfit = grandTotalRevenue - grandTotalCost
                val netProfit = grossProfit - Money(totalExpenses)

                if (rows.isNotEmpty() || totalExpenses > 0) {
                    rows.add(listOf("", "---", "---", "---", "---", "---"))
                    rows.add(listOf("", "1. TOTAL SALES REVENUE", "", grandTotalRevenue.toString(), "", ""))
                    rows.add(listOf("", "2. COST OF GOODS SOLD (COGS)", "", "", grandTotalCost.toString(), ""))
                    rows.add(listOf("", "3. GROSS PROFIT (Sales - Cost)", "", "", "", grossProfit.toString()))
                    rows.add(listOf("", "4. OPERATING EXPENSES", "", "", "", "- " + Money(totalExpenses).toString()))
                    rows.add(listOf("", "5. NET PROFIT / LOSS", "", "", "", netProfit.toString()))
                }

                ReportData(
                    title = "Profit & Loss Statement (Net Business Health)",
                    columns = listOf("S.No", "Item / Description", "Qty Sold", "Sales Revenue", "Purchase Cost", "Profit"),
                    rows = rows
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
                    rows = rows
                )
            }
        }
    }
}
