package com.kadaikutty.pos.core.sync

import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.database.SyncQueueEntity
import com.kadaikutty.pos.core.common.newRecordId

import kotlinx.coroutines.flow.first
import com.kadaikutty.pos.core.auth.SessionStore

class SyncManager(
    private val tenantDatabaseManager: com.kadaikutty.pos.core.database.TenantDatabaseManager?,
    private val syncScheduler: SyncScheduler,
    private val sessionStore: SessionStore,
    private val liveBackupWriter: com.kadaikutty.pos.core.backup.data.LiveBackupWriter?,
    private val scheduleEnabled: Boolean = true,
    private val fallbackDatabase: BillingDatabase? = null,
) {
    constructor(
        tenantDatabaseManager: com.kadaikutty.pos.core.database.TenantDatabaseManager,
        syncScheduler: SyncScheduler,
        sessionStore: SessionStore,
        liveBackupWriter: com.kadaikutty.pos.core.backup.data.LiveBackupWriter,
        scheduleEnabled: Boolean = true
    ) : this(tenantDatabaseManager, syncScheduler, sessionStore, liveBackupWriter, scheduleEnabled, null)

    constructor(
        database: BillingDatabase,
        syncScheduler: SyncScheduler,
        sessionStore: SessionStore,
        scheduleEnabled: Boolean = true
    ) : this(null, syncScheduler, sessionStore, null, scheduleEnabled, database)

    private val database: BillingDatabase
        get() = tenantDatabaseManager?.getDatabase() ?: fallbackDatabase ?: error("No BillingDatabase available")

    private fun requestSafely() {
        if (!scheduleEnabled) return
        try { syncScheduler.request() } catch (e: Exception) {
            android.util.Log.w("SyncManager", "Pending local operations will retry later", e)
        }
    }

    suspend fun enqueueCategory(category: com.kadaikutty.pos.feature.masters.data.CategoryEntity, operation: String) {
        val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
        val companyId = session.companyId
        val payload = toJson(mapOf(
            "id" to category.id,
            "companyId" to companyId,
            "name" to category.name,
            "createdAtEpochMs" to category.createdAtEpochMs,
            "updatedAtEpochMs" to category.updatedAtEpochMs,
            "syncStatus" to "SYNCED",
            "_schemaVersion" to 1
        ))
        enqueueItem(companyId, "Category", category.id, operation, payload)
    }

    suspend fun enqueueProduct(product: com.kadaikutty.pos.feature.masters.data.ProductEntity, operation: String) {
        val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
        val companyId = session.companyId
        val payload = toJson(mapOf(
            "id" to product.id,
            "companyId" to companyId,
            "name" to product.name,
            "categoryId" to product.categoryId,
            "purchasePriceMinorUnits" to product.purchasePriceMinorUnits,
            "salePriceMinorUnits" to product.salePriceMinorUnits,
            "unitType" to product.unitType,
            "barcode" to product.barcode,
            "minStockLevel" to product.minStockLevel,
            "createdAtEpochMs" to product.createdAtEpochMs,
            "updatedAtEpochMs" to product.updatedAtEpochMs,
            "syncStatus" to "SYNCED",
            "_schemaVersion" to 1
        ))
        enqueueItem(companyId, "Product", product.id, operation, payload)
    }

    suspend fun enqueueProducts(products: List<com.kadaikutty.pos.feature.masters.data.ProductEntity>, operation: String) {
        if (products.isEmpty()) return
        val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
        val companyId = session.companyId
        products.forEach { product ->
            val payload = toJson(mapOf(
                "id" to product.id,
                "companyId" to companyId,
                "name" to product.name,
                "categoryId" to product.categoryId,
                "purchasePriceMinorUnits" to product.purchasePriceMinorUnits,
                "salePriceMinorUnits" to product.salePriceMinorUnits,
                "unitType" to product.unitType,
                "barcode" to product.barcode,
                "minStockLevel" to product.minStockLevel,
                "createdAtEpochMs" to product.createdAtEpochMs,
                "updatedAtEpochMs" to product.updatedAtEpochMs,
                "syncStatus" to "SYNCED",
                "_schemaVersion" to 1
            ))
            enqueueItem(companyId, "Product", product.id, operation, payload, requestSync = false)
        }
        requestSafely()
    }

    suspend fun enqueueCustomer(customer: com.kadaikutty.pos.feature.masters.data.CustomerEntity, operation: String) {
        val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
        val companyId = session.companyId
        val payload = toJson(mapOf(
            "id" to customer.id,
            "companyId" to companyId,
            "name" to customer.name,
            "phone" to customer.phone,
            "address" to customer.address,
            "creditLimitMinorUnits" to customer.creditLimitMinorUnits,
            "createdAtEpochMs" to customer.createdAtEpochMs,
            "updatedAtEpochMs" to customer.updatedAtEpochMs,
            "syncStatus" to "SYNCED",
            "_schemaVersion" to 1
        ))
        enqueueItem(companyId, "Customer", customer.id, operation, payload)
    }

    suspend fun enqueueSupplier(supplier: com.kadaikutty.pos.feature.masters.data.SupplierEntity, operation: String) {
        val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
        val companyId = session.companyId
        val payload = toJson(mapOf(
            "id" to supplier.id,
            "companyId" to companyId,
            "name" to supplier.name,
            "phone" to supplier.phone,
            "address" to supplier.address,
            "createdAtEpochMs" to supplier.createdAtEpochMs,
            "updatedAtEpochMs" to supplier.updatedAtEpochMs,
            "syncStatus" to "SYNCED",
            "_schemaVersion" to 1
        ))
        enqueueItem(companyId, "Supplier", supplier.id, operation, payload)
    }

    suspend fun enqueueExpense(expense: com.kadaikutty.pos.feature.masters.data.ExpenseEntity, operation: String) {
        val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
        val companyId = session.companyId
        val payload = toJson(mapOf(
            "id" to expense.id,
            "companyId" to companyId,
            "amountMinorUnits" to expense.amountMinorUnits,
            "description" to expense.description,
            "createdAtEpochMs" to expense.createdAtEpochMs,
            "updatedAtEpochMs" to expense.updatedAtEpochMs,
            "_schemaVersion" to 1
        ))
        enqueueItem(companyId, "Expense", expense.id, operation, payload)
    }

    suspend fun enqueueSale(sale: com.kadaikutty.pos.feature.billing.data.SaleEntity, items: List<com.kadaikutty.pos.feature.billing.data.SaleItemEntity>, operation: String = "INSERT", editedAt: Long? = null) {
        val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
        val companyId = session.companyId
        val itemsList = items.map { item ->
            mapOf(
                "companyId" to companyId,
                "saleId" to sale.id,
                "productId" to item.productId,
                "quantity" to item.quantity,
                "unitPriceMinorUnits" to item.unitPriceMinorUnits,
                "lineTotalMinorUnits" to item.lineTotalMinorUnits,
                "discountMinorUnits" to item.discountMinorUnits,
                "unitType" to item.unitType, "productName" to item.productName,
                "costTotalMinorUnits" to item.costTotalMinorUnits, "netRevenueMinorUnits" to item.netRevenueMinorUnits
            )
        }
        val payload = toJson(mapOf(
            "id" to sale.id,
            "companyId" to companyId,
            "billNumber" to sale.billNumber, "revision" to sale.revision,
            "totalMinorUnits" to sale.totalMinorUnits,
            "createdAtEpochMs" to sale.createdAtEpochMs,
            "customerId" to sale.customerId,
            "paymentMode" to sale.paymentMode,
            "paidCashMinorUnits" to sale.paidCashMinorUnits,
            "paidUpiMinorUnits" to sale.paidUpiMinorUnits,
            "creditAppliedMinorUnits" to sale.creditAppliedMinorUnits,
            "discountMinorUnits" to sale.discountMinorUnits,
            "syncStatus" to "SYNCED",
            "_schemaVersion" to 1,
            "items" to itemsList
        ))
        enqueueItem(companyId, "Sale", sale.id, operation, payload, editedAtOverride = editedAt)
    }

    suspend fun enqueuePurchase(purchase: com.kadaikutty.pos.feature.purchase.data.PurchaseEntity, items: List<com.kadaikutty.pos.feature.purchase.data.PurchaseItemEntity>, operation: String = "INSERT", editedAt: Long? = null) {
        val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
        val companyId = session.companyId
        val itemsList = items.map { item ->
            mapOf(
                "companyId" to companyId,
                "purchaseId" to purchase.id,
                "productId" to item.productId,
                "quantity" to item.quantity,
                "unitValueMinorUnits" to item.unitValueMinorUnits,
                "lineTotalMinorUnits" to item.lineTotalMinorUnits, "unitType" to item.unitType
            )
        }
        val payload = toJson(mapOf(
            "id" to purchase.id,
            "companyId" to companyId,
            "supplierId" to purchase.supplierId,
            "totalMinorUnits" to purchase.totalMinorUnits,
            "createdAtEpochMs" to purchase.createdAtEpochMs,
            "invoiceNumber" to purchase.invoiceNumber,
            "notes" to purchase.notes,
            "paymentMode" to purchase.paymentMode,
            "paidCashMinorUnits" to purchase.paidCashMinorUnits,
            "paidUpiMinorUnits" to purchase.paidUpiMinorUnits,
            "creditAppliedMinorUnits" to purchase.creditAppliedMinorUnits,
            "orderNumber" to purchase.orderNumber, "revision" to purchase.revision,
            "syncStatus" to "SYNCED",
            "_schemaVersion" to 1,
            "items" to itemsList
        ))
        enqueueItem(companyId, "Purchase", purchase.id, operation, payload, editedAtOverride = editedAt)
    }

    suspend fun enqueueCustomerCredit(credit: com.kadaikutty.pos.feature.masters.data.CustomerCreditEntity, operation: String) {
        val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
        val companyId = session.companyId
        val payload = toJson(mapOf(
            "id" to credit.id,
            "companyId" to companyId,
            "customerId" to credit.customerId,
            "amountMinorUnits" to credit.amountMinorUnits,
            "reason" to credit.reason, "referenceId" to credit.referenceId,
            "dateEpochMs" to credit.dateEpochMs,
            "syncStatus" to "SYNCED",
            "_schemaVersion" to 1
        ))
        enqueueItem(companyId, "CustomerCredit", credit.id, operation, payload)
    }

    suspend fun enqueueSupplierCredit(credit: com.kadaikutty.pos.feature.masters.data.SupplierCreditEntity, operation: String) {
        val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
        val companyId = session.companyId
        val payload = toJson(mapOf(
            "id" to credit.id,
            "companyId" to companyId,
            "supplierId" to credit.supplierId,
            "amountMinorUnits" to credit.amountMinorUnits,
            "terms" to credit.terms, "referenceId" to credit.referenceId,
            "dueDateEpochMs" to credit.dueDateEpochMs,
            "dateEpochMs" to credit.dateEpochMs,
            "syncStatus" to "SYNCED",
            "_schemaVersion" to 1
        ))
        enqueueItem(companyId, "SupplierCredit", credit.id, operation, payload)
    }

    suspend fun enqueueStockMovement(movement: com.kadaikutty.pos.feature.billing.data.StockMovementEntity, operation: String = "INSERT") {
        val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
        val companyId = session.companyId
        val payload = toJson(mapOf(
            "id" to movement.id,
            "companyId" to companyId,
            "productId" to movement.productId,
            "quantityDelta" to movement.quantityDelta,
            "type" to movement.type,
            "referenceId" to movement.referenceId,
            "createdAtEpochMs" to movement.createdAtEpochMs,
            "_schemaVersion" to 1
        ))
        enqueueItem(companyId, "StockMovement", movement.id, operation, payload)
    }

    suspend fun enqueuePartialUpdate(entityType: String, entityId: String, updates: Map<String, Any?>) {
        val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
        val companyId = session.companyId
        
        // Add updated timestamp
        val finalUpdates = updates.toMutableMap()
        finalUpdates["updatedAtEpochMs"] = System.currentTimeMillis()
        
        val payload = toJson(finalUpdates)
        enqueueItem(companyId, entityType, entityId, "PARTIAL_UPDATE", payload)
    }

    /**
     * Queues a write decided by ConflictResolver. The payload is already the full record in the
     * sync shape, and the caller has closed every older queue item for this record first, so this
     * always starts a fresh item rather than merging into a stale one.
     *
     * [editedAt] is the time the content was really decided, not now: a restore or a re-send must
     * not look like a brand-new edit, or it would beat a genuinely later edit on another device.
     */
    suspend fun enqueueResolved(companyId: String, entityType: String, entityId: String, operation: String, payload: org.json.JSONObject, editedAt: Long) {
        enqueueItem(companyId, entityType, entityId, operation, payload.toString(), editedAtOverride = editedAt)
    }

    private suspend fun enqueueItem(
        companyId: String,
        entityType: String,
        entityId: String,
        operation: String,
        rawPayload: String,
        requestSync: Boolean = true,
        editedAtOverride: Long? = null
    ) {
        // When this change was made, in server time (TrustedClock), so a conflict with another
        // device can be settled by which edit really came last. See ConflictPolicy.
        val payloadJson = stampEditTime(rawPayload, editedAtOverride ?: com.kadaikutty.pos.core.common.TrustedClock.now())
        val existing = database.syncQueueDao().findPending(companyId, entityType, entityId)
        val now = System.currentTimeMillis()

        if (existing != null) {
            val folded = foldQueuedOperation(existing.operation, existing.payload, operation, payloadJson)
            if (folded != null) {
                database.syncQueueDao().updatePending(existing.id, folded.first, folded.second, now)
                // Only record what the queue actually accepted. Appending an operation the queue
                // rejected (an edit arriving after a queued DELETE) made the backup changelog
                // disagree with the queue and resurrected deleted rows on restore.
                liveBackupWriter?.appendChange(entityType, entityId, operation, payloadJson)
            }
            if (requestSync) requestSafely()
            return
        }

        val syncItem = SyncQueueEntity(
            id = newRecordId(),
            companyId = companyId,
            entityType = entityType,
            entityId = entityId,
            operation = operation,
            payload = payloadJson,
            status = SyncStatus.PENDING,
            attemptCount = 0,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        database.syncQueueDao().enqueue(syncItem)
        liveBackupWriter?.appendChange(entityType, entityId, operation, payloadJson)
        if (requestSync) requestSafely()
    }

    suspend fun enqueueAllDataForSync() {
        val session = sessionStore.activeSession.first() ?: return
        val companyId = session.companyId

        // Enqueue Masters
        database.masterDao().categories(companyId, "").first().forEach { enqueueCategory(it, "INSERT") }
        database.masterDao().products(companyId, "").first().forEach { enqueueProduct(it, "INSERT") }
        database.masterDao().customers(companyId, "").first().forEach { enqueueCustomer(it, "INSERT") }
        database.masterDao().suppliers(companyId, "").first().forEach { enqueueSupplier(it, "INSERT") }
        database.masterDao().expenses(companyId).first().forEach { enqueueExpense(it, "INSERT") }
        
        // Enqueue Credits
        database.masterDao().getAllCustomerCredits(companyId).forEach { enqueueCustomerCredit(it, "INSERT") }
        database.masterDao().getAllSupplierCredits(companyId).forEach { enqueueSupplierCredit(it, "INSERT") }
        
        // Enqueue Sales
        database.saleDao().getSales(companyId).first().forEach { sale ->
            val items = database.saleDao().getSaleItemsList(companyId, sale.id)
            enqueueSale(sale, items, "INSERT")
        }

        // Enqueue Purchases
        database.purchaseDao().getPurchases(companyId).first().forEach { purchase ->
            val items = database.purchaseDao().getPurchaseItemsList(companyId, purchase.id)
            enqueuePurchase(purchase, items)
        }
        
        // Stock rows too: after a restore they may exist nowhere but here, and without them every
        // other device would show the wrong stock for the restored bills and purchases.
        database.saleDao().allStockMovements(companyId).forEach { enqueueStockMovement(it, "INSERT") }
        
        // Trigger the scheduler immediately
        requestSafely()
    }

    companion object {
        /**
         * Folds a new write for a record into the one already waiting in the queue, so each record
         * has at most one pending operation. Returns the operation and payload to keep, or null to
         * keep the queued one unchanged.
         *
         * The old precedence table ranked a full UPDATE below everything else, so a bill edited
         * before its first sync, or a full update after a partial one, was silently dropped.
         */
        fun foldQueuedOperation(existingOp: String, existingPayload: String, incomingOp: String, incomingPayload: String): Pair<String, String>? {
            // A queued delete stands; nothing after it can bring the record back from this queue.
            if (existingOp == "DELETE" && incomingOp != "DELETE") return null
            if (incomingOp == "DELETE") return "DELETE" to incomingPayload
            val payload = if (incomingOp == "PARTIAL_UPDATE") mergePayloads(existingPayload, incomingPayload) else incomingPayload
            val op = when {
                // Never reached the server yet: whatever follows is still its first write.
                existingOp == "INSERT" -> "INSERT"
                existingOp == "PARTIAL_UPDATE" && incomingOp == "PARTIAL_UPDATE" -> "PARTIAL_UPDATE"
                else -> "UPDATE"
            }
            return op to payload
        }

        const val EDITED_AT = "editedAtEpochMs"

        fun stampEditTime(payload: String, editedAt: Long): String = try {
            org.json.JSONObject(payload).put(EDITED_AT, editedAt).toString()
        } catch (e: Exception) {
            payload
        }

        private fun mergePayloads(base: String, overlay: String): String = try {
            val merged = org.json.JSONObject(base)
            val incoming = org.json.JSONObject(overlay)
            incoming.keys().forEach { key -> merged.put(key, incoming.get(key)) }
            merged.toString()
        } catch (e: Exception) {
            overlay
        }
    }

    private fun toJson(value: Any?): String {
        return try {
            if (value is Map<*, *>) {
                val jsonObject = org.json.JSONObject()
                value.forEach { (k, v) ->
                    if (v is List<*>) {
                        val jsonArray = org.json.JSONArray()
                        v.forEach { item ->
                            if (item is Map<*, *>) {
                                jsonArray.put(org.json.JSONObject(item as Map<*, *>))
                            } else {
                                jsonArray.put(item)
                            }
                        }
                        jsonObject.put(k.toString(), jsonArray)
                    } else {
                        jsonObject.put(k.toString(), v)
                    }
                }
                jsonObject.toString()
            } else {
                org.json.JSONObject.wrap(value)?.toString() ?: "{}"
            }
        } catch (e: Exception) {
            "{}"
        }
    }
}
