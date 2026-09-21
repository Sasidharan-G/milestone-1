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
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

class PullWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PullEntryPoint {
        fun database(): BillingDatabase
        fun tenantDatabaseManager(): com.kadaikutty.pos.core.database.TenantDatabaseManager
        fun sessionStore(): SessionStore
        fun backendApiClient(): BackendApiClient
        fun syncManager(): SyncManager
    }

    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, PullEntryPoint::class.java)
        val session = entry.sessionStore().activeSession.first() ?: return Result.success()
        val database = entry.tenantDatabaseManager().getDatabase(session.companyId)
        var token = session.accessToken
        if (token.isNullOrBlank()) {
            val recovered = runCatching { entry.backendApiClient().autoRecoverSession(forceRefresh = false) }.getOrNull()
            token = recovered?.first
        }
        if (token.isNullOrBlank()) return Result.success()
        return SyncLock.mutex.withLock { pull(entry, database, session.companyId, token) }
    }

    private suspend fun pull(entry: PullEntryPoint, database: BillingDatabase, companyId: String, token: String): Result {
        val cursorKey = CURSOR_KEY
        val resolver = ConflictResolver(database, entry.syncManager())
        var cursor = database.localOperationDao().get(companyId, cursorKey) ?: "0"
        return try {
            while (true) {
                val response = entry.backendApiClient().pullSync(token, companyId, cursor)
                if (resetIfPurged(database, companyId, response.optLong("epoch", 0L))) {
                    cursor = "0"
                    continue
                }
                val records = response.optJSONArray("records") ?: response.optJSONArray("data")
                val nextCursor = response.optString("nextCursor", cursor)
                database.withTransaction {
                    if (records != null) for (index in 0 until records.length()) {
                        val record = records.getJSONObject(index)
                        // A record this build cannot apply must not hold the cursor back: the
                        // transaction would roll the cursor back with it and every retry would
                        // refetch the same page forever, wedging this tenant's pull for good.
                        // It is skipped and counted instead, so Sync Diagnostics can show it.
                        // A cross-tenant record is different - that is not bad data but a
                        // server or cursor fault, so it still aborts the whole batch.
                        requireSameTenant(record, companyId)
                        try {
                            applyRecord(database, resolver, companyId, record)
                        } catch (error: Exception) {
                            recordSkipped(database, companyId, record, error)
                        }
                    }
                    database.localOperationDao().put(LocalOperationEntity(companyId, cursorKey, nextCursor))
                }
                cursor = nextCursor
                if (!response.optBoolean("hasMore", false)) break
            }
            // Children of bills cancelled elsewhere, and customers/products that money or stock
            // still points at, are only knowable once the whole pull has landed.
            resolver.sweep(companyId)
            // Once per install, after a complete pull: rebuild bills whose stock or credit rows an
            // older build doubled. See ConflictResolver.repairDocumentChildren.
            if (database.localOperationDao().get(companyId, CHILD_REPAIR_KEY) == null) {
                // Marked done only when every document could be checked; ones with sync work still
                // queued are retried after the next pull.
                if (resolver.repairDocumentChildren(companyId) == 0) {
                    database.localOperationDao().put(LocalOperationEntity(companyId, CHILD_REPAIR_KEY, System.currentTimeMillis().toString()))
                }
            }
            // A sweep or a restore may have queued writes; send them now rather than in 15 minutes.
            if (database.syncQueueDao().pending(companyId, 1).isNotEmpty()) {
                runCatching { SyncScheduler(applicationContext).request() }
            }
            Result.success()
        } catch (error: BackendApiException) {
            if (error.code == "SESSION_REVOKED" || error.message.contains("signed out", ignoreCase = true)) Result.failure(errorData(error.message))
            else if (error.retryable || error.statusCode == 401 || error.code.contains("SESSION") || error.code.contains("TOKEN")) Result.retry()
            else Result.failure(errorData(error.message))
        } catch (error: java.io.IOException) {
            Result.retry()
        } catch (error: Exception) {
            Result.failure(errorData(error.message ?: "Cloud pull failed"))
        }
    }

    /**
     * The shop's cloud data was cleared on another device since this one last synced (its data
     * epoch moved on). Everything here predates that, including writes still waiting to go out,
     * which the server would refuse anyway. Empty this device and pull again from the start.
     *
     * A device with no epoch and no pull cursor has never synced (a fresh install), so it just
     * takes the current epoch; one that has synced before epochs existed is reset like any other.
     */
    private suspend fun resetIfPurged(database: BillingDatabase, companyId: String, serverEpoch: Long): Boolean {
        val dao = database.localOperationDao()
        val known = dao.get(companyId, EPOCH_KEY)?.toLongOrNull()
        if (known == serverEpoch) return false
        if (known == null && (serverEpoch == 0L || dao.get(companyId, CURSOR_KEY) == null)) {
            rememberEpoch(database, companyId, serverEpoch)
            return false
        }
        ShopDataWiper.wipe(database)
        rememberEpoch(database, companyId, serverEpoch)
        dao.put(LocalOperationEntity(companyId, REMOTE_RESET_KEY, System.currentTimeMillis().toString()))
        return true
    }

    private fun requireSameTenant(record: JSONObject, companyId: String) {
        require(record.optString("companyId") == companyId) { "Cross-tenant sync record rejected" }
    }

    private suspend fun recordSkipped(database: BillingDatabase, companyId: String, record: JSONObject, error: Exception) {
        val dao = database.localOperationDao()
        val skipped = (dao.get(companyId, SKIPPED_COUNT_KEY)?.toLongOrNull() ?: 0L) + 1
        dao.put(LocalOperationEntity(companyId, SKIPPED_COUNT_KEY, skipped.toString()))
        val id = record.optString("entityId").ifBlank { "unknown" }
        val type = record.optString("entityType").ifBlank { "unknown" }
        dao.put(LocalOperationEntity(companyId, SKIPPED_LAST_KEY, "$type/$id: ${error.message ?: error::class.java.simpleName}"))
    }

    private suspend fun applyRecord(database: BillingDatabase, resolver: ConflictResolver, companyId: String, record: JSONObject) {
        val type = record.getString("entityType")
        val id = record.getString("entityId")
        val version = record.getLong("version")
        val payload = record.optJSONObject("payload") ?: JSONObject()
        // This device has its own change to this record still on the way out. Writing the cloud
        // copy over it would lose that change, and recording the new version would make the push
        // look current and overwrite the other device instead of conflicting. So leave both alone:
        // the push will get a CONFLICT carrying this same record, and ConflictResolver merges.
        if (database.syncQueueDao().hasUnresolved(companyId, type, id, SyncWorker.MAX_SYNC_ATTEMPTS)) return
        if (record.optBoolean("deleted")) {
            if (!resolver.applyPulledDeletion(companyId, type, id, version, payload)) {
                RecordApplier.deleteRecord(database, companyId, type, id)
            }
        } else {
            if (type == "Sale" || type == "Purchase") resolver.onPulledDocument(companyId, type, id, version)
            resolver.preparePulledUpsert(companyId, type, id, payload)
            RecordApplier.upsertRecord(database, companyId, type, id, payload, record.optLong("updatedAtEpochMs"))
            resolver.recordCloudState(companyId, type, id, version, payload)
        }
    }

    private fun errorData(message: String) = androidx.work.workDataOf("error_reason" to message, "error_next_steps" to "Check your connection or Sync Diagnostics, then retry.")

    companion object {
        const val CURSOR_KEY = "sync_pull_cursor"
        /** The generation of cloud data this device holds; see the server's getSyncEpoch. */
        const val EPOCH_KEY = "sync_data_epoch"
        /** When this device last threw its data away because the cloud copy had been cleared. */
        const val REMOTE_RESET_KEY = "sync_remote_reset_at"

        suspend fun knownEpoch(database: BillingDatabase, companyId: String): Long =
            database.localOperationDao().get(companyId, EPOCH_KEY)?.toLongOrNull() ?: 0L

        suspend fun rememberEpoch(database: BillingDatabase, companyId: String, epoch: Long) {
            database.localOperationDao().put(LocalOperationEntity(companyId, EPOCH_KEY, epoch.toString()))
        }
        private const val CHILD_REPAIR_KEY = "legacy_child_repair_v1"

        /** Records the server sent that this build could not apply. Surfaced in Sync Diagnostics. */
        const val SKIPPED_COUNT_KEY = "sync_pull_skipped_count"
        const val SKIPPED_LAST_KEY = "sync_pull_skipped_last"
    }
}
