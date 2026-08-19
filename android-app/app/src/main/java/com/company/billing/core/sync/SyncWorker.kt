package com.company.billing.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.company.billing.core.database.BillingDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncEntryPoint {
        fun database(): BillingDatabase
        fun supabase(): SupabaseClient
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncEntryPoint::class.java
        )
        val database = entryPoint.database()
        val supabase = entryPoint.supabase()

        val syncQueueDao = database.syncQueueDao()

        try {
            while (true) {
                val pendingItems = syncQueueDao.pending(50)
                if (pendingItems.isEmpty()) break

                var allSuccessful = true
                val now = System.currentTimeMillis()

                for (item in pendingItems) {
                    try {
                        val operation = item.operation
                        val entityId = item.entityId
                        val entityType = item.entityType
                        val payload = item.payload

                        when (entityType) {
                            "Category" -> {
                                val table = supabase.from("categories")
                                if (operation == "DELETE") {
                                    table.delete { filter { eq("id", entityId) } }
                                } else {
                                    val local = json.decodeFromString<CategoryLocal>(payload)
                                    table.upsert(local.toSupabase())
                                }
                            }
                            "Product" -> {
                                val table = supabase.from("products")
                                if (operation == "DELETE") {
                                    table.delete { filter { eq("id", entityId) } }
                                } else {
                                    val local = json.decodeFromString<ProductLocal>(payload)
                                    table.upsert(local.toSupabase())
                                }
                            }
                            "Customer" -> {
                                val table = supabase.from("customers")
                                if (operation == "DELETE") {
                                    table.delete { filter { eq("id", entityId) } }
                                } else {
                                    val local = json.decodeFromString<CustomerLocal>(payload)
                                    table.upsert(local.toSupabase())
                                }
                            }
                            "Supplier" -> {
                                val table = supabase.from("suppliers")
                                if (operation == "DELETE") {
                                    table.delete { filter { eq("id", entityId) } }
                                } else {
                                    val local = json.decodeFromString<SupplierLocal>(payload)
                                    table.upsert(local.toSupabase())
                                }
                            }
                            "Expense" -> {
                                val table = supabase.from("expenses")
                                if (operation == "DELETE") {
                                    table.delete { filter { eq("id", entityId) } }
                                } else {
                                    val local = json.decodeFromString<ExpenseLocal>(payload)
                                    table.upsert(local.toSupabase())
                                }
                            }
                            "Sale" -> {
                                if (operation == "DELETE") {
                                    supabase.from("sales").delete { filter { eq("id", entityId) } }
                                } else {
                                    val local = json.decodeFromString<SaleLocal>(payload)
                                    // Upsert sale
                                    supabase.from("sales").upsert(
                                        SupabaseSale(
                                            id = local.id,
                                            billNumber = local.billNumber,
                                            totalMinorUnits = local.totalMinorUnits,
                                            createdAtEpochMs = local.createdAtEpochMs,
                                            customerId = local.customerId
                                        )
                                    )
                                    // Upsert sale items
                                    val supabaseItems = local.items.map { sItem ->
                                        SupabaseSaleItem(
                                            saleId = local.id,
                                            productId = sItem.productId,
                                            quantity = sItem.quantity,
                                            unitPriceMinorUnits = sItem.unitPriceMinorUnits,
                                            lineTotalMinorUnits = sItem.lineTotalMinorUnits
                                        )
                                    }
                                    supabase.from("sale_items").upsert(supabaseItems)
                                }
                            }
                            "Purchase" -> {
                                if (operation == "DELETE") {
                                    supabase.from("purchases").delete { filter { eq("id", entityId) } }
                                } else {
                                    val local = json.decodeFromString<PurchaseLocal>(payload)
                                    // Upsert purchase
                                    supabase.from("purchases").upsert(
                                        SupabasePurchase(
                                            id = local.id,
                                            supplierId = local.supplierId,
                                            totalMinorUnits = local.totalMinorUnits,
                                            createdAtEpochMs = local.createdAtEpochMs
                                        )
                                    )
                                    // Upsert purchase items
                                    val supabaseItems = local.items.map { pItem ->
                                        SupabasePurchaseItem(
                                            purchaseId = local.id,
                                            productId = pItem.productId,
                                            quantity = pItem.quantity,
                                            unitValueMinorUnits = pItem.unitValueMinorUnits,
                                            lineTotalMinorUnits = pItem.lineTotalMinorUnits
                                        )
                                    }
                                    supabase.from("purchase_items").upsert(supabaseItems)
                                }
                            }
                        }

                        syncQueueDao.updateStatus(item.id, SyncStatus.SYNCED, now)
                        updateEntitySyncStatus(database, entityType, entityId, "SYNCED")
                    } catch (e: Exception) {
                        if (isNetworkException(e)) {
                            // If network failure, do not fail permanently. Return retry.
                            return Result.retry()
                        }
                        allSuccessful = false
                        syncQueueDao.updateStatus(item.id, SyncStatus.FAILED, now, e.message ?: "Unknown sync error")
                        updateEntitySyncStatus(database, item.entityType, item.entityId, "FAILED")
                    }
                }

                if (!allSuccessful) {
                    return Result.failure()
                }
            }
            return Result.success()
        } catch (e: Exception) {
            return Result.failure()
        }
    }

    private fun isNetworkException(e: Throwable): Boolean {
        return e is java.io.IOException || 
               e is java.net.ConnectException || 
               e is java.net.UnknownHostException || 
               e is java.net.SocketTimeoutException || 
               e.javaClass.name.contains("ktor", ignoreCase = true)
    }

    private fun updateEntitySyncStatus(database: BillingDatabase, entityType: String, id: String, status: String) {
        val tableName = when (entityType) {
            "Category" -> "categories"
            "Product" -> "products"
            "Customer" -> "customers"
            "Supplier" -> "suppliers"
            "Expense" -> "expenses"
            "Sale" -> "sales"
            "Purchase" -> "purchases"
            else -> null
        }
        if (tableName != null) {
            try {
                database.openHelper.writableDatabase.execSQL(
                    "UPDATE $tableName SET syncStatus = '$status' WHERE id = '$id'"
                )
            } catch (ignored: Exception) {
            }
        }
    }
}
