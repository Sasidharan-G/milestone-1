package com.kadaikutty.pos.core.sync

import com.kadaikutty.pos.core.common.newRecordId
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.database.LocalOperationEntity
import com.kadaikutty.pos.core.database.SyncDeadLetterEntity
import com.kadaikutty.pos.core.database.SyncQueueEntity
import kotlinx.coroutines.sync.Mutex
import org.json.JSONArray
import org.json.JSONObject

/**
 * One push or pull at a time per process. WorkManager runs the one-off and the periodic workers
 * independently, so two pushes could send the same queue rows and both try to settle the same
 * conflict, and a pull could land in the middle of a conflict resolution.
 */
object SyncLock {
    val mutex = Mutex()
}

/**
 * Applies the server's answer to one pushed batch to the queue. Kept out of SyncWorker so it can be
 * tested against a real Room database (see PushResultHandlerTest).
 *
 * [sent] are the queue rows exactly as they were read and sent. A row may have been changed since:
 * an edit made while the request was in flight is folded into the same row, and must not be closed
 * along with the write that did land.
 */
class PushResultHandler(
    private val database: BillingDatabase,
    private val resolver: ConflictResolver,
) {
    data class Outcome(val synced: Int, val conflicts: Int, val rejected: Int, val retryNeeded: Boolean)

    suspend fun apply(companyId: String, sent: List<SyncQueueEntity>, results: JSONArray): Outcome {
        val queue = database.syncQueueDao()
        var synced = 0
        var conflicts = 0
        var rejected = 0
        var retryNeeded = false
        for (index in 0 until results.length()) {
            val result = results.getJSONObject(index)
            val item = sent.firstOrNull { it.id == result.optString("operationId") } ?: continue
            val now = System.currentTimeMillis()
            when (result.optString("status")) {
                "APPLIED", "DUPLICATE" -> {
                    // The cloud now holds what was sent, whatever happened to the row since.
                    result.optLong("version", -1).takeIf { it >= 0 }?.let { version ->
                        resolver.recordCloudState(companyId, item.entityType, item.entityId, version, sentPayload(companyId, item))
                    }
                    if (queue.markSyncedIfUnchanged(item.id, item.operation, item.payload, now) > 0) {
                        markEntity(companyId, item, SyncStatus.SYNCED)
                        synced++
                    }
                    // Otherwise the row was edited in flight: it stays PENDING and goes out next,
                    // written on top of the version recorded just above.
                }
                "CONFLICT" -> {
                    // Resolve against the row as it is now, so an edit made in flight is the one weighed.
                    val current = queue.getById(item.id)
                    if (current?.status == SyncStatus.PENDING) {
                        resolver.resolve(companyId, current, result.optJSONObject("record"))
                        conflicts++
                    }
                }
                "REJECTED" -> {
                    // The server will never take this exact write; retrying it only blocks the queue.
                    val error = result.optJSONObject("error")
                    val reason = "${error?.optString("code")?.ifBlank { null } ?: "REJECTED"}: ${error?.optString("message")?.ifBlank { null } ?: "Rejected by the server"}"
                    if (queue.markRejectedIfUnchanged(item.id, item.operation, item.payload, SyncWorker.MAX_SYNC_ATTEMPTS, reason, now) > 0) {
                        database.syncDeadLetterDao().insert(SyncDeadLetterEntity(newRecordId(), companyId, item.entityType, item.entityId, item.operation, item.payload, reason, SyncWorker.MAX_SYNC_ATTEMPTS, item.createdAtEpochMs, now, item.id))
                        markEntity(companyId, item, SyncStatus.FAILED)
                        rejected++
                    }
                }
                else -> retryNeeded = true
            }
        }
        if (rejected > 0) restartPull(companyId)
        return Outcome(synced, conflicts, rejected, retryNeeded)
    }

    /**
     * Pulls skip records that still have queued work, so as not to overwrite it. A write that is now
     * dead-lettered will never be sent, so everything is read again from the start, or this device
     * would keep its rejected version forever.
     */
    suspend fun restartPull(companyId: String) {
        database.localOperationDao().put(LocalOperationEntity(companyId, PullWorker.CURSOR_KEY, "0"))
    }

    /** What the cloud holds after this item applied, as the base for the next merge. */
    private suspend fun sentPayload(companyId: String, item: SyncQueueEntity): JSONObject {
        val sent = runCatching { JSONObject(item.payload) }.getOrElse { JSONObject() }
        if (item.operation != "PARTIAL_UPDATE") return sent
        val merged = resolver.cloudPayload(companyId, item.entityType, item.entityId) ?: JSONObject()
        sent.keys().forEach { merged.put(it, sent.get(it)) }
        return merged
    }

    fun markEntity(companyId: String, item: SyncQueueEntity, status: SyncStatus) {
        val table = ConflictResolver.TABLES[item.entityType] ?: return
        runCatching { database.openHelper.writableDatabase.execSQL("UPDATE $table SET syncStatus = ? WHERE id = ? AND companyId = ?", arrayOf<Any>(status.name, item.entityId, companyId)) }
    }
}
