package com.company.billing.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.company.billing.core.common.newRecordId
import com.company.billing.core.database.BillingDatabase
import com.company.billing.core.database.SyncDeadLetterEntity
import com.company.billing.core.database.SyncQueueEntity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private enum class EntitySyncResult { SUCCESS, FAILURE, RETRY }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncEntryPoint {
        fun database(): BillingDatabase
        fun firestore(): FirebaseFirestore
        fun sessionStore(): com.company.billing.core.auth.SessionStore
    }

    private val MAX_RETRY_ATTEMPTS = 5
    private val BASE_RETRY_DELAY_MINUTES = 1L

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncEntryPoint::class.java
        )
        val database = entryPoint.database()
        val firestore = entryPoint.firestore()
        val sessionStore = entryPoint.sessionStore()

        val activeSession = sessionStore.activeSession.first() ?: return Result.success()
        val companyId = activeSession.companyId

        val syncQueueDao = database.syncQueueDao()

        try {
            var cursor = 0L
            val batchSize = 50

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
                        EntitySyncResult.SUCCESS -> {}
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
            return Result.success()
        } catch (e: Exception) {
            return Result.failure()
        }
    }

    private suspend fun processEntityType(
        entityType: String,
        items: List<SyncQueueEntity>,
        companyId: String,
        firestore: FirebaseFirestore,
        syncQueueDao: com.company.billing.core.database.SyncQueueDao,
        database: BillingDatabase,
        now: Long
    ): EntitySyncResult {
        var allSuccessful = true
        val collectionName = getCollectionName(entityType) ?: return EntitySyncResult.FAILURE

        for (item in items) {
            val operation = item.operation
            val entityId = item.entityId
            val payload = item.payload

            try {
                if (operation != "DELETE") {
                    val localUpdatedAt = extractUpdatedAtFromPayload(payload)
                    if (localUpdatedAt > 0 && item.lastSyncedAtEpochMs >= localUpdatedAt) {
                        syncQueueDao.updateStatus(item.id, SyncStatus.SYNCED, now)
                        syncQueueDao.updateLastSyncedAt(item.id, now)
                        updateEntitySyncStatus(database, entityType, entityId, "SYNCED")
                        continue
                    }
                }

                val docRef = firestore.collection("users").document(companyId).collection(collectionName).document(entityId)

                if (operation == "DELETE") {
                    docRef.delete().await()
                } else {
                    val map = jsonToMap(JSONObject(payload))
                    
                    // Special handling for Sales/Purchases to separate items subcollection
                    if (entityType == "Sale" || entityType == "Purchase") {
                        val itemsList = map["items"] as? List<Map<String, Any>>
                        val mainDoc = map.toMutableMap().apply { remove("items") }
                        docRef.set(mainDoc).await()
                        
                        if (itemsList != null) {
                            val itemsCollection = docRef.collection("items")
                            // Delete existing items to avoid duplicates
                            val existingItems = itemsCollection.get().await()
                            existingItems.documents.forEach { it.reference.delete() }
                            
                            // Insert new items
                            for (subItem in itemsList) {
                                itemsCollection.document().set(subItem).await()
                            }
                        }
                    } else {
                        docRef.set(map).await()
                    }
                }

                syncQueueDao.updateStatus(item.id, SyncStatus.SYNCED, now)
                syncQueueDao.updateLastSyncedAt(item.id, now)
                updateEntitySyncStatus(database, entityType, entityId, "SYNCED")
            } catch (e: Exception) {
                val attemptCount = item.attemptCount + 1
                if (isNetworkException(e) || isRetryableError(e)) {
                    if (attemptCount < MAX_RETRY_ATTEMPTS) {
                        val delayMinutes = BASE_RETRY_DELAY_MINUTES * (2L shl (attemptCount - 1))
                        syncQueueDao.updateStatus(item.id, SyncStatus.PENDING, now,
                            "Retry $attemptCount/$MAX_RETRY_ATTEMPTS in ${delayMinutes}min: ${e.message ?: "Unknown error"}")
                        syncQueueDao.updateAttemptCount(item.id, attemptCount)
                        return EntitySyncResult.RETRY
                    } else {
                        allSuccessful = false
                        val deadLetter = SyncDeadLetterEntity(
                            id = com.company.billing.core.common.newRecordId(),
                            companyId = companyId,
                            entityType = entityType,
                            entityId = entityId,
                            operation = operation,
                            payload = payload,
                            lastError = "Max retries ($MAX_RETRY_ATTEMPTS) exceeded: ${e.message ?: "Unknown error"}",
                            attemptCount = attemptCount,
                            createdAtEpochMs = item.createdAtEpochMs,
                            lastAttemptAtEpochMs = now,
                            originalQueueId = item.id
                        )
                        database.syncDeadLetterDao().insert(deadLetter)
                        syncQueueDao.updateStatus(item.id, SyncStatus.FAILED, now, "Moved to dead letter after max retries")
                        updateEntitySyncStatus(database, item.entityType, item.entityId, "FAILED")
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
        else -> null
    }

    private fun jsonToMap(json: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.get(key)
            if (value is org.json.JSONArray) {
                map[key] = jsonToList(value)
            } else if (value is JSONObject) {
                map[key] = jsonToMap(value)
            } else {
                map[key] = value
            }
        }
        return map
    }

    private fun jsonToList(array: org.json.JSONArray): List<Any> {
        val list = mutableListOf<Any>()
        for (i in 0 until array.length()) {
            val value = array.get(i)
            if (value is org.json.JSONArray) {
                list.add(jsonToList(value))
            } else if (value is JSONObject) {
                list.add(jsonToMap(value))
            } else {
                list.add(value)
            }
        }
        return list
    }

    private fun isNetworkException(e: Throwable): Boolean {
        return e is java.io.IOException ||
               e is java.net.ConnectException ||
               e is java.net.UnknownHostException ||
               e is java.net.SocketTimeoutException
    }

    private fun isRetryableError(e: Throwable): Boolean {
        val message = e.message?.lowercase() ?: ""
        return message.contains("unavailable") || message.contains("deadline_exceeded")
    }

    private fun updateEntitySyncStatus(database: BillingDatabase, entityType: String, id: String, status: String) {
        val tableName = getCollectionName(entityType)
        if (tableName != null) {
            try {
                database.openHelper.writableDatabase.execSQL(
                    "UPDATE $tableName SET syncStatus = '$status' WHERE id = '$id'"
                )
            } catch (ignored: Exception) {}
        }
    }

    private fun extractUpdatedAtFromPayload(payload: String): Long {
        try {
            val start = payload.indexOf("\"updatedAtEpochMs\":")
            if (start == -1) return 0L
            val valueStart = start + "\"updatedAtEpochMs\":".length
            var valueEnd = payload.indexOf(',', valueStart)
            if (valueEnd == -1) valueEnd = payload.indexOf('}', valueStart)
            if (valueEnd == -1) return 0L
            val valueStr = payload.substring(valueStart, valueEnd).trim()
            return valueStr.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            return 0L
        }
    }
}