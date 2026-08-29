package com.kadaikutty.pos.feature.billing.presentation

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
import kotlinx.coroutines.flow.flowOf

@HiltViewModel
class BillingViewModel @Inject constructor(
    private val database: BillingDatabase,
    private val saleRepository: SaleRepository,
    private val shareManager: ShareManager,
    private val appPreferences: AppPreferences,
    private val sessionStore: SessionStore,
    private val syncManager: SyncManager,
    private val syncScheduler: com.kadaikutty.pos.core.sync.SyncScheduler
) : ViewModel() {

    fun forceSync() {
        syncScheduler.request()
    }

    private val masterDao = database.masterDao()
    private val saleDao = database.saleDao()
    private val purchaseDao = database.purchaseDao()
    private val draftCartDao = database.draftCartDao()

    private val stockBalances = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            purchaseDao.getStockBalances(companyId).map { list ->
                list.associate { it.productId to it.currentStock }
            }
        }

    init {
        viewModelScope.launch {
            val session = sessionStore.activeSession.first()
            val companyId = session?.companyId
            if (companyId != null) {
                val savedDrafts = draftCartDao.getDraftCart(companyId).first()
                if (savedDrafts.isNotEmpty()) {
                    _lines.value = savedDrafts.map { 
                        SaleLine(it.productId, it.productName, it.quantity, Money(it.unitPriceMinorUnits), it.unitType) 
                    }
                }
            }
        }
    }

    private fun saveDraftToDb() {
        viewModelScope.launch {
            val companyId = sessionStore.activeSession.first()?.companyId ?: return@launch
            draftCartDao.clearCart(companyId)
            if (_lines.value.isNotEmpty()) {
                val entities = _lines.value.map { line ->
                    com.kadaikutty.pos.feature.billing.data.DraftCartItemEntity(
                        id = com.kadaikutty.pos.core.common.newRecordId(),
                        companyId = companyId,
                        productId = line.productId,
                        productName = line.productName,
                        quantity = line.quantity,
                        unitPriceMinorUnits = line.unitPrice.minorUnits,
                        unitType = line.unitType
                    )
                }
                draftCartDao.insertItems(entities)
            }
        }
    }

    private val products = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            masterDao.products(companyId, "")
        }

    private val customers = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            masterDao.customers(companyId, "")
        }

    private val sales = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            saleDao.getSales(companyId)
        }

    private val _selectedCustomerId = MutableStateFlow<String?>(null)

    private val selectedCustomerCreditBalance = combine(sessionStore.activeSession, _selectedCustomerId) { session, customerId ->
        session to customerId
    }.flatMapLatest { (session, customerId) ->
        if (session == null || customerId.isNullOrBlank() || customerId == "online") {
            flowOf(0L)
        } else {
            masterDao.getCustomerCreditBalance(session.companyId, customerId).map { it ?: 0L }
        }
    }

    private val _lines = MutableStateFlow<List<SaleLine>>(emptyList())
    
    val uiState: StateFlow<BillingUiState> = combine(
        combine(products, customers, sales, stockBalances) { p, c, s, st -> 
            BillingUiState(products = p, customers = c, sales = s, stockBalances = st) 
        },
        combine(_lines, _selectedCustomerId, selectedCustomerCreditBalance) { l, cid, credit -> 
            Triple(l, cid, credit) 
        }
    ) { state1, state2 ->
        state1.copy(
            lines = state2.first,
            selectedCustomerId = state2.second,
            selectedCustomerCreditBalance = state2.third,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BillingUiState(isLoading = true))

    fun setCustomer(customerId: String?) {
        _selectedCustomerId.value = customerId
    }

    fun addQuickCustomer(
        name: String,
        phone: String?,
        address: String?,
        openingDueMinorUnits: Long,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
                val customerId = newRecordId()
                val customer = CustomerEntity(
                    id = customerId,
                    companyId = session.companyId,
                    name = name,
                    phone = phone?.trim()?.takeIf { it.isNotBlank() },
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

    fun addLine(productId: String, productName: String, quantity: Long, unitPrice: Money, unitType: String) {
        val current = _lines.value.toMutableList()
        val index = current.indexOfFirst { it.productId == productId }
        if (index >= 0) {
            val line = current[index]
            current[index] = line.copy(quantity = line.quantity + quantity)
        } else {
            current.add(SaleLine(productId, productName, quantity, unitPrice, unitType))
        }
        _lines.value = current
        saveDraftToDb()
    }

    fun removeLine(productId: String) {
        _lines.value = _lines.value.filterNot { it.productId == productId }
        saveDraftToDb()
    }

    fun updateQuantity(productId: String, newQty: Long) {
        if (newQty <= 0) {
            removeLine(productId)
            return
        }
        _lines.value = _lines.value.map {
            if (it.productId == productId) it.copy(quantity = newQty) else it
        }
        saveDraftToDb()
    }

    fun clearDraft() {
        _lines.value = emptyList()
        _selectedCustomerId.value = null
        saveDraftToDb()
    }

    fun onBarcodeScanned(barcode: String, onProductFound: ((ProductEntity) -> Unit)? = null, onProductNotFound: () -> Unit) {
        val product = uiState.value.products.find { it.barcode == barcode }
        if (product != null) {
            val quantity = if (product.unitType == "KG" || product.unitType == "LITER") 1000L else 1L
            addLine(product.id, product.name, quantity, Money(product.salePriceMinorUnits), product.unitType)
            onProductFound?.invoke(product)
        } else {
            onProductNotFound()
        }
    }

    fun getInsufficientStockItems(selectedLines: List<SaleLine>? = null): List<String> {
        val currentBalances = uiState.value.stockBalances
        val linesToCheck = selectedLines ?: _lines.value
        val outOfStockNames = mutableListOf<String>()
        for (line in linesToCheck) {
            val available = currentBalances[line.productId] ?: 0L
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
        viewModelScope.launch {
            if (_lines.value.isEmpty()) {
                onError(Exception("Cannot save empty sale bill"))
                return@launch
            }
            val draft = SaleDraft(
                lines = _lines.value, 
                customerId = _selectedCustomerId.value, 
                paymentMode = paymentMode, 
                paidCash = paidCash, 
                paidUpi = paidUpi, 
                creditApplied = creditApplied,
                globalDiscount = globalDiscount
            )
            when (val result = saleRepository.save(draft)) {
                is AppResult.Success -> {
                    val billNum = result.value
                    settlePreviousCredit(sessionStore.activeSession.first(), _selectedCustomerId.value, settlePreviousCreditMinorUnits, billNum)
                    
                    // Auto-print check
                    if (context != null && appPreferences.autoPrintReceipt.first()) {
                        printBill(context, billNum)
                    }

                    clearDraft()
                    onSuccess(billNum)
                }
                is AppResult.Failure -> {
                    onError(Exception(result.error.userMessage))
                }
            }
        }
    }

    private suspend fun settlePreviousCredit(session: com.kadaikutty.pos.core.auth.Session?, custId: String?, amount: Long, billNum: String) {
        if (amount > 0L && session != null && !custId.isNullOrBlank() && custId != "online") {
            val creditSettlement = CustomerCreditEntity(
                id = newRecordId(),
                companyId = session.companyId,
                customerId = custId,
                amountMinorUnits = -amount,
                reason = "Previous due settled in Bill $billNum",
                dateEpochMs = System.currentTimeMillis(),
                syncStatus = SyncStatus.LOCAL_ONLY
            )
            masterDao.insertCustomerCredit(creditSettlement)
            syncManager.enqueueCustomerCredit(creditSettlement, "INSERT")
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
        viewModelScope.launch {
            val selectedLines = _lines.value.filter { it.productId in selectedProductIds }
            if (selectedLines.isEmpty()) {
                onError(Exception("No items selected for split checkout"))
                return@launch
            }
            val draft = SaleDraft(
                lines = selectedLines, 
                customerId = _selectedCustomerId.value, 
                paymentMode = paymentMode, 
                paidCash = paidCash, 
                paidUpi = paidUpi, 
                creditApplied = creditApplied,
                globalDiscount = globalDiscount
            )
            when (val result = saleRepository.save(draft)) {
                is AppResult.Success -> {
                    val billNum = result.value
                    settlePreviousCredit(sessionStore.activeSession.first(), _selectedCustomerId.value, settlePreviousCreditMinorUnits, billNum)
                    _lines.value = _lines.value.filterNot { it.productId in selectedProductIds }
                    saveDraftToDb()
                    onSuccess(billNum)
                }
                is AppResult.Failure -> {
                    onError(Exception(result.error.userMessage))
                }
            }
        }
    }

    fun deleteSale(saleId: String, billNumber: String, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            when (val result = saleRepository.deleteSale(saleId, billNumber)) {
                is AppResult.Success -> onSuccess()
                is AppResult.Failure -> onError(Exception(result.error.userMessage))
            }
        }
    }

    fun loadSaleForEditing(sale: SaleEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val session = sessionStore.activeSession.first() ?: return@launch
            val items = saleDao.getSaleItemsList(session.companyId, sale.id)
            val allProds = masterDao.products(session.companyId, "").first()
            _selectedCustomerId.value = sale.customerId
            _lines.value = items.map { item ->
                val prod = allProds.find { it.id == item.productId }
                val prodName = prod?.name ?: "Product"
                val uType = prod?.unitType ?: "PIECE"
                SaleLine(
                    productId = item.productId,
                    productName = prodName,
                    quantity = item.quantity,
                    unitPrice = Money(item.unitPriceMinorUnits),
                    unitType = uType
                )
            }
            saleDao.deleteSaleCascade(session.companyId, sale.id, sale.billNumber)
            saveDraftToDb()
            onSuccess()
        }
    }

    fun shareBill(billNumber: String, isWhatsapp: Boolean) {
        viewModelScope.launch {
            val session = sessionStore.activeSession.first() ?: return@launch
            val companyId = session.companyId
            val saleList = saleDao.getSales(companyId).first()
            val sale = saleList.find { it.billNumber == billNumber } ?: return@launch
            val items = saleDao.getSaleItems(companyId, sale.id).first()
            val productsList = masterDao.products(companyId, "").first()
            val productsMap = productsList.associateBy { it.id }
            
            val shopName = appPreferences.shopName.first()
            val ownerName = appPreferences.ownerName.first()
            val gstNumber = appPreferences.gstNumber.first()
            val shopAddress = appPreferences.shopAddress.first()
            val shopPhone = appPreferences.shopPhone.first()
            
            val customerName = if (sale.customerId == null) {
                "Walk-in Customer"
            } else if (sale.customerId == "online") {
                "Online Customer"
            } else {
                masterDao.getCustomerById(companyId, sale.customerId)?.name ?: "Walk-in Customer"
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
        viewModelScope.launch {
            val macAddress = appPreferences.printerDeviceId.first()
            if (macAddress.isNullOrBlank()) return@launch // Printer not configured

            val session = sessionStore.activeSession.first() ?: return@launch
            val companyId = session.companyId
            val saleList = saleDao.getSales(companyId).first()
            val sale = saleList.find { it.billNumber == billNumber } ?: return@launch
            val items = saleDao.getSaleItems(companyId, sale.id).first()
            val productsList = masterDao.products(companyId, "").first()
            val productsMap = productsList.associateBy { it.id }
            
            val shopName = appPreferences.shopName.first().ifBlank { "My Shop" }
            val shopAddress = appPreferences.shopAddress.first()
            
            val customerName = if (sale.customerId == null) {
                "Walk-in Customer"
            } else if (sale.customerId == "online") {
                "Online Customer"
            } else {
                masterDao.getCustomerById(companyId, sale.customerId)?.name ?: "Walk-in Customer"
            }

            val printItems = items.map { item ->
                val p = productsMap[item.productId]
                val qtyStr = if (p?.unitType == "KG" || p?.unitType == "LITER") {
                    String.format("%.3f", item.quantity / 1000f)
                } else {
                    item.quantity.toString()
                }
                com.kadaikutty.pos.core.hardware.PrintItem(
                    name = p?.name ?: "Unknown",
                    qty = qtyStr,
                    price = Money(item.unitPriceMinorUnits).toString(),
                    total = Money(item.unitPriceMinorUnits * item.quantity).toString()
                )
            }

            val subtotal = Money(sale.totalMinorUnits + sale.discountMinorUnits).toString()
            val discount = Money(sale.discountMinorUnits).toString()
            val grandTotal = Money(sale.totalMinorUnits).toString()
            val dateStr = java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(sale.createdAtEpochMs))

            com.kadaikutty.pos.core.hardware.PrinterService.printReceipt(
                context = context,
                macAddress = macAddress,
                shopName = shopName,
                shopAddress = shopAddress,
                billNumber = billNumber,
                date = dateStr,
                customerName = customerName,
                items = printItems,
                subtotal = subtotal,
                discount = discount,
                grandTotal = grandTotal
            )
        }
    }
}
