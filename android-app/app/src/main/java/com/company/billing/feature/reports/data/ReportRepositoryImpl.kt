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
            ReportType.SALE_AMOUNT -> {
                val data = reportDao.getSaleAmountReport(companyId, fromMs, toMs)
                ReportData(
                    title = "Sale Amount Report",
                    columns = listOf("Date", "Total Sales"),
                    rows = data.map { listOf(it.date, Money(it.totalAmount).toString()) }
                )
            }
            ReportType.SALE_BILL -> {
                val data = reportDao.getSaleBillReport(companyId, fromMs, toMs)
                ReportData(
                    title = "Sale Bill Report",
                    columns = listOf("Bill Number", "Date", "Total Amount", "Customer"),
                    rows = data.map { listOf(it.billNumber, it.date, Money(it.totalAmount).toString(), it.customerName) }
                )
            }
            ReportType.ITEM_WISE -> {
                val data = reportDao.getItemWiseReport(companyId, fromMs, toMs)
                ReportData(
                    title = "Item-wise Report",
                    columns = listOf("Product Name", "Category", "Quantity Sold", "Total Revenue"),
                    rows = data.map { listOf(it.productName, it.categoryName, it.totalQty.toString(), Money(it.totalRevenue).toString()) }
                )
            }
            ReportType.STOCK -> {
                val data = reportDao.getStockReport(companyId)
                ReportData(
                    title = "Stock Report",
                    columns = listOf("Product Name", "Category", "Current Stock"),
                    rows = data.map { listOf(it.productName, it.categoryName, it.currentStock.toString()) }
                )
            }
            ReportType.PROFIT -> {
                val raw = reportDao.getProfitReportRaw(companyId, fromMs, toMs)
                val rows = mutableListOf<List<String>>()
                var grandTotalRevenue = Money.Zero
                var grandTotalCost = Money.Zero
                var grandTotalProfit = Money.Zero
                
                for (item in raw) {
                    val revenue = Money(item.totalRevenue)
                    val cost = costingStrategy.getProductCost(item.productId, item.totalQty)
                    val profit = revenue - cost
                    
                    grandTotalRevenue += revenue
                    grandTotalCost += cost
                    grandTotalProfit += profit
                    
                    rows.add(listOf(
                        item.productName,
                        item.totalQty.toString(),
                        revenue.toString(),
                        cost.toString(),
                        profit.toString()
                    ))
                }
                
                if (rows.isNotEmpty()) {
                    rows.add(listOf(
                        "TOTAL",
                        "",
                        grandTotalRevenue.toString(),
                        grandTotalCost.toString(),
                        grandTotalProfit.toString()
                    ))
                }

                ReportData(
                    title = "Profit Report (Disclaimer: Estimations based on weighted average purchase price)",
                    columns = listOf("Product Name", "Qty Sold", "Revenue", "Estimated Cost", "Estimated Profit"),
                    rows = rows
                )
            }
            ReportType.PURCHASE -> {
                val data = reportDao.getPurchaseReport(companyId, fromMs, toMs)
                ReportData(
                    title = "Purchase Report",
                    columns = listOf("Purchase ID", "Date", "Supplier", "Total Amount"),
                    rows = data.map { listOf(it.purchaseId, it.date, it.supplierName, Money(it.totalAmount).toString()) }
                )
            }
            ReportType.CUSTOMER -> {
                val data = reportDao.getCustomerReport(companyId, fromMs, toMs)
                ReportData(
                    title = "Customer Report",
                    columns = listOf("Customer Name", "Total Bills", "Total Spent"),
                    rows = data.map { listOf(it.customerName, it.totalBills.toString(), Money(it.totalSpent).toString()) }
                )
            }
            ReportType.SUPPLIER -> {
                val data = reportDao.getSupplierReport(companyId, fromMs, toMs)
                ReportData(
                    title = "Supplier Report",
                    columns = listOf("Supplier Name", "Total Bills", "Total Purchased"),
                    rows = data.map { listOf(it.supplierName, it.totalBills.toString(), Money(it.totalPurchased).toString()) }
                )
            }
            ReportType.EXPENSES -> {
                val data = reportDao.getExpensesReport(companyId, fromMs, toMs)
                ReportData(
                    title = "Expenses Report",
                    columns = listOf("Expense ID", "Date", "Description", "Amount"),
                    rows = data.map { listOf(it.expenseId, it.date, it.description, Money(it.amount).toString()) }
                )
            }
        }
    }
}
