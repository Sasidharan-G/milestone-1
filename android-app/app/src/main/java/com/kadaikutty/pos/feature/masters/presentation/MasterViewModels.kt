@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.kadaikutty.pos.feature.masters.presentation

import androidx.room.withTransaction
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.sync.SyncStatus
import com.kadaikutty.pos.core.sync.SyncManager
import com.kadaikutty.pos.core.common.CheckoutMath
import com.kadaikutty.pos.core.common.newRecordId
import com.kadaikutty.pos.core.common.InputRules
import com.kadaikutty.pos.feature.masters.data.CategoryEntity
import com.kadaikutty.pos.feature.masters.data.ProductEntity
import com.kadaikutty.pos.feature.masters.data.CustomerEntity
import com.kadaikutty.pos.feature.masters.data.SupplierEntity
import com.kadaikutty.pos.feature.masters.data.ExpenseEntity
import com.kadaikutty.pos.feature.masters.data.CustomerCreditEntity
import com.kadaikutty.pos.feature.masters.data.SupplierCreditEntity
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
import com.kadaikutty.pos.core.auth.SessionStore
import com.kadaikutty.pos.core.security.SyncWritePolicy
import kotlinx.coroutines.flow.map
import com.kadaikutty.pos.core.common.ProductImageStore
import dagger.hilt.android.qualifiers.ApplicationContext


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
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            onError(IllegalArgumentException("Category name cannot be blank"))
            return
        }
        viewModelScope.launch {
            try {
                SyncWritePolicy.requireCreate(sessionStore.activeSession.first(), "Category")
                val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
                
                // Duplicate prevention: check if category with same name exists
                val existing = dao.categories(session.companyId, "").first()
                val duplicate = existing.firstOrNull { it.name.trim().equals(cleanName, ignoreCase = true) }
                if (duplicate != null) {
                    onError(IllegalArgumentException("Category '$cleanName' already exists"))
                    return@launch
                }

                val category = CategoryEntity(
                    id = newRecordId(),
                    companyId = session.companyId,
                    name = cleanName,
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
        val cleanName = newName.trim()
        if (cleanName.isBlank()) {
            onError(IllegalArgumentException("Category name cannot be blank"))
            return
        }
        viewModelScope.launch {
            try {
                SyncWritePolicy.requireEdit(sessionStore.activeSession.first(), "Category")
                val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
                
                // Duplicate prevention: check if another category has the same name
                val existing = dao.categories(session.companyId, "").first()
                val duplicate = existing.firstOrNull { it.id != category.id && it.name.trim().equals(cleanName, ignoreCase = true) }
                if (duplicate != null) {
                    onError(IllegalArgumentException("Another category with name '$cleanName' already exists"))
                    return@launch
                }

                val updated = category.copy(
                    name = cleanName,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
                dao.updateCategory(updated)
                
                val updates = mutableMapOf<String, Any?>()
                if (category.name != cleanName) updates["name"] = cleanName
                if (updates.isNotEmpty()) {
                    syncManager.enqueuePartialUpdate("Category", category.id, updates)
                }
                
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun deleteCategory(category: CategoryEntity, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                SyncWritePolicy.requireEdit(sessionStore.activeSession.first(), "Category")
                // Same rule sync uses (ConflictResolver.isInUse): a delete that would orphan
                // products is refused here, and undone if it arrives from another device.
                require(dao.productCountInCategory(category.companyId, category.id) == 0) { "Move or delete the products in this category first." }
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
    private val sessionStore: SessionStore,
    private val syncScheduler: com.kadaikutty.pos.core.sync.SyncScheduler,
    private val appPreferences: com.kadaikutty.pos.core.preferences.AppPreferences,
    @ApplicationContext private val appContext: android.content.Context
) : ViewModel() {
    private val dao = database.masterDao()
    private val reportDao = database.reportDao()
    private val searchQuery = MutableStateFlow("")

    val productGridView: StateFlow<Boolean> = appPreferences.productGridView.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    val lowStockProducts: StateFlow<List<com.kadaikutty.pos.core.database.LowStockRow>> = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            if (companyId.isNotEmpty()) reportDao.getLowStockProducts(companyId)
            else kotlinx.coroutines.flow.flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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
        barcode: String?,
        minStockLevel: Double,
        openingStock: Double = 0.0,
        /** A photo already compressed to a temp file (see ProductImageStore), waiting for the new product's id. */
        pendingImagePath: String? = null,
        gstRateBps: Int = 0,
        hsnCode: String? = null,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val cleanName = name.trim()
        if (gstRateBps !in com.kadaikutty.pos.core.common.GstMath.RATES_BPS) {
            onError(IllegalArgumentException("Select a valid GST rate"))
            return
        }
        if (cleanName.isBlank()) {
            onError(IllegalArgumentException("Product name cannot be blank"))
            return
        }
        // Same guard updateProduct has. Without it a negative price saves here and only fails
        // later at billing, with a message about exceeding the supported limit.
        if (purchasePriceMinorUnits < 0 || salePriceMinorUnits < 0) {
            onError(IllegalArgumentException("Prices cannot be negative"))
            return
        }
        val cleanBarcode = barcode?.trim()?.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            try {
                SyncWritePolicy.requireCreate(sessionStore.activeSession.first(), "Product")
                val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
                
                // Duplicate check
                val existing = dao.getAllProducts(session.companyId)
                if (existing.any { it.name.trim().equals(cleanName, ignoreCase = true) }) {
                    onError(IllegalArgumentException("Product '$cleanName' already exists"))
                    return@launch
                }
                if (cleanBarcode != null && existing.any { !it.barcode.isNullOrBlank() && it.barcode.trim() == cleanBarcode }) {
                    onError(IllegalArgumentException("Product with barcode '$cleanBarcode' already exists"))
                    return@launch
                }

                val finalCategoryId = if (categoryId.isNotBlank()) {
                    categoryId
                } else {
                    val existingCats = dao.categories(session.companyId, "").first()
                    if (existingCats.isNotEmpty()) {
                        existingCats.first().id
                    } else {
                        val newCat = CategoryEntity(
                            id = newRecordId(),
                            companyId = session.companyId,
                            name = "General",
                            createdAtEpochMs = System.currentTimeMillis(),
                            updatedAtEpochMs = System.currentTimeMillis(),
                            syncStatus = SyncStatus.LOCAL_ONLY
                        )
                        dao.insertCategory(newCat)
                        syncManager.enqueueCategory(newCat, "INSERT")
                        newCat.id
                    }
                }

                val product = ProductEntity(
                    id = newRecordId(),
                    companyId = session.companyId,
                    name = cleanName,
                    categoryId = finalCategoryId,
                    purchasePriceMinorUnits = purchasePriceMinorUnits,
                    salePriceMinorUnits = salePriceMinorUnits,
                    unitType = unitType,
                    barcode = cleanBarcode,
                    minStockLevel = minStockLevel,
                    createdAtEpochMs = System.currentTimeMillis(),
                    updatedAtEpochMs = System.currentTimeMillis(),
                    syncStatus = SyncStatus.LOCAL_ONLY,
                    gstRateBps = gstRateBps,
                    hsnCode = hsnCode?.trim()?.ifBlank { null }
                )
                dao.insertProduct(product)
                syncManager.enqueueProduct(product, "INSERT")
                if (!pendingImagePath.isNullOrBlank()) {
                    val temp = java.io.File(pendingImagePath)
                    if (ProductImageStore.commitTemp(appContext, temp, product.id)) {
                        syncScheduler.requestProductImageUpload(product.id)
                    }
                }
                // Opening stock is only offered to an administrator in the UI, but a brand new product
                // never has a stock history, so this can never step on a movement sync already created.
                val openingUnits = com.kadaikutty.pos.feature.stock.domain.openingStockToStorageUnits(openingStock, unitType)
                if (openingUnits > 0L && session.role in listOf("ADMIN", "SUPER_ADMIN")) {
                    val movement = com.kadaikutty.pos.feature.billing.data.StockMovementEntity(
                        id = newRecordId(), companyId = session.companyId, productId = product.id,
                        quantityDelta = openingUnits, type = "ADJUSTMENT", referenceId = "Opening stock",
                        createdAtEpochMs = System.currentTimeMillis()
                    )
                    database.purchaseDao().insertStockMovements(listOf(movement))
                    syncManager.enqueueStockMovement(movement)
                }
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
        newBarcode: String?,
        newMinStockLevel: Double,
        newGstRateBps: Int = product.gstRateBps,
        newHsnCode: String? = product.hsnCode,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val cleanHsn = newHsnCode?.trim()?.ifBlank { null }
        val cleanName = newName.trim()
        if (cleanName.isBlank()) {
            onError(IllegalArgumentException("Product name cannot be blank"))
            return
        }
        val cleanBarcode = newBarcode?.trim()?.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            try {
                SyncWritePolicy.requireEdit(sessionStore.activeSession.first(), "Product")
                // Duplicate check
                val existing = dao.getAllProducts(product.companyId)
                if (existing.any { it.id != product.id && it.name.trim().equals(cleanName, ignoreCase = true) }) {
                    onError(IllegalArgumentException("Another product with name '$cleanName' already exists"))
                    return@launch
                }
                if (cleanBarcode != null && existing.any { it.id != product.id && !it.barcode.isNullOrBlank() && it.barcode.trim() == cleanBarcode }) {
                    onError(IllegalArgumentException("Another product with barcode '$cleanBarcode' already exists"))
                    return@launch
                }

                require(product.unitType == newUnitType || database.saleDao().movementCount(product.companyId, product.id) == 0) { "Unit cannot be changed after stock transactions. Create a new product." }
                require(newPurchasePriceMinorUnits >= 0 && newSalePriceMinorUnits >= 0) { "Prices cannot be negative" }
                require(newGstRateBps in com.kadaikutty.pos.core.common.GstMath.RATES_BPS) { "Select a valid GST rate" }
                val updated = product.copy(
                    gstRateBps = newGstRateBps,
                    hsnCode = cleanHsn,
                    name = cleanName,
                    categoryId = newCategoryId,
                    purchasePriceMinorUnits = newPurchasePriceMinorUnits,
                    salePriceMinorUnits = newSalePriceMinorUnits,
                    unitType = newUnitType,
                    barcode = cleanBarcode,
                    minStockLevel = newMinStockLevel,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
                dao.updateProduct(updated)
                
                val updates = mutableMapOf<String, Any?>()
                if (product.name != cleanName) updates["name"] = cleanName
                if (product.categoryId != newCategoryId) updates["categoryId"] = newCategoryId
                if (product.purchasePriceMinorUnits != newPurchasePriceMinorUnits) updates["purchasePriceMinorUnits"] = newPurchasePriceMinorUnits
                if (product.salePriceMinorUnits != newSalePriceMinorUnits) updates["salePriceMinorUnits"] = newSalePriceMinorUnits
                if (product.unitType != newUnitType) updates["unitType"] = newUnitType
                if (product.barcode != cleanBarcode) updates["barcode"] = cleanBarcode
                if (product.minStockLevel != newMinStockLevel) updates["minStockLevel"] = newMinStockLevel
                if (product.gstRateBps != newGstRateBps) updates["gstRateBps"] = newGstRateBps
                if (product.hsnCode != cleanHsn) updates["hsnCode"] = cleanHsn
                
                if (updates.isNotEmpty()) {
                    syncManager.enqueuePartialUpdate("Product", product.id, updates)
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    /**
     * Replaces (or adds) this product's photo from a freshly picked/captured [sourceUri]. Runs the
     * decode/compress off the main thread and only enqueues the upload once the file is safely on
     * disk, so this returns quickly regardless of how large the original photo was.
     */
    fun setProductImage(product: ProductEntity, sourceUri: android.net.Uri, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                SyncWritePolicy.requireEdit(sessionStore.activeSession.first(), "Product")
                val saved = ProductImageStore.compressInto(appContext, sourceUri, ProductImageStore.localFile(appContext, product.id))
                if (!saved) {
                    onError(IllegalArgumentException("Could not read that photo. Try another one."))
                    return@launch
                }
                syncScheduler.requestProductImageUpload(product.id)
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun removeProductImage(product: ProductEntity, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                SyncWritePolicy.requireEdit(sessionStore.activeSession.first(), "Product")
                ProductImageStore.delete(appContext, product.id)
                val updated = product.copy(imageUrl = null, updatedAtEpochMs = System.currentTimeMillis())
                dao.updateProduct(updated)
                syncManager.enqueuePartialUpdate("Product", product.id, mapOf("imageUrl" to null))
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    val stockBalances: StateFlow<Map<String, Long>> = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            if (companyId.isNotEmpty()) database.purchaseDao().getStockBalances(companyId)
            else kotlinx.coroutines.flow.flowOf(emptyList())
        }.map { list -> list.associate { it.productId to it.currentStock } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    fun adjustStock(
        product: ProductEntity,
        newQuantity: Long,
        reason: String,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        viewModelScope.launch {
            try {
                SyncWritePolicy.requireEdit(sessionStore.activeSession.first(), "Product")
                val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
                require(session.role in listOf("ADMIN", "SUPER_ADMIN")) { "Only an administrator can adjust stock" }
                require(product.companyId == session.companyId && newQuantity in 0..com.kadaikutty.pos.core.common.CheckoutMath.MAX_QUANTITY) { "Invalid stock adjustment" }
                database.withTransaction {
                val current = database.saleDao().stock(session.companyId, product.id)
                val delta = newQuantity - current
                if (delta == 0L) {
                    return@withTransaction
                }
                val movement = com.kadaikutty.pos.feature.billing.data.StockMovementEntity(
                    id = newRecordId(),
                    companyId = session.companyId,
                    productId = product.id,
                    quantityDelta = delta,
                    type = "ADJUSTMENT",
                    referenceId = reason.ifBlank { "Direct Stock Adjustment" },
                    createdAtEpochMs = System.currentTimeMillis()
                )
                database.purchaseDao().insertStockMovements(listOf(movement))
                syncManager.enqueueStockMovement(movement)
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun deleteProduct(product: ProductEntity, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                SyncWritePolicy.requireEdit(sessionStore.activeSession.first(), "Product")
                val stock = database.saleDao().stock(product.companyId, product.id)
                require(stock == 0L) { "This product still has stock ($stock). Adjust stock to zero before deleting it." }
                require(database.syncIntegrityDao().saleItemCount(product.companyId, product.id) == 0 &&
                    database.syncIntegrityDao().purchaseItemCount(product.companyId, product.id) == 0) {
                    "This product appears on bills or purchases, so it cannot be deleted."
                }
                dao.deleteProduct(product)
                syncManager.enqueueProduct(product, "DELETE")
                ProductImageStore.delete(appContext, product.id)
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun exportSampleProductTemplate(
        uri: android.net.Uri,
        context: android.content.Context,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.bufferedWriter(java.nio.charset.StandardCharsets.UTF_8).use { writer ->
                        writer.write("Product Name,Barcode,Category,Unit,Purchase Price,Selling Price,Min Stock,Opening Stock\n")
                        writer.write("Aashirvaad Superior MP Atta 5kg,8901725131456,Grocery,KG,220.00,265.00,10,40\n")
                        writer.write("Fortune Sunlite Sunflower Oil 1L,8906007280145,Oil & Ghee,LITER,110.00,135.00,15,60\n")
                        writer.write("Tata Salt Iodized 1kg,8904043901005,Grocery,PACK,20.00,28.00,25,100\n")
                        writer.write("Surf Excel Easy Wash Detergent 1kg,8901030384813,Household,PACK,125.00,150.00,10,35\n")
                        writer.write("Britannia Good Day Butter Cookies 100g,8901063012431,Snacks,PIECE,18.00,25.00,30,120\n")
                    }
                }
                onComplete(true, null)
            } catch (e: Exception) {
                onComplete(false, e.message ?: "Failed to export template")
            }
        }
    }

    fun importProductsFromCsv(
        uri: android.net.Uri,
        context: android.content.Context,
        onProgress: (current: Int, totalEstimate: Int) -> Unit,
        onComplete: (ProductImportSummary) -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val session = sessionStore.activeSession.first()
            if (session == null) {
                onComplete(ProductImportSummary(0, 0, 0, 0, "No active session found."))
                return@launch
            }
            val companyId = session.companyId

            try {
                SyncWritePolicy.requireCreate(sessionStore.activeSession.first(), "Product")
                // Pre-cache existing categories
                val categoryMap = mutableMapOf<String, String>()
                dao.categories(companyId, "").first().forEach {
                    categoryMap[it.name.trim().lowercase()] = it.id
                }

                // Ensure "General" category exists
                var generalCatId = categoryMap["general"]
                if (generalCatId == null) {
                    val generalCat = CategoryEntity(
                        id = newRecordId(),
                        companyId = companyId,
                        name = "General",
                        createdAtEpochMs = System.currentTimeMillis(),
                        updatedAtEpochMs = System.currentTimeMillis(),
                        syncStatus = SyncStatus.LOCAL_ONLY
                    )
                    dao.insertCategory(generalCat)
                    syncManager.enqueueCategory(generalCat, "INSERT")
                    categoryMap["general"] = generalCat.id
                    generalCatId = generalCat.id
                }

                // Helpers to normalize names and barcodes for collision checking
                fun cleanName(s: String): String = s.replace("\uFEFF", "").replace("\u200B", "").trim().replace("\\s+".toRegex(), " ").lowercase()
                fun cleanBarcode(s: String?): String? {
                    if (s.isNullOrBlank()) return null
                    var b = s.replace("\uFEFF", "").replace("\u200B", "").trim()
                    if (b.endsWith(".0") || b.endsWith(".00")) b = b.substringBefore(".")
                    return if (b.isBlank()) null else b
                }

                // Pre-cache existing products by Barcode and by Name
                val existingBarcodeMap = mutableMapOf<String, ProductEntity>()
                val existingNameMap = mutableMapOf<String, ProductEntity>()
                dao.getAllProducts(companyId).forEach {
                    cleanBarcode(it.barcode)?.let { b -> existingBarcodeMap[b] = it }
                    cleanName(it.name).let { n -> if (n.isNotBlank()) existingNameMap[n] = it }
                }

                var totalRead = 0
                var importedCount = 0
                var updatedCount = 0
                var skippedCount = 0
                // Opening stock: what is on the shelf now. Without it every imported product has stock 0,
                // which is at or below any minimum level, so every one of them shows as low stock.
                val canSetStock = session.role in listOf("ADMIN", "SUPER_ADMIN")
                val stockBatch = mutableListOf<com.kadaikutty.pos.feature.billing.data.StockMovementEntity>()
                val openingApplied = mutableSetOf<String>()
                var stockSetCount = 0
                var stockKeptCount = 0
                var unitKeptCount = 0
                var unstockedWithMinCount = 0
                var openingGivenButNotAllowed = false
                var zeroPriceCount = 0
                // Capped: a 5,000-row file with a bad column mapping would otherwise build a list as
                // long as the file itself for no benefit past the first handful of examples.
                val rowErrors = mutableListOf<String>()
                val MAX_ROW_ERRORS = 20
                var rowNumber = 0 // 1-based; counts the header line too, so a data row reads the same as in a spreadsheet

                val productBatch = mutableListOf<ProductEntity>()
                val newCategoriesBatch = mutableListOf<CategoryEntity>()

                suspend fun flushStock() {
                    if (stockBatch.isEmpty()) return
                    val movements = stockBatch.toList()
                    stockBatch.clear()
                    database.purchaseDao().insertStockMovements(movements)
                    movements.forEach { syncManager.enqueueStockMovement(it) }
                }

                val fileBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: byteArrayOf()
                val isXlsx = fileBytes.size >= 4 && fileBytes[0] == 0x50.toByte() && fileBytes[1] == 0x4B.toByte()

                val rawRows: List<List<String>> = if (isXlsx) {
                    parseXlsxRows(fileBytes)
                } else {
                    val rows = mutableListOf<List<String>>()
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(java.io.ByteArrayInputStream(fileBytes), java.nio.charset.StandardCharsets.UTF_8))
                    var line: String? = reader.readLine()
                    while (line != null) {
                        val curLine = line
                        line = reader.readLine()
                        if (curLine.isNotBlank()) {
                            rows.add(parseCsvLine(curLine))
                        }
                    }
                    rows
                }

                var isFirstLine = true
                var colName = -1
                var colBarcode = -1
                var colCategory = -1
                var colUnit = -1
                var colPurchase = -1
                var colSale = -1
                var colMinStock = -1
                var colOpening = -1
                // Optional GST columns ("GST %", "Tax", "HSN Code"); checked before barcode/price
                // below, since "HSN Code" contains "code" and "GST Rate" contains "rate".
                var colGst = -1
                var colHsn = -1
                var hasHeader = false

                for (tokens in rawRows) {
                    rowNumber++
                    if (tokens.isEmpty()) continue

                    if (isFirstLine) {
                        isFirstLine = false
                        val lowerTokens = tokens.map { cleanName(it) }
                        val looksLikeHeader = lowerTokens.any { token ->
                            token.contains("name") || token.contains("பெயர்") ||
                            token.contains("barcode") || token.contains("பார்கோடு") ||
                            token.contains("category") || token.contains("பிரிவு") ||
                            token.contains("unit") || token.contains("அலகு") ||
                            token.contains("price") || token.contains("rate") || token.contains("விலை") ||
                            token.contains("stock") || token.contains("alert") || token.contains("இருப்பு")
                        }

                        if (looksLikeHeader) {
                            hasHeader = true
                            for (i in tokens.indices) {
                                val h = cleanName(tokens[i])
                                when {
                                    (h.contains("hsn") || h.contains("sac")) -> colHsn = i
                                    (h.contains("gst") || h.contains("tax") || h.contains("வரி")) -> colGst = i
                                    (h.contains("min") || h.contains("alert") || h.contains("threshold") || h.contains("குறைந்த")) -> colMinStock = i
                                    (h.contains("open") || h.contains("qty") || h.contains("quantity") || h.contains("current") || h.contains("on hand") || h.contains("balance") || h == "stock" || h.contains("இருப்பு")) -> colOpening = i
                                    (h.contains("barcode") || h.contains("பார்கோடு") || h.contains("ean") || h.contains("code")) -> colBarcode = i
                                    (h.contains("cat") || h.contains("பிரிவு")) -> colCategory = i
                                    (h.contains("unit") || h.contains("அலகு")) -> colUnit = i
                                    (h.contains("pur") || h.contains("cost") || h.contains("வாங்கிய")) -> colPurchase = i
                                    (h.contains("sale") || h.contains("sell") || h.contains("mrp") || h.contains("விற்பனை")) -> colSale = i
                                    (h.contains("name") || h.contains("பெயர்") || h.contains("பொருள்") || h.contains("item")) -> colName = i
                                }
                            }
                            continue
                        }
                    }

                    try {
                        val rawName: String
                        val rawBarcode: String?
                        val rawCategory: String
                        val rawUnit: String
                        // Blank when the file has no unit for this row - an existing product then keeps its own.
                        val rawUnitText: String?
                        val rawPurchase: Double
                        val rawSale: Double
                        val rawMinStock: Double
                        val rawOpening: Double

                        if (hasHeader && colName >= 0) {
                            rawName = tokens.getOrNull(colName)?.replace("\uFEFF", "")?.replace("\u200B", "")?.trim() ?: ""
                            rawBarcode = if (colBarcode >= 0) tokens.getOrNull(colBarcode)?.let { cleanBarcode(it) } else null
                            rawCategory = if (colCategory >= 0) tokens.getOrNull(colCategory)?.trim()?.ifBlank { "General" } ?: "General" else "General"
                            rawUnitText = if (colUnit >= 0) tokens.getOrNull(colUnit) else null
                            rawUnit = com.kadaikutty.pos.feature.stock.domain.normalizeUnitType(rawUnitText)
                            rawPurchase = if (colPurchase >= 0) tokens.getOrNull(colPurchase)?.trim()?.replace("₹", "")?.replace(",", "")?.toDoubleOrNull() ?: 0.0 else 0.0
                            rawSale = if (colSale >= 0) tokens.getOrNull(colSale)?.trim()?.replace("₹", "")?.replace(",", "")?.toDoubleOrNull() ?: 0.0 else 0.0
                            rawMinStock = if (colMinStock >= 0) tokens.getOrNull(colMinStock)?.trim()?.toDoubleOrNull() ?: 0.0 else 0.0
                            rawOpening = if (colOpening >= 0) tokens.getOrNull(colOpening)?.trim()?.replace(",", "")?.toDoubleOrNull() ?: 0.0 else 0.0
                        } else if (tokens.size >= 8 && tokens[0].trim().toLongOrNull() != null) {
                            // Layout with index column: #, Name, Category, Purchase, Sale, Unit, Barcode, MinStock
                            rawName = tokens.getOrNull(1)?.replace("\uFEFF", "")?.replace("\u200B", "")?.trim() ?: ""
                            rawCategory = tokens.getOrNull(2)?.trim()?.ifBlank { "General" } ?: "General"
                            rawPurchase = tokens.getOrNull(3)?.trim()?.replace("₹", "")?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                            rawSale = tokens.getOrNull(4)?.trim()?.replace("₹", "")?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                            rawUnitText = tokens.getOrNull(5)
                            rawUnit = com.kadaikutty.pos.feature.stock.domain.normalizeUnitType(rawUnitText)
                            rawBarcode = cleanBarcode(tokens.getOrNull(6))
                            rawMinStock = tokens.getOrNull(7)?.trim()?.toDoubleOrNull() ?: 0.0
                            rawOpening = tokens.getOrNull(8)?.trim()?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                        } else {
                            // Default template layout: Name, Barcode, Category, Unit, Purchase, Sale, MinStock
                            rawName = tokens[0].replace("\uFEFF", "").replace("\u200B", "").trim()
                            rawBarcode = cleanBarcode(tokens.getOrNull(1))
                            rawCategory = tokens.getOrNull(2)?.trim()?.ifBlank { "General" } ?: "General"
                            rawUnitText = tokens.getOrNull(3)
                            rawUnit = com.kadaikutty.pos.feature.stock.domain.normalizeUnitType(rawUnitText)
                            rawPurchase = tokens.getOrNull(4)?.trim()?.replace("₹", "")?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                            rawSale = tokens.getOrNull(5)?.trim()?.replace("₹", "")?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                            rawMinStock = tokens.getOrNull(6)?.trim()?.toDoubleOrNull() ?: 0.0
                            rawOpening = tokens.getOrNull(7)?.trim()?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                        }

                        // null = the file says nothing, so an existing product keeps its own value.
                        val gstText = if (hasHeader && colGst >= 0) tokens.getOrNull(colGst)?.trim()?.removeSuffix("%")?.trim().orEmpty() else ""
                        val rawGstBps: Int? = gstText.toDoubleOrNull()?.let { Math.round(it * 100).toInt() }
                            ?.takeIf { it in com.kadaikutty.pos.core.common.GstMath.RATES_BPS }
                        if (gstText.isNotBlank() && rawGstBps == null && rowErrors.size < MAX_ROW_ERRORS) {
                            rowErrors.add("Row $rowNumber: GST '$gstText' is not a GST slab (0, 0.25, 3, 5, 12, 18, 28) - left unchanged")
                        }
                        val rawHsn: String? = if (hasHeader && colHsn >= 0) tokens.getOrNull(colHsn)?.filter(Char::isLetterOrDigit)?.take(8)?.ifBlank { null } else null

                        if (rawName.isBlank()) {
                            skippedCount++
                            if (rowErrors.size < MAX_ROW_ERRORS) rowErrors.add("Row $rowNumber: no product name")
                            continue
                        }

                        totalRead++

                        val catKey = rawCategory.lowercase()
                        var targetCatId = categoryMap[catKey]
                        if (targetCatId == null) {
                            val newCat = CategoryEntity(
                                id = newRecordId(),
                                companyId = companyId,
                                name = rawCategory,
                                createdAtEpochMs = System.currentTimeMillis(),
                                updatedAtEpochMs = System.currentTimeMillis(),
                                syncStatus = SyncStatus.LOCAL_ONLY
                            )
                            dao.insertCategory(newCat)
                            syncManager.enqueueCategory(newCat, "INSERT")
                            categoryMap[catKey] = newCat.id
                            targetCatId = newCat.id
                        }

                        val purchasePricePaise = CheckoutMath.rupeesToMinorUnits(rawPurchase)
                        val salePricePaise = CheckoutMath.rupeesToMinorUnits(rawSale)

                        val normName = cleanName(rawName)
                        val normBarcode = cleanBarcode(rawBarcode)

                        // Check if existing product by Barcode or by Name
                        val existingProduct = (if (normBarcode != null) existingBarcodeMap[normBarcode] else null) ?: existingNameMap[normName]

                        if (existingProduct != null) {
                            // A blank unit keeps the product's own unit (it used to fall back to PIECE), and a
                            // product with stock history never changes unit: 25000 g of rice would otherwise be
                            // re-read as 25000 pieces and every later sale priced x1000. Same rule as updateProduct.
                            val fileUnit = if (rawUnitText.isNullOrBlank()) existingProduct.unitType else rawUnit
                            val importUnit = if (fileUnit != existingProduct.unitType &&
                                database.saleDao().movementCount(companyId, existingProduct.id) > 0) {
                                unitKeptCount++
                                existingProduct.unitType
                            } else fileUnit
                            val updatedProduct = existingProduct.copy(
                                name = rawName,
                                categoryId = targetCatId ?: generalCatId,
                                purchasePriceMinorUnits = if (purchasePricePaise > 0) purchasePricePaise else existingProduct.purchasePriceMinorUnits,
                                salePriceMinorUnits = if (salePricePaise > 0) salePricePaise else existingProduct.salePriceMinorUnits,
                                unitType = importUnit,
                                barcode = normBarcode ?: existingProduct.barcode,
                                minStockLevel = if (rawMinStock > 0) rawMinStock else existingProduct.minStockLevel,
                                gstRateBps = rawGstBps ?: existingProduct.gstRateBps,
                                hsnCode = rawHsn ?: existingProduct.hsnCode,
                                updatedAtEpochMs = System.currentTimeMillis()
                            )
                            productBatch.add(updatedProduct)
                            if (normBarcode != null) existingBarcodeMap[normBarcode] = updatedProduct
                            if (normName.isNotBlank()) existingNameMap[normName] = updatedProduct
                            updatedCount++
                            if (salePricePaise <= 0 && updatedProduct.salePriceMinorUnits <= 0) zeroPriceCount++
                            val opening = com.kadaikutty.pos.feature.stock.domain.openingStockToStorageUnits(rawOpening, importUnit)
                            if (opening > 0L) {
                                if (!canSetStock) openingGivenButNotAllowed = true
                                // Only fills a product that has never had stock. A shop that re-imports an old
                                // file must not overwrite the quantities its sales and purchases have built up.
                                else if (updatedProduct.id in openingApplied) { /* same product twice in the file */ }
                                else if (database.saleDao().movementCount(companyId, updatedProduct.id) > 0) stockKeptCount++
                                else {
                                    stockBatch.add(com.kadaikutty.pos.feature.billing.data.StockMovementEntity(newRecordId(), companyId, updatedProduct.id, opening, "ADJUSTMENT", "Opening stock (import)", System.currentTimeMillis()))
                                    openingApplied.add(updatedProduct.id)
                                    stockSetCount++
                                }
                            }
                        } else {
                            val newProduct = ProductEntity(
                                id = newRecordId(),
                                companyId = companyId,
                                name = rawName,
                                categoryId = targetCatId ?: generalCatId,
                                purchasePriceMinorUnits = purchasePricePaise,
                                salePriceMinorUnits = salePricePaise,
                                unitType = rawUnit,
                                barcode = normBarcode,
                                minStockLevel = rawMinStock,
                                createdAtEpochMs = System.currentTimeMillis(),
                                updatedAtEpochMs = System.currentTimeMillis(),
                                syncStatus = SyncStatus.LOCAL_ONLY,
                                gstRateBps = rawGstBps ?: 0,
                                hsnCode = rawHsn
                            )
                            productBatch.add(newProduct)
                            if (normBarcode != null) existingBarcodeMap[normBarcode] = newProduct
                            if (normName.isNotBlank()) existingNameMap[normName] = newProduct
                            importedCount++
                            if (salePricePaise <= 0) zeroPriceCount++
                            val opening = com.kadaikutty.pos.feature.stock.domain.openingStockToStorageUnits(rawOpening, rawUnit)
                            if (opening > 0L && canSetStock) {
                                stockBatch.add(com.kadaikutty.pos.feature.billing.data.StockMovementEntity(newRecordId(), companyId, newProduct.id, opening, "ADJUSTMENT", "Opening stock (import)", System.currentTimeMillis()))
                                openingApplied.add(newProduct.id)
                                stockSetCount++
                            } else {
                                if (opening > 0L) openingGivenButNotAllowed = true
                                if (rawMinStock > 0) unstockedWithMinCount++
                            }
                        }

                        // Flush in batches of 500 to keep memory small and DB fast
                        if (productBatch.size >= 500) {
                            val syncBatch = productBatch.toList()
                            dao.insertProducts(syncBatch)
                            syncManager.enqueueProducts(syncBatch, "INSERT")
                            productBatch.clear()
                            flushStock()
                            onProgress(totalRead, totalRead)
                        }
                    } catch (rowErr: Exception) {
                        skippedCount++
                        if (rowErrors.size < MAX_ROW_ERRORS) rowErrors.add("Row $rowNumber: ${rowErr.message ?: rowErr.javaClass.simpleName}")
                    }
                }

                // Flush remaining batch
                if (productBatch.isNotEmpty()) {
                    val syncBatch = productBatch.toList()
                    dao.insertProducts(syncBatch)
                    syncManager.enqueueProducts(syncBatch, "INSERT")
                    productBatch.clear()
                }
                flushStock()

                onComplete(
                    ProductImportSummary(
                        totalRead = totalRead,
                        importedCount = importedCount,
                        updatedCount = updatedCount,
                        skippedCount = skippedCount,
                        errorMessage = null,
                        stockSetCount = stockSetCount,
                        stockKeptCount = stockKeptCount,
                        unitKeptCount = unitKeptCount,
                        unstockedWithMinCount = unstockedWithMinCount,
                        openingGivenButNotAllowed = openingGivenButNotAllowed,
                        zeroPriceCount = zeroPriceCount,
                        rowErrors = rowErrors
                    )
                )
            } catch (e: Exception) {
                onComplete(
                    ProductImportSummary(
                        totalRead = 0,
                        importedCount = 0,
                        updatedCount = 0,
                        skippedCount = 0,
                        errorMessage = e.message ?: "Failed to read data file"
                    )
                )
            }
        }
    }

    private fun parseXlsxRows(zipBytes: ByteArray): List<List<String>> {
        val sharedStrings = mutableListOf<String>()

        // 1. Read shared strings from xl/sharedStrings.xml
        try {
            java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zipBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "xl/sharedStrings.xml") {
                        val parser = org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser()
                        parser.setInput(java.io.InputStreamReader(zis, java.nio.charset.StandardCharsets.UTF_8))
                        var eventType = parser.eventType
                        var insideText = false
                        val currentText = StringBuilder()
                        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                            when (eventType) {
                                org.xmlpull.v1.XmlPullParser.START_TAG -> {
                                    if (parser.name == "t") {
                                        insideText = true
                                        currentText.setLength(0)
                                    }
                                }
                                org.xmlpull.v1.XmlPullParser.TEXT -> {
                                    if (insideText) currentText.append(parser.text)
                                }
                                org.xmlpull.v1.XmlPullParser.END_TAG -> {
                                    if (parser.name == "t") {
                                        insideText = false
                                    } else if (parser.name == "si") {
                                        sharedStrings.add(currentText.toString())
                                    }
                                }
                            }
                            eventType = parser.next()
                        }
                        break
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (ignored: Exception) {}

        // 2. Read sheet rows from xl/worksheets/sheet1.xml
        val rows = mutableListOf<List<String>>()
        try {
            java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zipBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "xl/worksheets/sheet1.xml" || (entry.name.startsWith("xl/worksheets/") && entry.name.endsWith(".xml"))) {
                        val parser = org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser()
                        parser.setInput(java.io.InputStreamReader(zis, java.nio.charset.StandardCharsets.UTF_8))
                        var eventType = parser.eventType
                        val currentRow = mutableMapOf<Int, String>()
                        var currentCellRef = ""
                        var currentCellType = ""
                        var insideValue = false
                        val cellVal = StringBuilder()

                        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                            when (eventType) {
                                org.xmlpull.v1.XmlPullParser.START_TAG -> {
                                    when (parser.name) {
                                        "row" -> {
                                            currentRow.clear()
                                        }
                                        "c" -> {
                                            currentCellRef = parser.getAttributeValue(null, "r") ?: ""
                                            currentCellType = parser.getAttributeValue(null, "t") ?: ""
                                            cellVal.setLength(0)
                                        }
                                        "v", "t" -> {
                                            insideValue = true
                                        }
                                    }
                                }
                                org.xmlpull.v1.XmlPullParser.TEXT -> {
                                    if (insideValue) cellVal.append(parser.text)
                                }
                                org.xmlpull.v1.XmlPullParser.END_TAG -> {
                                    when (parser.name) {
                                        "v", "t" -> {
                                            insideValue = false
                                        }
                                        "c" -> {
                                            val colIndex = colRefToIndex(currentCellRef)
                                            val rawStr = cellVal.toString().trim()
                                            val finalVal = if (currentCellType == "s") {
                                                val idx = rawStr.toIntOrNull() ?: -1
                                                if (idx in 0 until sharedStrings.size) sharedStrings[idx] else rawStr
                                            } else {
                                                // A numeric cell (barcode, price...) whose raw XML value came out in
                                                // scientific notation (e.g. "8.901001001E9") would otherwise import as
                                                // that literal text. A 13-digit barcode is within Double precision, so
                                                // this recovers the exact integer instead of a garbage barcode.
                                                plainNumericString(rawStr)
                                            }
                                            currentRow[colIndex] = finalVal
                                        }
                                        "row" -> {
                                            if (currentRow.isNotEmpty()) {
                                                val maxCol = currentRow.keys.maxOrNull() ?: 0
                                                val rowList = (0..maxCol).map { currentRow[it] ?: "" }
                                                rows.add(rowList)
                                            }
                                        }
                                    }
                                }
                            }
                            eventType = parser.next()
                        }
                        break
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (ignored: Exception) {}

        return rows
    }

    /** "8.901001001E9" -> "8901001001"; anything that is not scientific notation is returned unchanged. */
    private fun plainNumericString(raw: String): String {
        if (!raw.contains('E', ignoreCase = true)) return raw
        val value = raw.toDoubleOrNull() ?: return raw
        return if (value == Math.floor(value) && !value.isInfinite()) {
            java.math.BigDecimal(value).toBigInteger().toString()
        } else {
            java.math.BigDecimal(raw).toPlainString()
        }
    }

    private fun colRefToIndex(ref: String): Int {
        var col = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
            } else {
                break
            }
        }
        return if (col > 0) col - 1 else 0
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    cur.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(cur.toString().trim())
                cur.clear()
            } else {
                cur.append(c)
            }
            i++
        }
        result.add(cur.toString().trim())
        return result
    }
}

data class ProductImportSummary(
    val totalRead: Int,
    val importedCount: Int,
    val updatedCount: Int,
    val skippedCount: Int,
    val errorMessage: String? = null,
    /** Products that received their opening stock from this file. */
    val stockSetCount: Int = 0,
    /** Existing products that already had stock history, so their quantity was left alone. */
    val stockKeptCount: Int = 0,
    /** Existing products whose file row asked for a different unit, left unchanged because they have stock history. */
    val unitKeptCount: Int = 0,
    /** New products with a minimum level but no opening stock: they show as low stock until stocked. */
    val unstockedWithMinCount: Int = 0,
    /** The file had opening stock but the signed-in user may not set stock (administrators only). */
    val openingGivenButNotAllowed: Boolean = false,
    /** New or updated products left with no selling price (0), which cannot be billed until priced. */
    val zeroPriceCount: Int = 0,
    /** Up to 20 "Row N: reason" messages, so a rejected line can be found and fixed instead of just counted. */
    val rowErrors: List<String> = emptyList()
)

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

    fun addCustomer(
        name: String, 
        phone: String?, 
        address: String?, 
        initialDebtMinorUnits: Long = 0L,
        onSuccess: () -> Unit, 
        onError: (Throwable) -> Unit
    ) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            onError(IllegalArgumentException("Customer name cannot be blank"))
            return
        }
        val cleanPhone = phone?.trim()?.takeIf { it.isNotBlank() }
        InputRules.firstError(InputRules.checkName(cleanName), InputRules.checkPhone(cleanPhone.orEmpty()))?.let {
            onError(IllegalArgumentException(it))
            return
        }
        viewModelScope.launch {
            try {
                SyncWritePolicy.requireCreate(sessionStore.activeSession.first(), "Customer")
                val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
                
                // Duplicate check
                val existing = dao.customers(session.companyId, "").first()
                if (cleanPhone != null && existing.any { !it.phone.isNullOrBlank() && it.phone.trim() == cleanPhone }) {
                    onError(IllegalArgumentException("Customer with phone '$cleanPhone' already exists"))
                    return@launch
                }
                if (existing.any { it.name.trim().equals(cleanName, ignoreCase = true) }) {
                    onError(IllegalArgumentException("Customer '$cleanName' already exists"))
                    return@launch
                }

                val customerId = newRecordId()
                val customer = CustomerEntity(
                    id = customerId,
                    companyId = session.companyId,
                    name = cleanName,
                    phone = cleanPhone,
                    address = address?.trim()?.takeIf { it.isNotBlank() },
                    createdAtEpochMs = System.currentTimeMillis(),
                    updatedAtEpochMs = System.currentTimeMillis(),
                    syncStatus = SyncStatus.LOCAL_ONLY
                )
                dao.insertCustomer(customer)
                syncManager.enqueueCustomer(customer, "INSERT")

                if (initialDebtMinorUnits > 0L) {
                    val credit = CustomerCreditEntity(
                        id = newRecordId(),
                        companyId = session.companyId,
                        customerId = customerId,
                        amountMinorUnits = initialDebtMinorUnits,
                        reason = "Opening Balance",
                        dateEpochMs = System.currentTimeMillis(),
                        syncStatus = SyncStatus.LOCAL_ONLY
                    )
                    dao.insertCustomerCredit(credit)
                    syncManager.enqueueCustomerCredit(credit, "INSERT")
                }

                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun updateCustomer(customer: CustomerEntity, newName: String, newPhone: String?, newAddress: String?, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) {
            onError(IllegalArgumentException("Customer name cannot be blank"))
            return
        }
        val cleanPhone = newPhone?.trim()?.takeIf { it.isNotBlank() }
        InputRules.firstError(InputRules.checkName(cleanName), InputRules.checkPhone(cleanPhone.orEmpty()))?.let {
            onError(IllegalArgumentException(it))
            return
        }
        val cleanAddress = newAddress?.trim()?.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            try {
                SyncWritePolicy.requireEdit(sessionStore.activeSession.first(), "Customer")
                // Duplicate check
                val existing = dao.customers(customer.companyId, "").first()
                if (cleanPhone != null && existing.any { it.id != customer.id && !it.phone.isNullOrBlank() && it.phone.trim() == cleanPhone }) {
                    onError(IllegalArgumentException("Another customer with phone '$cleanPhone' already exists"))
                    return@launch
                }
                if (existing.any { it.id != customer.id && it.name.trim().equals(cleanName, ignoreCase = true) }) {
                    onError(IllegalArgumentException("Another customer with name '$cleanName' already exists"))
                    return@launch
                }

                val updated = customer.copy(
                    name = cleanName,
                    phone = cleanPhone,
                    address = cleanAddress,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
                dao.updateCustomer(updated)
                
                val updates = mutableMapOf<String, Any?>()
                if (customer.name != cleanName) updates["name"] = cleanName
                if (customer.phone != cleanPhone) updates["phone"] = cleanPhone
                if (customer.address != cleanAddress) updates["address"] = cleanAddress
                
                if (updates.isNotEmpty()) {
                    syncManager.enqueuePartialUpdate("Customer", customer.id, updates)
                }
                
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun deleteCustomer(customer: CustomerEntity, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                SyncWritePolicy.requireEdit(sessionStore.activeSession.first(), "Customer")
                require(dao.customerBalance(customer.companyId, customer.id) == 0L) { "This customer has a pending balance. Settle it before deleting." }
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
                SyncWritePolicy.requireCreate(sessionStore.activeSession.first(), "Customer")
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

    fun getCustomerLedger(customerId: String): Flow<List<LedgerEntry>> = sessionStore.activeSession.flatMapLatest { session ->
        val companyId = session?.companyId ?: ""
        dao.getCustomerCredits(companyId, customerId).map { credits ->
            var balance = 0L
            credits.reversed().map { credit ->
                balance += credit.amountMinorUnits
                LedgerEntry(
                    id = credit.id,
                    dateEpochMs = credit.dateEpochMs,
                    description = credit.reason,
                    debitMinorUnits = if (credit.amountMinorUnits > 0) credit.amountMinorUnits else 0L,
                    creditMinorUnits = if (credit.amountMinorUnits < 0) -credit.amountMinorUnits else 0L,
                    runningBalance = balance
                )
            }.reversed()
        }
    }

    // Direct SQL SUM via the already-existing getCustomerCreditBalance query, not derived from
    // getCustomerLedger: the list-row balance doesn't need the full running-balance reconstruction.
    fun getCustomerBalance(customerId: String): Flow<Long> = getCustomerCreditBalance(customerId).map { it ?: 0L }

    fun updateCustomerCreditLimit(customerId: String, limit: Long, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                SyncWritePolicy.requireEdit(sessionStore.activeSession.first(), "Customer")
                val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
                dao.updateCustomerCreditLimit(session.companyId, customerId, limit)
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun deleteCustomerCredit(creditId: String, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                SyncWritePolicy.requireEdit(sessionStore.activeSession.first(), "Customer")
                val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
                dao.deleteCustomerCreditById(session.companyId, creditId)
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
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            onError(IllegalArgumentException("Supplier name cannot be blank"))
            return
        }
        val cleanPhone = phone?.trim()?.takeIf { it.isNotBlank() }
        InputRules.firstError(InputRules.checkName(cleanName), InputRules.checkPhone(cleanPhone.orEmpty()))?.let {
            onError(IllegalArgumentException(it))
            return
        }
        viewModelScope.launch {
            try {
                SyncWritePolicy.requireCreate(sessionStore.activeSession.first(), "Supplier")
                val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
                
                // Duplicate check
                val existing = dao.suppliers(session.companyId, "").first()
                if (cleanPhone != null && existing.any { !it.phone.isNullOrBlank() && it.phone.trim() == cleanPhone }) {
                    onError(IllegalArgumentException("Supplier with phone '$cleanPhone' already exists"))
                    return@launch
                }
                if (existing.any { it.name.trim().equals(cleanName, ignoreCase = true) }) {
                    onError(IllegalArgumentException("Supplier '$cleanName' already exists"))
                    return@launch
                }

                val supplier = SupplierEntity(
                    id = newRecordId(),
                    companyId = session.companyId,
                    name = cleanName,
                    phone = cleanPhone,
                    address = address?.trim()?.takeIf { it.isNotBlank() },
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
        val cleanName = newName.trim()
        if (cleanName.isBlank()) {
            onError(IllegalArgumentException("Supplier name cannot be blank"))
            return
        }
        val cleanPhone = newPhone?.trim()?.takeIf { it.isNotBlank() }
        InputRules.firstError(InputRules.checkName(cleanName), InputRules.checkPhone(cleanPhone.orEmpty()))?.let {
            onError(IllegalArgumentException(it))
            return
        }
        val cleanAddress = newAddress?.trim()?.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            try {
                SyncWritePolicy.requireEdit(sessionStore.activeSession.first(), "Supplier")
                // Duplicate check
                val existing = dao.suppliers(supplier.companyId, "").first()
                if (cleanPhone != null && existing.any { it.id != supplier.id && !it.phone.isNullOrBlank() && it.phone.trim() == cleanPhone }) {
                    onError(IllegalArgumentException("Another supplier with phone '$cleanPhone' already exists"))
                    return@launch
                }
                if (existing.any { it.id != supplier.id && it.name.trim().equals(cleanName, ignoreCase = true) }) {
                    onError(IllegalArgumentException("Another supplier with name '$cleanName' already exists"))
                    return@launch
                }

                val updated = supplier.copy(
                    name = cleanName,
                    phone = cleanPhone,
                    address = cleanAddress,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
                dao.updateSupplier(updated)
                
                val updates = mutableMapOf<String, Any?>()
                if (supplier.name != cleanName) updates["name"] = cleanName
                if (supplier.phone != cleanPhone) updates["phone"] = cleanPhone
                if (supplier.address != cleanAddress) updates["address"] = cleanAddress
                
                if (updates.isNotEmpty()) {
                    syncManager.enqueuePartialUpdate("Supplier", supplier.id, updates)
                }
                
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun deleteSupplier(supplier: SupplierEntity, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                SyncWritePolicy.requireEdit(sessionStore.activeSession.first(), "Supplier")
                require(dao.supplierBalance(supplier.companyId, supplier.id) == 0L) { "This supplier has a pending balance. Settle it before deleting." }
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
                SyncWritePolicy.requireCreate(sessionStore.activeSession.first(), "Supplier")
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

    fun deleteSupplierCredit(creditId: String, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                SyncWritePolicy.requireEdit(sessionStore.activeSession.first(), "Supplier")
                val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
                dao.deleteSupplierCreditById(session.companyId, creditId)
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

    fun getTotalSupplierCreditsPayable(): Flow<Long?> = sessionStore.activeSession
        .flatMapLatest { session ->
            val companyId = session?.companyId ?: ""
            dao.getTotalSupplierCreditsPayable(companyId)
        }

    fun getSupplierLedger(supplierId: String): Flow<List<LedgerEntry>> = sessionStore.activeSession.flatMapLatest { session ->
        val companyId = session?.companyId ?: ""
        dao.getSupplierCredits(companyId, supplierId).map { credits ->
            var balance = 0L
            credits.reversed().map { credit ->
                balance += credit.amountMinorUnits
                LedgerEntry(
                    id = credit.id,
                    dateEpochMs = credit.dateEpochMs,
                    description = credit.terms,
                    debitMinorUnits = if (credit.amountMinorUnits > 0) credit.amountMinorUnits else 0L,
                    creditMinorUnits = if (credit.amountMinorUnits < 0) -credit.amountMinorUnits else 0L,
                    runningBalance = balance
                )
            }.reversed()
        }
    }
    
    // A direct SQL SUM, not derived from getSupplierLedger: the list-row balance doesn't need the
    // full per-entry running-balance reconstruction, just the total, so this avoids re-querying and
    // re-processing every credit row twice per supplier row in the list.
    fun getSupplierBalance(supplierId: String): Flow<Long> = sessionStore.activeSession.flatMapLatest { session ->
        val companyId = session?.companyId ?: ""
        dao.getSupplierCreditBalance(companyId, supplierId)
    }.map { it ?: 0L }
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
        val cleanDesc = description.trim()
        if (amountMinorUnits <= 0L || cleanDesc.isBlank()) {
            onError(IllegalArgumentException("Invalid expense amount or description"))
            return
        }
        viewModelScope.launch {
            try {
                SyncWritePolicy.requireCreate(sessionStore.activeSession.first(), "Expense")
                val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
                val expense = ExpenseEntity(
                    id = newRecordId(),
                    companyId = session.companyId,
                    amountMinorUnits = amountMinorUnits,
                    description = cleanDesc,
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
        val cleanDesc = newDescription.trim()
        if (newAmountMinorUnits <= 0L || cleanDesc.isBlank()) {
            onError(IllegalArgumentException("Invalid expense amount or description"))
            return
        }
        viewModelScope.launch {
            try {
                SyncWritePolicy.requireEdit(sessionStore.activeSession.first(), "Expense")
                val updated = expense.copy(
                    amountMinorUnits = newAmountMinorUnits,
                    description = cleanDesc,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
                dao.updateExpense(updated)
                
                val updates = mutableMapOf<String, Any?>()
                if (expense.amountMinorUnits != newAmountMinorUnits) updates["amountMinorUnits"] = newAmountMinorUnits
                if (expense.description != cleanDesc) updates["description"] = cleanDesc
                
                if (updates.isNotEmpty()) {
                    syncManager.enqueuePartialUpdate("Expense", expense.id, updates)
                }
                
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun deleteExpense(expense: ExpenseEntity, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
                SyncWritePolicy.requireEdit(sessionStore.activeSession.first(), "Expense")
                dao.deleteExpense(expense)
                syncManager.enqueueExpense(expense, "DELETE")
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }
}
