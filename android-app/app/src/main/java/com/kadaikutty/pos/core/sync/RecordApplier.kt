package com.kadaikutty.pos.core.sync

import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.feature.billing.data.SaleEntity
import com.kadaikutty.pos.feature.billing.data.SaleItemEntity
import com.kadaikutty.pos.feature.billing.data.StockMovementEntity
import com.kadaikutty.pos.feature.masters.data.CategoryEntity
import com.kadaikutty.pos.feature.masters.data.CustomerCreditEntity
import com.kadaikutty.pos.feature.masters.data.CustomerEntity
import com.kadaikutty.pos.feature.masters.data.ExpenseEntity
import com.kadaikutty.pos.feature.masters.data.ProductEntity
import com.kadaikutty.pos.feature.masters.data.SupplierCreditEntity
import com.kadaikutty.pos.feature.masters.data.SupplierEntity
import com.kadaikutty.pos.feature.purchase.data.PurchaseEntity
import com.kadaikutty.pos.feature.purchase.data.PurchaseItemEntity
import org.json.JSONObject

// Applies one entity's worth of change (upsert or delete) directly to the local database, keyed
// by the same {entityType, entityId, payload} shape used both by cloud sync records (PullWorker)
// and by the live local backup changelog (LiveBackupWriter), so both can replay through one path.
object RecordApplier {
    suspend fun deleteRecord(database: BillingDatabase, companyId: String, type: String, id: String) {
        when (type) {
            "Category" -> database.masterDao().deleteCategoryById(companyId, id)
            "Product" -> database.masterDao().deleteProductById(companyId, id)
            "Customer" -> database.masterDao().deleteCustomerById(companyId, id)
            "Supplier" -> database.masterDao().deleteSupplierById(companyId, id)
            "Expense" -> database.masterDao().deleteExpenseById(companyId, id)
            "CustomerCredit" -> database.masterDao().deleteCustomerCreditById(companyId, id)
            "SupplierCredit" -> database.masterDao().deleteSupplierCreditById(companyId, id)
            "Sale" -> database.saleDao().deleteSale(companyId, id)
            "Purchase" -> database.purchaseDao().deletePurchase(companyId, id)
            "StockMovement" -> database.saleDao().deleteStockMovementById(companyId, id)
        }
    }

    suspend fun upsertRecord(database: BillingDatabase, companyId: String, type: String, id: String, data: JSONObject, serverUpdatedAt: Long) {
        val updatedAt = maxOf(serverUpdatedAt, data.optLong("updatedAtEpochMs"))
        when (type) {
            "Category" -> database.masterDao().insertCategory(CategoryEntity(id, companyId, data.optString("name"), data.optLong("createdAtEpochMs"), updatedAt, SyncStatus.SYNCED))
            "Product" -> {
                fun cName(s: String): String = s.replace("﻿", "").replace("​", "").trim().replace("\\s+".toRegex(), " ").lowercase()
                fun cBarcode(s: String?): String? {
                    if (s.isNullOrBlank()) return null
                    var b = s.replace("﻿", "").replace("​", "").trim()
                    if (b.endsWith(".0") || b.endsWith(".00")) b = b.substringBefore(".")
                    return if (b.isBlank()) null else b
                }
                val incomingName = data.optString("name")
                val incomingBarcode = cBarcode(data.stringOrNull("barcode"))
                val normIncomingName = cName(incomingName)
                val existingList = database.masterDao().getAllProducts(companyId)
                // Name matching exists to merge a product this device created offline with the
                // one the cloud assigned an id to. It is restricted to rows that have never
                // synced: a row already carrying a server id is a distinct product, and two
                // real products sharing a name must not collapse into one.
                val existingProduct = existingList.find { it.id == id }
                    ?: (if (incomingBarcode != null) existingList.find { cBarcode(it.barcode) == incomingBarcode } else null)
                    ?: (if (normIncomingName.isNotBlank()) {
                        existingList.find { cName(it.name) == normIncomingName && it.syncStatus != SyncStatus.SYNCED }
                    } else null)
                val targetId = existingProduct?.id ?: id
                database.masterDao().insertProduct(ProductEntity(targetId, companyId, incomingName, data.optString("categoryId"), data.optLong("purchasePriceMinorUnits"), data.optLong("salePriceMinorUnits"), data.optString("unitType", "PIECE"), incomingBarcode, data.optDouble("minStockLevel"), data.optLong("createdAtEpochMs"), updatedAt, SyncStatus.SYNCED))
            }
            "Customer" -> database.masterDao().insertCustomer(CustomerEntity(id, companyId, data.optString("name"), data.stringOrNull("phone"), data.stringOrNull("address"), data.optLong("creditLimitMinorUnits"), data.optLong("createdAtEpochMs"), updatedAt, SyncStatus.SYNCED))
            "Supplier" -> database.masterDao().insertSupplier(SupplierEntity(id, companyId, data.optString("name"), data.stringOrNull("phone"), data.stringOrNull("address"), data.optLong("createdAtEpochMs"), updatedAt, SyncStatus.SYNCED))
            "Expense" -> database.masterDao().insertExpense(ExpenseEntity(id, companyId, data.optLong("amountMinorUnits"), data.optString("description"), data.optLong("createdAtEpochMs"), updatedAt, SyncStatus.SYNCED))
            "CustomerCredit" -> database.masterDao().insertCustomerCredit(CustomerCreditEntity(id = id, companyId = companyId, customerId = data.optString("customerId"), amountMinorUnits = data.optLong("amountMinorUnits"), reason = data.optString("reason"), dateEpochMs = data.optLong("dateEpochMs"), referenceId = data.stringOrNull("referenceId"), syncStatus = SyncStatus.SYNCED))
            "SupplierCredit" -> database.masterDao().insertSupplierCredit(SupplierCreditEntity(id = id, companyId = companyId, supplierId = data.optString("supplierId"), amountMinorUnits = data.optLong("amountMinorUnits"), terms = data.optString("terms"), dueDateEpochMs = data.optLong("dueDateEpochMs"), dateEpochMs = data.optLong("dateEpochMs"), referenceId = data.stringOrNull("referenceId"), syncStatus = SyncStatus.SYNCED))
            "Sale" -> {
                database.saleDao().insertSale(SaleEntity(id = id, companyId = companyId, billNumber = data.optString("billNumber"), totalMinorUnits = data.optLong("totalMinorUnits"), createdAtEpochMs = data.optLong("createdAtEpochMs"), syncStatus = SyncStatus.SYNCED, customerId = data.stringOrNull("customerId"), paymentMode = data.optString("paymentMode", "CASH"), paidCashMinorUnits = data.optLong("paidCashMinorUnits"), paidUpiMinorUnits = data.optLong("paidUpiMinorUnits"), creditAppliedMinorUnits = data.optLong("creditAppliedMinorUnits"), discountMinorUnits = data.optLong("discountMinorUnits"), revision = data.optLong("revision")))
                val items = data.optJSONArray("items")
                if (items != null) database.saleDao().insertItems((0 until items.length()).map { index ->
                    val item = items.getJSONObject(index)
                    SaleItemEntity(companyId, id, item.optString("productId"), item.optLong("quantity"), item.optLong("unitPriceMinorUnits"), item.optLong("lineTotalMinorUnits"), item.optLong("discountMinorUnits"), item.stringOrNull("unitType"), item.stringOrNull("productName"), item.longOrNull("costTotalMinorUnits"), item.longOrNull("netRevenueMinorUnits"))
                })
            }
            "Purchase" -> {
                database.purchaseDao().insertPurchase(PurchaseEntity(id = id, companyId = companyId, supplierId = data.optString("supplierId"), totalMinorUnits = data.optLong("totalMinorUnits"), createdAtEpochMs = data.optLong("createdAtEpochMs"), syncStatus = SyncStatus.SYNCED, invoiceNumber = data.stringOrNull("invoiceNumber"), notes = data.stringOrNull("notes"), paymentMode = data.optString("paymentMode", "CASH"), paidCashMinorUnits = data.optLong("paidCashMinorUnits"), paidUpiMinorUnits = data.optLong("paidUpiMinorUnits"), creditAppliedMinorUnits = data.optLong("creditAppliedMinorUnits"), orderNumber = data.stringOrNull("orderNumber"), revision = data.optLong("revision")))
                val items = data.optJSONArray("items")
                if (items != null) database.purchaseDao().insertItems((0 until items.length()).map { index ->
                    val item = items.getJSONObject(index)
                    PurchaseItemEntity(companyId, id, item.optString("productId"), item.optLong("quantity"), item.optLong("unitValueMinorUnits"), item.optLong("lineTotalMinorUnits"), item.stringOrNull("unitType"))
                })
            }
            "StockMovement" -> database.saleDao().insertStockMovements(listOf(StockMovementEntity(id, companyId, data.optString("productId"), data.optLong("quantityDelta"), data.optString("type"), data.optString("referenceId"), data.optLong("createdAtEpochMs"))))
        }
    }
}

fun JSONObject.stringOrNull(key: String): String? = if (isNull(key) || !has(key)) null else optString(key).takeIf { it.isNotBlank() }
fun JSONObject.longOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else optLong(key)
