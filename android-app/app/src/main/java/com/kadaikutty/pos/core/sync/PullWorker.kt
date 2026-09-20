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
                    if (records != null) for (index in 0 until records.length()) applyRecord(database, session.companyId, records.getJSONObject(index))
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

    private suspend fun applyRecord(database: BillingDatabase, companyId: String, record: JSONObject) {
        require(record.getString("companyId") == companyId) { "Cross-tenant sync record rejected" }
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
}
