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
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSales(items: List<SaleEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertSalesSync(items: List<SaleEntity>)
    @Query("DELETE FROM sales WHERE companyId = :companyId") suspend fun deleteSalesByCompany(companyId: String)
    @Query("DELETE FROM sales WHERE companyId = :companyId") fun deleteSalesByCompanySync(companyId: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertItems(items: List<SaleItemEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertItemsSync(items: List<SaleItemEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertStockMovements(movements: List<StockMovementEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertStockMovementsSync(movements: List<StockMovementEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCustomerCredit(credit: com.kadaikutty.pos.feature.masters.data.CustomerCreditEntity)
    @Transaction suspend fun saveSale(sale: SaleEntity, items: List<SaleItemEntity>, movements: List<StockMovementEntity>, customerCredit: com.kadaikutty.pos.feature.masters.data.CustomerCreditEntity? = null) { 
        insertSale(sale)
        insertItems(items)
        insertStockMovements(movements)
        if (customerCredit != null) {
            insertCustomerCredit(customerCredit)
        }
    }
    @Query("DELETE FROM sale_items WHERE companyId = :companyId") suspend fun deleteSaleItemsByCompany(companyId: String)
    @Query("DELETE FROM sale_items WHERE companyId = :companyId") fun deleteSaleItemsByCompanySync(companyId: String)
    @Query("DELETE FROM stock_movements WHERE companyId = :companyId") suspend fun deleteStockMovementsByCompany(companyId: String)
    @Query("DELETE FROM stock_movements WHERE companyId = :companyId") fun deleteStockMovementsByCompanySync(companyId: String)

    @Query("SELECT * FROM sales WHERE companyId = :companyId ORDER BY createdAtEpochMs DESC")
    fun getSales(companyId: String): Flow<List<SaleEntity>>
    
    @Query("SELECT * FROM sales WHERE companyId = :companyId ORDER BY createdAtEpochMs DESC")
    fun getSalesPaged(companyId: String): PagingSource<Int, SaleEntity>
    
    @Query("SELECT COUNT(*) FROM sales WHERE companyId = :companyId AND createdAtEpochMs >= :sinceEpochMs")
    fun getSalesCountSince(companyId: String, sinceEpochMs: Long): Flow<Int>
    
    @Query("SELECT SUM(totalMinorUnits) FROM sales WHERE companyId = :companyId AND createdAtEpochMs >= :sinceEpochMs")
    fun getSalesTotalSince(companyId: String, sinceEpochMs: Long): Flow<Long?>
    
    @Query("SELECT * FROM sales WHERE companyId = :companyId ORDER BY createdAtEpochMs DESC LIMIT :limit")
    fun getRecentSales(companyId: String, limit: Int): Flow<List<SaleEntity>>
    @Query("SELECT * FROM sale_items WHERE companyId = :companyId AND saleId = :saleId")
    fun getSaleItems(companyId: String, saleId: String): Flow<List<SaleItemEntity>>

    @Query("SELECT * FROM sales WHERE companyId = :companyId AND customerId = :customerId ORDER BY createdAtEpochMs DESC")
    fun getSalesForCustomer(companyId: String, customerId: String): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE companyId = :companyId AND (billNumber = :billNumber OR id = :billNumber) LIMIT 1")
    suspend fun getSaleByBillNumber(companyId: String, billNumber: String): SaleEntity?

    @Query("SELECT * FROM sales WHERE companyId = :companyId AND id = :saleId LIMIT 1")
    suspend fun getSaleById(companyId: String, saleId: String): SaleEntity?

    @Query("SELECT * FROM sale_items WHERE companyId = :companyId AND saleId = :saleId")
    suspend fun getSaleItemsList(companyId: String, saleId: String): List<SaleItemEntity>

    @Query("SELECT billNumber FROM sales WHERE companyId = :companyId ORDER BY createdAtEpochMs DESC LIMIT 1")
    suspend fun getLatestBillNumber(companyId: String): String?

    @Query("SELECT billNumber FROM sales WHERE companyId = :companyId")
    suspend fun getAllBillNumbers(companyId: String): List<String>

    @Query("SELECT SUM(paidCashMinorUnits) FROM sales WHERE companyId = :companyId AND createdAtEpochMs > :sinceEpochMs")
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
