package com.company.billing.feature.purchase.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.company.billing.core.common.AppResult
import com.company.billing.core.common.Money
import com.company.billing.core.database.BillingDatabase
import com.company.billing.feature.purchase.domain.PurchaseDraft
import com.company.billing.feature.purchase.domain.PurchaseLine
import com.company.billing.feature.purchase.domain.PurchaseRepository
import com.company.billing.feature.masters.data.SupplierEntity
import com.company.billing.feature.masters.data.ProductEntity
import com.company.billing.feature.purchase.data.PurchaseEntity
import com.company.billing.feature.stock.domain.ProductStock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PurchaseViewModel @Inject constructor(
    private val database: BillingDatabase,
    private val purchaseRepository: PurchaseRepository
) : ViewModel() {

    private val masterDao = database.masterDao()
    private val purchaseDao = database.purchaseDao()

    val products: StateFlow<List<ProductEntity>> = masterDao.products("")
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val suppliers: StateFlow<List<SupplierEntity>> = masterDao.suppliers("")
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val purchases: StateFlow<List<PurchaseEntity>> = purchaseDao.getPurchases()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val stocks: StateFlow<List<ProductStock>> = purchaseDao.getStockBalances()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedSupplierId = MutableStateFlow<String?>(null)
    val selectedSupplierId: StateFlow<String?> = _selectedSupplierId.asStateFlow()

    private val _lines = MutableStateFlow<List<PurchaseLine>>(emptyList())
    val lines: StateFlow<List<PurchaseLine>> = _lines.asStateFlow()

    fun setSupplier(supplierId: String?) {
        _selectedSupplierId.value = supplierId
    }

    fun addLine(productId: String, quantity: Long, unitCost: Money) {
        val current = _lines.value.toMutableList()
        val index = current.indexOfFirst { it.productId == productId }
        if (index >= 0) {
            val line = current[index]
            current[index] = line.copy(quantity = line.quantity + quantity)
        } else {
            current.add(PurchaseLine(productId, quantity, unitCost))
        }
        _lines.value = current
    }

    fun removeLine(productId: String) {
        _lines.value = _lines.value.filterNot { it.productId == productId }
    }

    fun updateQuantity(productId: String, newQty: Long) {
        if (newQty <= 0) {
            removeLine(productId)
            return
        }
        _lines.value = _lines.value.map {
            if (it.productId == productId) it.copy(quantity = newQty) else it
        }
    }

    fun clearDraft() {
        _lines.value = emptyList()
        _selectedSupplierId.value = null
    }

    fun save(onSuccess: (String) -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            val supplierId = _selectedSupplierId.value
            if (supplierId.isNullOrEmpty()) {
                onError(Exception("Supplier is required"))
                return@launch
            }
            if (_lines.value.isEmpty()) {
                onError(Exception("Cannot save empty purchase bill"))
                return@launch
            }
            val draft = PurchaseDraft(supplierId = supplierId, lines = _lines.value)
            when (val result = purchaseRepository.save(draft)) {
                is AppResult.Success -> {
                    clearDraft()
                    onSuccess(result.value)
                }
                is AppResult.Failure -> {
                    onError(Exception(result.error.userMessage))
                }
            }
        }
    }
}
