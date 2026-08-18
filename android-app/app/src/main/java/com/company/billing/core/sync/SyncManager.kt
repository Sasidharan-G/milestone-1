package com.company.billing.core.sync

import com.company.billing.core.database.BillingDatabase
import com.company.billing.core.database.SyncQueueEntity
import com.company.billing.core.common.newRecordId

class SyncManager(
    private val database: BillingDatabase,
    private val syncScheduler: SyncScheduler
) {

    suspend fun enqueueCategory(category: com.company.billing.feature.masters.data.CategoryEntity, operation: String) {
        val payload = toJson(mapOf(
            "id" to category.id,
            "name" to category.name,
            "createdAtEpochMs" to category.createdAtEpochMs,
            "updatedAtEpochMs" to category.updatedAtEpochMs
        ))
        enqueueItem("Category", category.id, operation, payload)
    }

    suspend fun enqueueProduct(product: com.company.billing.feature.masters.data.ProductEntity, operation: String) {
        val payload = toJson(mapOf(
            "id" to product.id,
            "name" to product.name,
            "categoryId" to product.categoryId,
            "createdAtEpochMs" to product.createdAtEpochMs,
            "updatedAtEpochMs" to product.updatedAtEpochMs
        ))
        enqueueItem("Product", product.id, operation, payload)
    }

    suspend fun enqueueCustomer(customer: com.company.billing.feature.masters.data.CustomerEntity, operation: String) {
        val payload = toJson(mapOf(
            "id" to customer.id,
            "name" to customer.name,
            "createdAtEpochMs" to customer.createdAtEpochMs,
            "updatedAtEpochMs" to customer.updatedAtEpochMs
        ))
        enqueueItem("Customer", customer.id, operation, payload)
    }

    suspend fun enqueueSupplier(supplier: com.company.billing.feature.masters.data.SupplierEntity, operation: String) {
        val payload = toJson(mapOf(
            "id" to supplier.id,
            "name" to supplier.name,
            "createdAtEpochMs" to supplier.createdAtEpochMs,
            "updatedAtEpochMs" to supplier.updatedAtEpochMs
        ))
        enqueueItem("Supplier", supplier.id, operation, payload)
    }

    suspend fun enqueueExpense(expense: com.company.billing.feature.masters.data.ExpenseEntity, operation: String) {
        val payload = toJson(mapOf(
            "id" to expense.id,
            "amountMinorUnits" to expense.amountMinorUnits,
            "description" to expense.description,
            "createdAtEpochMs" to expense.createdAtEpochMs,
            "updatedAtEpochMs" to expense.updatedAtEpochMs
        ))
        enqueueItem("Expense", expense.id, operation, payload)
    }

    suspend fun enqueueSale(sale: com.company.billing.feature.billing.data.SaleEntity, items: List<com.company.billing.feature.billing.data.SaleItemEntity>) {
        val itemsList = items.map { item ->
            mapOf(
                "productId" to item.productId,
                "quantity" to item.quantity,
                "unitPriceMinorUnits" to item.unitPriceMinorUnits,
                "lineTotalMinorUnits" to item.lineTotalMinorUnits
            )
        }
        val payload = toJson(mapOf(
            "id" to sale.id,
            "billNumber" to sale.billNumber,
            "totalMinorUnits" to sale.totalMinorUnits,
            "createdAtEpochMs" to sale.createdAtEpochMs,
            "customerId" to sale.customerId,
            "items" to itemsList
        ))
        enqueueItem("Sale", sale.id, "INSERT", payload)
    }

    suspend fun enqueuePurchase(purchase: com.company.billing.feature.purchase.data.PurchaseEntity, items: List<com.company.billing.feature.purchase.data.PurchaseItemEntity>) {
        val itemsList = items.map { item ->
            mapOf(
                "productId" to item.productId,
                "quantity" to item.quantity,
                "unitValueMinorUnits" to item.unitValueMinorUnits,
                "lineTotalMinorUnits" to item.lineTotalMinorUnits
            )
        }
        val payload = toJson(mapOf(
            "id" to purchase.id,
            "supplierId" to purchase.supplierId,
            "totalMinorUnits" to purchase.totalMinorUnits,
            "createdAtEpochMs" to purchase.createdAtEpochMs,
            "items" to itemsList
        ))
        enqueueItem("Purchase", purchase.id, "INSERT", payload)
    }

    private suspend fun enqueueItem(
        entityType: String,
        entityId: String,
        operation: String,
        payloadJson: String
    ) {
        val syncItem = SyncQueueEntity(
            id = newRecordId(),
            entityType = entityType,
            entityId = entityId,
            operation = operation,
            payload = payloadJson,
            status = SyncStatus.PENDING,
            attemptCount = 0,
            createdAtEpochMs = System.currentTimeMillis(),
            updatedAtEpochMs = System.currentTimeMillis()
        )
        database.syncQueueDao().enqueue(syncItem)
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
