package com.kadaikutty.pos.core.sync

import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.database.SyncQueueEntity
import com.kadaikutty.pos.core.common.newRecordId

import kotlinx.coroutines.flow.first
import com.kadaikutty.pos.core.auth.SessionStore

class SyncManager(
    private val database: BillingDatabase,
    private val syncScheduler: SyncScheduler,
    private val sessionStore: SessionStore
) {

    private val OPERATION_PRECEDENCE = mapOf("DELETE" to 3, "INSERT" to 2, "UPDATE" to 1)

    suspend fun enqueueCategory(category: com.kadaikutty.pos.feature.masters.data.CategoryEntity, operation: String) {
        val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
        val companyId = session.companyId
        val payload = toJson(mapOf(
            "id" to category.id,
            "companyId" to companyId,
            "name" to category.name,
            "createdAtEpochMs" to category.createdAtEpochMs,
            "updatedAtEpochMs" to category.updatedAtEpochMs
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
            "updatedAtEpochMs" to product.updatedAtEpochMs
        ))
        enqueueItem(companyId, "Product", product.id, operation, payload)
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
            "updatedAtEpochMs" to customer.updatedAtEpochMs
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
            "updatedAtEpochMs" to supplier.updatedAtEpochMs
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
            "updatedAtEpochMs" to expense.updatedAtEpochMs
        ))
        enqueueItem(companyId, "Expense", expense.id, operation, payload)
    }

    suspend fun enqueueSale(sale: com.kadaikutty.pos.feature.billing.data.SaleEntity, items: List<com.kadaikutty.pos.feature.billing.data.SaleItemEntity>, operation: String = "INSERT") {
        val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
        val companyId = session.companyId
        val itemsList = items.map { item ->
            mapOf(
                "companyId" to companyId,
                "productId" to item.productId,
                "quantity" to item.quantity,
                "unitPriceMinorUnits" to item.unitPriceMinorUnits,
                "lineTotalMinorUnits" to item.lineTotalMinorUnits
            )
        }
        val payload = toJson(mapOf(
            "id" to sale.id,
            "companyId" to companyId,
            "billNumber" to sale.billNumber,
            "totalMinorUnits" to sale.totalMinorUnits,
            "createdAtEpochMs" to sale.createdAtEpochMs,
            "customerId" to sale.customerId,
            "items" to itemsList
        ))
        enqueueItem(companyId, "Sale", sale.id, operation, payload)
    }

    suspend fun enqueuePurchase(purchase: com.kadaikutty.pos.feature.purchase.data.PurchaseEntity, items: List<com.kadaikutty.pos.feature.purchase.data.PurchaseItemEntity>) {
        val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
        val companyId = session.companyId
        val itemsList = items.map { item ->
            mapOf(
                "companyId" to companyId,
                "productId" to item.productId,
                "quantity" to item.quantity,
                "unitValueMinorUnits" to item.unitValueMinorUnits,
                "lineTotalMinorUnits" to item.lineTotalMinorUnits
            )
        }
        val payload = toJson(mapOf(
            "id" to purchase.id,
            "companyId" to companyId,
            "supplierId" to purchase.supplierId,
            "totalMinorUnits" to purchase.totalMinorUnits,
            "createdAtEpochMs" to purchase.createdAtEpochMs,
            "items" to itemsList
        ))
        enqueueItem(companyId, "Purchase", purchase.id, "INSERT", payload)
    }

    suspend fun enqueueCustomerCredit(credit: com.kadaikutty.pos.feature.masters.data.CustomerCreditEntity, operation: String) {
        val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
        val companyId = session.companyId
        val payload = toJson(mapOf(
            "id" to credit.id,
            "companyId" to companyId,
            "customerId" to credit.customerId,
            "amountMinorUnits" to credit.amountMinorUnits,
            "reason" to credit.reason,
            "dateEpochMs" to credit.dateEpochMs
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
            "terms" to credit.terms,
            "dueDateEpochMs" to credit.dueDateEpochMs,
            "dateEpochMs" to credit.dateEpochMs
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
            "createdAtEpochMs" to movement.createdAtEpochMs
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

    private suspend fun enqueueItem(
        companyId: String,
        entityType: String,
        entityId: String,
        operation: String,
        payloadJson: String
    ) {
        val existing = database.syncQueueDao().findPending(companyId, entityType, entityId)
        val now = System.currentTimeMillis()

        if (existing != null) {
            val incomingPrec = OPERATION_PRECEDENCE[operation] ?: 1
            val existingPrec = OPERATION_PRECEDENCE[existing.operation] ?: 1

            if (incomingPrec >= existingPrec) {
                if (operation == "PARTIAL_UPDATE" && existing.operation == "PARTIAL_UPDATE") {
                    // Merge payloads if both are partial updates
                    try {
                        val existingMap = org.json.JSONObject(existing.payload)
                        val incomingMap = org.json.JSONObject(payloadJson)
                        val incomingKeys = incomingMap.keys()
                        while (incomingKeys.hasNext()) {
                            val key = incomingKeys.next()
                            existingMap.put(key, incomingMap.get(key))
                        }
                        database.syncQueueDao().updatePending(existing.id, operation, existingMap.toString(), now)
                    } catch (e: Exception) {
                        database.syncQueueDao().updatePending(existing.id, operation, payloadJson, now)
                    }
                } else if (operation == "PARTIAL_UPDATE" && existing.operation == "INSERT") {
                    // If it's an insert in queue, merge partial updates into the insert payload
                    try {
                        val existingMap = org.json.JSONObject(existing.payload)
                        val incomingMap = org.json.JSONObject(payloadJson)
                        val incomingKeys = incomingMap.keys()
                        while (incomingKeys.hasNext()) {
                            val key = incomingKeys.next()
                            existingMap.put(key, incomingMap.get(key))
                        }
                        database.syncQueueDao().updatePending(existing.id, "INSERT", existingMap.toString(), now)
                    } catch (e: Exception) {
                        // ignore
                    }
                } else {
                    database.syncQueueDao().updatePending(existing.id, operation, payloadJson, now)
                }
            }
            syncScheduler.request()
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
        syncScheduler.request()
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
        // For customer credits and supplier credits, we don't have a simple getAll getter. Let's just grab sales and purchases.
        
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
        
        // Enqueue StockMovements
        // Ideally we'd have a getStockMovements query, but we can skip bulk enqueue for stock movements
        // as they are created with sales and purchases. But we could fetch all via MasterDao or similar if needed.
        
        // Trigger the scheduler immediately
        syncScheduler.request()
    }

    private fun toJson(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> "\"${value.replace("\"", "\\\"")}\""
            is Number, is Boolean -> "$value"
            is Map<*, *> -> {
                value.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
                    "\"$k\":${toJson(v)}"
                }
            }
            is List<*> -> {
                value.joinToString(prefix = "[", postfix = "]") { toJson(it) }
            }
            else -> "\"$value\""
        }
    }
}
