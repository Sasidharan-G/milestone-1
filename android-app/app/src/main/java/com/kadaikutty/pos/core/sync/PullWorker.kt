package com.kadaikutty.pos.core.sync

import android.content.Context
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kadaikutty.pos.core.auth.SessionStore
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.database.LocalOperationEntity
import com.kadaikutty.pos.core.network.BackendApiClient
import com.kadaikutty.pos.core.network.BackendApiException
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
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import org.json.JSONObject

class PullWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PullEntryPoint {
        fun database(): BillingDatabase
        fun sessionStore(): SessionStore
        fun backendApiClient(): BackendApiClient
    }

    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, PullEntryPoint::class.java)
        val database = entry.database()
        val session = entry.sessionStore().activeSession.first() ?: return Result.success()
        val token = session.accessToken ?: return Result.success()
        val cursorKey = "sync_pull_cursor"
        var cursor = database.localOperationDao().get(session.companyId, cursorKey) ?: "0"
        return try {
            do {
                val response = entry.backendApiClient().pullSync(token, session.companyId, cursor)
                val records = response.optJSONArray("records") ?: response.optJSONArray("data")
                val nextCursor = response.optString("nextCursor", cursor)
                database.withTransaction {
                    if (records != null) for (index in 0 until records.length()) applyRecord(database, session.companyId, records.getJSONObject(index))
                    database.localOperationDao().put(LocalOperationEntity(session.companyId, cursorKey, nextCursor))
                }
                cursor = nextCursor
                val hasMore = response.optBoolean("hasMore", false)
            } while (hasMore)
            Result.success()
        } catch (error: BackendApiException) {
            if (error.retryable) Result.retry() else Result.failure(errorData(error.message))
        } catch (error: java.io.IOException) {
            Result.retry()
        } catch (error: Exception) {
            Result.failure(errorData(error.message ?: "Cloud pull failed"))
        }
    }

    private suspend fun applyRecord(database: BillingDatabase, companyId: String, record: JSONObject) {
        require(record.getString("companyId") == companyId) { "Cross-tenant sync record rejected" }
        val type = record.getString("entityType")
        val id = record.getString("entityId")
        val version = record.getLong("version")
        if (record.optBoolean("deleted")) {
            deleteRecord(database, companyId, type, id)
        } else {
            upsertRecord(database, companyId, type, id, record.getJSONObject("payload"), record.optLong("updatedAtEpochMs"))
        }
        database.localOperationDao().put(LocalOperationEntity(companyId, "cloud_version:$type:$id", version.toString()))
    }

    private suspend fun deleteRecord(database: BillingDatabase, companyId: String, type: String, id: String) {
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

    private suspend fun upsertRecord(database: BillingDatabase, companyId: String, type: String, id: String, data: JSONObject, serverUpdatedAt: Long) {
        val updatedAt = maxOf(serverUpdatedAt, data.optLong("updatedAtEpochMs"))
        when (type) {
            "Category" -> database.masterDao().insertCategory(CategoryEntity(id, companyId, data.optString("name"), data.optLong("createdAtEpochMs"), updatedAt, SyncStatus.SYNCED))
            "Product" -> database.masterDao().insertProduct(ProductEntity(id, companyId, data.optString("name"), data.optString("categoryId"), data.optLong("purchasePriceMinorUnits"), data.optLong("salePriceMinorUnits"), data.optString("unitType", "PIECE"), data.stringOrNull("barcode"), data.optDouble("minStockLevel"), data.optLong("createdAtEpochMs"), updatedAt, SyncStatus.SYNCED))
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

    private fun errorData(message: String) = androidx.work.workDataOf("error_reason" to message, "error_next_steps" to "Check your connection or Sync Diagnostics, then retry.")
}

private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key) || !has(key)) null else optString(key).takeIf { it.isNotBlank() }
private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else optLong(key)
