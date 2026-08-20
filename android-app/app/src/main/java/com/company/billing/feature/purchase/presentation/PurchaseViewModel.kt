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
import com.company.billing.feature.masters.data.CategoryEntity
import com.company.billing.feature.purchase.data.PurchaseEntity
import com.company.billing.feature.stock.domain.ProductStock
import com.company.billing.core.preferences.AppPreferences
import com.google.ai.client.generativeai.GenerativeModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import com.company.billing.core.auth.SessionStore
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

    val products: StateFlow<List<ProductEntity>> = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            masterDao.products(companyId, "")
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val suppliers: StateFlow<List<SupplierEntity>> = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            masterDao.suppliers(companyId, "")
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val purchases: StateFlow<List<PurchaseEntity>> = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            purchaseDao.getPurchases(companyId)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val stocks: StateFlow<List<ProductStock>> = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            purchaseDao.getStockBalances(companyId)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val geminiApiKey: StateFlow<String?> = appPreferences.geminiApi
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

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
                val source = android.graphics.ImageDecoder.createSource(contentResolver, imageUri)
                val bitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
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
                        syncStatus = com.company.billing.core.sync.SyncStatus.PENDING
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
                        syncStatus = com.company.billing.core.sync.SyncStatus.PENDING
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
                            syncStatus = com.company.billing.core.sync.SyncStatus.PENDING
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
