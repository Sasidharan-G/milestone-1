package com.company.billing.feature.billing.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.company.billing.core.common.AppResult
import com.company.billing.core.common.Money
import com.company.billing.core.database.BillingDatabase
import com.company.billing.feature.billing.domain.SaleDraft
import com.company.billing.feature.billing.domain.SaleLine
import com.company.billing.feature.billing.domain.SaleRepository
import com.company.billing.feature.masters.data.CustomerEntity
import com.company.billing.feature.masters.data.ProductEntity
import com.company.billing.feature.billing.data.SaleEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.company.billing.core.sharing.ShareManager
import kotlinx.coroutines.flow.first

@HiltViewModel
class BillingViewModel @Inject constructor(
    private val database: BillingDatabase,
    private val saleRepository: SaleRepository,
    private val shareManager: ShareManager
) : ViewModel() {

    private val masterDao = database.masterDao()
    private val saleDao = database.saleDao()

    val products: StateFlow<List<ProductEntity>> = masterDao.products("")
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val customers: StateFlow<List<CustomerEntity>> = masterDao.customers("")
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val sales: StateFlow<List<SaleEntity>> = saleDao.getSales()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedCustomerId = MutableStateFlow<String?>(null)
    val selectedCustomerId: StateFlow<String?> = _selectedCustomerId.asStateFlow()

    private val _lines = MutableStateFlow<List<SaleLine>>(emptyList())
    val lines: StateFlow<List<SaleLine>> = _lines.asStateFlow()

    fun setCustomer(customerId: String?) {
        _selectedCustomerId.value = customerId
    }

    fun addLine(productId: String, productName: String, quantity: Long, unitPrice: Money) {
        val current = _lines.value.toMutableList()
        val index = current.indexOfFirst { it.productId == productId }
        if (index >= 0) {
            val line = current[index]
            current[index] = line.copy(quantity = line.quantity + quantity)
        } else {
            current.add(SaleLine(productId, productName, quantity, unitPrice))
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
        _selectedCustomerId.value = null
    }

    fun save(onSuccess: (String) -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            if (_lines.value.isEmpty()) {
                onError(Exception("Cannot save empty sale bill"))
                return@launch
            }
            val draft = SaleDraft(lines = _lines.value, customerId = _selectedCustomerId.value)
            when (val result = saleRepository.save(draft)) {
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

    fun shareBill(billNumber: String, isWhatsapp: Boolean) {
        viewModelScope.launch {
            val saleList = saleDao.getSales().first()
            val sale = saleList.find { it.billNumber == billNumber } ?: return@launch
            val items = saleDao.getSaleItems(sale.id).first()
            
            val summary = StringBuilder().apply {
                appendLine("INVOICE SUMMARY - ${sale.billNumber}")
                appendLine("Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(sale.createdAtEpochMs))}")
                appendLine("---------------------------")
                for (item in items) {
                    // Try to resolve product name from DB or use ID
                    val pName = masterDao.products("").first().find { it.id == item.productId }?.name ?: "Product"
                    appendLine("$pName:")
                    appendLine("  ${item.quantity} x ${com.company.billing.core.common.Money(item.unitPriceMinorUnits)} = ${com.company.billing.core.common.Money(item.lineTotalMinorUnits)}")
                }
                appendLine("---------------------------")
                appendLine("TOTAL: ${com.company.billing.core.common.Money(sale.totalMinorUnits)}")
                appendLine("Thank you for your business!")
            }.toString()

            val pkg = if (isWhatsapp) ShareManager.PACKAGE_WHATSAPP else null
            shareManager.shareText(summary, pkg)
        }
    }
}
