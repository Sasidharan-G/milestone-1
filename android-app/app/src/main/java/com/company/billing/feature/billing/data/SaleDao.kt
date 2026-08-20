package com.company.billing.feature.billing.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao interface SaleDao {
    @Insert suspend fun insertSale(sale: SaleEntity)
    @Insert suspend fun insertItems(items: List<SaleItemEntity>)
    @Insert suspend fun insertStockMovements(movements: List<StockMovementEntity>)
    @Transaction suspend fun saveSale(sale: SaleEntity, items: List<SaleItemEntity>, movements: List<StockMovementEntity>) { insertSale(sale); insertItems(items); insertStockMovements(movements) }

    @Query("SELECT * FROM sales WHERE companyId = :companyId ORDER BY createdAtEpochMs DESC")
    fun getSales(companyId: String): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sale_items WHERE companyId = :companyId AND saleId = :saleId")
    fun getSaleItems(companyId: String, saleId: String): Flow<List<SaleItemEntity>>

    @Query("SELECT * FROM sales WHERE companyId = :companyId AND customerId = :customerId ORDER BY createdAtEpochMs DESC")
    fun getSalesForCustomer(companyId: String, customerId: String): Flow<List<SaleEntity>>
}
