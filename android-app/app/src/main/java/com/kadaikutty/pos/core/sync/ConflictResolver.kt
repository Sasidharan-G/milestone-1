package com.kadaikutty.pos.core.sync

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.kadaikutty.pos.core.common.newRecordId
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.database.LocalOperationEntity
import com.kadaikutty.pos.core.database.SyncConflictEntity
import com.kadaikutty.pos.core.database.SyncQueueEntity
import com.kadaikutty.pos.core.sync.ConflictPolicy.Kind
import com.kadaikutty.pos.core.sync.ConflictPolicy.LocalIntent
import com.kadaikutty.pos.core.sync.ConflictPolicy.Resolution
import com.kadaikutty.pos.core.sync.ConflictPolicy.ServerState
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * Carries out ConflictPolicy against the local database, so that no sync conflict is ever left
 * stuck and nothing is lost without a trace in the conflict log.
 *
 * It is called from three places:
 * - [resolve]: the server answered CONFLICT to a push.
 * - [applyPulledDeletion] / [preparePulledUpsert]: a pull is about to delete or write a record
 *   that something on this device still depends on.
 * - [sweep]: after every push and pull, to clean up children of cancelled bills and to restore
 *   customers, suppliers and products that money or stock still points at.
 *
 * Bookkeeping it keeps in local_operations, per record:
 * - `cloud_version:<type>:<id>`: last cloud version this device saw; the base of the next write.
 * - `cloud_payload:<type>:<id>`: last cloud content of a MASTER record; the base of a three-way
 *   merge, and the data to bring a record back if it was deleted elsewhere while still in use.
 * - `doc_deleted:<type>:<id>`: a cancelled bill or purchase, whose stock and credit rows must go too.
 */
class ConflictResolver(
    private val database: BillingDatabase,
    private val syncManager: SyncManager,
) {
    private val ops get() = database.localOperationDao()
    private val queue get() = database.syncQueueDao()
    private val integrity get() = database.syncIntegrityDao()

    // ---------------------------------------------------------------------------------------
    // Cloud bookkeeping, shared with SyncWorker and PullWorker.
    // ---------------------------------------------------------------------------------------

    suspend fun recordCloudState(companyId: String, type: String, id: String, version: Long, payload: JSONObject?) {
        ops.put(LocalOperationEntity(companyId, versionKey(type, id), version.toString()))
        // Only master records are ever merged or restored, so only their content is kept. Bills
        // with all their lines would bloat this table for nothing.
        // A tombstone from an older build carries no data (just companyId). Keep the last real
        // copy instead of overwriting it with nothing, or the record could never be restored.
        val hasData = payload != null && payload.keys().asSequence().any { it !in VOLATILE_KEYS }
        if (hasData && ConflictPolicy.kindOf(type) == Kind.MASTER) {
            ops.put(LocalOperationEntity(companyId, payloadKey(type, id), payload.toString()))
        }
    }

    suspend fun cloudPayload(companyId: String, type: String, id: String): JSONObject? =
        ops.get(companyId, payloadKey(type, id))?.let { runCatching { JSONObject(it) }.getOrNull() }

    private suspend fun hasCloudVersion(companyId: String, type: String, id: String): Boolean =
        (ops.get(companyId, versionKey(type, id))?.toLongOrNull() ?: 0L) > 0L

    private suspend fun forgetCloudState(companyId: String, type: String, id: String) {
        ops.delete(companyId, versionKey(type, id))
        ops.delete(companyId, payloadKey(type, id))
    }

    // ---------------------------------------------------------------------------------------
    // Push conflicts.
    // ---------------------------------------------------------------------------------------

    /**
     * Settles one CONFLICT answer. [record] is the server's current copy, or null when the server
     * has none. Every open queue item for the record is closed first, because the resolution now
     * decides what (if anything) is sent next.
     */
    suspend fun resolve(companyId: String, item: SyncQueueEntity, record: JSONObject?): Resolution = database.withTransaction {
        val type = item.entityType
        val id = item.entityId
        val kind = ConflictPolicy.kindOf(type)
        val serverState = when {
            record == null -> ServerState.MISSING
            record.optBoolean("deleted") -> ServerState.DELETED
            else -> ServerState.LIVE
        }
        val serverPayload = record?.optJSONObject("payload") ?: JSONObject()
        val base = cloudPayload(companyId, type, id) // read before it is overwritten below
        val localRow = readRow(companyId, type, id)
        val intent = if (localRow != null) LocalIntent.UPSERT else LocalIntent.DELETE
        val inUse = kind == Kind.MASTER && localRow != null && isInUse(companyId, type, id)
        // Server-corrected edit times of both sides (TrustedClock). A queued item from before this
        // build has none and counts as oldest, so it never beats a real edit.
        val localEditedAt = ConflictPolicy.editTime(runCatching { JSONObject(item.payload) }.getOrNull(), 0L)
        val serverEditedAt = ConflictPolicy.editTime(serverPayload, record?.optLong("updatedAtEpochMs") ?: 0L)
        var resolution = ConflictPolicy.decide(kind, intent, serverState, inUse, localEditIsLater = localEditedAt > serverEditedAt)

        // Two identical ledger rows under one id are not a collision, just a re-send.
        if (resolution == Resolution.REKEYED && localRow != null && sameRecord(localRow, serverPayload)) {
            resolution = Resolution.ALREADY_IN_SYNC
        }

        val now = System.currentTimeMillis()
        queue.supersede(companyId, type, id, "Resolved: ${resolution.name}", now)
        if (record != null) recordCloudState(companyId, type, id, record.optLong("version"), serverPayload)
        else forgetCloudState(companyId, type, id)

        val clashes = mutableListOf<ConflictPolicy.FieldClash>()
        when (resolution) {
            Resolution.SERVER_WINS -> when {
                kind == Kind.DOCUMENT && serverState == ServerState.DELETED -> dropDocumentLocally(companyId, type, id)
                kind == Kind.DOCUMENT -> adoptServerDocument(companyId, type, id, serverPayload, record!!.optLong("updatedAtEpochMs"))
                else -> if (!deleteLocally(companyId, type, id)) {
                    // Something still references it (a foreign key refused the delete): keep it after all.
                    resolution = Resolution.RESTORED
                    syncManager.enqueueResolved(companyId, type, id, "UPDATE", localRow!!, editedAt = 0L)
                }
            }
            Resolution.LOCAL_WINS -> if (intent == LocalIntent.UPSERT) {
                // Only documents get here with an edit: this device's edit of the bill was made
                // later than the one in the cloud, so it replaces it whole.
                keepLocalDocument(companyId, type, id, localEditedAt)
            } else {
                // Send the delete again against the version that is there now. The server's own
                // copy goes along so the cloud tombstone still holds the data.
                syncManager.enqueueResolved(companyId, type, id, "DELETE", serverPayload, editedAt = localEditedAt)
                if (kind == Kind.DOCUMENT) {
                    markDocumentDeleted(companyId, type, id)
                    sweepDocument(companyId, type, id)
                }
            }
            Resolution.MERGED -> {
                val result = ConflictPolicy.merge(base, localRow!!, serverPayload, localEditedAt, serverEditedAt)
                clashes += result.clashes
                preparePulledUpsert(companyId, type, id, result.merged)
                RecordApplier.upsertRecord(database, companyId, type, id, result.merged, record!!.optLong("updatedAtEpochMs"))
                if (result.needsPush) syncManager.enqueueResolved(companyId, type, id, "UPDATE", result.merged, editedAt = maxOf(localEditedAt, serverEditedAt))
                else if (result.clashes.isEmpty()) resolution = Resolution.ALREADY_IN_SYNC
            }
            // Restores carry edit time 0: they are about the record existing, not its content, so
            // any real edit of its fields elsewhere still wins.
            Resolution.RESTORED -> syncManager.enqueueResolved(companyId, type, id, "UPDATE", localRow!!, editedAt = 0L)
            Resolution.RECREATED -> recreate(companyId, type, id, localRow!!, localEditedAt)
            Resolution.REKEYED -> {
                val newId = newRecordId()
                val copy = JSONObject(localRow!!.toString()).put("id", newId)
                RecordApplier.upsertRecord(database, companyId, type, newId, copy, now)
                RecordApplier.upsertRecord(database, companyId, type, id, serverPayload, record!!.optLong("updatedAtEpochMs"))
                syncManager.enqueueResolved(companyId, type, newId, "INSERT", copy, editedAt = localEditedAt)
            }
            Resolution.ALREADY_IN_SYNC -> if (kind == Kind.DOCUMENT && serverState == ServerState.DELETED) {
                markDocumentDeleted(companyId, type, id)
            }
        }
        markRowStatus(type, id, if (queue.hasUnresolved(companyId, type, id, SyncWorker.MAX_SYNC_ATTEMPTS)) SyncStatus.PENDING else SyncStatus.SYNCED)
        if (resolution != Resolution.ALREADY_IN_SYNC) {
            log(companyId, type, id, resolution, describe(type, resolution, serverState, intent, localRow ?: JSONObject(item.payload), serverPayload, clashes),
                JSONObject()
                    .put("local", runCatching { JSONObject(item.payload) }.getOrElse { JSONObject() })
                    .put("localRow", localRow ?: JSONObject.NULL)
                    .put("server", serverPayload)
                    .put("serverState", serverState.name)
                    .put("clashes", JSONArray(clashes.map { JSONObject().put("field", it.field).put("local", it.local ?: JSONObject.NULL).put("server", it.server ?: JSONObject.NULL).put("kept", it.kept) })))
        }
        resolution
    }

    // ---------------------------------------------------------------------------------------
    // Pull side.
    // ---------------------------------------------------------------------------------------

    /**
     * A pulled record says this entity was deleted. Returns true when it was handled here, false
     * when the caller should apply the plain delete.
     */
    suspend fun applyPulledDeletion(companyId: String, type: String, id: String, version: Long, payload: JSONObject): Boolean {
        recordCloudState(companyId, type, id, version, payload)
        return when (ConflictPolicy.kindOf(type)) {
            Kind.DOCUMENT -> { dropDocumentLocally(companyId, type, id); true }
            Kind.LEDGER -> false
            Kind.MASTER -> {
                val localRow = readRow(companyId, type, id) ?: return false
                if (isInUse(companyId, type, id) || !deleteLocally(companyId, type, id)) {
                    syncManager.enqueueResolved(companyId, type, id, "UPDATE", localRow, editedAt = 0L)
                    log(companyId, type, id, Resolution.RESTORED,
                        "${label(type, localRow)} was deleted on another device but is still in use here (money owed, stock, or linked records), so it was restored.",
                        JSONObject().put("localRow", localRow).put("server", payload))
                }
                true
            }
        }
    }

    /**
     * Runs before a pulled (or merged) record is written: makes sure every row it points at by
     * foreign key exists, and that a pulled bill cannot overwrite another bill with the same number.
     * Mutates [payload] in place when it has to.
     */
    suspend fun preparePulledUpsert(companyId: String, type: String, id: String, payload: JSONObject) {
        when (type) {
            "Product" -> {
                ensureCategory(companyId, payload.optString("categoryId"))
                // Two devices can each create the same item offline. Both are real rows other bills
                // may already point at, so they are kept, and the owner is told once.
                val barcode = payload.stringOrNull("barcode")
                val other = barcode?.let { integrity.otherProductWithBarcode(companyId, it, id) }
                val noticeKey = "dup_barcode_logged:$id"
                if (other != null && ops.get(companyId, noticeKey) == null) {
                    ops.put(LocalOperationEntity(companyId, noticeKey, other))
                    log(companyId, type, id, "DUPLICATE_BARCODE",
                        "Two products share barcode $barcode (\"${payload.optString("name")}\" and another one). Both were kept; merge or delete one of them in Products.",
                        JSONObject().put("server", payload).put("otherProductId", other))
                }
            }
            "Sale" -> {
                payload.optJSONArray("items")?.let { items ->
                    for (i in 0 until items.length()) {
                        val line = items.getJSONObject(i)
                        ensureProduct(companyId, line.optString("productId"), line.stringOrNull("productName"), line.stringOrNull("unitType"))
                    }
                }
                // sales has a unique (companyId, billNumber) index and inserts use REPLACE, which
                // would silently delete the other bill. Bill numbers carry a per-install prefix so
                // this should never happen; if it ever does, keep both bills.
                val number = payload.optString("billNumber")
                val clash = if (number.isBlank()) null else database.saleDao().getSaleByBillNumber(companyId, number)
                if (clash != null && clash.id != id) {
                    val renamed = "$number-${id.take(4).uppercase()}"
                    payload.put("billNumber", renamed)
                    log(companyId, type, id, Resolution.REKEYED,
                        "Two bills arrived with number $number. Both were kept; this one is shown as $renamed on this device.",
                        JSONObject().put("server", payload).put("otherSaleId", clash.id))
                }
            }
            "Purchase" -> payload.optJSONArray("items")?.let { items ->
                for (i in 0 until items.length()) {
                    val line = items.getJSONObject(i)
                    ensureProduct(companyId, line.optString("productId"), null, line.stringOrNull("unitType"))
                }
            }
        }
    }

    /**
     * After every push and pull: removes stock and credit rows of cancelled documents, and brings
     * back customers, suppliers and products that money or stock still points at.
     */
    suspend fun sweep(companyId: String) = database.withTransaction {
        val now = System.currentTimeMillis()
        for (entry in ops.byPrefix(companyId, DOC_TOMBSTONE_PREFIX)) {
            val (type, id) = entry.key.removePrefix(DOC_TOMBSTONE_PREFIX).split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            if (id.isBlank()) { ops.delete(companyId, entry.key); continue }
            if (documentExists(companyId, type, id)) { ops.delete(companyId, entry.key); continue }
            val remaining = sweepDocument(companyId, type, id)
            val age = now - (entry.value.toLongOrNull() ?: now)
            if (remaining == 0 && age > TOMBSTONE_RETENTION_MS) ops.delete(companyId, entry.key)
        }
        for (entry in ops.byPrefix(companyId, OWNED_CHILDREN_PREFIX)) {
            val (type, id) = entry.key.removePrefix(OWNED_CHILDREN_PREFIX).split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            if (id.isBlank() || !documentExists(companyId, type, id)) { ops.delete(companyId, entry.key); continue }
            enforceOwnedChildren(companyId, type, id)
        }
        integrity.orphanCustomerIds(companyId).forEach { restoreMissing(companyId, "Customer", it) }
        integrity.orphanSupplierIds(companyId).forEach { restoreMissing(companyId, "Supplier", it) }
        integrity.orphanProductIds(companyId).forEach { restoreMissing(companyId, "Product", it) }
    }

    // ---------------------------------------------------------------------------------------
    // One-time repair of documents damaged by older builds.
    // ---------------------------------------------------------------------------------------

    /**
     * Before this build, a bill edit that lost a conflict still pushed its stock and credit rows,
     * so the cloud (and every device) could hold both edits' rows for one bill: stock and dues
     * counted twice. This finds every bill and purchase whose rows no longer add up to its lines
     * and rebuilds them from the document itself.
     *
     * The rebuilt rows use ids and payloads derived only from the document, so when several
     * devices run this at once they write identical rows and the server treats the repeats as
     * no-ops instead of doubling again. Documents with anything still in the sync queue are
     * skipped; they are settled by the normal conflict path first.
     *
     * Documents with no stock rows at all are left alone: those come from builds that never wrote
     * them, and creating them now would move stock that was never counted.
     */
    /** Returns how many documents had to be skipped because they still had sync work queued. */
    suspend fun repairDocumentChildren(companyId: String): Int = database.withTransaction {
        var skipped = 0
        for (sale in database.saleDao().getSales(companyId).first()) {
            val lines = database.saleDao().getSaleItemsList(companyId, sale.id).groupBy { it.productId }.mapValues { (_, l) -> -l.sumOf { it.quantity } }
            if (repairDocument(companyId, "Sale", sale.id, sale.revision, sale.createdAtEpochMs, "SALE", lines, sale.creditAppliedMinorUnits, "Bill ${sale.billNumber}") == null) skipped++
        }
        for (purchase in database.purchaseDao().getPurchases(companyId).first()) {
            val lines = database.purchaseDao().getPurchaseItemsList(companyId, purchase.id).groupBy { it.productId }.mapValues { (_, l) -> l.sumOf { it.quantity } }
            if (repairDocument(companyId, "Purchase", purchase.id, purchase.revision, purchase.createdAtEpochMs, "PURCHASE", lines, purchase.creditAppliedMinorUnits, "Purchase ${purchase.orderNumber ?: purchase.id}") == null) skipped++
        }
        skipped
    }

    private suspend fun repairDocument(
        companyId: String, type: String, id: String, revision: Long, createdAt: Long,
        movementType: String, expectedStock: Map<String, Long>, creditApplied: Long, label: String,
    ): Boolean? {
        // null = not checked yet: something for this document is still queued. The repair is
        // run again after the next pull until nothing had to be skipped.
        if (queue.hasUnresolved(companyId, type, id, SyncWorker.MAX_SYNC_ATTEMPTS)) return null
        val children = childrenOf(companyId, type, id)
        if (children.any { (childType, childId) -> queue.hasUnresolved(companyId, childType, childId, SyncWorker.MAX_SYNC_ATTEMPTS) }) return null

        val detail = JSONObject()
        var changed = false

        // Stock rows.
        val movements = database.saleDao().movementsFor(companyId, id).filter { it.type == movementType }
        val actualStock = movements.groupBy { it.productId }.mapValues { (_, m) -> m.sumOf { it.quantityDelta } }
        if (movements.isNotEmpty() && ConflictPolicy.childTotalsMismatch(expectedStock, actualStock)) {
            detail.put("stockBefore", JSONObject(actualStock as Map<*, *>)).put("stockAfter", JSONObject(expectedStock as Map<*, *>))
            movements.forEach { removeChild(companyId, "StockMovement", it.id) }
            for ((productId, quantity) in expectedStock) {
                if (quantity == 0L) continue
                val rowId = "repair:$type:$id:$revision:$productId"
                val row = com.kadaikutty.pos.feature.billing.data.StockMovementEntity(rowId, companyId, productId, quantity, movementType, id, createdAt)
                database.saleDao().insertStockMovements(listOf(row))
                syncManager.enqueueResolved(companyId, "StockMovement", rowId, "INSERT", JSONObject()
                    .put("id", rowId).put("companyId", companyId).put("productId", productId).put("quantityDelta", quantity)
                    .put("type", movementType).put("referenceId", id).put("createdAtEpochMs", createdAt).put("_schemaVersion", 1), editedAt = 0L)
            }
            changed = true
        }

        // Credit rows.
        if (type == "Sale") {
            val credits = database.saleDao().creditsFor(companyId, id)
            // Settlement rows of an edit that no longer exists.
            credits.filter { it.amountMinorUnits < 0 && ConflictPolicy.isStaleRevisionRow(it.id, "sale-settlement", id, revision) }.forEach {
                removeChild(companyId, "CustomerCredit", it.id); changed = true
                detail.put("removedStaleSettlement", it.id)
            }
            val positives = credits.filter { it.amountMinorUnits > 0 }
            if (positives.isNotEmpty() && positives.sumOf { it.amountMinorUnits } != creditApplied) {
                val template = positives.minBy { it.id }
                detail.put("creditBefore", positives.sumOf { it.amountMinorUnits }).put("creditAfter", creditApplied)
                positives.forEach { removeChild(companyId, "CustomerCredit", it.id) }
                if (creditApplied > 0) {
                    val rowId = "repair-credit:$type:$id:$revision"
                    val row = template.copy(id = rowId, amountMinorUnits = creditApplied, syncStatus = SyncStatus.LOCAL_ONLY)
                    database.masterDao().insertCustomerCredit(row)
                    syncManager.enqueueResolved(companyId, "CustomerCredit", rowId, "INSERT", JSONObject()
                        .put("id", rowId).put("companyId", companyId).put("customerId", row.customerId).put("amountMinorUnits", row.amountMinorUnits)
                        .put("reason", row.reason).put("referenceId", id).put("dateEpochMs", row.dateEpochMs).put("syncStatus", "SYNCED").put("_schemaVersion", 1), editedAt = 0L)
                }
                changed = true
            }
        } else {
            val positives = database.purchaseDao().creditsFor(companyId, id).filter { it.amountMinorUnits > 0 }
            if (positives.isNotEmpty() && positives.sumOf { it.amountMinorUnits } != creditApplied) {
                val template = positives.minBy { it.id }
                detail.put("creditBefore", positives.sumOf { it.amountMinorUnits }).put("creditAfter", creditApplied)
                positives.forEach { removeChild(companyId, "SupplierCredit", it.id) }
                if (creditApplied > 0) {
                    val rowId = "repair-credit:$type:$id:$revision"
                    val row = template.copy(id = rowId, amountMinorUnits = creditApplied, syncStatus = SyncStatus.LOCAL_ONLY)
                    database.masterDao().insertSupplierCredit(row)
                    syncManager.enqueueResolved(companyId, "SupplierCredit", rowId, "INSERT", JSONObject()
                        .put("id", rowId).put("companyId", companyId).put("supplierId", row.supplierId).put("amountMinorUnits", row.amountMinorUnits)
                        .put("terms", row.terms).put("referenceId", id).put("dueDateEpochMs", row.dueDateEpochMs).put("dateEpochMs", row.dateEpochMs)
                        .put("syncStatus", "SYNCED").put("_schemaVersion", 1), editedAt = 0L)
                }
                changed = true
            }
        }

        if (changed) {
            log(companyId, type, id, "REPAIRED",
                "$label had stock or credit entries that did not match its lines (left over from an edit on two devices in an older version). They were rebuilt from the bill itself.",
                detail)
        }
        return changed
    }

    /** Removes one child row here, and from the cloud if it ever got there. */
    private suspend fun removeChild(companyId: String, childType: String, childId: String) {
        queue.supersede(companyId, childType, childId, "Resolved: rebuilt from its document", System.currentTimeMillis())
        if (hasCloudVersion(companyId, childType, childId)) {
            readRow(companyId, childType, childId)?.let { syncManager.enqueueResolved(companyId, childType, childId, "DELETE", it, com.kadaikutty.pos.core.common.TrustedClock.now()) }
        }
        RecordApplier.deleteRecord(database, companyId, childType, childId)
    }

    // ---------------------------------------------------------------------------------------
    // Documents.
    // ---------------------------------------------------------------------------------------

    private suspend fun adoptServerDocument(companyId: String, type: String, id: String, serverPayload: JSONObject, serverUpdatedAt: Long) {
        // The other device's edit owns the bill now; this device stops guarding its rows.
        ops.delete(companyId, ownedKey(type, id))
        // This device's losing edit created stock and credit rows that never reached the cloud;
        // they belong to a version of the bill that no longer exists. Rows that did reach the
        // cloud are the server's business and stay until the server says otherwise.
        for ((childType, childId) in childrenOf(companyId, type, id)) {
            if (!hasCloudVersion(companyId, childType, childId)) {
                queue.supersede(companyId, childType, childId, "Resolved: part of a losing edit", System.currentTimeMillis())
                RecordApplier.deleteRecord(database, companyId, childType, childId)
            }
        }
        preparePulledUpsert(companyId, type, id, serverPayload)
        RecordApplier.upsertRecord(database, companyId, type, id, serverPayload, serverUpdatedAt)
    }

    /** Deletes a document here and makes sure its stock and credit rows go everywhere. */
    private suspend fun dropDocumentLocally(companyId: String, type: String, id: String) {
        ops.delete(companyId, ownedKey(type, id))
        markDocumentDeleted(companyId, type, id)
        sweepDocument(companyId, type, id)
        when (type) {
            "Sale" -> { database.saleDao().deleteSaleItems(companyId, id); database.saleDao().deleteSale(companyId, id) }
            "Purchase" -> { database.purchaseDao().deletePurchaseItems(companyId, id); database.purchaseDao().deletePurchase(companyId, id) }
        }
    }

    suspend fun markDocumentDeleted(companyId: String, type: String, id: String) {
        ops.put(LocalOperationEntity(companyId, "$DOC_TOMBSTONE_PREFIX$type:$id", System.currentTimeMillis().toString()))
    }

    /** Removes every child row of a deleted document; returns how many were found. */
    private suspend fun sweepDocument(companyId: String, type: String, id: String): Int {
        val children = childrenOf(companyId, type, id)
        for ((childType, childId) in children) {
            if (hasCloudVersion(companyId, childType, childId)) {
                // It exists in the cloud: the delete has to travel.
                if (!queue.hasPendingDelete(companyId, childType, childId)) {
                    queue.supersede(companyId, childType, childId, "Resolved: parent document cancelled", System.currentTimeMillis())
                    readRow(companyId, childType, childId)?.let { syncManager.enqueueResolved(companyId, childType, childId, "DELETE", it, com.kadaikutty.pos.core.common.TrustedClock.now()) }
                }
            } else {
                // Never left this device: just drop it and anything queued for it.
                queue.supersede(companyId, childType, childId, "Resolved: parent document cancelled", System.currentTimeMillis())
            }
            RecordApplier.deleteRecord(database, companyId, childType, childId)
        }
        return children.size
    }

    private suspend fun childrenOf(companyId: String, type: String, id: String): List<Pair<String, String>> = when (type) {
        "Sale" -> database.saleDao().movementsFor(companyId, id).map { "StockMovement" to it.id } +
            database.saleDao().creditsFor(companyId, id).map { "CustomerCredit" to it.id }
        "Purchase" -> database.saleDao().movementsFor(companyId, id).map { "StockMovement" to it.id } +
            database.purchaseDao().creditsFor(companyId, id).map { "SupplierCredit" to it.id }
        else -> emptyList()
    }

    private suspend fun documentExists(companyId: String, type: String, id: String): Boolean = when (type) {
        "Sale" -> database.saleDao().getSaleById(companyId, id) != null
        "Purchase" -> database.purchaseDao().getById(companyId, id) != null
        else -> false
    }

    private suspend fun recreate(companyId: String, type: String, id: String, localRow: JSONObject, editedAt: Long) {
        when (type) {
            "Sale" -> database.saleDao().getSaleById(companyId, id)?.let { syncManager.enqueueSale(it, database.saleDao().getSaleItemsList(companyId, id), "INSERT", editedAt) }
            "Purchase" -> database.purchaseDao().getById(companyId, id)?.let { syncManager.enqueuePurchase(it, database.purchaseDao().getPurchaseItemsList(companyId, id), "INSERT", editedAt) }
            else -> syncManager.enqueueResolved(companyId, type, id, "INSERT", localRow, editedAt)
        }
    }

    /**
     * This device's edit of a bill is the later one and replaces the cloud copy whole. The stock
     * and credit rows of the other device's edit may already be in the cloud, or still arriving;
     * they belong to a version of the bill that no longer exists. So the bill's valid rows are
     * written down here (this edit's own, which never left the device because children wait for
     * their parent), and every sweep removes any other row that turns up for this bill, until
     * someone else makes a newer version of it (see [onPulledDocument]).
     */
    private suspend fun keepLocalDocument(companyId: String, type: String, id: String, editedAt: Long) {
        val ours = childrenOf(companyId, type, id).filter { (childType, childId) -> !hasCloudVersion(companyId, childType, childId) }.map { it.second }
        ops.put(LocalOperationEntity(companyId, ownedKey(type, id), JSONArray(ours).toString()))
        when (type) {
            "Sale" -> database.saleDao().getSaleById(companyId, id)?.let { syncManager.enqueueSale(it, database.saleDao().getSaleItemsList(companyId, id), "UPDATE", editedAt) }
            "Purchase" -> database.purchaseDao().getById(companyId, id)?.let { syncManager.enqueuePurchase(it, database.purchaseDao().getPurchaseItemsList(companyId, id), "UPDATE", editedAt) }
        }
        enforceOwnedChildren(companyId, type, id)
    }

    private suspend fun enforceOwnedChildren(companyId: String, type: String, id: String) {
        val owned = ops.get(companyId, ownedKey(type, id))
            ?.let { raw -> runCatching { JSONArray(raw).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } }.getOrNull() }
            ?: return
        for ((childType, childId) in childrenOf(companyId, type, id)) {
            if (childId !in owned) removeChild(companyId, childType, childId)
        }
    }

    /**
     * A pulled copy of a bill. If it is newer than anything this device wrote, someone else now
     * owns the bill's latest version, so this device stops policing its rows.
     */
    suspend fun onPulledDocument(companyId: String, type: String, id: String, version: Long) {
        val known = ops.get(companyId, versionKey(type, id))?.toLongOrNull() ?: 0L
        if (version > known) ops.delete(companyId, ownedKey(type, id))
    }

    /**
     * This device just edited a bill itself. If it was policing the bill's rows, the valid set is
     * now this edit's rows.
     */
    suspend fun onLocalDocumentEdit(companyId: String, type: String, id: String) {
        if (ops.get(companyId, ownedKey(type, id)) == null) return
        val current = childrenOf(companyId, type, id).map { it.second }
        ops.put(LocalOperationEntity(companyId, ownedKey(type, id), JSONArray(current).toString()))
    }

    // ---------------------------------------------------------------------------------------
    // Masters that other rows depend on.
    // ---------------------------------------------------------------------------------------

    /** Deleting this record here would lose track of money, stock, bill lines or products. */
    suspend fun isInUse(companyId: String, type: String, id: String): Boolean = when (type) {
        "Customer" -> database.masterDao().customerBalance(companyId, id) != 0L
        "Supplier" -> database.masterDao().supplierBalance(companyId, id) != 0L
        "Product" -> database.saleDao().stock(companyId, id) != 0L ||
            integrity.saleItemCount(companyId, id) > 0 || integrity.purchaseItemCount(companyId, id) > 0
        "Category" -> database.masterDao().productCountInCategory(companyId, id) > 0
        else -> false
    }

    private suspend fun ensureCategory(companyId: String, categoryId: String) {
        if (categoryId.isBlank() || readRow(companyId, "Category", categoryId) != null) return
        restoreMissing(companyId, "Category", categoryId)
    }

    private suspend fun ensureProduct(companyId: String, productId: String, name: String?, unitType: String?) {
        if (productId.isBlank() || readRow(companyId, "Product", productId) != null) return
        restoreMissing(companyId, "Product", productId, name, unitType)
    }

    /**
     * Brings back a record that other rows still point at, from the last cloud copy this device
     * saw if it has one, or as a clearly named placeholder if not. The placeholder carries a zero
     * timestamp so any real copy of the record wins every field when it turns up.
     */
    private suspend fun restoreMissing(companyId: String, type: String, id: String, nameHint: String? = null, unitHint: String? = null) {
        val known = cloudPayload(companyId, type, id)?.takeIf { it.has("name") }
        val data = (known ?: placeholder(companyId, type, nameHint, unitHint)).put("id", id).put("companyId", companyId)
        if (type == "Product") {
            val categoryId = data.optString("categoryId")
            if (categoryId.isBlank()) data.put("categoryId", integrity.anyCategoryId(companyId) ?: createPlaceholderCategory(companyId))
            else ensureCategory(companyId, categoryId)
        }
        RecordApplier.upsertRecord(database, companyId, type, id, data, data.optLong("updatedAtEpochMs"))
        if (!queue.hasUnresolved(companyId, type, id, SyncWorker.MAX_SYNC_ATTEMPTS)) {
            // Edit time of the copy it came from, or 0 for a placeholder: never newer than a real edit.
            syncManager.enqueueResolved(companyId, type, id, if (hasCloudVersion(companyId, type, id)) "UPDATE" else "INSERT", data,
                editedAt = if (known != null) ConflictPolicy.editTime(known, 0L) else 0L)
        }
        log(companyId, type, id, Resolution.RESTORED,
            if (known != null) "${label(type, data)} had been deleted on another device while still in use here, so it was restored."
            else "${label(type, data)} was missing but still in use (money, stock or bill lines point at it), so a placeholder was created. Rename it if needed.",
            JSONObject().put("restored", data))
    }

    private suspend fun createPlaceholderCategory(companyId: String): String {
        val id = "restored-category"
        if (readRow(companyId, "Category", id) == null) restoreMissing(companyId, "Category", id)
        return id
    }

    private fun placeholder(companyId: String, type: String, nameHint: String?, unitHint: String?): JSONObject {
        val base = JSONObject().put("companyId", companyId).put("createdAtEpochMs", 0L).put("updatedAtEpochMs", 0L)
        return when (type) {
            "Category" -> base.put("name", "Restored category")
            "Product" -> base.put("name", nameHint ?: "Restored product").put("categoryId", "")
                .put("purchasePriceMinorUnits", 0L).put("salePriceMinorUnits", 0L)
                .put("unitType", unitHint ?: "PIECE").put("barcode", JSONObject.NULL).put("minStockLevel", 0.0)
            "Customer" -> base.put("name", nameHint ?: "Restored customer").put("phone", JSONObject.NULL).put("address", JSONObject.NULL).put("creditLimitMinorUnits", 0L)
            "Supplier" -> base.put("name", nameHint ?: "Restored supplier").put("phone", JSONObject.NULL).put("address", JSONObject.NULL)
            else -> base.put("name", nameHint ?: "Restored record")
        }
    }

    // ---------------------------------------------------------------------------------------
    // Rows as JSON.
    // ---------------------------------------------------------------------------------------

    /** The local row in the same shape the sync payloads use (column name = payload key). */
    suspend fun readRow(companyId: String, type: String, id: String): JSONObject? {
        val table = TABLES[type] ?: return null
        return database.query(SimpleSQLiteQuery("SELECT * FROM $table WHERE companyId = ? AND id = ?", arrayOf(companyId, id))).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val row = JSONObject()
            for (index in 0 until cursor.columnCount) {
                val name = cursor.getColumnName(index)
                if (name == "syncStatus") continue
                when (cursor.getType(index)) {
                    android.database.Cursor.FIELD_TYPE_NULL -> row.put(name, JSONObject.NULL)
                    android.database.Cursor.FIELD_TYPE_INTEGER -> row.put(name, cursor.getLong(index))
                    android.database.Cursor.FIELD_TYPE_FLOAT -> row.put(name, cursor.getDouble(index))
                    android.database.Cursor.FIELD_TYPE_STRING -> row.put(name, cursor.getString(index))
                    else -> Unit
                }
            }
            row
        }
    }

    private suspend fun deleteLocally(companyId: String, type: String, id: String): Boolean =
        runCatching { RecordApplier.deleteRecord(database, companyId, type, id) }.isSuccess

    private fun markRowStatus(type: String, id: String, status: SyncStatus) {
        val table = TABLES[type] ?: return
        runCatching { database.openHelper.writableDatabase.execSQL("UPDATE $table SET syncStatus = ? WHERE id = ?", arrayOf<Any>(status.name, id)) }
    }

    private fun sameRecord(a: JSONObject, b: JSONObject): Boolean {
        val keys = (a.keys().asSequence() + b.keys().asSequence()).toSet() - VOLATILE_KEYS
        return keys.all { ConflictPolicy.same(a.opt(it), b.opt(it)) }
    }

    // ---------------------------------------------------------------------------------------
    // Log.
    // ---------------------------------------------------------------------------------------

    private suspend fun log(companyId: String, type: String, id: String, resolution: Resolution, summary: String, detail: JSONObject) =
        log(companyId, type, id, resolution.name, summary, detail)

    private suspend fun log(companyId: String, type: String, id: String, resolution: String, summary: String, detail: JSONObject) {
        database.syncConflictDao().insert(SyncConflictEntity(newRecordId(), companyId, type, id, resolution, summary, detail.toString(), System.currentTimeMillis()))
    }

    private fun describe(type: String, resolution: Resolution, server: ServerState, intent: LocalIntent, local: JSONObject, serverPayload: JSONObject, clashes: List<ConflictPolicy.FieldClash>): String {
        val what = label(type, if (serverPayload.length() > 1) serverPayload else local)
        return when (resolution) {
            Resolution.SERVER_WINS -> when {
                ConflictPolicy.kindOf(type) == Kind.DOCUMENT && server == ServerState.DELETED ->
                    "$what was cancelled on another device while this device changed it. The cancellation stands; this device's change is saved in this log."
                ConflictPolicy.kindOf(type) == Kind.DOCUMENT ->
                    "$what was edited on two devices. The other device's edit was made later, so it was kept; this device's edit is saved in this log so it can be redone."
                else -> "$what was deleted on another device while this device changed it. The delete stands."
            }
            Resolution.LOCAL_WINS -> if (intent == LocalIntent.UPSERT)
                "$what was edited on two devices. This device's edit was made later, so it was kept; the other edit is saved in this log so it can be redone."
                else "$what was changed on another device after this device deleted or cancelled it. The delete/cancel stands."
            Resolution.MERGED -> if (clashes.isEmpty()) "$what was changed on two devices; both sets of changes were combined."
                else "$what was changed on two devices; both were combined. Same field changed on both: ${clashes.joinToString { "${it.field} (kept ${it.kept})" }}."
            Resolution.RESTORED -> "$what was deleted on another device but is still in use here, so it was restored."
            Resolution.RECREATED -> "$what was missing from the cloud and was uploaded again from this device."
            Resolution.REKEYED -> "$what collided with a different entry using the same id. Both were kept."
            Resolution.ALREADY_IN_SYNC -> "$what was already in sync."
        }
    }

    private fun label(type: String, payload: JSONObject): String = when (type) {
        "Sale" -> "Bill ${payload.optString("billNumber").ifBlank { "" }}".trim()
        "Purchase" -> "Purchase ${payload.optString("orderNumber").ifBlank { payload.optString("invoiceNumber") }}".trim()
        "StockMovement" -> "A stock entry"
        "CustomerCredit" -> "A customer credit entry"
        "SupplierCredit" -> "A supplier credit entry"
        "Expense" -> "Expense \"${payload.optString("description")}\""
        else -> "$type \"${payload.optString("name")}\""
    }

    companion object {
        const val DOC_TOMBSTONE_PREFIX = "doc_deleted:"
        const val OWNED_CHILDREN_PREFIX = "doc_owned_children:"
        fun ownedKey(type: String, id: String) = "$OWNED_CHILDREN_PREFIX$type:$id"
        private const val TOMBSTONE_RETENTION_MS = 180L * 24 * 60 * 60 * 1000
        private val VOLATILE_KEYS = setOf("companyId", "syncStatus", "_schemaVersion")

        fun versionKey(type: String, id: String) = "cloud_version:$type:$id"
        fun payloadKey(type: String, id: String) = "cloud_payload:$type:$id"

        val TABLES = mapOf(
            "Category" to "categories", "Product" to "products", "Customer" to "customers", "Supplier" to "suppliers",
            "Expense" to "expenses", "Sale" to "sales", "Purchase" to "purchases", "CustomerCredit" to "customer_credits",
            "SupplierCredit" to "supplier_credits", "StockMovement" to "stock_movements"
        )
    }
}
