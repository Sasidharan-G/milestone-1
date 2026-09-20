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
import org.json.JSONObject

class PullWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PullEntryPoint {
        fun database(): BillingDatabase
        fun tenantDatabaseManager(): com.kadaikutty.pos.core.database.TenantDatabaseManager
        fun sessionStore(): SessionStore
        fun backendApiClient(): BackendApiClient
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
        val cursorKey = "sync_pull_cursor"
        var cursor = database.localOperationDao().get(session.companyId, cursorKey) ?: "0"
        return try {
            do {
                val response = entry.backendApiClient().pullSync(token, session.companyId, cursor)
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
                        requireSameTenant(record, session.companyId)
                        try {
                            applyRecord(database, session.companyId, record)
                        } catch (error: Exception) {
                            recordSkipped(database, session.companyId, record, error)
                        }
                    }
                    database.localOperationDao().put(LocalOperationEntity(session.companyId, cursorKey, nextCursor))
                }
                cursor = nextCursor
                val hasMore = response.optBoolean("hasMore", false)
            } while (hasMore)
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

    private suspend fun applyRecord(database: BillingDatabase, companyId: String, record: JSONObject) {
        val type = record.getString("entityType")
        val id = record.getString("entityId")
        val version = record.getLong("version")
        if (record.optBoolean("deleted")) {
            RecordApplier.deleteRecord(database, companyId, type, id)
        } else {
            val tombstone = database.syncQueueDao().findPending(companyId, type, id)
            val serverUpdatedAt = record.optLong("updatedAtEpochMs")
            if (tombstone != null && tombstone.operation == "DELETE" && tombstone.createdAtEpochMs > serverUpdatedAt) {
                // Local tombstone is newer than cloud update -> preserve local deletion
                return
            }
            RecordApplier.upsertRecord(database, companyId, type, id, record.getJSONObject("payload"), serverUpdatedAt)
        }
        database.localOperationDao().put(LocalOperationEntity(companyId, "cloud_version:$type:$id", version.toString()))
    }

    private fun errorData(message: String) = androidx.work.workDataOf("error_reason" to message, "error_next_steps" to "Check your connection or Sync Diagnostics, then retry.")

    companion object {
        /** Records the server sent that this build could not apply. Surfaced in Sync Diagnostics. */
        const val SKIPPED_COUNT_KEY = "sync_pull_skipped_count"
        const val SKIPPED_LAST_KEY = "sync_pull_skipped_last"
    }
}
