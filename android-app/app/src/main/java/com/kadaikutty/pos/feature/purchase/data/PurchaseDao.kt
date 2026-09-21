package com.kadaikutty.pos.feature.purchase.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kadaikutty.pos.feature.billing.data.StockMovementEntity
import com.kadaikutty.pos.feature.stock.domain.ProductStock
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Query("SELECT * FROM supplier_credits WHERE companyId = :companyId AND referenceId = :id")
    suspend fun creditsFor(companyId: String, id: String): List<com.kadaikutty.pos.feature.masters.data.SupplierCreditEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun createPurchase(purchase: PurchaseEntity)
    @androidx.room.Update suspend fun updatePurchase(purchase: PurchaseEntity)
    @Query("SELECT * FROM purchases WHERE companyId = :companyId AND id = :id")
    suspend fun getById(companyId: String, id: String): PurchaseEntity?
    @Query("SELECT COUNT(*) FROM supplier_credits WHERE companyId = :companyId AND referenceId = :id")
    suspend fun linkedCreditCount(companyId: String, id: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPurchase(purchase: PurchaseEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPurchases(items: List<PurchaseEntity>)
    @Query("DELETE FROM purchases WHERE companyId = :companyId") suspend fun deletePurchasesByCompany(companyId: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertItems(items: List<PurchaseItemEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertStockMovements(movements: List<StockMovementEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSupplierCredit(credit: com.kadaikutty.pos.feature.masters.data.SupplierCreditEntity)
    
    @Query("DELETE FROM purchase_items WHERE companyId = :companyId") suspend fun deletePurchaseItemsByCompany(companyId: String)

    @Query("""
        SELECT 
            p.id as productId, 
            p.name as productName, 
            c.name as categoryName, 
            COALESCE(SUM(sm.quantityDelta), 0) as currentStock,
            p.minStockLevel as minStockLevel,
            p.unitType as unitType
        FROM products p
        INNER JOIN categories c ON p.categoryId = c.id
        LEFT JOIN stock_movements sm ON p.id = sm.productId AND sm.companyId = :companyId
        WHERE p.companyId = :companyId AND c.companyId = :companyId
        GROUP BY p.id
        ORDER BY p.name ASC
    """)
    fun getStockBalances(companyId: String): Flow<List<ProductStock>>

    @Query("SELECT * FROM purchases WHERE companyId = :companyId ORDER BY createdAtEpochMs DESC")
    fun getPurchases(companyId: String): Flow<List<PurchaseEntity>>

    /**
     * Purchase History with its search and date filter. [query] matches the invoice or order
     * number, notes, supplier name or phone, a product purchased, the payment mode, or the
     * whole-rupee amount. [fromEpochMs] is inclusive and [toEpochMs] exclusive.
     */
    @Query(
        "SELECT p.* FROM purchases p LEFT JOIN suppliers s ON s.id = p.supplierId AND s.companyId = p.companyId " +
        "WHERE p.companyId = :companyId AND p.createdAtEpochMs >= :fromEpochMs AND p.createdAtEpochMs < :toEpochMs " +
        "AND (:query = '' OR p.invoiceNumber LIKE '%' || :query || '%' OR p.orderNumber LIKE '%' || :query || '%' " +
        "OR p.notes LIKE '%' || :query || '%' OR s.name LIKE '%' || :query || '%' OR s.phone LIKE '%' || :query || '%' " +
        "OR p.paymentMode LIKE '%' || :query || '%' OR CAST(p.totalMinorUnits / 100 AS TEXT) LIKE :query || '%' " +
        "OR EXISTS (SELECT 1 FROM purchase_items i JOIN products pr ON pr.id = i.productId AND pr.companyId = i.companyId " +
        "WHERE i.companyId = p.companyId AND i.purchaseId = p.id AND pr.name LIKE '%' || :query || '%')) " +
        "ORDER BY p.createdAtEpochMs DESC"
    )
    fun searchPurchases(companyId: String, query: String, fromEpochMs: Long, toEpochMs: Long): Flow<List<PurchaseEntity>>

    @Query("SELECT SUM(totalMinorUnits) FROM purchases WHERE companyId = :companyId AND createdAtEpochMs >= :sinceEpochMs")
    fun getPurchasesTotalSince(companyId: String, sinceEpochMs: Long): Flow<Long?>

    @Query("SELECT * FROM purchase_items WHERE companyId = :companyId AND purchaseId = :purchaseId")
    fun getPurchaseItems(companyId: String, purchaseId: String): Flow<List<PurchaseItemEntity>>

    @Query("SELECT SUM(unitValueMinorUnits * 1.0 * quantity) / NULLIF(SUM(quantity), 0) FROM purchase_items WHERE companyId = :companyId AND productId = :productId")
    suspend fun getAveragePurchasePrice(companyId: String, productId: String): Double?

    @Query("SELECT * FROM purchases WHERE companyId = :companyId AND supplierId = :supplierId ORDER BY createdAtEpochMs DESC")
    fun getPurchasesForSupplier(companyId: String, supplierId: String): Flow<List<PurchaseEntity>>

    @Query("SELECT orderNumber FROM purchases WHERE companyId = :companyId")
    suspend fun getAllOrderNumbers(companyId: String): List<String?>

    @Query("SELECT * FROM purchase_items WHERE companyId = :companyId AND purchaseId = :purchaseId")
    suspend fun getPurchaseItemsList(companyId: String, purchaseId: String): List<PurchaseItemEntity>

    @Query("DELETE FROM purchases WHERE companyId = :companyId AND id = :purchaseId")
    suspend fun deletePurchase(companyId: String, purchaseId: String)

    @Query("DELETE FROM purchase_items WHERE companyId = :companyId AND purchaseId = :purchaseId")
    suspend fun deletePurchaseItems(companyId: String, purchaseId: String)

    @Query("DELETE FROM stock_movements WHERE companyId = :companyId AND referenceId = :purchaseId")
    suspend fun deletePurchaseStockMovements(companyId: String, purchaseId: String)

    @Query("DELETE FROM supplier_credits WHERE companyId = :companyId AND (referenceId = :referenceId OR terms LIKE '%' || :referenceId || '%')")
    suspend fun deleteSupplierCreditsByTerms(companyId: String, referenceId: String)

    @Transaction
    suspend fun deletePurchaseCascade(companyId: String, purchaseId: String, orderOrInvoice: String) {
        deletePurchaseItems(companyId, purchaseId)
        deletePurchaseStockMovements(companyId, purchaseId)
        if (orderOrInvoice.isNotBlank()) {
            deleteSupplierCreditsByTerms(companyId, purchaseId)
        }
        deletePurchase(companyId, purchaseId)
    }
}

