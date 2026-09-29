package com.kadaikutty.pos.core.database

import androidx.room.Dao
import androidx.room.Query

// Helper data classes for Room queries mapping.
data class SaleBillRow(val billNumber: String, val date: String, val totalAmount: Long, val customerName: String)
data class StockReportRow(
    val productName: String,
    val categoryName: String,
    val unitType: String,
    val purchasePrice: Long,
    val salePrice: Long,
    val currentStock: Long,
    val openingStock: Long = 0L,
    val inwardQty: Long = 0L,
    val outwardQty: Long = 0L,
    val lastUpdatedEpochMs: Long = 0L
)
data class ProfitReportRawRow(val productId: String, val productName: String, val totalQty: Long, val totalRevenue: Long, val recordedCost: Long? = null, val unitType: String = "PIECE")
data class PurchaseReportRow(val purchaseId: String, val orderNumber: String?, val invoiceNumber: String?, val date: String, val supplierName: String, val paymentMode: String, val totalAmount: Long)
data class ExpenseReportRow(val expenseId: String, val date: String, val description: String, val amount: Long)
data class LowStockRow(val productName: String, val categoryName: String, val currentStock: Long, val minStockLevel: Double, val unitType: String = "PIECE")

@Dao
interface ReportDao {
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
        WHERE s.companyId = :companyId AND s.status != 'VOID'
          AND (:fromEpochMs IS NULL OR s.createdAtEpochMs >= :fromEpochMs)
          AND (:toEpochMs IS NULL OR s.createdAtEpochMs <= :toEpochMs)
        ORDER BY s.createdAtEpochMs DESC
    """)
    suspend fun getSaleBillReport(companyId: String, fromEpochMs: Long?, toEpochMs: Long?): List<SaleBillRow>

    @Query("""
        SELECT
            p.name as productName,
            COALESCE(cat.name, 'General') as categoryName,
            p.unitType as unitType,
            p.purchasePriceMinorUnits as purchasePrice,
            p.salePriceMinorUnits as salePrice,
            COALESCE(SUM(CASE WHEN (:toEpochMs IS NULL OR sm.createdAtEpochMs <= :toEpochMs) THEN sm.quantityDelta ELSE 0 END), 0) as currentStock,
            COALESCE(SUM(CASE WHEN (:fromEpochMs IS NOT NULL AND sm.createdAtEpochMs < :fromEpochMs) THEN sm.quantityDelta ELSE 0 END), 0) as openingStock,
            COALESCE(SUM(CASE WHEN (:fromEpochMs IS NULL OR sm.createdAtEpochMs >= :fromEpochMs) AND (:toEpochMs IS NULL OR sm.createdAtEpochMs <= :toEpochMs) AND sm.quantityDelta > 0 THEN sm.quantityDelta ELSE 0 END), 0) as inwardQty,
            COALESCE(SUM(CASE WHEN (:fromEpochMs IS NULL OR sm.createdAtEpochMs >= :fromEpochMs) AND (:toEpochMs IS NULL OR sm.createdAtEpochMs <= :toEpochMs) AND sm.quantityDelta < 0 THEN ABS(sm.quantityDelta) ELSE 0 END), 0) as outwardQty,
            COALESCE(MAX(sm.createdAtEpochMs), p.updatedAtEpochMs) as lastUpdatedEpochMs
        FROM products p
        LEFT JOIN categories cat ON p.categoryId = cat.id AND cat.companyId = :companyId
        LEFT JOIN stock_movements sm ON p.id = sm.productId AND sm.companyId = :companyId
        WHERE p.companyId = :companyId
        GROUP BY p.id
        ORDER BY p.name ASC
    """)
    suspend fun getStockReport(companyId: String, fromEpochMs: Long?, toEpochMs: Long?): List<StockReportRow>

    @Query("""
        SELECT 
            p.name as productName, 
            cat.name as categoryName, 
            COALESCE(SUM(sm.quantityDelta), 0) as currentStock,
            p.minStockLevel,
            p.unitType as unitType
        FROM products p
        INNER JOIN categories cat ON p.categoryId = cat.id AND cat.companyId = :companyId
        LEFT JOIN stock_movements sm ON p.id = sm.productId AND sm.companyId = :companyId
        WHERE p.companyId = :companyId AND p.minStockLevel > 0
        GROUP BY p.id
        HAVING currentStock <= p.minStockLevel * (CASE WHEN p.unitType IN ('KG', 'LITER') THEN 1000 ELSE 1 END)
        ORDER BY currentStock ASC
    """)
    fun getLowStockProducts(companyId: String): kotlinx.coroutines.flow.Flow<List<LowStockRow>>

    @Query("""
        SELECT 
            p.id as productId,
            p.name as productName, 
            p.unitType as unitType,
            SUM(si.quantity) as totalQty, 
            SUM(COALESCE(si.netRevenueMinorUnits, si.lineTotalMinorUnits - (
                CAST(s.discountMinorUnits * 1.0 * (SELECT SUM(x.lineTotalMinorUnits) FROM sale_items x WHERE x.saleId = si.saleId AND x.productId <= si.productId) / MAX(1, (SELECT SUM(x.lineTotalMinorUnits) FROM sale_items x WHERE x.saleId = si.saleId)) AS INTEGER)
                - CAST(s.discountMinorUnits * 1.0 * COALESCE((SELECT SUM(x.lineTotalMinorUnits) FROM sale_items x WHERE x.saleId = si.saleId AND x.productId < si.productId), 0) / MAX(1, (SELECT SUM(x.lineTotalMinorUnits) FROM sale_items x WHERE x.saleId = si.saleId)) AS INTEGER)
            ))) as totalRevenue,
            CASE WHEN COUNT(si.costTotalMinorUnits) = COUNT(*) THEN SUM(si.costTotalMinorUnits) ELSE NULL END as recordedCost
        FROM sale_items si
        INNER JOIN sales s ON si.saleId = s.id AND s.companyId = :companyId AND s.status != 'VOID'
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

    @Query("SELECT SUM(totalMinorUnits) FROM sales WHERE companyId = :companyId AND status != 'VOID' AND (:fromEpochMs IS NULL OR createdAtEpochMs >= :fromEpochMs) AND (:toEpochMs IS NULL OR createdAtEpochMs <= :toEpochMs)")
    suspend fun getTotalSalesSum(companyId: String, fromEpochMs: Long?, toEpochMs: Long?): Long?

    @Query("SELECT SUM(totalMinorUnits) FROM purchases WHERE companyId = :companyId AND (:fromEpochMs IS NULL OR createdAtEpochMs >= :fromEpochMs) AND (:toEpochMs IS NULL OR createdAtEpochMs <= :toEpochMs)")
    suspend fun getTotalPurchasesSum(companyId: String, fromEpochMs: Long?, toEpochMs: Long?): Long?

    @Query("SELECT SUM(amountMinorUnits) FROM expenses WHERE companyId = :companyId AND (:fromEpochMs IS NULL OR createdAtEpochMs >= :fromEpochMs) AND (:toEpochMs IS NULL OR createdAtEpochMs <= :toEpochMs)")
    suspend fun getTotalExpensesSum(companyId: String, fromEpochMs: Long?, toEpochMs: Long?): Long?
}
