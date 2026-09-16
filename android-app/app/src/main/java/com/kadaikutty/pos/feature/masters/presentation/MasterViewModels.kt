@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.kadaikutty.pos.feature.masters.presentation

import androidx.room.withTransaction
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.sync.SyncStatus
import com.kadaikutty.pos.core.sync.SyncManager
import com.kadaikutty.pos.core.common.newRecordId
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
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            onError(IllegalArgumentException("Category name cannot be blank"))
            return
        }
        viewModelScope.launch {
            try {
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
    private val reportDao = database.reportDao()
    private val searchQuery = MutableStateFlow("")

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
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            onError(IllegalArgumentException("Product name cannot be blank"))
            return
        }
        val cleanBarcode = barcode?.trim()?.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            try {
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
        newBarcode: String?,
        newMinStockLevel: Double,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) {
            onError(IllegalArgumentException("Product name cannot be blank"))
            return
        }
        val cleanBarcode = newBarcode?.trim()?.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            try {
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
                val updated = product.copy(
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
                
                if (updates.isNotEmpty()) {
                    syncManager.enqueuePartialUpdate("Product", product.id, updates)
                }
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
                dao.deleteProduct(product)
                syncManager.enqueueProduct(product, "DELETE")
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
                        writer.write("Product Name,Barcode,Category,Unit,Purchase Price,Selling Price,Min Stock\n")
                        writer.write("Aashirvaad Superior MP Atta 5kg,8901725131456,Grocery,KG,220.00,265.00,10\n")
                        writer.write("Fortune Sunlite Sunflower Oil 1L,8906007280145,Oil & Ghee,LITER,110.00,135.00,15\n")
                        writer.write("Tata Salt Iodized 1kg,8904043901005,Grocery,PACK,20.00,28.00,25\n")
                        writer.write("Surf Excel Easy Wash Detergent 1kg,8901030384813,Household,PACK,125.00,150.00,10\n")
                        writer.write("Britannia Good Day Butter Cookies 100g,8901063012431,Snacks,PIECE,18.00,25.00,30\n")
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

                // Pre-cache existing products by Barcode and by Name
                val existingBarcodeMap = mutableMapOf<String, ProductEntity>()
                val existingNameMap = mutableMapOf<String, ProductEntity>()
                dao.getAllProducts(companyId).forEach {
                    if (!it.barcode.isNullOrBlank()) {
                        existingBarcodeMap[it.barcode.trim()] = it
                    }
                    existingNameMap[it.name.trim().lowercase()] = it
                }

                var totalRead = 0
                var importedCount = 0
                var updatedCount = 0
                var skippedCount = 0

                val productBatch = mutableListOf<ProductEntity>()
                val newCategoriesBatch = mutableListOf<CategoryEntity>()

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
                for (tokens in rawRows) {
                    if (tokens.isEmpty()) continue

                    if (isFirstLine) {
                        isFirstLine = false
                        val firstCell = tokens[0].trim()
                        if (firstCell.contains("Product Name", ignoreCase = true) || (tokens.size > 1 && tokens[1].contains("Barcode", ignoreCase = true))) {
                            continue
                        }
                    }

                    if (tokens[0].isBlank()) {
                        skippedCount++
                        continue
                    }

                    totalRead++

                    try {
                        val rawName = tokens[0].trim()
                        val rawBarcode = tokens.getOrNull(1)?.trim()?.ifBlank { null }
                        val rawCategory = tokens.getOrNull(2)?.trim()?.ifBlank { "General" } ?: "General"
                        val rawUnit = tokens.getOrNull(3)?.trim()?.uppercase()?.ifBlank { "PIECE" } ?: "PIECE"
                        val rawPurchase = tokens.getOrNull(4)?.trim()?.replace("₹", "")?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                        val rawSale = tokens.getOrNull(5)?.trim()?.replace("₹", "")?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                        val rawMinStock = tokens.getOrNull(6)?.trim()?.toDoubleOrNull() ?: 0.0

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

                        val purchasePricePaise = (rawPurchase * 100).toLong()
                        val salePricePaise = (rawSale * 100).toLong()

                        // Check if existing product by Barcode or by Name
                        val existingProduct = (if (rawBarcode != null) existingBarcodeMap[rawBarcode] else null) ?: existingNameMap[rawName.lowercase()]

                        if (existingProduct != null) {
                            val updatedProduct = existingProduct.copy(
                                name = rawName,
                                categoryId = targetCatId ?: generalCatId,
                                purchasePriceMinorUnits = if (purchasePricePaise > 0) purchasePricePaise else existingProduct.purchasePriceMinorUnits,
                                salePriceMinorUnits = if (salePricePaise > 0) salePricePaise else existingProduct.salePriceMinorUnits,
                                unitType = rawUnit,
                                barcode = rawBarcode ?: existingProduct.barcode,
                                minStockLevel = if (rawMinStock > 0) rawMinStock else existingProduct.minStockLevel,
                                updatedAtEpochMs = System.currentTimeMillis()
                            )
                            productBatch.add(updatedProduct)
                            updatedCount++
                        } else {
                            val newProduct = ProductEntity(
                                id = newRecordId(),
                                companyId = companyId,
                                name = rawName,
                                categoryId = targetCatId ?: generalCatId,
                                purchasePriceMinorUnits = purchasePricePaise,
                                salePriceMinorUnits = salePricePaise,
                                unitType = rawUnit,
                                barcode = rawBarcode,
                                minStockLevel = rawMinStock,
                                createdAtEpochMs = System.currentTimeMillis(),
                                updatedAtEpochMs = System.currentTimeMillis(),
                                syncStatus = SyncStatus.LOCAL_ONLY
                            )
                            productBatch.add(newProduct)
                            if (rawBarcode != null) existingBarcodeMap[rawBarcode] = newProduct
                            existingNameMap[rawName.lowercase()] = newProduct
                            importedCount++
                        }

                        // Flush in batches of 500 to keep memory small and DB fast
                        if (productBatch.size >= 500) {
                            val syncBatch = productBatch.toList()
                            dao.insertProducts(syncBatch)
                            syncManager.enqueueProducts(syncBatch, "INSERT")
                            productBatch.clear()
                            onProgress(totalRead, totalRead)
                        }
                    } catch (rowErr: Exception) {
                        skippedCount++
                    }
                }

                // Flush remaining batch
                if (productBatch.isNotEmpty()) {
                    val syncBatch = productBatch.toList()
                    dao.insertProducts(syncBatch)
                    syncManager.enqueueProducts(syncBatch, "INSERT")
                    productBatch.clear()
                }

                onComplete(
                    ProductImportSummary(
                        totalRead = totalRead,
                        importedCount = importedCount,
                        updatedCount = updatedCount,
                        skippedCount = skippedCount,
                        errorMessage = null
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
                                                rawStr
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
    val errorMessage: String? = null
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
        viewModelScope.launch {
            try {
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
        val cleanAddress = newAddress?.trim()?.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            try {
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

    fun deleteCustomerCredit(creditId: String, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
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
        viewModelScope.launch {
            try {
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
        val cleanAddress = newAddress?.trim()?.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            try {
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

    fun deleteSupplierCredit(creditId: String, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch {
            try {
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
            val orderTitle = when {
                !purchase.invoiceNumber.isNullOrBlank() -> "Purchase #${purchase.invoiceNumber}"
                !purchase.orderNumber.isNullOrBlank() -> "Order #${purchase.orderNumber}"
                else -> "Purchase #${purchase.id.take(4)}"
            }
            entries.add(LedgerEntry(purchase.id, purchase.createdAtEpochMs, orderTitle, purchase.totalMinorUnits, 0L, 0L))
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
        val cleanDesc = description.trim()
        if (amountMinorUnits <= 0L || cleanDesc.isBlank()) {
            onError(IllegalArgumentException("Invalid expense amount or description"))
            return
        }
        viewModelScope.launch {
            try {
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
                dao.deleteExpense(expense)
                syncManager.enqueueExpense(expense, "DELETE")
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }
}
