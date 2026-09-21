package com.kadaikutty.pos.feature.purchase.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kadaikutty.pos.core.common.AppResult
import com.kadaikutty.pos.core.common.Money
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.feature.purchase.domain.PurchaseDraft
import com.kadaikutty.pos.feature.purchase.domain.PurchaseLine
import com.kadaikutty.pos.feature.purchase.domain.PurchaseRepository
import com.kadaikutty.pos.feature.masters.data.SupplierEntity
import com.kadaikutty.pos.feature.masters.data.ProductEntity
import com.kadaikutty.pos.feature.purchase.data.PurchaseEntity
import com.kadaikutty.pos.feature.purchase.data.PurchaseItemEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import com.kadaikutty.pos.core.auth.SessionStore
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class PurchaseViewModel @Inject constructor(
    private val database: BillingDatabase,
    private val purchaseRepository: PurchaseRepository,
    private val sessionStore: SessionStore
) : ViewModel() {

    private var checkoutId = com.kadaikutty.pos.core.common.newRecordId()
    private var editingPurchase: PurchaseEntity? = null
    private val _operationError = MutableStateFlow<String?>(null)
    val operationError = _operationError.asStateFlow()
    private val errors = kotlinx.coroutines.CoroutineExceptionHandler { _, error -> _operationError.value = error.message }
    private val masterDao = database.masterDao()
    private val purchaseDao = database.purchaseDao()

    private val products = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            masterDao.products(companyId, "")
        }

    private val suppliers = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            masterDao.suppliers(companyId, "")
        }

    private val _historyFilter = MutableStateFlow(com.kadaikutty.pos.core.ui.HistoryFilter())
    val historyFilter: StateFlow<com.kadaikutty.pos.core.ui.HistoryFilter> = _historyFilter.asStateFlow()
    fun setHistoryFilter(filter: com.kadaikutty.pos.core.ui.HistoryFilter) { _historyFilter.value = filter }

    // Purchase History, narrowed in the database. Typing waits a moment so each key press does
    // not run a new query.
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private val purchases = combine(sessionStore.activeSession, _historyFilter.debounce(250)) { session, filter -> session to filter }
        .flatMapLatest { (session, filter) ->
            val companyId = session?.companyId ?: ""
            val (from, to) = filter.bounds()
            purchaseRepository.searchPurchases(companyId, filter.query.trim(), from, to)
        }

    private val stocks = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            purchaseRepository.getStockBalances(companyId)
        }

    private val _selectedSupplierId = MutableStateFlow<String?>(null)

    private val _lines = MutableStateFlow<List<PurchaseLine>>(emptyList())
    
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    
    val uiState: StateFlow<PurchaseUiState> = combine(
        combine(products, suppliers, purchases, stocks) { p, s, pur, st ->
            PurchaseUiState(products = p, suppliers = s, purchases = pur, stocks = st)
        },
        combine(_lines, _selectedSupplierId) { l, sid ->
            Pair(l, sid)
        }
    ) { state1, state2 ->
        state1.copy(
            lines = state2.first,
            selectedSupplierId = state2.second,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PurchaseUiState(isLoading = true))

    fun setSupplier(supplierId: String?) {
        if (_isSaving.value) return
        _selectedSupplierId.value = supplierId
    }

    fun addLine(productId: String, quantity: Long, unitCost: Money, unitType: String = "PIECE", supplierId: String? = null) {
        if (_isSaving.value) return
        if (quantity !in 1..com.kadaikutty.pos.core.common.CheckoutMath.MAX_QUANTITY || unitCost.minorUnits !in 0..com.kadaikutty.pos.core.common.CheckoutMath.MAX_AMOUNT) { _operationError.value = "Enter a valid price and quantity"; return }
        val targetSupplierId = supplierId ?: _selectedSupplierId.value
        val current = _lines.value.toMutableList()
        val index = current.indexOfFirst { it.productId == productId && it.supplierId == targetSupplierId }
        if (index >= 0) {
            val line = current[index]
            if (line.quantity + quantity > com.kadaikutty.pos.core.common.CheckoutMath.MAX_QUANTITY) { _operationError.value = "Quantity exceeds the supported limit"; return }
            current[index] = line.copy(quantity = line.quantity + quantity)
        } else {
            current.add(PurchaseLine(productId, quantity, unitCost, unitType, targetSupplierId))
        }
        if (runCatching { current.forEach { it.total } }.isFailure) { _operationError.value = "Line amount exceeds the supported limit"; return }
        _lines.value = current
    }

    fun removeLine(productId: String, supplierId: String? = null) {
        if (_isSaving.value) return
        _lines.value = _lines.value.filterNot { 
            if (supplierId != null) it.productId == productId && it.supplierId == supplierId 
            else it.productId == productId 
        }
    }

    fun updateQuantity(productId: String, newQty: Long, supplierId: String? = null) {
        if (_isSaving.value || newQty > com.kadaikutty.pos.core.common.CheckoutMath.MAX_QUANTITY) return
        if (newQty <= 0) {
            removeLine(productId, supplierId)
            return
        }
        val updated = _lines.value.map {
            if (it.productId == productId && (supplierId == null || it.supplierId == supplierId)) {
                it.copy(quantity = newQty)
            } else {
                it
            }
        }
        if (runCatching { updated.forEach { it.total } }.isFailure) { _operationError.value = "Line amount exceeds the supported limit"; return }
        _lines.value = updated
    }

    fun clearDraft(afterSave: Boolean = false) {
        if (_isSaving.value && !afterSave) return
        checkoutId = com.kadaikutty.pos.core.common.newRecordId()
        editingPurchase = null
        _lines.value = emptyList()
        _selectedSupplierId.value = null
    }

    fun save(
        invoiceNumber: String? = null,
        notes: String? = null,
        paymentMode: String = "CASH",
        paidCash: Money = Money.Zero,
        paidUpi: Money = Money.Zero,
        creditApplied: Money = Money.Zero,
        onSuccess: (String) -> Unit, 
        onError: (Throwable) -> Unit
    ) {
        if (_isSaving.value) return
        _isSaving.value = true
        viewModelScope.launch(errors) {
            try {
                if (_lines.value.isEmpty()) {
                    onError(Exception("Cannot save empty purchase bill"))
                    return@launch
                }

                val groups = _lines.value.groupBy { it.supplierId ?: _selectedSupplierId.value }.entries.toList()
                require(groups.all { !it.key.isNullOrBlank() }) { "Every item needs a supplier" }
                require(editingPurchase == null || groups.size == 1) { "A purchase edit must contain one supplier" }
                val totals = groups.map { it.value.fold(Money.Zero) { sum, line -> sum + line.total }.minorUnits }
                val total = totals.sum()
                val cashTotal = if (paymentMode == "CASH") total else paidCash.minorUnits
                val upiTotal = if (paymentMode in listOf("UPI", "GPAY")) total else paidUpi.minorUnits
                val creditTotal = if (paymentMode == "CREDIT") total else creditApplied.minorUnits
                com.kadaikutty.pos.core.common.CheckoutMath.validate(total, cashTotal, upiTotal, creditTotal)
                val cashParts = com.kadaikutty.pos.core.common.CheckoutMath.allocate(cashTotal, totals)
                val remaining = totals.mapIndexed { i, amount -> amount - cashParts[i] }
                val upiParts = com.kadaikutty.pos.core.common.CheckoutMath.allocate(upiTotal, remaining)
                val drafts = groups.mapIndexed { i, group ->
                    PurchaseDraft(supplierId = group.key!!, lines = group.value, invoiceNumber = invoiceNumber,
                        notes = notes, paymentMode = paymentMode, paidCash = Money(cashParts[i]), paidUpi = Money(upiParts[i]),
                        creditApplied = Money(totals[i] - cashParts[i] - upiParts[i]), requestId = "$checkoutId:${group.key}",
                        editingPurchaseId = editingPurchase?.id, expectedRevision = editingPurchase?.revision)
                }
                val createdOrderIds = when (val result = purchaseRepository.saveBatch(drafts)) {
                    is AppResult.Success -> result.value
                    is AppResult.Failure -> { onError(Exception(result.error.userMessage)); return@launch }
                }

                clearDraft(afterSave = true)
                onSuccess(createdOrderIds.joinToString(", "))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                onError(e)
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun deletePurchase(
        purchase: PurchaseEntity,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        viewModelScope.launch(errors) {
            val orderOrInv = purchase.invoiceNumber ?: purchase.orderNumber ?: purchase.id
            when (val result = purchaseRepository.deletePurchase(purchase.id, orderOrInv)) {
                is AppResult.Success -> onSuccess()
                is AppResult.Failure -> onError(Exception(result.error.userMessage))
            }
        }
    }

    fun loadPurchaseForEditing(
        purchase: PurchaseEntity,
        onLoaded: (String?) -> Unit
    ) {
        viewModelScope.launch(errors) {
            val items = purchaseRepository.getPurchaseItemsList(purchase.id)
            if (items.isNotEmpty()) {
                val orderOrInv = purchase.invoiceNumber ?: purchase.orderNumber ?: purchase.id
                val session = sessionStore.activeSession.first() ?: error("Sign in first")
                require(session.role in listOf("ADMIN", "SUPER_ADMIN")) { "Only an administrator can edit purchases" }
                require(_lines.value.isEmpty()) { "Clear the current cart before editing a purchase" }
                editingPurchase = purchase
                checkoutId = com.kadaikutty.pos.core.common.newRecordId()

                _selectedSupplierId.value = purchase.supplierId
                _lines.value = items.map {
                    PurchaseLine(
                        productId = it.productId,
                        quantity = it.quantity,
                        unitValue = Money(it.unitValueMinorUnits),
                        supplierId = purchase.supplierId,
                        unitType = it.unitType ?: masterDao.getProductById(purchase.companyId, it.productId)?.unitType ?: "PIECE"
                    )
                }
                onLoaded(purchase.invoiceNumber)
            }
        }
    }

    fun getPurchaseItemsFlow(purchaseId: String): Flow<List<PurchaseItemEntity>> {
        return sessionStore.activeSession.flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            if (companyId.isNotEmpty()) purchaseRepository.getPurchaseItems(companyId, purchaseId)
            else kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }
}

