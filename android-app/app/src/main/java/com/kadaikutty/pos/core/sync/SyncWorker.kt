package com.kadaikutty.pos.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.kadaikutty.pos.core.common.newRecordId
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.database.SyncDeadLetterEntity
import com.kadaikutty.pos.core.database.SyncQueueEntity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private enum class EntitySyncResult { SUCCESS, FAILURE, RETRY }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncEntryPoint {
        fun database(): BillingDatabase
        fun sessionStore(): com.kadaikutty.pos.core.auth.SessionStore
        fun analyticsManager(): com.kadaikutty.pos.core.analytics.AnalyticsManager
    }

    private val MaxRetryAttempts = 5
    private val BaseRetryDelayMinutes = 1L

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncEntryPoint::class.java,
        )
        val database = entryPoint.database()
        val sessionStore = entryPoint.sessionStore()
        val analyticsManager = entryPoint.analyticsManager()

        val activeSession = sessionStore.activeSession.first() ?: return Result.success()
        val companyId = activeSession.companyId
        
        val syncQueueDao = database.syncQueueDao()
        val firestore = FirebaseFirestore.getInstance()

        try {
            var cursor = 0L
            val batchSize = 50
            var totalSynced = 0
            val syncStartTime = System.currentTimeMillis()

            while (true) {
                val pendingItems = syncQueueDao.pendingAfterCursor(companyId, cursor, batchSize)
                if (pendingItems.isEmpty()) break

                var allSuccessful = true
                val now = System.currentTimeMillis()

                val itemsByEntityType = pendingItems.groupBy { it.entityType }

                val results = coroutineScope {
                    itemsByEntityType.map { (entityType, items) ->
                        async {
                            processEntityType(entityType, items, companyId, firestore, syncQueueDao, database, now)
                        }
                    }.awaitAll()
                }

                var hasRetry = false
                var hasFailure = false

                for (result in results) {
                    when (result) {
                        EntitySyncResult.FAILURE -> hasFailure = true
                        EntitySyncResult.RETRY -> hasRetry = true
                        EntitySyncResult.SUCCESS -> totalSynced++
                    }
                }

                if (hasFailure || hasRetry) {
                    allSuccessful = false
                }

                if (pendingItems.isNotEmpty()) {
                    cursor = pendingItems.maxByOrNull { it.createdAtEpochMs }?.createdAtEpochMs ?: cursor
                }

                if (!allSuccessful) {
                    return if (hasRetry && !hasFailure) Result.retry() else Result.failure()
                }
            }

            if (totalSynced > 0) {
                val durationMs = System.currentTimeMillis() - syncStartTime
                analyticsManager.logEvent(
                    com.kadaikutty.pos.core.analytics.AnalyticsEvents.EVENT_SYNC_COMPLETED,
                    mapOf(
                        com.kadaikutty.pos.core.analytics.AnalyticsEvents.PARAM_RECORDS_PUSHED to totalSynced * batchSize,
                        com.kadaikutty.pos.core.analytics.AnalyticsEvents.PARAM_SYNC_DURATION to durationMs
                    )
                )
            }
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure()
        }
    }

    private suspend fun processEntityType(
        entityType: String,
        items: List<SyncQueueEntity>,
        companyId: String,
        firestore: FirebaseFirestore,
        syncQueueDao: com.kadaikutty.pos.core.database.SyncQueueDao,
        database: BillingDatabase,
        now: Long
    ): EntitySyncResult {
        var allSuccessful = true
        val collectionName = getCollectionName(entityType)
        
        if (collectionName == null) {
            items.forEach { 
                syncQueueDao.updateStatus(it.id, SyncStatus.FAILED, now, "Unknown entity type") 
            }
            return EntitySyncResult.FAILURE
        }

        for (item in items) {
            val operation = item.operation
            val entityId = item.entityId
            val payload = item.payload

            try {
                if (operation != "DELETE") {
                    val localUpdatedAt = extractUpdatedAtFromPayload(payload)
                    if ((localUpdatedAt > 0) && (item.lastSyncedAtEpochMs >= localUpdatedAt)) {
                        syncQueueDao.updateStatus(item.id, SyncStatus.SYNCED, now)
                        syncQueueDao.updateLastSyncedAt(item.id, now)
                        updateEntitySyncStatus(database, entityType, entityId, "SYNCED")
                        continue
                    }
                }

                val docRef = firestore.collection("users").document(companyId).collection(collectionName).document(entityId)

                if (operation == "DELETE") {
                    docRef.set(mapOf(
                        "isDeleted" to true, 
                        "updatedAtEpochMs" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    ), com.google.firebase.firestore.SetOptions.merge()).await()
                } else {
                    val jsonMap = jsonObjectToMap(JSONObject(payload)).toMutableMap()
                    jsonMap["updatedAtEpochMs"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
                    docRef.set(jsonMap, com.google.firebase.firestore.SetOptions.merge()).await()
                }

                syncQueueDao.updateStatus(item.id, SyncStatus.SYNCED, now)
                syncQueueDao.updateLastSyncedAt(item.id, now)
                updateEntitySyncStatus(database, entityType, entityId, "SYNCED")

            } catch (e: Exception) {
                val attemptCount = item.attemptCount + 1
                if (isNetworkException(e) || isRetryableError(e)) {
                    if (attemptCount < MaxRetryAttempts) {
                        val delayMinutes = BaseRetryDelayMinutes * (2L shl (attemptCount - 1))
                        syncQueueDao.updateStatus(item.id, SyncStatus.PENDING, now,
                            "Retry $attemptCount/$MaxRetryAttempts in ${delayMinutes}min: ${e.message ?: "Unknown error"}")
                        syncQueueDao.updateAttemptCount(item.id, attemptCount)
                        return EntitySyncResult.RETRY
                    } else {
                        allSuccessful = false
                        moveToDeadLetter(database, syncQueueDao, item, companyId, attemptCount, now, e)
                    }
                } else {
                    allSuccessful = false
                    syncQueueDao.updateStatus(item.id, SyncStatus.FAILED, now, e.message ?: "Unknown sync error")
                    updateEntitySyncStatus(database, item.entityType, item.entityId, "FAILED")
                }
            }
        }

        if (!allSuccessful) {
            return EntitySyncResult.FAILURE
        }
        return EntitySyncResult.SUCCESS
    }

    private suspend fun moveToDeadLetter(
        database: BillingDatabase,
        syncQueueDao: com.kadaikutty.pos.core.database.SyncQueueDao,
        item: SyncQueueEntity,
        companyId: String,
        attemptCount: Int,
        now: Long,
        e: Exception
    ) {
        val deadLetter = SyncDeadLetterEntity(
            id = newRecordId(),
            companyId = companyId,
            entityType = item.entityType,
            entityId = item.entityId,
            operation = item.operation,
            payload = item.payload,
            lastError = "Max retries ($MaxRetryAttempts) exceeded: ${e.message ?: "Unknown error"}",
            attemptCount = attemptCount,
            createdAtEpochMs = item.createdAtEpochMs,
            lastAttemptAtEpochMs = now,
            originalQueueId = item.id
        )
        database.syncDeadLetterDao().insert(deadLetter)
        syncQueueDao.updateStatus(item.id, SyncStatus.FAILED, now, "Moved to dead letter after max retries")
        updateEntitySyncStatus(database, item.entityType, item.entityId, "FAILED")
    }

    private fun getCollectionName(entityType: String): String? = when (entityType) {
        "Category" -> "categories"
        "Product" -> "products"
        "Customer" -> "customers"
        "Supplier" -> "suppliers"
        "Expense" -> "expenses"
        "Sale" -> "sales"
        "Purchase" -> "purchases"
        "CustomerCredit" -> "customer_credits"
        "SupplierCredit" -> "supplier_credits"
        "StockMovement" -> "stock_movements"
        else -> null
    }

    private fun isNetworkException(e: Throwable): Boolean {
        val message = e.message?.lowercase() ?: ""
        return e is java.io.IOException || message.contains("network") || message.contains("timeout") || message.contains("offline")
    }

    private fun isRetryableError(e: Throwable): Boolean {
        val message = e.message?.lowercase() ?: ""
        return message.contains("unavailable") || 
               message.contains("deadline_exceeded") ||
               message.contains("500") ||
               message.contains("503")
    }

    private fun updateEntitySyncStatus(database: BillingDatabase, entityType: String, id: String, status: String) {
        val tableName = getCollectionName(entityType)
        if (tableName != null) {
            try {
                database.openHelper.writableDatabase.execSQL(
                    "UPDATE $tableName SET syncStatus = '$status' WHERE id = '$id'"
                )
            } catch (_: Exception) {}
        }
    }

    private fun extractUpdatedAtFromPayload(payload: String): Long {
        return try {
            val start = payload.indexOf("\"updatedAtEpochMs\":")
            if (start == -1) return 0L
            val valueStart = start + "\"updatedAtEpochMs\":".length
            var valueEnd = payload.indexOf(',', valueStart)
            if (valueEnd == -1) valueEnd = payload.indexOf('}', valueStart)
            if (valueEnd == -1) return 0L
            val valueStr = payload.substring(valueStart, valueEnd).trim()
            valueStr.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun jsonObjectToMap(json: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            var value = json.get(key)
            if (value is JSONArray) {
                value = jsonArrayToList(value)
            } else if (value is JSONObject) {
                value = jsonObjectToMap(value)
            } else if (value == JSONObject.NULL) {
                continue
            }
            map[key] = value
        }
        return map
    }

    private fun jsonArrayToList(array: JSONArray): List<Any> {
        val list = mutableListOf<Any>()
        for (i in 0 until array.length()) {
            var value = array.get(i)
            if (value is JSONArray) {
                value = jsonArrayToList(value)
            } else if (value is JSONObject) {
                value = jsonObjectToMap(value)
            } else if (value == JSONObject.NULL) {
                continue
            }
            list.add(value)
        }
        return list
    }
}
