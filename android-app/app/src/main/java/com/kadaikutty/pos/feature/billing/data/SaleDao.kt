package com.kadaikutty.pos.feature.billing.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

@Dao interface SaleDao {
    @Query("SELECT * FROM stock_movements WHERE companyId = :companyId AND referenceId = :id")
    suspend fun movementsFor(companyId: String, id: String): List<StockMovementEntity>
    @Query("SELECT * FROM customer_credits WHERE companyId = :companyId AND referenceId = :id")
    suspend fun creditsFor(companyId: String, id: String): List<com.kadaikutty.pos.feature.masters.data.CustomerCreditEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun createSale(sale: SaleEntity)
    @androidx.room.Update suspend fun updateSale(sale: SaleEntity)
    @Query("SELECT COALESCE(SUM(quantityDelta), 0) FROM stock_movements WHERE companyId = :companyId AND productId = :productId")
    suspend fun stock(companyId: String, productId: String): Long
    @Query("SELECT COUNT(*) FROM stock_movements WHERE companyId = :companyId AND productId = :productId")
    suspend fun movementCount(companyId: String, productId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSale(sale: SaleEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertItems(items: List<SaleItemEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertStockMovements(movements: List<StockMovementEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCustomerCredit(credit: com.kadaikutty.pos.feature.masters.data.CustomerCreditEntity)
    @Query("SELECT * FROM stock_movements WHERE companyId = :companyId") suspend fun allStockMovements(companyId: String): List<StockMovementEntity>

    @Query("SELECT * FROM sales WHERE companyId = :companyId ORDER BY createdAtEpochMs DESC")
    fun getSales(companyId: String): Flow<List<SaleEntity>>

    /**
     * Sales History with its search and date filter. [query] matches the bill number, customer
     * name or phone, a product on the bill, the payment mode, or the whole-rupee amount; empty
     * matches everything. [fromEpochMs] is inclusive and [toEpochMs] exclusive.
     */
    @Query(
        "SELECT s.* FROM sales s LEFT JOIN customers c ON c.id = s.customerId AND c.companyId = s.companyId " +
        "WHERE s.companyId = :companyId AND s.createdAtEpochMs >= :fromEpochMs AND s.createdAtEpochMs < :toEpochMs " +
        "AND (:query = '' OR s.billNumber LIKE '%' || :query || '%' OR c.name LIKE '%' || :query || '%' " +
        "OR c.phone LIKE '%' || :query || '%' OR s.paymentMode LIKE '%' || :query || '%' " +
        "OR CAST(s.totalMinorUnits / 100 AS TEXT) LIKE :query || '%' " +
        "OR (s.customerId IS NULL AND 'walk-in' LIKE '%' || :query || '%') " +
        "OR EXISTS (SELECT 1 FROM sale_items i WHERE i.companyId = s.companyId AND i.saleId = s.id AND i.productName LIKE '%' || :query || '%')) " +
        "ORDER BY s.createdAtEpochMs DESC"
    )
    fun searchSalesPaged(companyId: String, query: String, fromEpochMs: Long, toEpochMs: Long): PagingSource<Int, SaleEntity>
    
    @Query("SELECT COUNT(*) FROM sales WHERE companyId = :companyId AND createdAtEpochMs >= :sinceEpochMs AND status != 'VOID'")
    fun getSalesCountSince(companyId: String, sinceEpochMs: Long): Flow<Int>
    
    @Query("SELECT SUM(totalMinorUnits) FROM sales WHERE companyId = :companyId AND createdAtEpochMs >= :sinceEpochMs AND status != 'VOID'")
    fun getSalesTotalSince(companyId: String, sinceEpochMs: Long): Flow<Long?>

    @Query("SELECT COUNT(*) FROM sales WHERE companyId = :companyId AND createdAtEpochMs >= :fromEpochMs AND createdAtEpochMs < :toEpochMs AND status != 'VOID'")
    fun getSalesCountBetween(companyId: String, fromEpochMs: Long, toEpochMs: Long): Flow<Int>

    @Query("SELECT SUM(totalMinorUnits) FROM sales WHERE companyId = :companyId AND createdAtEpochMs >= :fromEpochMs AND createdAtEpochMs < :toEpochMs AND status != 'VOID'")
    fun getSalesTotalBetween(companyId: String, fromEpochMs: Long, toEpochMs: Long): Flow<Long?>
    
    @Query("SELECT * FROM sales WHERE companyId = :companyId AND status != 'VOID' ORDER BY createdAtEpochMs DESC LIMIT :limit")
    fun getRecentSales(companyId: String, limit: Int): Flow<List<SaleEntity>>
    @Query("SELECT * FROM sale_items WHERE companyId = :companyId AND saleId = :saleId")
    fun getSaleItems(companyId: String, saleId: String): Flow<List<SaleItemEntity>>

    @Query("SELECT * FROM sales WHERE companyId = :companyId AND (billNumber = :billNumber OR id = :billNumber) LIMIT 1")
    suspend fun getSaleByBillNumber(companyId: String, billNumber: String): SaleEntity?

    @Query("SELECT * FROM sales WHERE companyId = :companyId AND id = :saleId LIMIT 1")
    suspend fun getSaleById(companyId: String, saleId: String): SaleEntity?

    @Query("SELECT * FROM sale_items WHERE companyId = :companyId AND saleId = :saleId")
    suspend fun getSaleItemsList(companyId: String, saleId: String): List<SaleItemEntity>

    @Query("SELECT billNumber FROM sales WHERE companyId = :companyId")
    suspend fun getAllBillNumbers(companyId: String): List<String>

    @Query("SELECT SUM(paidCashMinorUnits) FROM sales WHERE companyId = :companyId AND createdAtEpochMs > :sinceEpochMs AND status != 'VOID'")
    suspend fun getCashSalesSumSince(companyId: String, sinceEpochMs: Long): Long?

    @Query("DELETE FROM sales WHERE companyId = :companyId AND id = :saleId")
    suspend fun deleteSale(companyId: String, saleId: String)

    @Query("DELETE FROM sale_items WHERE companyId = :companyId AND saleId = :saleId")
    suspend fun deleteSaleItems(companyId: String, saleId: String)

    @Query("DELETE FROM stock_movements WHERE companyId = :companyId AND referenceId = :saleId")
    suspend fun deleteSaleStockMovements(companyId: String, saleId: String)

    @Query("DELETE FROM stock_movements WHERE companyId = :companyId AND id = :movementId")
    suspend fun deleteStockMovementById(companyId: String, movementId: String)

    @Query("DELETE FROM customer_credits WHERE companyId = :companyId AND referenceId = :reasonPattern")
    suspend fun deleteCustomerCreditsByReason(companyId: String, reasonPattern: String)

    @Transaction
    suspend fun deleteSaleCascade(companyId: String, saleId: String, billNumber: String) {
        deleteSaleItems(companyId, saleId)
        deleteSaleStockMovements(companyId, saleId)
        deleteCustomerCreditsByReason(companyId, saleId)
        deleteSale(companyId, saleId)
    }
}
