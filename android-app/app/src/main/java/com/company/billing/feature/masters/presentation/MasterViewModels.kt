package com.company.billing.feature.masters.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.company.billing.core.database.BillingDatabase
import com.company.billing.core.sync.SyncStatus
import com.company.billing.core.sync.SyncManager
import com.company.billing.core.common.newRecordId
import com.company.billing.feature.masters.data.CategoryEntity
import com.company.billing.feature.masters.data.ProductEntity
import com.company.billing.feature.masters.data.CustomerEntity
import com.company.billing.feature.masters.data.SupplierEntity
import com.company.billing.feature.masters.data.ExpenseEntity
import com.company.billing.feature.masters.data.CustomerCreditEntity
import com.company.billing.feature.masters.data.SupplierCreditEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val database: BillingDatabase,
    private val syncManager: SyncManager
) : ViewModel() {
    private val dao = database.masterDao()
    private val searchQuery = MutableStateFlow("")
    val categories: StateFlow<List<CategoryEntity>> = searchQuery
        .flatMapLatest { dao.categories(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateSearch(query: String) { searchQuery.value = query }

    fun addCategory(name: String, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                val category = CategoryEntity(
                    id = newRecordId(),
                    name = name,
                    createdAtEpochMs = System.currentTimeMillis(),
                    updatedAtEpochMs = System.currentTimeMillis(),
                    syncStatus = SyncStatus.LOCAL_ONLY
                )
                dao.insertCategory(category)
                syncManager.enqueueCategory(category, "INSERT")
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun updateCategory(category: CategoryEntity, newName: String, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                val updated = category.copy(
                    name = newName,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
                dao.updateCategory(updated)
                syncManager.enqueueCategory(updated, "UPDATE")
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun deleteCategory(category: CategoryEntity, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                dao.deleteCategory(category)
                syncManager.enqueueCategory(category, "DELETE")
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }
}

@HiltViewModel
class ProductViewModel @Inject constructor(
    private val database: BillingDatabase,
    private val syncManager: SyncManager
) : ViewModel() {
    private val dao = database.masterDao()
    private val searchQuery = MutableStateFlow("")
    val products: StateFlow<List<ProductEntity>> = searchQuery
        .flatMapLatest { dao.products(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val categories: StateFlow<List<CategoryEntity>> = dao.categories("")
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateSearch(query: String) { searchQuery.value = query }

    fun addProduct(
        name: String,
        categoryId: String,
        purchasePriceMinorUnits: Long,
        salePriceMinorUnits: Long,
        unitType: String,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val product = ProductEntity(
                    id = newRecordId(),
                    name = name,
                    categoryId = categoryId,
                    purchasePriceMinorUnits = purchasePriceMinorUnits,
                    salePriceMinorUnits = salePriceMinorUnits,
                    unitType = unitType,
                    createdAtEpochMs = System.currentTimeMillis(),
                    updatedAtEpochMs = System.currentTimeMillis(),
                    syncStatus = SyncStatus.LOCAL_ONLY
                )
                dao.insertProduct(product)
                syncManager.enqueueProduct(product, "INSERT")
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun updateProduct(
        product: ProductEntity,
        newName: String,
        newCategoryId: String,
        newPurchasePriceMinorUnits: Long,
        newSalePriceMinorUnits: Long,
        newUnitType: String,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val updated = product.copy(
                    name = newName,
                    categoryId = newCategoryId,
                    purchasePriceMinorUnits = newPurchasePriceMinorUnits,
                    salePriceMinorUnits = newSalePriceMinorUnits,
                    unitType = newUnitType,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
                dao.updateProduct(updated)
                syncManager.enqueueProduct(updated, "UPDATE")
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun deleteProduct(product: ProductEntity, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                dao.deleteProduct(product)
                syncManager.enqueueProduct(product, "DELETE")
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }
}

@HiltViewModel
class CustomerViewModel @Inject constructor(
    private val database: BillingDatabase,
    private val syncManager: SyncManager
) : ViewModel() {
    private val dao = database.masterDao()
    private val searchQuery = MutableStateFlow("")
    val customers: StateFlow<List<CustomerEntity>> = searchQuery
        .flatMapLatest { dao.customers(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateSearch(query: String) { searchQuery.value = query }

    fun addCustomer(name: String, phone: String?, address: String?, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                val customer = CustomerEntity(
                    id = newRecordId(),
                    name = name,
                    phone = phone,
                    address = address,
                    createdAtEpochMs = System.currentTimeMillis(),
                    updatedAtEpochMs = System.currentTimeMillis(),
                    syncStatus = SyncStatus.LOCAL_ONLY
                )
                dao.insertCustomer(customer)
                syncManager.enqueueCustomer(customer, "INSERT")
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun updateCustomer(customer: CustomerEntity, newName: String, newPhone: String?, newAddress: String?, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                val updated = customer.copy(
                    name = newName,
                    phone = newPhone,
                    address = newAddress,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
                dao.updateCustomer(updated)
                syncManager.enqueueCustomer(updated, "UPDATE")
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun deleteCustomer(customer: CustomerEntity, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                dao.deleteCustomer(customer)
                syncManager.enqueueCustomer(customer, "DELETE")
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun addCustomerCredit(customerId: String, amountMinorUnits: Long, reason: String, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                val credit = CustomerCreditEntity(
                    id = newRecordId(),
                    customerId = customerId,
                    amountMinorUnits = amountMinorUnits,
                    reason = reason,
                    dateEpochMs = System.currentTimeMillis(),
                    syncStatus = SyncStatus.LOCAL_ONLY
                )
                dao.insertCustomerCredit(credit)
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun getCustomerCredits(customerId: String): Flow<List<CustomerCreditEntity>> = dao.getCustomerCredits(customerId)

    fun getCustomerCreditBalance(customerId: String): Flow<Long?> = dao.getCustomerCreditBalance(customerId)

    fun getTotalCustomerCreditsReceivable(): Flow<Long?> = dao.getTotalCustomerCreditsReceivable()

    fun updateCustomerCreditLimit(customerId: String, limit: Long, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                dao.updateCustomerCreditLimit(customerId, limit)
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }
}

@HiltViewModel
class SupplierViewModel @Inject constructor(
    private val database: BillingDatabase,
    private val syncManager: SyncManager
) : ViewModel() {
    private val dao = database.masterDao()
    private val searchQuery = MutableStateFlow("")
    val suppliers: StateFlow<List<SupplierEntity>> = searchQuery
        .flatMapLatest { dao.suppliers(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateSearch(query: String) { searchQuery.value = query }

    fun addSupplier(name: String, phone: String?, address: String?, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                val supplier = SupplierEntity(
                    id = newRecordId(),
                    name = name,
                    phone = phone,
                    address = address,
                    createdAtEpochMs = System.currentTimeMillis(),
                    updatedAtEpochMs = System.currentTimeMillis(),
                    syncStatus = SyncStatus.LOCAL_ONLY
                )
                dao.insertSupplier(supplier)
                syncManager.enqueueSupplier(supplier, "INSERT")
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun updateSupplier(supplier: SupplierEntity, newName: String, newPhone: String?, newAddress: String?, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                val updated = supplier.copy(
                    name = newName,
                    phone = newPhone,
                    address = newAddress,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
                dao.updateSupplier(updated)
                syncManager.enqueueSupplier(updated, "UPDATE")
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun deleteSupplier(supplier: SupplierEntity, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                dao.deleteSupplier(supplier)
                syncManager.enqueueSupplier(supplier, "DELETE")
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun addSupplierCredit(supplierId: String, amountMinorUnits: Long, terms: String, dueDateEpochMs: Long, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                val credit = SupplierCreditEntity(
                    id = newRecordId(),
                    supplierId = supplierId,
                    amountMinorUnits = amountMinorUnits,
                    terms = terms,
                    dueDateEpochMs = dueDateEpochMs,
                    dateEpochMs = System.currentTimeMillis(),
                    syncStatus = SyncStatus.LOCAL_ONLY
                )
                dao.insertSupplierCredit(credit)
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun getSupplierCredits(supplierId: String): Flow<List<SupplierCreditEntity>> = dao.getSupplierCredits(supplierId)

    fun getSupplierCreditBalance(supplierId: String): Flow<Long?> = dao.getSupplierCreditBalance(supplierId)

    fun getTotalSupplierCreditsPayable(): Flow<Long?> = dao.getTotalSupplierCreditsPayable()
}

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val database: BillingDatabase,
    private val syncManager: SyncManager
) : ViewModel() {
    private val dao = database.masterDao()
    val expenses: StateFlow<List<ExpenseEntity>> = dao.expenses()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun addExpense(amountMinorUnits: Long, description: String, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                val expense = ExpenseEntity(
                    id = newRecordId(),
                    amountMinorUnits = amountMinorUnits,
                    description = description,
                    createdAtEpochMs = System.currentTimeMillis(),
                    updatedAtEpochMs = System.currentTimeMillis(),
                    syncStatus = SyncStatus.LOCAL_ONLY
                )
                dao.insertExpense(expense)
                syncManager.enqueueExpense(expense, "INSERT")
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun updateExpense(expense: ExpenseEntity, newAmountMinorUnits: Long, newDescription: String, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                val updated = expense.copy(
                    amountMinorUnits = newAmountMinorUnits,
                    description = newDescription,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
                dao.updateExpense(updated)
                syncManager.enqueueExpense(updated, "UPDATE")
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun deleteExpense(expense: ExpenseEntity, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                dao.deleteExpense(expense)
                syncManager.enqueueExpense(expense, "DELETE")
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }
}
