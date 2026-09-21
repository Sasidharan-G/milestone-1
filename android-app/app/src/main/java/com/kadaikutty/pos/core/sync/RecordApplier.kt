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
            "Purchase" -> { database.purchaseDao().deletePurchaseItems(companyId, id); database.purchaseDao().deletePurchase(companyId, id) }
            "StockMovement" -> database.saleDao().deleteStockMovementById(companyId, id)
        }
    }

    suspend fun upsertRecord(database: BillingDatabase, companyId: String, type: String, id: String, data: JSONObject, serverUpdatedAt: Long) {
        val updatedAt = maxOf(serverUpdatedAt, data.optLong("updatedAtEpochMs"))
        when (type) {
            "Category" -> database.masterDao().insertCategory(CategoryEntity(id, companyId, data.optString("name"), data.optLong("createdAtEpochMs"), updatedAt, SyncStatus.SYNCED))
            "Product" -> {
                // Matched by id only. Matching by name or barcode used to fold a product created on
                // another device into a local one with a different id, so the two devices ended up
                // with different product lists and that device's bill lines pointed at nothing.
                // Two products sharing a barcode are now kept apart and reported instead (see
                // ConflictResolver.preparePulledUpsert).
                val barcode = data.stringOrNull("barcode")?.replace("\uFEFF", "")?.replace("\u200B", "")?.trim()
                    ?.let { if (it.endsWith(".0") || it.endsWith(".00")) it.substringBefore(".") else it }?.ifBlank { null }
                database.masterDao().insertProduct(ProductEntity(id, companyId, data.optString("name"), data.optString("categoryId"), data.optLong("purchasePriceMinorUnits"), data.optLong("salePriceMinorUnits"), data.optString("unitType", "PIECE"), barcode, data.optDouble("minStockLevel", 0.0), data.optLong("createdAtEpochMs"), updatedAt, SyncStatus.SYNCED))
            }
            "Customer" -> database.masterDao().insertCustomer(CustomerEntity(id, companyId, data.optString("name"), data.stringOrNull("phone"), data.stringOrNull("address"), data.optLong("creditLimitMinorUnits"), data.optLong("createdAtEpochMs"), updatedAt, SyncStatus.SYNCED))
            "Supplier" -> database.masterDao().insertSupplier(SupplierEntity(id, companyId, data.optString("name"), data.stringOrNull("phone"), data.stringOrNull("address"), data.optLong("createdAtEpochMs"), updatedAt, SyncStatus.SYNCED))
            "Expense" -> database.masterDao().insertExpense(ExpenseEntity(id, companyId, data.optLong("amountMinorUnits"), data.optString("description"), data.optLong("createdAtEpochMs"), updatedAt, SyncStatus.SYNCED))
            "CustomerCredit" -> database.masterDao().insertCustomerCredit(CustomerCreditEntity(id = id, companyId = companyId, customerId = data.optString("customerId"), amountMinorUnits = data.optLong("amountMinorUnits"), reason = data.optString("reason"), dateEpochMs = data.optLong("dateEpochMs"), referenceId = data.stringOrNull("referenceId"), syncStatus = SyncStatus.SYNCED))
            "SupplierCredit" -> database.masterDao().insertSupplierCredit(SupplierCreditEntity(id = id, companyId = companyId, supplierId = data.optString("supplierId"), amountMinorUnits = data.optLong("amountMinorUnits"), terms = data.optString("terms"), dueDateEpochMs = data.optLong("dueDateEpochMs"), dateEpochMs = data.optLong("dateEpochMs"), referenceId = data.stringOrNull("referenceId"), syncStatus = SyncStatus.SYNCED))
            "Sale" -> {
                // A pulled bill replaces its lines wholesale: an edit that dropped a line must not
                // leave the old line behind.
                database.saleDao().deleteSaleItems(companyId, id)
                database.saleDao().insertSale(SaleEntity(id = id, companyId = companyId, billNumber = data.optString("billNumber"), totalMinorUnits = data.optLong("totalMinorUnits"), createdAtEpochMs = data.optLong("createdAtEpochMs"), syncStatus = SyncStatus.SYNCED, customerId = data.stringOrNull("customerId"), paymentMode = data.optString("paymentMode", "CASH"), paidCashMinorUnits = data.optLong("paidCashMinorUnits"), paidUpiMinorUnits = data.optLong("paidUpiMinorUnits"), creditAppliedMinorUnits = data.optLong("creditAppliedMinorUnits"), discountMinorUnits = data.optLong("discountMinorUnits"), revision = data.optLong("revision")))
                val items = data.optJSONArray("items")
                if (items != null) database.saleDao().insertItems((0 until items.length()).map { index ->
                    val item = items.getJSONObject(index)
                    SaleItemEntity(companyId, id, item.optString("productId"), item.optLong("quantity"), item.optLong("unitPriceMinorUnits"), item.optLong("lineTotalMinorUnits"), item.optLong("discountMinorUnits"), item.stringOrNull("unitType"), item.stringOrNull("productName"), item.longOrNull("costTotalMinorUnits"), item.longOrNull("netRevenueMinorUnits"))
                })
            }
            "Purchase" -> {
                // purchase_items has no cascade from purchases, so stale lines of an edited
                // purchase stayed forever and inflated stock and cost reports.
                database.purchaseDao().deletePurchaseItems(companyId, id)
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
