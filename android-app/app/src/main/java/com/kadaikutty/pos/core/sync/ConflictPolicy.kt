package com.kadaikutty.pos.core.sync

import org.json.JSONArray
import org.json.JSONObject

/**
 * The rules for every way two devices can disagree about one record. Pure functions on JSON, no
 * database, so each rule is pinned by ConflictPolicyTest. ConflictResolver carries the decisions
 * out against Room.
 *
 * Three families of records behave differently:
 * - MASTER (Category, Product, Customer, Supplier, Expense): edited in place. Merged field by field.
 * - DOCUMENT (Sale, Purchase): a bill with lines and money attached. Never merged line by line.
 * - LEDGER (StockMovement, CustomerCredit, SupplierCredit): append-only rows that are never edited,
 *   only created and deleted. Stock and balances are their sum, so they must never be lost or doubled.
 */
object ConflictPolicy {
    enum class Kind { MASTER, DOCUMENT, LEDGER }

    fun kindOf(entityType: String): Kind = when (entityType) {
        "Sale", "Purchase" -> Kind.DOCUMENT
        "StockMovement", "CustomerCredit", "SupplierCredit" -> Kind.LEDGER
        else -> Kind.MASTER
    }

    /** What this device wants for the record, read from its own database at resolution time. */
    enum class LocalIntent { UPSERT, DELETE }

    /** What the server holds now. */
    enum class ServerState { MISSING, LIVE, DELETED }

    enum class Resolution {
        /** Server copy is the answer; the local change is dropped (and logged when it lost something). */
        SERVER_WINS,
        /** Local change re-sent on top of the server's current version. */
        LOCAL_WINS,
        /** Both sides' changes kept, field by field. */
        MERGED,
        /** Deleted on one side but still in use on this device (money owed, stock, products in a category): restored. */
        RESTORED,
        /** The server lost the record entirely; re-created from this device. */
        RECREATED,
        /** Two different ledger rows collided on one id; the local one is kept under a new id. */
        REKEYED,
        /** Both sides already agree. */
        ALREADY_IN_SYNC,
    }

    /**
     * The decision table. [inUse] matters only for MASTER records and means "deleting this here
     * would lose track of money, stock or products"; see ConflictResolver.isInUse.
     */
    fun decide(kind: Kind, local: LocalIntent, server: ServerState, inUse: Boolean, localEditIsLater: Boolean = false): Resolution = when (server) {
        // The record was purged from the cloud (or never landed). Nothing to lose by re-sending.
        ServerState.MISSING -> if (local == LocalIntent.DELETE) Resolution.ALREADY_IN_SYNC else Resolution.RECREATED

        ServerState.DELETED -> when {
            local == LocalIntent.DELETE -> Resolution.ALREADY_IN_SYNC
            // A cancelled bill stays cancelled: its money and stock were already reversed.
            kind == Kind.DOCUMENT -> Resolution.SERVER_WINS
            // Ledger rows are never edited, so an upsert here is a stale re-upload of a deleted row.
            kind == Kind.LEDGER -> Resolution.SERVER_WINS
            // Deleting a customer who still owes money, or a product still in stock, would silently
            // lose it. Everywhere else the delete stands.
            inUse -> Resolution.RESTORED
            else -> Resolution.SERVER_WINS
        }

        ServerState.LIVE -> when {
            // Cancelling a bill beats an edit made elsewhere; deleting a master record or a ledger
            // row beats an edit too — the delete was checked against usage when it was made.
            local == LocalIntent.DELETE -> Resolution.LOCAL_WINS
            // Two edits of one bill cannot be merged line by line without inventing a bill nobody
            // made, so one whole edit wins: the one made later, by server-corrected edit time
            // (TrustedClock). A tie goes to the server. The losing edit is logged in full.
            kind == Kind.DOCUMENT -> if (localEditIsLater) Resolution.LOCAL_WINS else Resolution.SERVER_WINS
            // Ledger rows are never edited, so two different payloads under one id are two real
            // entries that happened to share an id. Keep both.
            kind == Kind.LEDGER -> Resolution.REKEYED
            else -> Resolution.MERGED
        }
    }

    /** Keys that describe the write, not the record. Never compared, never merged. */
    private val ignoredKeys = setOf("id", "companyId", "syncStatus", "_schemaVersion", "createdAtEpochMs", "updatedAtEpochMs", SyncManager.EDITED_AT)

    /**
     * When a side's edit was made, in server time. Every write since this build carries
     * `editedAtEpochMs` from TrustedClock. A server copy written by an older build has none, so the
     * server's own receive time stands in: it is later than the real edit, which errs towards the
     * server, never towards a device clock.
     *
     * An explicit 0 is kept as 0, not replaced by the fallback: restores and placeholders are
     * written with 0 on purpose so that any real edit beats them.
     */
    fun editTime(payload: JSONObject?, fallback: Long): Long =
        if (payload != null && payload.has(SyncManager.EDITED_AT)) payload.optLong(SyncManager.EDITED_AT, 0L) else fallback

    data class FieldClash(val field: String, val local: Any?, val server: Any?, val kept: String)

    data class MergeResult(
        val merged: JSONObject,
        val clashes: List<FieldClash>,
        /** True when the merged record differs from the server's, i.e. it has to be sent back. */
        val needsPush: Boolean,
    )

    /**
     * Three-way merge for a MASTER record.
     *
     * [base] is the last version this device saw from the cloud (null if it never had one), [local]
     * is this device's current row, [server] the cloud's current record. A field changed only on
     * one side takes that side's value. A field changed on both sides to different values is a
     * clash; the side edited later ([localEditedAt] vs [serverEditedAt], both server-corrected, see
     * [editTime]) wins, ties go to the server, and every clash is reported so nothing is
     * overwritten without a trace.
     */
    fun merge(base: JSONObject?, local: JSONObject, server: JSONObject, localEditedAt: Long, serverEditedAt: Long): MergeResult {
        val merged = JSONObject(server.toString())
        val clashes = mutableListOf<FieldClash>()
        val localNewer = localEditedAt > serverEditedAt
        for (key in local.keys().asSequence().toList()) {
            if (key in ignoredKeys) continue
            val localValue = local.opt(key)
            val serverValue = server.opt(key)
            if (same(localValue, serverValue)) continue
            val localChanged = base == null || !same(localValue, base.opt(key))
            val serverChanged = base != null && !same(serverValue, base.opt(key))
            when {
                !localChanged -> Unit // Only the server moved: keep it.
                !serverChanged && base != null -> merged.put(key, localValue ?: JSONObject.NULL)
                else -> {
                    // Both moved, or there is no base to tell which one did.
                    val keepLocal = localNewer
                    if (keepLocal) merged.put(key, localValue ?: JSONObject.NULL)
                    clashes += FieldClash(key, plain(localValue), plain(serverValue), if (keepLocal) "local" else "server")
                }
            }
        }
        merged.put("updatedAtEpochMs", maxOf(local.optLong("updatedAtEpochMs"), server.optLong("updatedAtEpochMs")))
        val needsPush = server.keys().asSequence().plus(merged.keys().asSequence()).toSet()
            .filter { it !in ignoredKeys }
            .any { !same(merged.opt(it), server.opt(it)) }
        return MergeResult(merged, clashes, needsPush)
    }

    /**
     * True when a document's stock rows no longer add up to its lines, per product. This is what an
     * edit that lost a conflict under older builds left behind: both edits' stock rows in the cloud.
     */
    fun childTotalsMismatch(expected: Map<String, Long>, actual: Map<String, Long>): Boolean =
        (expected.keys + actual.keys).any { (expected[it] ?: 0L) != (actual[it] ?: 0L) }

    /**
     * Credit rows written by older builds had ids like `sale-settlement:<billId>:<revision>`. One whose
     * revision is not the bill's current one belongs to an edit that no longer exists.
     */
    fun isStaleRevisionRow(rowId: String, prefix: String, documentId: String, currentRevision: Long): Boolean {
        val head = "$prefix:$documentId:"
        if (!rowId.startsWith(head)) return false
        val revision = rowId.removePrefix(head).toLongOrNull() ?: return false
        return revision != currentRevision
    }

    /** Value equality that treats JSON null, a missing key and Kotlin null alike, and numbers by value. */
    fun same(a: Any?, b: Any?): Boolean {
        val x = plain(a)
        val y = plain(b)
        if (x == null || y == null) return x == null && y == null
        if (x is Number && y is Number) return x.toDouble() == y.toDouble()
        if (x is JSONObject || x is JSONArray || y is JSONObject || y is JSONArray) return x.toString() == y.toString()
        return x.toString() == y.toString()
    }

    private fun plain(value: Any?): Any? = if (value == null || value == JSONObject.NULL) null else value
}
