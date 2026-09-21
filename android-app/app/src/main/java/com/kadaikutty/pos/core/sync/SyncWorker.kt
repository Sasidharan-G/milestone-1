package com.kadaikutty.pos.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kadaikutty.pos.core.common.newRecordId
import com.kadaikutty.pos.core.database.BillingDatabase
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
import kotlinx.coroutines.sync.withLock
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
        fun syncManager(): SyncManager
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
            // Offline or signed in offline without tokens: quietly retry when connected.
            return Result.retry()
        }
        return SyncLock.mutex.withLock { push(entryPoint, database, session.companyId, token, sessionToken) }
    }

    private suspend fun push(entryPoint: SyncEntryPoint, database: BillingDatabase, companyId: String, token: String, sessionToken: String?): Result {
        val queue = database.syncQueueDao()
        val resolver = ConflictResolver(database, entryPoint.syncManager())
        val handler = PushResultHandler(database, resolver)
        runCatching { LegacyTenantMigration.runOnce(database, companyId) }
        runCatching { queue.retryFailed(companyId, System.currentTimeMillis(), MAX_SYNC_ATTEMPTS) }
        runCatching { queue.requeueConflicts(companyId, System.currentTimeMillis()) }
        var synced = 0
        var batchSize = BATCH
        var lastBatch: List<SyncQueueEntity> = emptyList()
        return try {
            var rounds = 0
            while (true) {
                if (++rounds > MAX_ROUNDS) return Result.retry() // more left; carry on after a pause
                val items = nextBatch(database, companyId, batchSize)
                if (items.isEmpty()) break
                lastBatch = items
                val operations = JSONArray()
                for (item in items) {
                    val baseVersion = database.localOperationDao().get(companyId, ConflictResolver.versionKey(item.entityType, item.entityId))?.toLongOrNull() ?: 0L
                    // A delete carries the full record too, so the cloud tombstone still holds the
                    // data if another device later needs to restore it (see ConflictResolver).
                    val payloadObj = runCatching { JSONObject(item.payload) }.getOrElse { JSONObject() }
                    if (payloadObj.has("companyId")) payloadObj.put("companyId", companyId)
                    operations.put(JSONObject()
                        .put("operationId", item.id)
                        .put("companyId", companyId)
                        .put("entityType", item.entityType)
                        .put("entityId", item.entityId)
                        .put("operation", item.operation)
                        .put("baseVersion", baseVersion)
                        .put("schemaVersion", 1)
                        .put("payload", payloadObj))
                }
                val response = try {
                    send(entryPoint, companyId, operations, token, sessionToken, PullWorker.knownEpoch(database, companyId))
                } catch (e: BackendApiException) {
                    if (e.statusCode != 413) throw e
                    // The batch as a whole is over the server's body limit. Send it in halves; a
                    // single record that is still too big on its own can never be sent.
                    if (items.size > 1) {
                        batchSize = maxOf(1, items.size / 2)
                    } else {
                        deadLetter(database, companyId, items.single(), "${e.code}: ${e.message}")
                        handler.restartPull(companyId)
                    }
                    continue
                }
                val outcome = handler.apply(companyId, items, response.optJSONArray("results") ?: JSONArray())
                synced += outcome.synced
                if (outcome.retryNeeded) return Result.retry()
                batchSize = BATCH
            }
            resolver.sweep(companyId)
            if (synced > 0) entryPoint.analyticsManager().logEvent(com.kadaikutty.pos.core.analytics.AnalyticsEvents.EVENT_SYNC_COMPLETED, mapOf(com.kadaikutty.pos.core.analytics.AnalyticsEvents.PARAM_RECORDS_PUSHED to synced))
            // Anything a resolution queued (a merge to send back, a restore) goes out on the next
            // round of this same loop; nothing is left waiting for the 15-minute periodic sync.
            Result.success()
        } catch (error: BackendApiException) {
            handleFailure(database, companyId, error, lastBatch, handler)
        } catch (error: java.io.IOException) {
            Result.retry()
        } catch (error: Exception) {
            Result.failure(errorData(error.message ?: "Sync failed", "Open Sync Diagnostics and retry."))
        }
    }

    private suspend fun send(entryPoint: SyncEntryPoint, companyId: String, operations: JSONArray, token: String, sessionToken: String?, epoch: Long): JSONObject {
        var lastError: BackendApiException? = null
        for (attempt in 1..2) {
            try {
                val active = entryPoint.sessionStore().activeSession.first()
                val currentToken = active?.accessToken?.trim()?.takeIf { it.isNotBlank() } ?: token
                val currentSessionToken = active?.sessionToken?.takeIf { it.isNotBlank() } ?: sessionToken
                return entryPoint.backendApiClient().pushSync(currentToken, companyId, operations, currentSessionToken, epoch)
            } catch (e: BackendApiException) {
                if (e.code == "AUTH_TOKEN_INVALID" && attempt < 2) {
                    lastError = e
                    delay(1000)
                    continue
                }
                throw e
            }
        }
        throw lastError ?: IllegalStateException("Sync push failed")
    }

    /**
     * The next batch to send. A stock or credit row that belongs to a bill or purchase waits until
     * that bill itself has synced: if the bill loses a conflict, rows of the losing edit must never
     * have reached the cloud, or stock and dues would be counted twice everywhere.
     */
    private suspend fun nextBatch(database: BillingDatabase, companyId: String, size: Int): List<SyncQueueEntity> {
        val queue = database.syncQueueDao()
        val window = queue.pendingWindow(companyId, WINDOW)
        if (window.isEmpty()) return emptyList()
        val openParents = queue.unresolvedEntityIds(companyId, listOf("Sale", "Purchase"), MAX_SYNC_ATTEMPTS).toSet()
        return window.filter { item ->
            if (item.entityType !in CHILD_TYPES) return@filter true
            val parentId = runCatching { JSONObject(item.payload).optString("referenceId") }.getOrDefault("")
            parentId.isBlank() || parentId !in openParents
        }.take(size)
    }

    private suspend fun handleFailure(database: BillingDatabase, companyId: String, error: BackendApiException, sent: List<SyncQueueEntity>, handler: PushResultHandler): Result {
        if (error.code == "SESSION_REVOKED" || error.message.contains("signed out", ignoreCase = true)) return Result.failure(errorData(error.message, "Account signed out on another device."))
        if (error.retryable || error.statusCode == 401 || error.code.contains("SESSION") || error.code.contains("TOKEN")) return Result.retry()
        // The shop's cloud data was cleared on another device. These writes belong to data that
        // no longer exists; the pull resets this device (see PullWorker.resetIfPurged).
        if (error.code == "SYNC_EPOCH_STALE") {
            runCatching { SyncScheduler(applicationContext).requestPull() }
            return Result.success()
        }
        // Nothing is wrong with the records themselves: the subscription lapsed or the account was
        // switched off. Counting these as failed attempts dead-lettered a whole shop's offline
        // bills after five tries, so they never synced after the renewal. Leave the queue alone.
        if (error.code in ACCESS_PAUSED_CODES) {
            return Result.failure(errorData(error.message, "Bills are saved on this device and will sync once access is restored."))
        }
        // Blame only the batch that was actually sent, not whatever happens to be first in the queue.
        val now = System.currentTimeMillis()
        var deadLettered = false
        for (sentItem in sent) {
            val item = database.syncQueueDao().getById(sentItem.id)?.takeIf { it.status == SyncStatus.PENDING } ?: continue
            val attempts = item.attemptCount + 1
            if (attempts >= MAX_SYNC_ATTEMPTS) {
                database.syncDeadLetterDao().insert(SyncDeadLetterEntity(newRecordId(), companyId, item.entityType, item.entityId, item.operation, item.payload, "${error.code}: ${error.message}", attempts, item.createdAtEpochMs, now, item.id))
                deadLettered = true
            }
            database.syncQueueDao().updateAttemptCount(item.id, attempts)
            database.syncQueueDao().updateStatus(item.id, SyncStatus.FAILED, now, "${error.code}: ${error.message}")
            handler.markEntity(companyId, item, SyncStatus.FAILED)
        }
        if (deadLettered) {
            // While these items were pending, pulls skipped any cloud change to the same records
            // so as not to overwrite them. Now they will never be sent, so read everything again
            // from the start, or this device would keep its rejected version forever.
            handler.restartPull(companyId)
        }
        return Result.failure(errorData(error.message, "Items are saved locally. Cloud sync will retry automatically."))
    }

    private suspend fun deadLetter(database: BillingDatabase, companyId: String, item: SyncQueueEntity, reason: String) {
        val now = System.currentTimeMillis()
        if (database.syncQueueDao().markRejectedIfUnchanged(item.id, item.operation, item.payload, MAX_SYNC_ATTEMPTS, reason, now) > 0) {
            database.syncDeadLetterDao().insert(SyncDeadLetterEntity(newRecordId(), companyId, item.entityType, item.entityId, item.operation, item.payload, reason, MAX_SYNC_ATTEMPTS, item.createdAtEpochMs, now, item.id))
        }
    }

    private fun errorData(reason: String, next: String) = androidx.work.workDataOf("error_reason" to reason, "error_next_steps" to next)

    companion object {
        /** After this many failed attempts an item is dead-lettered instead of retried again. */
        const val MAX_SYNC_ATTEMPTS = 5
        private const val BATCH = 50
        private const val WINDOW = 500
        private const val MAX_ROUNDS = 200
        private val CHILD_TYPES = setOf("StockMovement", "CustomerCredit", "SupplierCredit")
        private val ACCESS_PAUSED_CODES = setOf("LICENSE_INACTIVE", "AUTH_ACCOUNT_INACTIVE", "ACCOUNT_NOT_FOUND")
    }
}
