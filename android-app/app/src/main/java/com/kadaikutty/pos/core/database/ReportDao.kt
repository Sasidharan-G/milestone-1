package com.kadaikutty.pos.core.database

import androidx.room.Dao
import androidx.room.Query

// Helper data classes for Room queries mapping.
data class SaleAmountRow(val date: String, val totalAmount: Long)
data class SaleBillRow(val billNumber: String, val date: String, val totalAmount: Long, val customerName: String)
data class ItemWiseRow(val productName: String, val categoryName: String, val totalQty: Long, val totalRevenue: Long)
data class StockReportRow(val productName: String, val categoryName: String, val unitType: String, val purchasePrice: Long, val salePrice: Long, val currentStock: Long)
data class ProfitReportRawRow(val productId: String, val productName: String, val totalQty: Long, val totalRevenue: Long)
data class PurchaseReportRow(val purchaseId: String, val orderNumber: String?, val invoiceNumber: String?, val date: String, val supplierName: String, val paymentMode: String, val totalAmount: Long)
data class CustomerReportRow(val customerName: String, val totalBills: Long, val totalSpent: Long)
data class SupplierReportRow(val supplierName: String, val totalBills: Long, val totalPurchased: Long)
data class ExpenseReportRow(val expenseId: String, val date: String, val description: String, val amount: Long)
data class LowStockRow(val productName: String, val categoryName: String, val currentStock: Long, val minStockLevel: Double)

@Dao
interface ReportDao {
    @Query("""
        SELECT 
            strftime('%Y-%m-%d', datetime(createdAtEpochMs / 1000, 'unixepoch', 'localtime')) as date, 
            SUM(totalMinorUnits) as totalAmount
        FROM sales
        WHERE companyId = :companyId
          AND (:fromEpochMs IS NULL OR createdAtEpochMs >= :fromEpochMs)
          AND (:toEpochMs IS NULL OR createdAtEpochMs <= :toEpochMs)
        GROUP BY date
        ORDER BY date DESC
    """)
    suspend fun getSaleAmountReport(companyId: String, fromEpochMs: Long?, toEpochMs: Long?): List<SaleAmountRow>

    @Query("""
        SELECT 
            s.billNumber, 
            strftime('%Y-%m-%d %H:%M:%S', datetime(s.createdAtEpochMs / 1000, 'unixepoch', 'localtime')) as date, 
            s.totalMinorUnits as totalAmount, 
            CASE 
                WHEN s.customerId = 'online' THEN 'Online Customer' 
                WHEN s.customerId IS NULL THEN 'Walk-in Customer'
                ELSE COALESCE(c.name, 'Walk-in Customer')
            END as customerName
        FROM sales s
        LEFT JOIN customers c ON s.customerId = c.id AND c.companyId = :companyId
        WHERE s.companyId = :companyId
          AND (:fromEpochMs IS NULL OR s.createdAtEpochMs >= :fromEpochMs)
          AND (:toEpochMs IS NULL OR s.createdAtEpochMs <= :toEpochMs)
        ORDER BY s.createdAtEpochMs DESC
    """)
    suspend fun getSaleBillReport(companyId: String, fromEpochMs: Long?, toEpochMs: Long?): List<SaleBillRow>

    @Query("""
        SELECT 
            p.name as productName, 
            cat.name as categoryName, 
            SUM(si.quantity) as totalQty, 
            SUM(si.lineTotalMinorUnits) as totalRevenue
        FROM sale_items si
        INNER JOIN sales s ON si.saleId = s.id AND s.companyId = :companyId
        INNER JOIN products p ON si.productId = p.id AND p.companyId = :companyId
        INNER JOIN categories cat ON p.categoryId = cat.id AND cat.companyId = :companyId
        WHERE si.companyId = :companyId
          AND (:fromEpochMs IS NULL OR s.createdAtEpochMs >= :fromEpochMs)
          AND (:toEpochMs IS NULL OR s.createdAtEpochMs <= :toEpochMs)
        GROUP BY p.id
        ORDER BY totalRevenue DESC
    """)
    suspend fun getItemWiseReport(companyId: String, fromEpochMs: Long?, toEpochMs: Long?): List<ItemWiseRow>

    @Query("""
        SELECT 
            p.name as productName, 
            cat.name as categoryName, 
            p.unitType as unitType,
            p.purchasePriceMinorUnits as purchasePrice,
            p.salePriceMinorUnits as salePrice,
            COALESCE(SUM(sm.quantityDelta), 0) as currentStock
        FROM products p
        INNER JOIN categories cat ON p.categoryId = cat.id AND cat.companyId = :companyId
        LEFT JOIN stock_movements sm ON p.id = sm.productId AND sm.companyId = :companyId
        WHERE p.companyId = :companyId
        GROUP BY p.id
        ORDER BY p.name ASC
    """)
    suspend fun getStockReport(companyId: String): List<StockReportRow>

    @Query("""
        SELECT 
            p.name as productName, 
            cat.name as categoryName, 
            COALESCE(SUM(sm.quantityDelta), 0) as currentStock,
            p.minStockLevel
        FROM products p
        INNER JOIN categories cat ON p.categoryId = cat.id AND cat.companyId = :companyId
        LEFT JOIN stock_movements sm ON p.id = sm.productId AND sm.companyId = :companyId
        WHERE p.companyId = :companyId
        GROUP BY p.id
        HAVING currentStock < p.minStockLevel
        ORDER BY currentStock ASC
    """)
    fun getLowStockProducts(companyId: String): kotlinx.coroutines.flow.Flow<List<LowStockRow>>

    @Query("""
        SELECT 
            p.id as productId,
            p.name as productName, 
            SUM(si.quantity) as totalQty, 
            SUM(si.lineTotalMinorUnits) as totalRevenue
        FROM sale_items si
        INNER JOIN sales s ON si.saleId = s.id AND s.companyId = :companyId
        INNER JOIN products p ON si.productId = p.id AND p.companyId = :companyId
        WHERE si.companyId = :companyId
          AND (:fromEpochMs IS NULL OR s.createdAtEpochMs >= :fromEpochMs)
          AND (:toEpochMs IS NULL OR s.createdAtEpochMs <= :toEpochMs)
        GROUP BY p.id
    """)
    suspend fun getProfitReportRaw(companyId: String, fromEpochMs: Long?, toEpochMs: Long?): List<ProfitReportRawRow>

    @Query("""
        SELECT 
            p.id as purchaseId, 
            p.orderNumber as orderNumber,
            p.invoiceNumber as invoiceNumber,
            strftime('%Y-%m-%d %H:%M:%S', datetime(p.createdAtEpochMs / 1000, 'unixepoch', 'localtime')) as date, 
            COALESCE(s.name, 'General Supplier') as supplierName, 
            p.paymentMode as paymentMode,
            p.totalMinorUnits as totalAmount
        FROM purchases p
        LEFT JOIN suppliers s ON p.supplierId = s.id AND s.companyId = :companyId
        WHERE p.companyId = :companyId
          AND (:fromEpochMs IS NULL OR p.createdAtEpochMs >= :fromEpochMs)
          AND (:toEpochMs IS NULL OR p.createdAtEpochMs <= :toEpochMs)
        ORDER BY p.createdAtEpochMs DESC
    """)
    suspend fun getPurchaseReport(companyId: String, fromEpochMs: Long?, toEpochMs: Long?): List<PurchaseReportRow>

    @Query("""
        SELECT 
            CASE 
                WHEN s.customerId = 'online' THEN 'Online Customer' 
                WHEN s.customerId IS NULL THEN 'Walk-in Customer'
                ELSE COALESCE(c.name, 'Walk-in Customer')
            END as customerName, 
            COUNT(s.id) as totalBills, 
            SUM(s.totalMinorUnits) as totalSpent
        FROM sales s
        LEFT JOIN customers c ON s.customerId = c.id AND c.companyId = :companyId
        WHERE s.companyId = :companyId
          AND (:fromEpochMs IS NULL OR s.createdAtEpochMs >= :fromEpochMs)
          AND (:toEpochMs IS NULL OR s.createdAtEpochMs <= :toEpochMs)
        GROUP BY customerName
        ORDER BY totalSpent DESC
    """)
    suspend fun getCustomerReport(companyId: String, fromEpochMs: Long?, toEpochMs: Long?): List<CustomerReportRow>

    @Query("""
        SELECT 
            s.name as supplierName, 
            COUNT(p.id) as totalBills, 
            SUM(p.totalMinorUnits) as totalPurchased
        FROM suppliers s
        INNER JOIN purchases p ON s.id = p.supplierId AND p.companyId = :companyId
        WHERE s.companyId = :companyId
          AND (:fromEpochMs IS NULL OR p.createdAtEpochMs >= :fromEpochMs)
          AND (:toEpochMs IS NULL OR p.createdAtEpochMs <= :toEpochMs)
        GROUP BY s.id
        ORDER BY totalPurchased DESC
    """)
    suspend fun getSupplierReport(companyId: String, fromEpochMs: Long?, toEpochMs: Long?): List<SupplierReportRow>

    @Query("""
        SELECT 
            id as expenseId, 
            strftime('%Y-%m-%d %H:%M:%S', datetime(createdAtEpochMs / 1000, 'unixepoch', 'localtime')) as date, 
            description, 
            amountMinorUnits as amount
        FROM expenses
        WHERE companyId = :companyId
          AND (:fromEpochMs IS NULL OR createdAtEpochMs >= :fromEpochMs)
          AND (:toEpochMs IS NULL OR createdAtEpochMs <= :toEpochMs)
        ORDER BY createdAtEpochMs DESC
    """)
    suspend fun getExpensesReport(companyId: String, fromEpochMs: Long?, toEpochMs: Long?): List<ExpenseReportRow>

    @Query("SELECT SUM(totalMinorUnits) FROM sales WHERE companyId = :companyId AND (:fromEpochMs IS NULL OR createdAtEpochMs >= :fromEpochMs) AND (:toEpochMs IS NULL OR createdAtEpochMs <= :toEpochMs)")
    suspend fun getTotalSalesSum(companyId: String, fromEpochMs: Long?, toEpochMs: Long?): Long?

    @Query("SELECT SUM(totalMinorUnits) FROM purchases WHERE companyId = :companyId AND (:fromEpochMs IS NULL OR createdAtEpochMs >= :fromEpochMs) AND (:toEpochMs IS NULL OR createdAtEpochMs <= :toEpochMs)")
    suspend fun getTotalPurchasesSum(companyId: String, fromEpochMs: Long?, toEpochMs: Long?): Long?

    @Query("SELECT SUM(amountMinorUnits) FROM expenses WHERE companyId = :companyId AND (:fromEpochMs IS NULL OR createdAtEpochMs >= :fromEpochMs) AND (:toEpochMs IS NULL OR createdAtEpochMs <= :toEpochMs)")
    suspend fun getTotalExpensesSum(companyId: String, fromEpochMs: Long?, toEpochMs: Long?): Long?
}
