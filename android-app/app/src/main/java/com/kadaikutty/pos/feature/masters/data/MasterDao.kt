package com.kadaikutty.pos.feature.masters.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Delete
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao interface MasterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCategory(item: CategoryEntity)
    @Query("SELECT * FROM categories WHERE companyId = :companyId AND name LIKE '%' || :query || '%' ORDER BY name LIMIT 500") fun categories(companyId: String, query: String): Flow<List<CategoryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertProduct(item: ProductEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertProducts(items: List<ProductEntity>)
    @Query("SELECT * FROM products WHERE companyId = :companyId AND name LIKE '%' || :query || '%' ORDER BY name LIMIT 500") fun products(companyId: String, query: String): Flow<List<ProductEntity>>
    @Query("SELECT * FROM products WHERE companyId = :companyId AND id = :id") suspend fun getProductById(companyId: String, id: String): ProductEntity?
    @Query("SELECT * FROM products WHERE companyId = :companyId") suspend fun getAllProducts(companyId: String): List<ProductEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCustomer(item: CustomerEntity)
    @Query("SELECT * FROM customers WHERE companyId = :companyId AND name LIKE '%' || :query || '%' ORDER BY name LIMIT 500") fun customers(companyId: String, query: String): Flow<List<CustomerEntity>>
    @Query("SELECT * FROM customers WHERE companyId = :companyId AND id = :id") suspend fun getCustomerById(companyId: String, id: String): CustomerEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSupplier(item: SupplierEntity)
    @Query("SELECT * FROM suppliers WHERE companyId = :companyId AND name LIKE '%' || :query || '%' ORDER BY name LIMIT 500") fun suppliers(companyId: String, query: String): Flow<List<SupplierEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertExpense(item: ExpenseEntity)
    @Query("SELECT * FROM expenses WHERE companyId = :companyId ORDER BY createdAtEpochMs DESC") fun expenses(companyId: String): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCustomerCredit(item: CustomerCreditEntity)
    @Query("SELECT * FROM customer_credits WHERE companyId = :companyId AND customerId = :customerId ORDER BY dateEpochMs DESC, id DESC") fun getCustomerCredits(companyId: String, customerId: String): Flow<List<CustomerCreditEntity>>
    @Query("SELECT SUM(amountMinorUnits) FROM customer_credits WHERE companyId = :companyId AND customerId = :customerId") fun getCustomerCreditBalance(companyId: String, customerId: String): Flow<Long?>
    @Query("SELECT SUM(amountMinorUnits) FROM customer_credits WHERE companyId = :companyId") fun getTotalCustomerCreditsReceivable(companyId: String): Flow<Long?>
    @Query("SELECT * FROM customer_credits WHERE companyId = :companyId") suspend fun getAllCustomerCredits(companyId: String): List<CustomerCreditEntity>
    @Query("UPDATE customers SET creditLimitMinorUnits = :limit WHERE companyId = :companyId AND id = :customerId") suspend fun updateCustomerCreditLimit(companyId: String, customerId: String, limit: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSupplierCredit(item: SupplierCreditEntity)
    @Query("SELECT * FROM supplier_credits WHERE companyId = :companyId AND supplierId = :supplierId ORDER BY dateEpochMs DESC, id DESC") fun getSupplierCredits(companyId: String, supplierId: String): Flow<List<SupplierCreditEntity>>
    @Query("SELECT SUM(amountMinorUnits) FROM supplier_credits WHERE companyId = :companyId AND supplierId = :supplierId") fun getSupplierCreditBalance(companyId: String, supplierId: String): Flow<Long?>
    @Query("SELECT SUM(amountMinorUnits) FROM supplier_credits WHERE companyId = :companyId") fun getTotalSupplierCreditsPayable(companyId: String): Flow<Long?>
    @Query("SELECT * FROM supplier_credits WHERE companyId = :companyId") suspend fun getAllSupplierCredits(companyId: String): List<SupplierCreditEntity>
  
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

    @Query("DELETE FROM categories WHERE companyId = :companyId AND id = :id")
    suspend fun deleteCategoryById(companyId: String, id: String)

    @Query("DELETE FROM products WHERE companyId = :companyId AND id = :id")
    suspend fun deleteProductById(companyId: String, id: String)

    @Query("DELETE FROM customers WHERE companyId = :companyId AND id = :id")
    suspend fun deleteCustomerById(companyId: String, id: String)

    @Query("DELETE FROM suppliers WHERE companyId = :companyId AND id = :id")
    suspend fun deleteSupplierById(companyId: String, id: String)

    @Query("DELETE FROM expenses WHERE companyId = :companyId AND id = :id")
    suspend fun deleteExpenseById(companyId: String, id: String)

    @Query("DELETE FROM customer_credits WHERE companyId = :companyId AND id = :id")
    suspend fun deleteCustomerCreditById(companyId: String, id: String)

    @Query("DELETE FROM supplier_credits WHERE companyId = :companyId AND id = :id")
    suspend fun deleteSupplierCreditById(companyId: String, id: String)

    // Balances and usage, read by the delete guards and by sync conflict resolution: a customer or
    // supplier with money outstanding, a product with stock, or a category in use must not vanish.
    @Query("SELECT COALESCE(SUM(amountMinorUnits), 0) FROM customer_credits WHERE companyId = :companyId AND customerId = :customerId")
    suspend fun customerBalance(companyId: String, customerId: String): Long

    @Query("SELECT COALESCE(SUM(amountMinorUnits), 0) FROM supplier_credits WHERE companyId = :companyId AND supplierId = :supplierId")
    suspend fun supplierBalance(companyId: String, supplierId: String): Long

    @Query("SELECT COUNT(*) FROM products WHERE companyId = :companyId AND categoryId = :categoryId")
    suspend fun productCountInCategory(companyId: String, categoryId: String): Int

    /** Products whose stock went below zero, which happens when two devices sell the last units offline. */
    @Query("SELECT p.id AS id, p.name AS name, SUM(m.quantityDelta) AS stock FROM products p JOIN stock_movements m ON m.companyId = p.companyId AND m.productId = p.id WHERE p.companyId = :companyId GROUP BY p.id, p.name HAVING SUM(m.quantityDelta) < 0 ORDER BY p.name")
    fun negativeStockProducts(companyId: String): Flow<List<NegativeStockProduct>>
}

data class NegativeStockProduct(val id: String, val name: String, val stock: Long)
