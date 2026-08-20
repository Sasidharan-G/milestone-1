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

import kotlinx.coroutines.flow.first
import com.company.billing.core.auth.SessionStore
import kotlinx.coroutines.flow.map

data class LedgerEntry(
    val id: String,
    val dateEpochMs: Long,
    val description: String,
    val debitMinorUnits: Long,
    val creditMinorUnits: Long,
    val runningBalance: Long
)

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val database: BillingDatabase,
    private val syncManager: SyncManager,
    private val sessionStore: SessionStore
) : ViewModel() {
    private val dao = database.masterDao()
    private val searchQuery = MutableStateFlow("")
    val categories: StateFlow<List<CategoryEntity>> = kotlinx.coroutines.flow.combine(
        sessionStore.activeSession,
        searchQuery
    ) { session, query ->
        val companyId = session?.companyId ?: ""
        companyId to query
    }.flatMapLatest { (companyId, query) ->
        dao.categories(companyId, query)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateSearch(query: String) { searchQuery.value = query }

    fun addCategory(name: String, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
                val category = CategoryEntity(
                    id = newRecordId(),
                    companyId = session.companyId,
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
    private val syncManager: SyncManager,
    private val sessionStore: SessionStore
) : ViewModel() {
    private val dao = database.masterDao()
    private val searchQuery = MutableStateFlow("")

    val products: StateFlow<List<ProductEntity>> = kotlinx.coroutines.flow.combine(
        sessionStore.activeSession,
        searchQuery
    ) { session, query ->
        val companyId = session?.companyId ?: ""
        companyId to query
    }.flatMapLatest { (companyId, query) ->
        dao.products(companyId, query)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val categories: StateFlow<List<CategoryEntity>> = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            dao.categories(companyId, "")
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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
                val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
                val product = ProductEntity(
                    id = newRecordId(),
                    companyId = session.companyId,
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
    private val syncManager: SyncManager,
    private val sessionStore: SessionStore
) : ViewModel() {
    private val dao = database.masterDao()
    private val searchQuery = MutableStateFlow("")

    val customers: StateFlow<List<CustomerEntity>> = kotlinx.coroutines.flow.combine(
        sessionStore.activeSession,
        searchQuery
    ) { session, query ->
        val companyId = session?.companyId ?: ""
        companyId to query
    }.flatMapLatest { (companyId, query) ->
        dao.customers(companyId, query)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateSearch(query: String) { searchQuery.value = query }

    fun addCustomer(name: String, phone: String?, address: String?, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
                val customer = CustomerEntity(
                    id = newRecordId(),
                    companyId = session.companyId,
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
                val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
                val credit = CustomerCreditEntity(
                    id = newRecordId(),
                    companyId = session.companyId,
                    customerId = customerId,
                    amountMinorUnits = amountMinorUnits,
                    reason = reason,
                    dateEpochMs = System.currentTimeMillis(),
                    syncStatus = SyncStatus.LOCAL_ONLY
                )
                dao.insertCustomerCredit(credit)
                syncManager.enqueueCustomerCredit(credit, "INSERT")
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun getCustomerCredits(customerId: String): Flow<List<CustomerCreditEntity>> = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            dao.getCustomerCredits(companyId, customerId)
        }

    fun getCustomerCreditBalance(customerId: String): Flow<Long?> = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            dao.getCustomerCreditBalance(companyId, customerId)
        }

    fun getTotalCustomerCreditsReceivable(): Flow<Long?> = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            dao.getTotalCustomerCreditsReceivable(companyId)
        }

    fun getCustomerLedger(customerId: String): Flow<List<LedgerEntry>> = kotlinx.coroutines.flow.combine(
        sessionStore.activeSession.flatMapLatest { session -> 
            database.saleDao().getSalesForCustomer(session?.companyId ?: "", customerId) 
        },
        sessionStore.activeSession.flatMapLatest { session -> 
            dao.getCustomerCredits(session?.companyId ?: "", customerId) 
        }
    ) { sales, credits ->
        val entries = mutableListOf<LedgerEntry>()
        sales.forEach { sale ->
            entries.add(LedgerEntry(sale.id, sale.createdAtEpochMs, "Bill #${sale.billNumber}", sale.totalMinorUnits, 0L, 0L))
        }
        credits.forEach { credit ->
            entries.add(LedgerEntry(credit.id, credit.dateEpochMs, credit.reason, 0L, credit.amountMinorUnits, 0L))
        }
        val sorted = entries.sortedBy { it.dateEpochMs }
        var balance = 0L
        sorted.map { entry ->
            balance += entry.debitMinorUnits
            balance -= entry.creditMinorUnits
            entry.copy(runningBalance = balance)
        }.reversed()
    }

    fun getCustomerBalance(customerId: String): Flow<Long> = getCustomerLedger(customerId).map { ledger ->
        ledger.firstOrNull()?.runningBalance ?: 0L
    }

    fun updateCustomerCreditLimit(customerId: String, limit: Long, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
                dao.updateCustomerCreditLimit(session.companyId, customerId, limit)
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
    private val syncManager: SyncManager,
    private val sessionStore: SessionStore
) : ViewModel() {
    private val dao = database.masterDao()
    private val searchQuery = MutableStateFlow("")

    val suppliers: StateFlow<List<SupplierEntity>> = kotlinx.coroutines.flow.combine(
        sessionStore.activeSession,
        searchQuery
    ) { session, query ->
        val companyId = session?.companyId ?: ""
        companyId to query
    }.flatMapLatest { (companyId, query) ->
        dao.suppliers(companyId, query)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateSearch(query: String) { searchQuery.value = query }

    fun addSupplier(name: String, phone: String?, address: String?, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
                val supplier = SupplierEntity(
                    id = newRecordId(),
                    companyId = session.companyId,
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
                val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
                val credit = SupplierCreditEntity(
                    id = newRecordId(),
                    companyId = session.companyId,
                    supplierId = supplierId,
                    amountMinorUnits = amountMinorUnits,
                    terms = terms,
                    dueDateEpochMs = dueDateEpochMs,
                    dateEpochMs = System.currentTimeMillis(),
                    syncStatus = SyncStatus.LOCAL_ONLY
                )
                dao.insertSupplierCredit(credit)
                syncManager.enqueueSupplierCredit(credit, "INSERT")
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun getSupplierCredits(supplierId: String): Flow<List<SupplierCreditEntity>> = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            dao.getSupplierCredits(companyId, supplierId)
        }

    fun getSupplierCreditBalance(supplierId: String): Flow<Long?> = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            dao.getSupplierCreditBalance(companyId, supplierId)
        }

    fun getTotalSupplierCreditsPayable(): Flow<Long?> = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            dao.getTotalSupplierCreditsPayable(companyId)
        }

    fun getSupplierLedger(supplierId: String): Flow<List<LedgerEntry>> = kotlinx.coroutines.flow.combine(
        sessionStore.activeSession.flatMapLatest { session -> 
            database.purchaseDao().getPurchasesForSupplier(session?.companyId ?: "", supplierId) 
        },
        sessionStore.activeSession.flatMapLatest { session -> 
            dao.getSupplierCredits(session?.companyId ?: "", supplierId) 
        }
    ) { purchases, credits ->
        val entries = mutableListOf<LedgerEntry>()
        purchases.forEach { purchase ->
            entries.add(LedgerEntry(purchase.id, purchase.createdAtEpochMs, "Purchase", purchase.totalMinorUnits, 0L, 0L))
        }
        credits.forEach { credit ->
            entries.add(LedgerEntry(credit.id, credit.dateEpochMs, credit.terms, 0L, credit.amountMinorUnits, 0L))
        }
        val sorted = entries.sortedBy { it.dateEpochMs }
        var balance = 0L
        sorted.map { entry ->
            balance += entry.debitMinorUnits
            balance -= entry.creditMinorUnits
            entry.copy(runningBalance = balance)
        }.reversed()
    }
    
    fun getSupplierBalance(supplierId: String): Flow<Long> = getSupplierLedger(supplierId).map { ledger ->
        ledger.firstOrNull()?.runningBalance ?: 0L
    }
}

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val database: BillingDatabase,
    private val syncManager: SyncManager,
    private val sessionStore: SessionStore
) : ViewModel() {
    private val dao = database.masterDao()

    val expenses: StateFlow<List<ExpenseEntity>> = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            dao.expenses(companyId)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun addExpense(amountMinorUnits: Long, description: String, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
                val expense = ExpenseEntity(
                    id = newRecordId(),
                    companyId = session.companyId,
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
