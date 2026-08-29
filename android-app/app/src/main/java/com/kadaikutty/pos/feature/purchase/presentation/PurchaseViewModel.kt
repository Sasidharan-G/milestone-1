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
import com.kadaikutty.pos.feature.masters.data.CategoryEntity
import com.kadaikutty.pos.feature.purchase.data.PurchaseEntity
import com.kadaikutty.pos.feature.purchase.data.PurchaseItemEntity
import com.kadaikutty.pos.feature.stock.domain.ProductStock
import com.kadaikutty.pos.core.preferences.AppPreferences
import com.google.ai.client.generativeai.GenerativeModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import com.kadaikutty.pos.core.auth.SessionStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject

data class ParsedInvoice(
    val supplierName: String,
    val items: List<ParsedInvoiceItem>
)

data class ParsedInvoiceItem(
    val productName: String,
    val quantity: Long,
    val unitPrice: Double
)

@HiltViewModel
class PurchaseViewModel @Inject constructor(
    private val database: BillingDatabase,
    private val purchaseRepository: PurchaseRepository,
    private val appPreferences: AppPreferences,
    private val sessionStore: SessionStore
) : ViewModel() {

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

    private val purchases = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            purchaseRepository.getPurchases(companyId)
        }

    private val stocks = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            purchaseRepository.getStockBalances(companyId)
        }

    val geminiApiKey: StateFlow<String?> = appPreferences.geminiApi
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _selectedSupplierId = MutableStateFlow<String?>(null)

    private val _lines = MutableStateFlow<List<PurchaseLine>>(emptyList())
    
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
        _selectedSupplierId.value = supplierId
    }

    fun addLine(productId: String, quantity: Long, unitCost: Money, supplierId: String? = null) {
        val targetSupplierId = supplierId ?: _selectedSupplierId.value
        val current = _lines.value.toMutableList()
        val index = current.indexOfFirst { it.productId == productId && it.supplierId == targetSupplierId }
        if (index >= 0) {
            val line = current[index]
            current[index] = line.copy(quantity = line.quantity + quantity)
        } else {
            current.add(PurchaseLine(productId, quantity, unitCost, targetSupplierId))
        }
        _lines.value = current
    }

    fun removeLine(productId: String, supplierId: String? = null) {
        _lines.value = _lines.value.filterNot { 
            if (supplierId != null) it.productId == productId && it.supplierId == supplierId 
            else it.productId == productId 
        }
    }

    fun updateQuantity(productId: String, newQty: Long, supplierId: String? = null) {
        if (newQty <= 0) {
            removeLine(productId, supplierId)
            return
        }
        _lines.value = _lines.value.map {
            if (it.productId == productId && (supplierId == null || it.supplierId == supplierId)) {
                it.copy(quantity = newQty)
            } else {
                it
            }
        }
    }

    fun clearDraft() {
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
        viewModelScope.launch {
            if (_lines.value.isEmpty()) {
                onError(Exception("Cannot save empty purchase bill"))
                return@launch
            }

            val linesBySupplier = _lines.value.groupBy { it.supplierId ?: _selectedSupplierId.value }
            val createdOrderIds = mutableListOf<String>()

            for ((suppId, supplierLines) in linesBySupplier) {
                if (suppId.isNullOrBlank()) {
                    onError(Exception("Supplier is missing for some items in the purchase cart. Please ensure each item has a supplier."))
                    return@launch
                }
                val supplierTotal = supplierLines.fold(Money.Zero) { sum, line -> sum + line.total }
                
                val (sPaidCash, sPaidUpi, sCredit) = when (paymentMode) {
                    "CASH" -> Triple(supplierTotal, Money.Zero, Money.Zero)
                    "UPI" -> Triple(Money.Zero, supplierTotal, Money.Zero)
                    "CREDIT" -> Triple(Money.Zero, Money.Zero, supplierTotal)
                    else -> {
                        val grandTotal = _lines.value.fold(Money.Zero) { sum, line -> sum + line.total }
                        val ratio = if (grandTotal.minorUnits > 0) supplierTotal.minorUnits.toDouble() / grandTotal.minorUnits else 1.0
                        Triple(
                            Money((paidCash.minorUnits * ratio).toLong()),
                            Money((paidUpi.minorUnits * ratio).toLong()),
                            Money((creditApplied.minorUnits * ratio).toLong())
                        )
                    }
                }

                val draft = PurchaseDraft(
                    supplierId = suppId, 
                    lines = supplierLines,
                    invoiceNumber = invoiceNumber,
                    notes = notes,
                    paymentMode = paymentMode,
                    paidCash = sPaidCash,
                    paidUpi = sPaidUpi,
                    creditApplied = sCredit
                )
                when (val result = purchaseRepository.save(draft)) {
                    is AppResult.Success -> {
                        createdOrderIds.add(result.value)
                    }
                    is AppResult.Failure -> {
                        onError(Exception(result.error.userMessage))
                        return@launch
                    }
                }
            }

            clearDraft()
            onSuccess(createdOrderIds.joinToString(", "))
        }
    }

    fun deletePurchase(
        purchase: PurchaseEntity,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        viewModelScope.launch {
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
        viewModelScope.launch {
            val items = purchaseRepository.getPurchaseItemsList(purchase.id)
            if (items.isNotEmpty()) {
                val orderOrInv = purchase.invoiceNumber ?: purchase.orderNumber ?: purchase.id
                // Delete old purchase cascade so re-saving won't duplicate stock/credit
                purchaseRepository.deletePurchase(purchase.id, orderOrInv)

                _selectedSupplierId.value = purchase.supplierId
                _lines.value = items.map {
                    PurchaseLine(
                        productId = it.productId,
                        quantity = it.quantity,
                        unitValue = Money(it.unitValueMinorUnits),
                        supplierId = purchase.supplierId
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

    fun parseInvoiceImage(
        context: android.content.Context,
        imageUri: android.net.Uri,
        apiKey: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // 1. Load image Bitmap
                val contentResolver = context.contentResolver
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    val source = android.graphics.ImageDecoder.createSource(contentResolver, imageUri)
                    android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(contentResolver, imageUri).copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                }

                val parsedInvoice = if (!apiKey.isNullOrBlank()) {
                    callGeminiApi(apiKey, bitmap)
                } else {
                    delay(1500) // Simulate processing delay
                    simulateInvoiceParse()
                }

                if (parsedInvoice == null) {
                    onError("Failed to parse invoice details. Please double-check formatting or key.")
                    return@launch
                }

                // 2. Map or insert Supplier
                val session = sessionStore.activeSession.first() ?: return@launch
                val companyId = session.companyId
                
                val allSuppliers = masterDao.suppliers(companyId, "").first()
                var supplier = allSuppliers.find { it.name.equals(parsedInvoice.supplierName, ignoreCase = true) }
                if (supplier == null) {
                    val newSupplier = SupplierEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        companyId = companyId,
                        name = parsedInvoice.supplierName,
                        createdAtEpochMs = System.currentTimeMillis(),
                        updatedAtEpochMs = System.currentTimeMillis(),
                        syncStatus = com.kadaikutty.pos.core.sync.SyncStatus.PENDING
                    )
                    masterDao.insertSupplier(newSupplier)
                    supplier = newSupplier
                }
                _selectedSupplierId.value = supplier.id

                // 3. Map or insert Products
                val allProducts = masterDao.products(companyId, "").first()
                val allCategories = masterDao.categories(companyId, "").first()

                var defaultCategoryId = allCategories.firstOrNull()?.id
                if (defaultCategoryId == null) {
                    val newCategory = CategoryEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        companyId = companyId,
                        name = "General",
                        createdAtEpochMs = System.currentTimeMillis(),
                        updatedAtEpochMs = System.currentTimeMillis(),
                        syncStatus = com.kadaikutty.pos.core.sync.SyncStatus.PENDING
                    )
                    masterDao.insertCategory(newCategory)
                    defaultCategoryId = newCategory.id
                }

                val draftLines = mutableListOf<PurchaseLine>()
                for (item in parsedInvoice.items) {
                    var product = allProducts.find { it.name.equals(item.productName, ignoreCase = true) }
                    if (product == null) {
                        val newProduct = ProductEntity(
                            id = java.util.UUID.randomUUID().toString(),
                            companyId = companyId,
                            name = item.productName,
                            categoryId = defaultCategoryId!!,
                            createdAtEpochMs = System.currentTimeMillis(),
                            updatedAtEpochMs = System.currentTimeMillis(),
                            syncStatus = com.kadaikutty.pos.core.sync.SyncStatus.PENDING
                        )
                        masterDao.insertProduct(newProduct)
                        product = newProduct
                    }
                    val unitCostMinor = (item.unitPrice * 100).toLong()
                    draftLines.add(PurchaseLine(product.id, item.quantity, Money(unitCostMinor)))
                }

                _lines.value = draftLines
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Unknown scanning error")
            }
        }
    }

    private suspend fun callGeminiApi(apiKey: String, bitmap: android.graphics.Bitmap): ParsedInvoice? {
        return try {
            val model = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey
            )
            val response = model.generateContent(
                com.google.ai.client.generativeai.type.content {
                    image(bitmap)
                    text("""
                        Analyze this invoice image and extract the following details.
                        Output ONLY a raw, single-line JSON block matching this structure (do not include markdown block quotes like ```json or prefixing):
                        {"supplierName": "Supplier Name", "items": [{"productName": "Product Name", "quantity": 10, "unitPrice": 15.50}]}
                    """.trimIndent())
                }
            )
            val cleanJson = response.text?.trim() ?: return null
            val jsonToParse = cleanJson.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            parseJsonInvoice(jsonToParse)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun simulateInvoiceParse(): ParsedInvoice {
        return ParsedInvoice(
            supplierName = "AI Smart Wholesalers Ltd",
            items = listOf(
                ParsedInvoiceItem("Wireless Keyboard", 15, 25.00),
                ParsedInvoiceItem("Ergonomic Mouse", 20, 18.50),
                ParsedInvoiceItem("HDMI Cable 2m", 50, 4.99)
            )
        )
    }

    private fun parseJsonInvoice(jsonStr: String): ParsedInvoice? {
        return try {
            val obj = org.json.JSONObject(jsonStr)
            val supplierName = obj.optString("supplierName", "AI Supplier")
            val itemsArray = obj.getJSONArray("items")
            val itemsList = mutableListOf<ParsedInvoiceItem>()
            for (i in 0 until itemsArray.length()) {
                val itemObj = itemsArray.getJSONObject(i)
                itemsList.add(
                    ParsedInvoiceItem(
                        productName = itemObj.getString("productName"),
                        quantity = itemObj.getLong("quantity"),
                        unitPrice = itemObj.getDouble("unitPrice")
                    )
                )
            }
            ParsedInvoice(supplierName, itemsList)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
