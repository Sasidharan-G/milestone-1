package com.kadaikutty.pos.feature.billing.presentation

import androidx.room.withTransaction
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kadaikutty.pos.core.common.AppResult
import com.kadaikutty.pos.core.common.Money
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.feature.billing.domain.SaleDraft
import com.kadaikutty.pos.feature.billing.domain.SaleLine
import com.kadaikutty.pos.feature.billing.domain.SaleRepository
import com.kadaikutty.pos.feature.masters.data.CustomerEntity
import com.kadaikutty.pos.feature.masters.data.ProductEntity
import com.kadaikutty.pos.feature.billing.data.SaleEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.kadaikutty.pos.core.sharing.ShareManager
import com.kadaikutty.pos.core.preferences.AppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import com.kadaikutty.pos.core.auth.SessionStore

import com.kadaikutty.pos.core.sync.SyncManager
import com.kadaikutty.pos.core.sync.SyncStatus
import com.kadaikutty.pos.feature.masters.data.CustomerCreditEntity
import com.kadaikutty.pos.core.common.newRecordId
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOf
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn

@HiltViewModel
class BillingViewModel @Inject constructor(
    private val database: BillingDatabase,
    private val saleRepository: SaleRepository,
    private val shareManager: ShareManager,
    private val appPreferences: AppPreferences,
    private val sessionStore: SessionStore,
    private val syncManager: SyncManager,
    private val syncScheduler: com.kadaikutty.pos.core.sync.SyncScheduler,
    private val analyticsManager: com.kadaikutty.pos.core.analytics.AnalyticsManager,
    private val printerManager: com.kadaikutty.pos.core.printer.data.PrinterManager
) : ViewModel() {

    private val _discountInput = MutableStateFlow("")
    val discountInput = _discountInput.asStateFlow()
    fun setDiscountInput(value: String) {
        if (_isSaving.value) return
        if (com.kadaikutty.pos.core.common.CheckoutMath.parseAmount(value) == null) { _operationError.value = "Enter a valid discount with up to 2 decimals"; return }
        _discountInput.value = value
        saveDraftToDb()
    }
    private var checkoutId = newRecordId()
    private var editingSaleId: String? = null
    private var editingRevision: Long? = null
    private var originalQuantities: Map<String, Long> = emptyMap()
    private val _operationError = MutableStateFlow<String?>(null)
    val operationError = _operationError.asStateFlow()
    private val errors = kotlinx.coroutines.CoroutineExceptionHandler { _, error ->
        _operationError.value = error.message ?: "Operation failed. Your saved bills are unchanged."
    }
    private val masterDao = database.masterDao()
    private val saleDao = database.saleDao()
    private val purchaseDao = database.purchaseDao()
    private val draftCartDao = database.draftCartDao()
    private val auditLogDao = database.auditLogDao()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val stockBalances = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            purchaseDao.getStockBalances(companyId).map { list ->
                list.associateBy { it.productId }
            }.map { map -> map.mapValues { it.value.currentStock } }
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val heldCartsSummary: StateFlow<List<com.kadaikutty.pos.feature.billing.data.HeldCartSummary>> = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            if (companyId.isBlank()) flowOf(emptyList()) else draftCartDao.getHeldCartsSummary(companyId)
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val heldCartsCount: StateFlow<Int> = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            if (companyId.isBlank()) flowOf(0) else draftCartDao.getHeldCartsCount(companyId)
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), 0)

    fun holdCurrentCart(label: String = "") {
        viewModelScope.launch(errors) {
            val companyId = sessionStore.activeSession.first()?.companyId ?: return@launch
            if (_isSaving.value || _lines.value.isEmpty()) return@launch
            saveDraftJob?.cancel()
            saveDraftJob?.join()
            val parkId = "held_" + System.currentTimeMillis()
            val cleanLabel = if (label.isNotBlank()) label else "Hold #${parkId.takeLast(4)}"
            val now = System.currentTimeMillis()

            val entities = _lines.value.map { line ->
                com.kadaikutty.pos.feature.billing.data.DraftCartItemEntity(
                    id = newRecordId(),
                    companyId = companyId,
                    productId = line.productId,
                    productName = line.productName,
                    quantity = line.quantity,
                    unitPriceMinorUnits = line.unitPrice.minorUnits,
                    unitType = line.unitType,
                    customerId = _selectedCustomerId.value,
                    checkoutId = checkoutId,
                    editingSaleId = editingSaleId,
                    editingRevision = editingRevision,
                    lineDiscountMinorUnits = line.discount.minorUnits,
                    cartDiscountMinorUnits = com.kadaikutty.pos.core.common.CheckoutMath.parseAmount(_discountInput.value) ?: 0,
                    parkId = parkId,
                    parkLabel = cleanLabel,
                    parkedAtEpochMs = now
                )
            }
            database.withTransaction {
                draftCartDao.insertItems(entities)
                draftCartDao.clearCart(companyId)
            }
            _lines.value = emptyList()
            _selectedCustomerId.value = null
            editingSaleId = null
            editingRevision = null
            originalQuantities = emptyMap()
            checkoutId = newRecordId()
            _discountInput.value = ""
        }
    }

    fun resumeHeldCart(parkId: String) {
        viewModelScope.launch(errors) {
            val companyId = sessionStore.activeSession.first()?.companyId ?: return@launch
            require(_lines.value.isEmpty()) { "Hold or clear the current cart before resuming another cart" }
            saveDraftJob?.cancel()
            saveDraftJob?.join()
            val items = draftCartDao.getItemsByParkId(companyId, parkId)
            if (items.isNotEmpty()) {
                _lines.value = items.map {
                    SaleLine(it.productId, it.productName, it.quantity, Money(it.unitPriceMinorUnits), it.unitType, Money(it.lineDiscountMinorUnits))
                }
                restoreCartHeader(companyId, items.first())
                database.withTransaction {
                    draftCartDao.replaceActive(companyId, items.map { it.copy(parkId = "active") })
                    draftCartDao.clearCartByParkId(companyId, parkId)
                }
            }
        }
    }

    fun discardHeldCart(parkId: String) {
        viewModelScope.launch(errors) {
            val companyId = sessionStore.activeSession.first()?.companyId ?: return@launch
            draftCartDao.clearCartByParkId(companyId, parkId)
        }
    }

    init {
        viewModelScope.launch(errors) {
            val session = sessionStore.activeSession.first()
            val companyId = session?.companyId
            if (companyId != null) {
                val savedDrafts = draftCartDao.getDraftCart(companyId).first()
                if (savedDrafts.isNotEmpty()) {
                    restoreCartHeader(companyId, savedDrafts.first())
                    _lines.value = savedDrafts.map { 
                        SaleLine(it.productId, it.productName, it.quantity, Money(it.unitPriceMinorUnits), it.unitType, Money(it.lineDiscountMinorUnits)) 
                    }
                }
            }
        }
    }

    private suspend fun restoreCartHeader(company: String, item: com.kadaikutty.pos.feature.billing.data.DraftCartItemEntity) {
        _discountInput.value = if (item.cartDiscountMinorUnits == 0L) "" else java.math.BigDecimal.valueOf(item.cartDiscountMinorUnits, 2).toPlainString()
        _selectedCustomerId.value = item.customerId
        checkoutId = item.checkoutId ?: newRecordId()
        editingSaleId = item.editingSaleId
        editingRevision = item.editingRevision
        originalQuantities = editingSaleId?.let { saleDao.getSaleItemsList(company, it).associate { line -> line.productId to line.quantity } }.orEmpty()
    }

    private var saveDraftJob: kotlinx.coroutines.Job? = null

    private fun saveDraftToDb(immediate: Boolean = false) {
        saveDraftJob?.cancel()
        saveDraftJob = viewModelScope.launch(errors) {
            if (!immediate) {
                kotlinx.coroutines.delay(200)
            }
            val companyId = sessionStore.activeSession.first()?.companyId ?: return@launch
            database.withTransaction {
            if (_lines.value.isNotEmpty()) {
                val entities = _lines.value.map { line ->
                    com.kadaikutty.pos.feature.billing.data.DraftCartItemEntity(
                        id = newRecordId(),
                        companyId = companyId,
                        productId = line.productId,
                        productName = line.productName,
                        quantity = line.quantity,
                        unitPriceMinorUnits = line.unitPrice.minorUnits,
                        unitType = line.unitType,
                        customerId = _selectedCustomerId.value,
                        checkoutId = checkoutId,
                        editingSaleId = editingSaleId,
                        editingRevision = editingRevision,
                        lineDiscountMinorUnits = line.discount.minorUnits,
                        cartDiscountMinorUnits = com.kadaikutty.pos.core.common.CheckoutMath.parseAmount(_discountInput.value) ?: 0
                    )
                }
                draftCartDao.replaceActive(companyId, entities)
            } else draftCartDao.clearCart(companyId)
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val products = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            masterDao.products(companyId, "")
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val customers = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            masterDao.customers(companyId, "")
        }

    private val _historyFilter = MutableStateFlow(com.kadaikutty.pos.core.ui.HistoryFilter())
    val historyFilter: StateFlow<com.kadaikutty.pos.core.ui.HistoryFilter> = _historyFilter
    fun setHistoryFilter(filter: com.kadaikutty.pos.core.ui.HistoryFilter) { _historyFilter.value = filter }

    // Sales History, narrowed in the database. Typing waits a moment so each key press does not
    // start a new query.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    val pagedSales: kotlinx.coroutines.flow.Flow<PagingData<SaleEntity>> = combine(sessionStore.activeSession, _historyFilter.debounce(250)) { session, filter -> session to filter }
        .flatMapLatest { (session, filter) ->
            val companyId = session?.companyId ?: ""
            if (companyId.isBlank()) {
                kotlinx.coroutines.flow.emptyFlow()
            } else {
                val (from, to) = filter.bounds()
                val query = filter.query.trim()
                Pager(
                    config = PagingConfig(pageSize = 20, enablePlaceholders = false),
                    pagingSourceFactory = { saleDao.searchSalesPaged(companyId, query, from, to) }
                ).flow
            }
        }.cachedIn(viewModelScope)

    private val _selectedCustomerId = MutableStateFlow<String?>(null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val selectedCustomerCreditBalance = combine(sessionStore.activeSession, _selectedCustomerId) { session, customerId ->
        session to customerId
    }.flatMapLatest { (session, customerId) ->
        if ((session == null) || customerId.isNullOrBlank() || (customerId == "online")) {
            flowOf(0L)
        } else {
            masterDao.getCustomerCreditBalance(session.companyId, customerId).map { it ?: 0L }
        }
    }

    private val _lines = MutableStateFlow<List<SaleLine>>(emptyList())
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private data class BillingStateData(
        val lines: List<SaleLine>,
        val customerId: String?,
        val credit: Long,
        val isSaving: Boolean
    )

    val uiState: StateFlow<BillingUiState> = combine(
        combine(products, customers, stockBalances) { p, c, st -> 
            BillingUiState(products = p, customers = c, stockBalances = st) 
        },
        combine(_lines, _selectedCustomerId, selectedCustomerCreditBalance, _isSaving) { l, cid, credit, saving -> 
            BillingStateData(l, cid, credit, saving) 
        }
    ) { state1, state2 ->
        state1.copy(
            lines = state2.lines,
            selectedCustomerId = state2.customerId,
            selectedCustomerCreditBalance = state2.credit,
            isSaving = state2.isSaving,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BillingUiState(isLoading = true))

    fun setCustomer(customerId: String?) {
        if (_isSaving.value) return
        _selectedCustomerId.value = customerId
        saveDraftToDb()
    }

    fun addQuickCustomer(
        name: String,
        phone: String?,
        address: String?,
        openingDueMinorUnits: Long,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        viewModelScope.launch(errors) {
            try {
                val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
                val cleanName = name.trim()
                val cleanPhone = phone?.trim()?.filter { it.isDigit() }?.takeLast(10)?.takeIf { it.isNotBlank() }

                // Check for existing customer to prevent duplicate creation
                val existingCustomers = masterDao.customers(session.companyId, "").first()
                if (cleanPhone != null) {
                    val phoneMatch = existingCustomers.firstOrNull { it.phone?.filter { ch -> ch.isDigit() }?.takeLast(10) == cleanPhone }
                    if (phoneMatch != null) {
                        _selectedCustomerId.value = phoneMatch.id
                        onSuccess(phoneMatch.id)
                        return@launch
                    }
                }
                val nameMatch = existingCustomers.firstOrNull { it.name.equals(cleanName, ignoreCase = true) }
                if (nameMatch != null) {
                    _selectedCustomerId.value = nameMatch.id
                    onSuccess(nameMatch.id)
                    return@launch
                }

                val customerId = newRecordId()
                val customer = CustomerEntity(
                    id = customerId,
                    companyId = session.companyId,
                    name = cleanName,
                    phone = cleanPhone ?: phone?.trim()?.takeIf { it.isNotBlank() },
                    address = address?.trim()?.takeIf { it.isNotBlank() },
                    creditLimitMinorUnits = 0L,
                    createdAtEpochMs = System.currentTimeMillis(),
                    updatedAtEpochMs = System.currentTimeMillis(),
                    syncStatus = SyncStatus.LOCAL_ONLY
                )
                masterDao.insertCustomer(customer)
                syncManager.enqueueCustomer(customer, "INSERT")

                if (openingDueMinorUnits > 0L) {
                    val credit = CustomerCreditEntity(
                        id = newRecordId(),
                        companyId = session.companyId,
                        customerId = customerId,
                        amountMinorUnits = openingDueMinorUnits,
                        reason = "Opening Balance / Previous Debt",
                        dateEpochMs = System.currentTimeMillis(),
                        syncStatus = SyncStatus.LOCAL_ONLY
                    )
                    masterDao.insertCustomerCredit(credit)
                    syncManager.enqueueCustomerCredit(credit, "INSERT")
                }

                _selectedCustomerId.value = customerId
                onSuccess(customerId)
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    private fun formatStock(quantity: Long, unitType: String): String {
        return if (unitType == "KG" || unitType == "LITER") {
            val label = if (unitType == "KG") "Kg" else "Ltr"
            String.format(java.util.Locale.US, "%.3f %s", quantity / 1000.0, label)
        } else {
            "$quantity Pcs"
        }
    }

    private fun validateStockForCart(productId: String, productName: String, requestedQuantity: Long, unitType: String): String? {
        val available = (uiState.value.stockBalances[productId] ?: 0L) + (originalQuantities[productId] ?: 0L)
        val currentInCart = _lines.value.firstOrNull { it.productId == productId }?.quantity ?: 0L
        val totalRequested = currentInCart + requestedQuantity
        if (totalRequested > com.kadaikutty.pos.core.common.CheckoutMath.MAX_QUANTITY) return "Quantity exceeds the supported limit"
        return when {
            available <= 0L -> "$productName is out of stock!"
            totalRequested > available -> "$productName is out of stock! Available: ${formatStock(available, unitType)}, Cart: ${formatStock(totalRequested, unitType)}"
            else -> null
        }
    }

    fun addLine(productId: String, productName: String, quantity: Long, unitPrice: Money, unitType: String): String? {
        if (_isSaving.value) return "Please wait for checkout to finish"
        if (quantity !in 1..com.kadaikutty.pos.core.common.CheckoutMath.MAX_QUANTITY || unitPrice.minorUnits !in 0..com.kadaikutty.pos.core.common.CheckoutMath.MAX_AMOUNT) return "Enter a valid price and quantity"
        validateStockForCart(productId, productName, quantity, unitType)?.let { return it }
        val current = _lines.value.toMutableList()
        val index = current.indexOfFirst { it.productId == productId }
        if (index >= 0) {
            val line = current[index]
            current[index] = line.copy(quantity = line.quantity + quantity)
        } else {
            current.add(SaleLine(productId, productName, quantity, unitPrice, unitType))
        }
        if (runCatching { current.forEach { it.lineTotal } }.isFailure) return "Line amount exceeds the supported limit"
        _lines.value = current
        saveDraftToDb()
        return null
    }

    fun removeLine(productId: String) {
        if (_isSaving.value) return
        _lines.value = _lines.value.filterNot { it.productId == productId }
        saveDraftToDb()
    }

    fun updateQuantity(productId: String, newQty: Long) {
        if (_isSaving.value) return
        if (newQty > com.kadaikutty.pos.core.common.CheckoutMath.MAX_QUANTITY) {
            _operationError.value = "Quantity exceeds the supported limit"
            return
        }
        if (newQty <= 0) {
            removeLine(productId)
            return
        }
        val line = _lines.value.firstOrNull { it.productId == productId } ?: return
        val available = (uiState.value.stockBalances[productId] ?: 0L) + (originalQuantities[productId] ?: 0L)
        if (available <= 0L) {
            _operationError.value = "${line.productName} is out of stock!"
            return
        }
        if (newQty > available) {
            _operationError.value = "${line.productName} is out of stock! Available: ${formatStock(available, line.unitType)}, Cart: ${formatStock(newQty, line.unitType)}"
            return
        }
        val updated = _lines.value.map { if (it.productId == productId) it.copy(quantity = newQty) else it }
        if (runCatching { updated.forEach { it.lineTotal } }.isFailure) { _operationError.value = "Line amount exceeds the supported limit"; return }
        _lines.value = updated
        saveDraftToDb()
    }

    fun clearDraft(afterSave: Boolean = false) {
        if (_isSaving.value && !afterSave) return
        editingSaleId = null
        editingRevision = null
        originalQuantities = emptyMap()
        checkoutId = newRecordId()
        _discountInput.value = ""
        _lines.value = emptyList()
        _selectedCustomerId.value = null
        saveDraftToDb()
    }

    fun onBarcodeScanned(barcode: String, onProductFound: ((ProductEntity) -> Unit)? = null, onProductNotFound: () -> Unit) {
        val product = uiState.value.products.find { it.barcode == barcode }
        if (product != null) {
            val quantity = if (product.unitType == "KG" || product.unitType == "LITER") 1000L else 1L
            val error = addLine(product.id, product.name, quantity, Money(product.salePriceMinorUnits), product.unitType)
            if (error == null) {
                onProductFound?.invoke(product)
            } else {
                onProductNotFound()
            }
        } else {
            onProductNotFound()
        }
    }

    fun getInsufficientStockItems(selectedLines: List<SaleLine>? = null): List<String> {
        val currentBalances = uiState.value.stockBalances
        val linesToCheck = selectedLines ?: _lines.value
        val outOfStockNames = mutableListOf<String>()
        for (line in linesToCheck) {
            val available = (currentBalances[line.productId] ?: 0L) + (originalQuantities[line.productId] ?: 0L)
            if (line.quantity > available) {
                outOfStockNames.add("${line.productName} (Available: $available, Cart: ${line.quantity})")
            }
        }
        return outOfStockNames
    }

    fun save(
        paymentMode: String, 
        paidCash: Money = Money.Zero, 
        paidUpi: Money = Money.Zero, 
        creditApplied: Money = Money.Zero, 
        globalDiscount: Money = Money.Zero,
        settlePreviousCreditMinorUnits: Long = 0L,
        context: android.content.Context? = null,
        onSuccess: (String) -> Unit, 
        onError: (Throwable) -> Unit
    ) {
        if (_isSaving.value) return
        _isSaving.value = true
        viewModelScope.launch(errors) {
            try {
                val session = sessionStore.activeSession.first()
                if (session == null || !session.permissions.contains(com.kadaikutty.pos.core.security.Permission.SALE_CREATE)) {
                    onError(Exception("You do not have permission to create sales."))
                    return@launch
                }
                if (_lines.value.isEmpty()) {
                    onError(Exception("Cannot save empty sale bill"))
                    return@launch
                }
                val insufficientStock = getInsufficientStockItems()
                if (insufficientStock.isNotEmpty()) {
                    onError(Exception("Stock illa / insufficient stock: ${insufficientStock.joinToString(", ")}"))
                    return@launch
                }
                val draft = SaleDraft(
                    lines = _lines.value, 
                    customerId = _selectedCustomerId.value, 
                    paymentMode = paymentMode, 
                    paidCash = paidCash, 
                    paidUpi = paidUpi, 
                    creditApplied = creditApplied,
                    globalDiscount = globalDiscount,
                    requestId = checkoutId,
                    editingSaleId = editingSaleId,
                    expectedRevision = editingRevision,
                    previousDue = settlePreviousCreditMinorUnits
                )
                saveDraftJob?.cancel()
                saveDraftJob?.join()
                when (val result = saleRepository.save(draft)) {
                    is AppResult.Success -> {
                        val billNum = result.value

                        
                        // Auto-print check
                        if (context != null && appPreferences.autoPrintReceipt.first()) {
                            printBill(context, billNum)
                        }

                        runCatching { analyticsManager.logEvent(
                            com.kadaikutty.pos.core.analytics.AnalyticsEvents.EVENT_SALE_COMPLETED,
                            mapOf(
                                com.kadaikutty.pos.core.analytics.AnalyticsEvents.PARAM_CART_SIZE to draft.lines.size,
                                // draft.total is the amount actually charged: it applies the per-line and cart discounts this sum used to ignore.
                                com.kadaikutty.pos.core.analytics.AnalyticsEvents.PARAM_TOTAL_AMOUNT to draft.total.minorUnits,
                                com.kadaikutty.pos.core.analytics.AnalyticsEvents.PARAM_PAYMENT_METHOD to paymentMode
                            )
                        ) }

                        clearDraft(afterSave = true)
                        onSuccess(billNum)
                    }
                    is AppResult.Failure -> {
                        onError(Exception(result.error.userMessage))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) { onError(e)
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun checkoutSelectedItems(
        selectedProductIds: Set<String>, 
        paymentMode: String, 
        paidCash: Money = Money.Zero, 
        paidUpi: Money = Money.Zero, 
        creditApplied: Money = Money.Zero, 
        globalDiscount: Money = Money.Zero,
        settlePreviousCreditMinorUnits: Long = 0L,
        onSuccess: (String) -> Unit, 
        onError: (Throwable) -> Unit
    ) {
        if (_isSaving.value) return
        _isSaving.value = true
        viewModelScope.launch(errors) {
            try {
                val session = sessionStore.activeSession.first()
                if (session == null || !session.permissions.contains(com.kadaikutty.pos.core.security.Permission.SALE_CREATE)) {
                    onError(Exception("You do not have permission to create sales."))
                    return@launch
                }
                require(editingSaleId == null) { "Finish editing the whole bill before split checkout" }
                val selectedLines = _lines.value.filter { it.productId in selectedProductIds }
                if (selectedLines.isEmpty()) {
                    onError(Exception("No items selected for split checkout"))
                    return@launch
                }
                val insufficientStock = getInsufficientStockItems(selectedLines)
                if (insufficientStock.isNotEmpty()) {
                    onError(Exception("Stock illa / insufficient stock: ${insufficientStock.joinToString(", ")}"))
                    return@launch
                }
                val draft = SaleDraft(
                    lines = selectedLines, 
                    customerId = _selectedCustomerId.value, 
                    paymentMode = paymentMode, 
                    paidCash = paidCash, 
                    paidUpi = paidUpi, 
                    creditApplied = creditApplied,
                    globalDiscount = globalDiscount,
                    requestId = checkoutId,
                    editingSaleId = editingSaleId,
                    expectedRevision = editingRevision,
                    previousDue = settlePreviousCreditMinorUnits
                )
                saveDraftJob?.cancel()
                saveDraftJob?.join()
                when (val result = saleRepository.save(draft)) {
                    is AppResult.Success -> {
                        val billNum = result.value

                        
                        runCatching { analyticsManager.logEvent(
                            com.kadaikutty.pos.core.analytics.AnalyticsEvents.EVENT_SALE_COMPLETED,
                            mapOf(
                                com.kadaikutty.pos.core.analytics.AnalyticsEvents.PARAM_CART_SIZE to draft.lines.size,
                                // draft.total is the amount actually charged: it applies the per-line and cart discounts this sum used to ignore.
                                com.kadaikutty.pos.core.analytics.AnalyticsEvents.PARAM_TOTAL_AMOUNT to draft.total.minorUnits,
                                com.kadaikutty.pos.core.analytics.AnalyticsEvents.PARAM_PAYMENT_METHOD to paymentMode
                            )
                        ) }

                        _discountInput.value = ""
                        checkoutId = draft.nextCartRequestId
                        _lines.value = _lines.value.filterNot { it.productId in selectedProductIds }
                        saveDraftToDb()
                        onSuccess(billNum)
                    }
                    is AppResult.Failure -> {
                        onError(Exception(result.error.userMessage))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) { onError(e)
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun deleteSale(saleId: String, billNumber: String, reason: String = "Cancelled by cashier", onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch(errors) {
            val session = sessionStore.activeSession.first()
            val isAdminOrManager = session?.role in listOf("ADMIN", "SUPER_ADMIN", "OWNER") || session?.permissions?.contains(com.kadaikutty.pos.core.security.Permission.USER_MANAGE) == true
            if (session == null || !isAdminOrManager) {
                onError(Exception("Only admin or manager can delete sales."))
                return@launch
            }
            when (val result = saleRepository.deleteSale(saleId, billNumber, reason)) {
                is AppResult.Success -> {
                    onSuccess()
                }
                is AppResult.Failure -> onError(Exception(result.error.userMessage))
            }
        }
    }

    fun loadSaleForEditing(sale: SaleEntity, onSuccess: () -> Unit) {
        viewModelScope.launch(errors) {
            val session = sessionStore.activeSession.first() ?: return@launch
            require(session.role in listOf("ADMIN", "SUPER_ADMIN")) { "Only an administrator can edit bills" }
            require(_lines.value.isEmpty()) { "Hold or clear the current cart before editing a bill" }
            val items = saleDao.getSaleItemsList(session.companyId, sale.id)
            val allocatedDiscount = com.kadaikutty.pos.core.common.CheckoutMath.allocate(sale.discountMinorUnits.coerceAtMost(items.sumOf { it.lineTotalMinorUnits }), items.map { it.lineTotalMinorUnits })
            val allProds = masterDao.products(session.companyId, "").first()
            _selectedCustomerId.value = sale.customerId
            _lines.value = items.mapIndexed { index, item ->
                val prod = allProds.find { it.id == item.productId }
                val prodName = item.productName ?: prod?.name ?: "Product"
                val uType = item.unitType ?: prod?.unitType ?: "PIECE"
                SaleLine(
                    productId = item.productId,
                    productName = prodName,
                    quantity = item.quantity,
                    unitPrice = Money(item.unitPriceMinorUnits),
                    unitType = uType,
                    discount = Money(item.discountMinorUnits + allocatedDiscount[index])
                )
            }
            _discountInput.value = ""
            editingSaleId = sale.id
            editingRevision = sale.revision
            checkoutId = newRecordId()
            originalQuantities = items.associate { it.productId to it.quantity }
            saveDraftToDb()
            onSuccess()
        }
    }

    fun shareBill(billNumber: String, isWhatsapp: Boolean) {
        viewModelScope.launch(errors) {
            val session = sessionStore.activeSession.first() ?: return@launch
            val companyId = session.companyId
            val sale = saleDao.getSaleByBillNumber(companyId, billNumber) ?: return@launch
            val items = saleDao.getSaleItems(companyId, sale.id).first()
            val productsList = masterDao.products(companyId, "").first()
            val productsMap = productsList.associateBy { it.id }
            
            val shopName = appPreferences.shopName.first()
            val ownerName = appPreferences.ownerName.first()
            val gstNumber = appPreferences.gstNumber.first()
            val shopAddress = appPreferences.shopAddress.first()
            val shopPhone = appPreferences.shopPhone.first()
            
            val customerName = when {
                sale.customerId == null -> "Walk-in Customer"
                sale.customerId == "online" -> "Online Customer"
                else -> masterDao.getCustomerById(companyId, sale.customerId)?.name ?: "Walk-in Customer"
            }
            
            val shopEmail = appPreferences.shopEmail.first()
            val shopLogoPath = appPreferences.shopLogoPath.first()
            val cashierName = session.displayName

            val pdfBytes = shareManager.generatePdfInvoice(
                sale = sale,
                items = items,
                productsMap = productsMap,
                customerName = customerName,
                shopName = shopName,
                ownerName = ownerName,
                gstNumber = gstNumber,
                shopAddress = shopAddress,
                shopPhone = shopPhone,
                shopEmail = shopEmail,
                cashierName = cashierName,
                shopLogoPath = shopLogoPath
            )
            
            val filename = "invoice_${sale.billNumber.replace("-", "_")}.pdf"
            if (isWhatsapp) {
                shareManager.shareFile(pdfBytes, filename, "application/pdf", ShareManager.PACKAGE_WHATSAPP)
            } else {
                shareManager.shareFile(pdfBytes, filename, "application/pdf", null)
            }
        }
    }

    fun printBill(context: android.content.Context, billNumber: String) {
        viewModelScope.launch(errors) {
            val macAddress = appPreferences.printerDeviceId.first()
            if (macAddress.isNullOrBlank()) {
                // Returning silently made Print Bill look broken: the sale is saved, the button
                // does nothing and nothing says why.
                _operationError.value = "No printer configured. Add one in Settings to print bills."
                return@launch
            }

            val session = sessionStore.activeSession.first() ?: return@launch
            val companyId = session.companyId
            val sale = saleDao.getSaleByBillNumber(companyId, billNumber) ?: return@launch
            val items = saleDao.getSaleItems(companyId, sale.id).first()
            val productsList = masterDao.products(companyId, "").first()
            val productsMap = productsList.associateBy { it.id }
            
            val shopName = appPreferences.shopName.first().ifBlank { "Store" }
            val shopAddress = appPreferences.shopAddress.first()
            
            val customerName = if (sale.customerId == null) {
                "Walk-in Customer"
            } else if (sale.customerId == "online") {
                "Online Customer"
            } else {
                masterDao.getCustomerById(companyId, sale.customerId)?.name ?: "Walk-in Customer"
            }

            val printItems = items.map { item ->
                // Unit and name as saved on the bill, so a later product edit can't change a reprint.
                val p = productsMap[item.productId]
                val qtyStr = com.kadaikutty.pos.feature.stock.domain.storageUnitsToTyped(item.quantity, item.unitType ?: p?.unitType)
                com.kadaikutty.pos.core.printer.domain.BillItem(
                    name = item.productName ?: p?.name ?: "Unknown",
                    quantityText = qtyStr,
                    price = Money(item.unitPriceMinorUnits).toString(),
                    total = Money(item.lineTotalMinorUnits).toString()
                )
            }

            val subtotal = Money(sale.totalMinorUnits + sale.discountMinorUnits).toString()
            val discount = Money(sale.discountMinorUnits).toString()
            val grandTotal = Money(sale.totalMinorUnits).toString()
            val dateStr = java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(sale.createdAtEpochMs))

            val document = com.kadaikutty.pos.core.printer.domain.BillReceipt.document(
                shopName = shopName,
                shopAddress = shopAddress,
                billNumber = billNumber,
                date = dateStr,
                customerName = customerName,
                items = printItems,
                subtotal = subtotal,
                discount = discount,
                grandTotal = grandTotal,
                paperWidth = appPreferences.printerPaperWidth.first()
            )
            val printerType = com.kadaikutty.pos.core.printer.data.PrinterManager.PrinterType
                .fromSetting(appPreferences.printerType.first())
            val result = printerManager.printJob(printerType, macAddress, document)
            if (result is com.kadaikutty.pos.core.printer.domain.PrinterResult.Failure) {
                android.widget.Toast.makeText(context, "Bill saved. Printing failed: ${result.error.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}
