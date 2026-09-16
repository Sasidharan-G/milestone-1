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
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertPurchasesSync(items: List<PurchaseEntity>)
    @Query("DELETE FROM purchases WHERE companyId = :companyId") suspend fun deletePurchasesByCompany(companyId: String)
    @Query("DELETE FROM purchases WHERE companyId = :companyId") fun deletePurchasesByCompanySync(companyId: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertItems(items: List<PurchaseItemEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertItemsSync(items: List<PurchaseItemEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertStockMovements(movements: List<StockMovementEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSupplierCredit(credit: com.kadaikutty.pos.feature.masters.data.SupplierCreditEntity)
    
    @Transaction suspend fun savePurchase(
        purchase: PurchaseEntity, 
        items: List<PurchaseItemEntity>, 
        movements: List<StockMovementEntity>,
        supplierCredit: com.kadaikutty.pos.feature.masters.data.SupplierCreditEntity? = null
    ) { 
        insertPurchase(purchase)
        insertItems(items)
        insertStockMovements(movements) 
        if (supplierCredit != null) {
            insertSupplierCredit(supplierCredit)
        }
    }
    @Query("DELETE FROM purchase_items WHERE companyId = :companyId") suspend fun deletePurchaseItemsByCompany(companyId: String)
    @Query("DELETE FROM purchase_items WHERE companyId = :companyId") fun deletePurchaseItemsByCompanySync(companyId: String)

    @Query("""
        SELECT 
            p.id as productId, 
            p.name as productName, 
            c.name as categoryName, 
            COALESCE(SUM(sm.quantityDelta), 0) as currentStock
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

    @Query("DELETE FROM supplier_credits WHERE companyId = :companyId AND referenceId = :termsPattern")
    suspend fun deleteSupplierCreditsByTerms(companyId: String, termsPattern: String)

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

