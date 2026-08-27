package com.kadaikutty.pos.feature.purchase.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.kadaikutty.pos.feature.billing.data.StockMovementEntity
import com.kadaikutty.pos.feature.stock.domain.ProductStock
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Insert suspend fun insertPurchase(purchase: PurchaseEntity)
    @Insert suspend fun insertPurchases(items: List<PurchaseEntity>)
    @Insert fun insertPurchasesSync(items: List<PurchaseEntity>)
    @Query("DELETE FROM purchases WHERE companyId = :companyId") suspend fun deletePurchasesByCompany(companyId: String)
    @Query("DELETE FROM purchases WHERE companyId = :companyId") fun deletePurchasesByCompanySync(companyId: String)
    @Insert suspend fun insertItems(items: List<PurchaseItemEntity>)
    @Insert fun insertItemsSync(items: List<PurchaseItemEntity>)
    @Insert suspend fun insertStockMovements(movements: List<StockMovementEntity>)
    @Insert suspend fun insertSupplierCredit(credit: com.kadaikutty.pos.feature.masters.data.SupplierCreditEntity)
    
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

    @Query("SELECT * FROM purchase_items WHERE companyId = :companyId AND purchaseId = :purchaseId")
    fun getPurchaseItems(companyId: String, purchaseId: String): Flow<List<PurchaseItemEntity>>

    @Query("SELECT AVG(unitValueMinorUnits) FROM purchase_items WHERE companyId = :companyId AND productId = :productId")
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

    @Query("DELETE FROM supplier_credits WHERE companyId = :companyId AND terms LIKE :termsPattern")
    suspend fun deleteSupplierCreditsByTerms(companyId: String, termsPattern: String)

    @Transaction
    suspend fun deletePurchaseCascade(companyId: String, purchaseId: String, orderOrInvoice: String) {
        deletePurchaseItems(companyId, purchaseId)
        deletePurchaseStockMovements(companyId, purchaseId)
        if (orderOrInvoice.isNotBlank()) {
            deleteSupplierCreditsByTerms(companyId, "%$orderOrInvoice%")
        }
        deletePurchase(companyId, purchaseId)
    }
}

