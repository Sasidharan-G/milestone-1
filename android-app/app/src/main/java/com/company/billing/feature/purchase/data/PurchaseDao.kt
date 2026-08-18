package com.company.billing.feature.purchase.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.company.billing.feature.billing.data.StockMovementEntity
import com.company.billing.feature.stock.domain.ProductStock
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Insert suspend fun insertPurchase(purchase: PurchaseEntity)
    @Insert suspend fun insertItems(items: List<PurchaseItemEntity>)
    @Insert suspend fun insertStockMovements(movements: List<StockMovementEntity>)
    
    @Transaction suspend fun savePurchase(purchase: PurchaseEntity, items: List<PurchaseItemEntity>, movements: List<StockMovementEntity>) { 
        insertPurchase(purchase)
        insertItems(items)
        insertStockMovements(movements) 
    }

    @Query("""
        SELECT 
            p.id as productId, 
            p.name as productName, 
            c.name as categoryName, 
            COALESCE(SUM(sm.quantityDelta), 0) as currentStock
        FROM products p
        INNER JOIN categories c ON p.categoryId = c.id
        LEFT JOIN stock_movements sm ON p.id = sm.productId
        GROUP BY p.id
        ORDER BY p.name ASC
    """)
    fun getStockBalances(): Flow<List<ProductStock>>

    @Query("SELECT * FROM purchases ORDER BY createdAtEpochMs DESC")
    fun getPurchases(): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId")
    fun getPurchaseItems(purchaseId: String): Flow<List<PurchaseItemEntity>>

    @Query("SELECT AVG(unitValueMinorUnits) FROM purchase_items WHERE productId = :productId")
    suspend fun getAveragePurchasePrice(productId: String): Double?
}

