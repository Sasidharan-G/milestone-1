package com.company.billing.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.company.billing.core.database.BillingDatabase
import com.company.billing.core.network.BillingApi
import com.company.billing.core.network.SyncBatchItem
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.IOException

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncEntryPoint {
        fun database(): BillingDatabase
        fun api(): BillingApi
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncEntryPoint::class.java
        )
        val database = entryPoint.database()
        val api = entryPoint.api()

        val syncQueueDao = database.syncQueueDao()

        try {
            while (true) {
                val pendingItems = syncQueueDao.pending(50)
                if (pendingItems.isEmpty()) break

                val batch = pendingItems.map {
                    SyncBatchItem(
                        id = it.id,
                        entityType = it.entityType,
                        entityId = it.entityId,
                        operation = it.operation,
                        payload = it.payload
                    )
                }

                val response = try {
                    api.syncBatch(batch)
                } catch (ioe: IOException) {
                    return Result.retry()
                } catch (e: Exception) {
                    null
                }

                val now = System.currentTimeMillis()
                if (response != null && response.isSuccessful) {
                    for (item in pendingItems) {
                        syncQueueDao.updateStatus(item.id, SyncStatus.SYNCED, now)
                        updateEntitySyncStatus(database, item.entityType, item.entityId, "SYNCED")
                    }
                } else {
                    val errMsg = response?.errorBody()?.string() ?: "Unknown backend error"
                    for (item in pendingItems) {
                        syncQueueDao.updateStatus(item.id, SyncStatus.FAILED, now, errMsg)
                        updateEntitySyncStatus(database, item.entityType, item.entityId, "FAILED")
                    }
                    return Result.failure()
                }
            }
            return Result.success()
        } catch (e: Exception) {
            return Result.failure()
        }
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
