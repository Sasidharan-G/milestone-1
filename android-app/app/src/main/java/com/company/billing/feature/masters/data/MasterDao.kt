package com.company.billing.feature.masters.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao interface MasterDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCategory(item: CategoryEntity)
    @Query("SELECT * FROM categories WHERE name LIKE '%' || :query || '%' ORDER BY name") fun categories(query: String): Flow<List<CategoryEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertProduct(item: ProductEntity)
    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' ORDER BY name") fun products(query: String): Flow<List<ProductEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCustomer(item: CustomerEntity)
    @Query("SELECT * FROM customers WHERE name LIKE '%' || :query || '%' ORDER BY name") fun customers(query: String): Flow<List<CustomerEntity>>
    @Query("SELECT * FROM customers WHERE id = :id") suspend fun getCustomerById(id: String): CustomerEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSupplier(item: SupplierEntity)
    @Query("SELECT * FROM suppliers WHERE name LIKE '%' || :query || '%' ORDER BY name") fun suppliers(query: String): Flow<List<SupplierEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertExpense(item: ExpenseEntity)
    @Query("SELECT * FROM expenses ORDER BY createdAtEpochMs DESC") fun expenses(): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCustomerCredit(item: CustomerCreditEntity)
    @Query("SELECT * FROM customer_credits WHERE customerId = :customerId ORDER BY dateEpochMs DESC") fun getCustomerCredits(customerId: String): Flow<List<CustomerCreditEntity>>
    @Query("SELECT SUM(amountMinorUnits) FROM customer_credits WHERE customerId = :customerId") fun getCustomerCreditBalance(customerId: String): Flow<Long?>
    @Query("SELECT SUM(amountMinorUnits) FROM customer_credits") fun getTotalCustomerCreditsReceivable(): Flow<Long?>
    @Query("UPDATE customers SET creditLimitMinorUnits = :limit WHERE id = :customerId") suspend fun updateCustomerCreditLimit(customerId: String, limit: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSupplierCredit(item: SupplierCreditEntity)
    @Query("SELECT * FROM supplier_credits WHERE supplierId = :supplierId ORDER BY dateEpochMs DESC") fun getSupplierCredits(supplierId: String): Flow<List<SupplierCreditEntity>>
    @Query("SELECT SUM(amountMinorUnits) FROM supplier_credits WHERE supplierId = :supplierId") fun getSupplierCreditBalance(supplierId: String): Flow<Long?>
    @Query("SELECT SUM(amountMinorUnits) FROM supplier_credits") fun getTotalSupplierCreditsPayable(): Flow<Long?>
}
