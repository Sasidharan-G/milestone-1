package com.kadaikutty.pos.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kadaikutty.pos.core.common.newRecordId
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.database.LocalOperationEntity
import com.kadaikutty.pos.core.database.SyncDeadLetterEntity
import com.kadaikutty.pos.core.database.SyncQueueEntity
import com.kadaikutty.pos.core.network.BackendApiClient
import com.kadaikutty.pos.core.network.BackendApiException
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncEntryPoint {
        fun database(): BillingDatabase
        fun tenantDatabaseManager(): com.kadaikutty.pos.core.database.TenantDatabaseManager
        fun sessionStore(): com.kadaikutty.pos.core.auth.SessionStore
        fun backendApiClient(): BackendApiClient
        fun analyticsManager(): com.kadaikutty.pos.core.analytics.AnalyticsManager
        fun appPreferences(): com.kadaikutty.pos.core.preferences.AppPreferences
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, SyncEntryPoint::class.java)
        val session = entryPoint.sessionStore().activeSession.first() ?: return Result.success()
        val database = entryPoint.tenantDatabaseManager().getDatabase(session.companyId)
        var token = session.accessToken
        var sessionToken = session.sessionToken

        if (token.isNullOrBlank() || sessionToken.isNullOrBlank()) {
            val recovered = runCatching { entryPoint.backendApiClient().autoRecoverSession(forceRefresh = false) }.getOrNull()
            if (recovered != null) {
                token = recovered.first
                sessionToken = recovered.second
            }
        }

        if (token.isNullOrBlank()) {
            // In offline mode, do not fail with a sign-in error banner; quietly retry when connected
            return Result.retry()
        }
        val queue = database.syncQueueDao()
        runCatching { queue.migrateTenantData("company_main", session.companyId) }
        runCatching { queue.retryFailed(session.companyId, System.currentTimeMillis(), MAX_SYNC_ATTEMPTS) }
        var synced = 0
        return try {
            while (true) {
                val items = queue.pending(session.companyId, 50)
                if (items.isEmpty()) break
                val operations = JSONArray()
                for (item in items) {
                    val versionKey = "cloud_version:${item.entityType}:${item.entityId}"
                    val baseVersion = database.localOperationDao().get(session.companyId, versionKey)?.toLongOrNull() ?: 0L
                    val payloadObj = if (item.operation == "DELETE") JSONObject() else JSONObject(item.payload)
                    if (payloadObj.has("companyId")) {
                        payloadObj.put("companyId", session.companyId)
                    }
                    operations.put(JSONObject()
                        .put("operationId", item.id)
                        .put("companyId", session.companyId)
                        .put("entityType", item.entityType)
                        .put("entityId", item.entityId)
                        .put("operation", item.operation)
                        .put("baseVersion", baseVersion)
                        .put("schemaVersion", 1)
                        .put("payload", payloadObj))
                }
                var response: JSONObject? = null
                var authError: BackendApiException? = null
                for (attempt in 1..2) {
                    try {
                        val active = entryPoint.sessionStore().activeSession.first()
                        val currentToken = active?.accessToken?.trim()?.takeIf { it.isNotBlank() } ?: token
                        val currentSessionToken = active?.sessionToken?.takeIf { it.isNotBlank() } ?: sessionToken
                        response = entryPoint.backendApiClient().pushSync(currentToken, session.companyId, operations, currentSessionToken)
                        authError = null
                        break
                    } catch (e: BackendApiException) {
                        if (e.code == "AUTH_TOKEN_INVALID" && attempt < 2) {
                            authError = e
                            delay(1000)
                            continue
                        }
                        throw e
                    }
                }
                if (response == null && authError != null) throw authError
                val results = response?.optJSONArray("results") ?: JSONArray()
                var retryNeeded = false
                for (index in 0 until results.length()) {
                    val result = results.getJSONObject(index)
                    val item = items.firstOrNull { it.id == result.optString("operationId") } ?: continue
                    when (result.optString("status")) {
                        "APPLIED", "DUPLICATE" -> {
                            val now = System.currentTimeMillis()
                            queue.updateStatus(item.id, SyncStatus.SYNCED, now)
                            queue.updateLastSyncedAt(item.id, now)
                            result.optLong("version", -1).takeIf { it >= 0 }?.let { version ->
                                database.localOperationDao().put(LocalOperationEntity(session.companyId, "cloud_version:${item.entityType}:${item.entityId}", version.toString()))
                            }
                            updateEntitySyncStatus(database, item.entityType, item.entityId, "SYNCED")
                            synced++
                        }
                        "CONFLICT" -> {
                            queue.updateStatus(item.id, SyncStatus.CONFLICT, System.currentTimeMillis(), "Record changed on another device")
                            updateEntitySyncStatus(database, item.entityType, item.entityId, "CONFLICT")
                        }
                        else -> retryNeeded = true
                    }
                }
                if (retryNeeded) return Result.retry()
            }
            if (synced > 0) entryPoint.analyticsManager().logEvent(com.kadaikutty.pos.core.analytics.AnalyticsEvents.EVENT_SYNC_COMPLETED, mapOf(com.kadaikutty.pos.core.analytics.AnalyticsEvents.PARAM_RECORDS_PUSHED to synced))
            Result.success()
        } catch (error: BackendApiException) {
            handleFailure(database, session.companyId, error)
        } catch (error: java.io.IOException) {
            Result.retry()
        } catch (error: Exception) {
            Result.failure(errorData(error.message ?: "Sync failed", "Open Sync Diagnostics and retry."))
        }
    }

    private suspend fun handleFailure(database: BillingDatabase, companyId: String, error: BackendApiException): Result {
        if (error.code == "SESSION_REVOKED" || error.message.contains("signed out", ignoreCase = true)) return Result.failure(errorData(error.message, "Account signed out on another device."))
        if (error.retryable || error.statusCode == 401 || error.code.contains("SESSION") || error.code.contains("TOKEN")) return Result.retry()
        val items = database.syncQueueDao().pending(companyId, 50)
        val now = System.currentTimeMillis()
        for (item in items) {
            val attempts = item.attemptCount + 1
            if (attempts >= MAX_SYNC_ATTEMPTS) {
                database.syncDeadLetterDao().insert(SyncDeadLetterEntity(newRecordId(), companyId, item.entityType, item.entityId, item.operation, item.payload, "${error.code}: ${error.message}", attempts, item.createdAtEpochMs, now, item.id))
            }
            database.syncQueueDao().updateAttemptCount(item.id, attempts)
            database.syncQueueDao().updateStatus(item.id, SyncStatus.FAILED, now, "${error.code}: ${error.message}")
            updateEntitySyncStatus(database, item.entityType, item.entityId, "FAILED")
        }
        return Result.failure(errorData(error.message, "Items are saved locally. Cloud sync will retry automatically."))
    }

    private fun updateEntitySyncStatus(database: BillingDatabase, entityType: String, id: String, status: String) {
        val table = when (entityType) {
            "Category" -> "categories"; "Product" -> "products"; "Customer" -> "customers"; "Supplier" -> "suppliers"
            "Expense" -> "expenses"; "Sale" -> "sales"; "Purchase" -> "purchases"; "CustomerCredit" -> "customer_credits"
            "SupplierCredit" -> "supplier_credits"; "StockMovement" -> "stock_movements"; else -> null
        } ?: return
        runCatching { database.openHelper.writableDatabase.execSQL("UPDATE $table SET syncStatus = ? WHERE id = ?", arrayOf(status, id)) }
    }

    private fun errorData(reason: String, next: String) = androidx.work.workDataOf("error_reason" to reason, "error_next_steps" to next)

    companion object {
        /** After this many failed attempts an item is dead-lettered instead of retried again. */
        const val MAX_SYNC_ATTEMPTS = 5
    }
}

