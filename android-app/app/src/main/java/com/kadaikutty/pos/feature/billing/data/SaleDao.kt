package com.kadaikutty.pos.feature.billing.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao interface SaleDao {
    @Insert suspend fun insertSale(sale: SaleEntity)
    @Insert suspend fun insertSales(items: List<SaleEntity>)
    @Insert fun insertSalesSync(items: List<SaleEntity>)
    @Query("DELETE FROM sales WHERE companyId = :companyId") suspend fun deleteSalesByCompany(companyId: String)
    @Query("DELETE FROM sales WHERE companyId = :companyId") fun deleteSalesByCompanySync(companyId: String)
    @Insert suspend fun insertItems(items: List<SaleItemEntity>)
    @Insert fun insertItemsSync(items: List<SaleItemEntity>)
    @Insert suspend fun insertStockMovements(movements: List<StockMovementEntity>)
    @Insert fun insertStockMovementsSync(movements: List<StockMovementEntity>)
    @Insert suspend fun insertCustomerCredit(credit: com.kadaikutty.pos.feature.masters.data.CustomerCreditEntity)
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

    @Query("SELECT * FROM sale_items WHERE companyId = :companyId AND saleId = :saleId")
    fun getSaleItems(companyId: String, saleId: String): Flow<List<SaleItemEntity>>

    @Query("SELECT * FROM sales WHERE companyId = :companyId AND customerId = :customerId ORDER BY createdAtEpochMs DESC")
    fun getSalesForCustomer(companyId: String, customerId: String): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE companyId = :companyId AND id = :saleId LIMIT 1")
    suspend fun getSaleById(companyId: String, saleId: String): SaleEntity?

    @Query("SELECT * FROM sale_items WHERE companyId = :companyId AND saleId = :saleId")
    suspend fun getSaleItemsList(companyId: String, saleId: String): List<SaleItemEntity>

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

    @Query("DELETE FROM customer_credits WHERE companyId = :companyId AND reason LIKE :reasonPattern")
    suspend fun deleteCustomerCreditsByReason(companyId: String, reasonPattern: String)

    @Transaction
    suspend fun deleteSaleCascade(companyId: String, saleId: String, billNumber: String) {
        deleteSaleItems(companyId, saleId)
        deleteSaleStockMovements(companyId, saleId)
        deleteCustomerCreditsByReason(companyId, "%$billNumber%")
        deleteSale(companyId, saleId)
    }
}
