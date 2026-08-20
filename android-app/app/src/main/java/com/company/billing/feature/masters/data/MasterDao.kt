package com.company.billing.feature.masters.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Delete
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao interface MasterDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCategory(item: CategoryEntity)
    @Query("SELECT * FROM categories WHERE companyId = :companyId AND name LIKE '%' || :query || '%' ORDER BY name") fun categories(companyId: String, query: String): Flow<List<CategoryEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertProduct(item: ProductEntity)
    @Query("SELECT * FROM products WHERE companyId = :companyId AND name LIKE '%' || :query || '%' ORDER BY name") fun products(companyId: String, query: String): Flow<List<ProductEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCustomer(item: CustomerEntity)
    @Query("SELECT * FROM customers WHERE companyId = :companyId AND name LIKE '%' || :query || '%' ORDER BY name") fun customers(companyId: String, query: String): Flow<List<CustomerEntity>>
    @Query("SELECT * FROM customers WHERE companyId = :companyId AND id = :id") suspend fun getCustomerById(companyId: String, id: String): CustomerEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSupplier(item: SupplierEntity)
    @Query("SELECT * FROM suppliers WHERE companyId = :companyId AND name LIKE '%' || :query || '%' ORDER BY name") fun suppliers(companyId: String, query: String): Flow<List<SupplierEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertExpense(item: ExpenseEntity)
    @Query("SELECT * FROM expenses WHERE companyId = :companyId ORDER BY createdAtEpochMs DESC") fun expenses(companyId: String): Flow<List<ExpenseEntity>>
 
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCustomerCredit(item: CustomerCreditEntity)
    @Query("SELECT * FROM customer_credits WHERE companyId = :companyId AND customerId = :customerId ORDER BY dateEpochMs DESC") fun getCustomerCredits(companyId: String, customerId: String): Flow<List<CustomerCreditEntity>>
    @Query("SELECT SUM(amountMinorUnits) FROM customer_credits WHERE companyId = :companyId AND customerId = :customerId") fun getCustomerCreditBalance(companyId: String, customerId: String): Flow<Long?>
    @Query("SELECT SUM(amountMinorUnits) FROM customer_credits WHERE companyId = :companyId") fun getTotalCustomerCreditsReceivable(companyId: String): Flow<Long?>
    @Query("UPDATE customers SET creditLimitMinorUnits = :limit WHERE companyId = :companyId AND id = :customerId") suspend fun updateCustomerCreditLimit(companyId: String, customerId: String, limit: Long)
 
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSupplierCredit(item: SupplierCreditEntity)
    @Query("SELECT * FROM supplier_credits WHERE companyId = :companyId AND supplierId = :supplierId ORDER BY dateEpochMs DESC") fun getSupplierCredits(companyId: String, supplierId: String): Flow<List<SupplierCreditEntity>>
    @Query("SELECT SUM(amountMinorUnits) FROM supplier_credits WHERE companyId = :companyId AND supplierId = :supplierId") fun getSupplierCreditBalance(companyId: String, supplierId: String): Flow<Long?>
    @Query("SELECT SUM(amountMinorUnits) FROM supplier_credits WHERE companyId = :companyId") fun getTotalSupplierCreditsPayable(companyId: String): Flow<Long?>
 
    @Update suspend fun updateCategory(item: CategoryEntity)
    @Delete suspend fun deleteCategory(item: CategoryEntity)
 
    @Update suspend fun updateProduct(item: ProductEntity)
    @Delete suspend fun deleteProduct(item: ProductEntity)
 
    @Update suspend fun updateCustomer(item: CustomerEntity)
    @Delete suspend fun deleteCustomer(item: CustomerEntity)
 
    @Update suspend fun updateSupplier(item: SupplierEntity)
    @Delete suspend fun deleteSupplier(item: SupplierEntity)
 
    @Update suspend fun updateExpense(item: ExpenseEntity)
    @Delete suspend fun deleteExpense(item: ExpenseEntity)
}
