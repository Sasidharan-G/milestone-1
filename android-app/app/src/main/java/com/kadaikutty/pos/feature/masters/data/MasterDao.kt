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
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCategories(items: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertCategoriesSync(items: List<CategoryEntity>)
    @Query("DELETE FROM categories WHERE companyId = :companyId") suspend fun deleteCategoriesByCompany(companyId: String)
    @Query("DELETE FROM categories WHERE companyId = :companyId") fun deleteCategoriesByCompanySync(companyId: String)
    @Query("SELECT * FROM categories WHERE companyId = :companyId AND name LIKE '%' || :query || '%' ORDER BY name LIMIT 500") fun categories(companyId: String, query: String): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories WHERE companyId = :companyId AND name LIKE '%' || :query || '%' ORDER BY name LIMIT 500") fun categoriesDebounced(companyId: String, query: String): Flow<List<CategoryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertProduct(item: ProductEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertProducts(items: List<ProductEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertProductsSync(items: List<ProductEntity>)
    @Query("DELETE FROM products WHERE companyId = :companyId") suspend fun deleteProductsByCompany(companyId: String)
    @Query("DELETE FROM products WHERE companyId = :companyId") fun deleteProductsByCompanySync(companyId: String)
    @Query("SELECT * FROM products WHERE companyId = :companyId AND name LIKE '%' || :query || '%' ORDER BY name LIMIT 500") fun products(companyId: String, query: String): Flow<List<ProductEntity>>
    @Query("SELECT * FROM products WHERE companyId = :companyId AND name LIKE '%' || :query || '%' ORDER BY name LIMIT 500") fun productsDebounced(companyId: String, query: String): Flow<List<ProductEntity>>
    @Query("SELECT * FROM products WHERE companyId = :companyId AND id = :id") suspend fun getProductById(companyId: String, id: String): ProductEntity?
    @Query("SELECT * FROM products WHERE companyId = :companyId") suspend fun getAllProducts(companyId: String): List<ProductEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCustomer(item: CustomerEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCustomers(items: List<CustomerEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertCustomersSync(items: List<CustomerEntity>)
    @Query("DELETE FROM customers WHERE companyId = :companyId") suspend fun deleteCustomersByCompany(companyId: String)
    @Query("DELETE FROM customers WHERE companyId = :companyId") fun deleteCustomersByCompanySync(companyId: String)
    @Query("SELECT * FROM customers WHERE companyId = :companyId AND name LIKE '%' || :query || '%' ORDER BY name LIMIT 500") fun customers(companyId: String, query: String): Flow<List<CustomerEntity>>
    @Query("SELECT * FROM customers WHERE companyId = :companyId AND name LIKE '%' || :query || '%' ORDER BY name LIMIT 500") fun customersDebounced(companyId: String, query: String): Flow<List<CustomerEntity>>
    @Query("SELECT * FROM customers WHERE companyId = :companyId AND id = :id") suspend fun getCustomerById(companyId: String, id: String): CustomerEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSupplier(item: SupplierEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSuppliers(items: List<SupplierEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertSuppliersSync(items: List<SupplierEntity>)
    @Query("DELETE FROM suppliers WHERE companyId = :companyId") suspend fun deleteSuppliersByCompany(companyId: String)
    @Query("DELETE FROM suppliers WHERE companyId = :companyId") fun deleteSuppliersByCompanySync(companyId: String)
    @Query("SELECT * FROM suppliers WHERE companyId = :companyId AND name LIKE '%' || :query || '%' ORDER BY name LIMIT 500") fun suppliers(companyId: String, query: String): Flow<List<SupplierEntity>>
    @Query("SELECT * FROM suppliers WHERE companyId = :companyId AND name LIKE '%' || :query || '%' ORDER BY name LIMIT 500") fun suppliersDebounced(companyId: String, query: String): Flow<List<SupplierEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertExpense(item: ExpenseEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertExpenses(items: List<ExpenseEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertExpensesSync(items: List<ExpenseEntity>)
    @Query("DELETE FROM expenses WHERE companyId = :companyId") suspend fun deleteExpensesByCompany(companyId: String)
    @Query("DELETE FROM expenses WHERE companyId = :companyId") fun deleteExpensesByCompanySync(companyId: String)
    @Query("SELECT * FROM expenses WHERE companyId = :companyId ORDER BY createdAtEpochMs DESC") fun expenses(companyId: String): Flow<List<ExpenseEntity>>
  
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCustomerCredit(item: CustomerCreditEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCustomerCredits(items: List<CustomerCreditEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertCustomerCreditsSync(items: List<CustomerCreditEntity>)
    @Query("DELETE FROM customer_credits WHERE companyId = :companyId") suspend fun deleteCustomerCreditsByCompany(companyId: String)
    @Query("DELETE FROM customer_credits WHERE companyId = :companyId") fun deleteCustomerCreditsByCompanySync(companyId: String)
    @Query("SELECT * FROM customer_credits WHERE companyId = :companyId AND customerId = :customerId ORDER BY dateEpochMs DESC") fun getCustomerCredits(companyId: String, customerId: String): Flow<List<CustomerCreditEntity>>
    @Query("SELECT SUM(amountMinorUnits) FROM customer_credits WHERE companyId = :companyId AND customerId = :customerId") fun getCustomerCreditBalance(companyId: String, customerId: String): Flow<Long?>
    @Query("SELECT SUM(amountMinorUnits) FROM customer_credits WHERE companyId = :companyId") fun getTotalCustomerCreditsReceivable(companyId: String): Flow<Long?>
    @Query("UPDATE customers SET creditLimitMinorUnits = :limit WHERE companyId = :companyId AND id = :customerId") suspend fun updateCustomerCreditLimit(companyId: String, customerId: String, limit: Long)
  
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSupplierCredit(item: SupplierCreditEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSupplierCredits(items: List<SupplierCreditEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertSupplierCreditsSync(items: List<SupplierCreditEntity>)
    @Query("DELETE FROM supplier_credits WHERE companyId = :companyId") suspend fun deleteSupplierCreditsByCompany(companyId: String)
    @Query("DELETE FROM supplier_credits WHERE companyId = :companyId") fun deleteSupplierCreditsByCompanySync(companyId: String)
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
}
