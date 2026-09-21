package com.kadaikutty.pos.core.sync

import com.kadaikutty.pos.core.sync.ConflictPolicy.Kind
import com.kadaikutty.pos.core.sync.ConflictPolicy.LocalIntent
import com.kadaikutty.pos.core.sync.ConflictPolicy.Resolution
import com.kadaikutty.pos.core.sync.ConflictPolicy.ServerState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictPolicyTest {

    // ---- decision table: every (kind, local, server) combination ---------------------------

    @Test
    fun `server lost the record - recreate unless this device deleted it too`() {
        for (kind in Kind.values()) {
            assertEquals(Resolution.RECREATED, ConflictPolicy.decide(kind, LocalIntent.UPSERT, ServerState.MISSING, inUse = false))
            assertEquals(Resolution.ALREADY_IN_SYNC, ConflictPolicy.decide(kind, LocalIntent.DELETE, ServerState.MISSING, inUse = false))
        }
    }

    @Test
    fun `both sides deleted - nothing to do`() {
        for (kind in Kind.values()) {
            assertEquals(Resolution.ALREADY_IN_SYNC, ConflictPolicy.decide(kind, LocalIntent.DELETE, ServerState.DELETED, inUse = true))
        }
    }

    @Test
    fun `a cancelled bill stays cancelled even if edited here`() {
        assertEquals(Resolution.SERVER_WINS, ConflictPolicy.decide(Kind.DOCUMENT, LocalIntent.UPSERT, ServerState.DELETED, inUse = false))
    }

    @Test
    fun `a stale re-upload of a deleted ledger row does not bring it back`() {
        assertEquals(Resolution.SERVER_WINS, ConflictPolicy.decide(Kind.LEDGER, LocalIntent.UPSERT, ServerState.DELETED, inUse = false))
    }

    @Test
    fun `a master deleted elsewhere is restored only while still in use here`() {
        assertEquals(Resolution.RESTORED, ConflictPolicy.decide(Kind.MASTER, LocalIntent.UPSERT, ServerState.DELETED, inUse = true))
        assertEquals(Resolution.SERVER_WINS, ConflictPolicy.decide(Kind.MASTER, LocalIntent.UPSERT, ServerState.DELETED, inUse = false))
    }

    @Test
    fun `a local delete or cancel beats an edit made elsewhere`() {
        for (kind in Kind.values()) {
            assertEquals(Resolution.LOCAL_WINS, ConflictPolicy.decide(kind, LocalIntent.DELETE, ServerState.LIVE, inUse = false))
        }
    }

    @Test
    fun `two edits of one bill - the one made later wins, a tie goes to the server`() {
        assertEquals(Resolution.LOCAL_WINS, ConflictPolicy.decide(Kind.DOCUMENT, LocalIntent.UPSERT, ServerState.LIVE, inUse = false, localEditIsLater = true))
        assertEquals(Resolution.SERVER_WINS, ConflictPolicy.decide(Kind.DOCUMENT, LocalIntent.UPSERT, ServerState.LIVE, inUse = false, localEditIsLater = false))
    }

    @Test
    fun `edit time never overrides a cancel, a delete or a restore`() {
        // A later edit does not bring back a cancelled bill.
        assertEquals(Resolution.SERVER_WINS, ConflictPolicy.decide(Kind.DOCUMENT, LocalIntent.UPSERT, ServerState.DELETED, inUse = false, localEditIsLater = true))
        // A cancel here beats an edit there, whenever the edit was made.
        assertEquals(Resolution.LOCAL_WINS, ConflictPolicy.decide(Kind.DOCUMENT, LocalIntent.DELETE, ServerState.LIVE, inUse = false, localEditIsLater = false))
        // A customer who still owes money is restored whatever the times.
        assertEquals(Resolution.RESTORED, ConflictPolicy.decide(Kind.MASTER, LocalIntent.UPSERT, ServerState.DELETED, inUse = true, localEditIsLater = false))
    }

    @Test
    fun `edit time - missing falls back to the server's receive time, an explicit zero stays zero`() {
        assertEquals(500L, ConflictPolicy.editTime(JSONObject(), 500L))
        assertEquals(500L, ConflictPolicy.editTime(null, 500L))
        assertEquals(0L, ConflictPolicy.editTime(JSONObject().put(SyncManager.EDITED_AT, 0L), 500L))
        assertEquals(42L, ConflictPolicy.editTime(JSONObject().put(SyncManager.EDITED_AT, 42L), 500L))
    }

    @Test
    fun `the edit timestamp is never treated as a changed field`() {
        val base = product("Tea", 1000, null, 1)
        val result = merge(base, product("Tea", 1000, null, 9), product("Tea", 1000, null, 5))
        assertTrue(result.clashes.isEmpty())
        assertFalse(result.needsPush)
    }

    @Test
    fun `two different ledger rows on one id are both kept`() {
        assertEquals(Resolution.REKEYED, ConflictPolicy.decide(Kind.LEDGER, LocalIntent.UPSERT, ServerState.LIVE, inUse = false))
    }

    @Test
    fun `two edits of a master record are merged`() {
        assertEquals(Resolution.MERGED, ConflictPolicy.decide(Kind.MASTER, LocalIntent.UPSERT, ServerState.LIVE, inUse = true))
    }

    @Test
    fun `entity kinds`() {
        assertEquals(Kind.DOCUMENT, ConflictPolicy.kindOf("Sale"))
        assertEquals(Kind.DOCUMENT, ConflictPolicy.kindOf("Purchase"))
        assertEquals(Kind.LEDGER, ConflictPolicy.kindOf("StockMovement"))
        assertEquals(Kind.LEDGER, ConflictPolicy.kindOf("CustomerCredit"))
        assertEquals(Kind.LEDGER, ConflictPolicy.kindOf("SupplierCredit"))
        for (type in listOf("Category", "Product", "Customer", "Supplier", "Expense")) assertEquals(Kind.MASTER, ConflictPolicy.kindOf(type))
    }

    // ---- three-way merge -------------------------------------------------------------------

    private fun product(name: String, price: Long, barcode: String?, updatedAt: Long) = JSONObject()
        .put("id", "p1").put("companyId", "c").put("name", name).put("salePriceMinorUnits", price)
        .put("barcode", barcode ?: JSONObject.NULL).put("updatedAtEpochMs", updatedAt).put(SyncManager.EDITED_AT, updatedAt)

    private fun merge(base: JSONObject?, local: JSONObject, server: JSONObject) =
        ConflictPolicy.merge(base, local, server, local.getLong(SyncManager.EDITED_AT), server.getLong(SyncManager.EDITED_AT))

    @Test
    fun `different fields changed on each side are both kept`() {
        val base = product("Tea", 1000, null, 1)
        val local = product("Tea", 1200, null, 5)      // price changed here
        val server = product("Tea Powder", 1000, null, 3) // name changed there
        val result = merge(base, local, server)
        assertEquals("Tea Powder", result.merged.getString("name"))
        assertEquals(1200L, result.merged.getLong("salePriceMinorUnits"))
        assertTrue(result.clashes.isEmpty())
        assertTrue(result.needsPush)
    }

    @Test
    fun `same field changed on both sides - later edit wins and the clash is reported`() {
        val base = product("Tea", 1000, null, 1)
        val localLater = merge(base, product("Tea", 1200, null, 9), product("Tea", 1100, null, 3))
        assertEquals(1200L, localLater.merged.getLong("salePriceMinorUnits"))
        assertEquals(listOf("salePriceMinorUnits"), localLater.clashes.map { it.field })
        assertEquals("local", localLater.clashes.single().kept)

        val serverLater = merge(base, product("Tea", 1200, null, 3), product("Tea", 1100, null, 9))
        assertEquals(1100L, serverLater.merged.getLong("salePriceMinorUnits"))
        assertEquals("server", serverLater.clashes.single().kept)
        assertFalse(serverLater.needsPush)
    }

    @Test
    fun `a timestamp tie goes to the server`() {
        val result = merge(product("Tea", 1000, null, 1), product("Tea", 1200, null, 5), product("Tea", 1100, null, 5))
        assertEquals(1100L, result.merged.getLong("salePriceMinorUnits"))
    }

    @Test
    fun `only the server changed - nothing to send back`() {
        val base = product("Tea", 1000, null, 1)
        val result = merge(base, product("Tea", 1000, null, 1), product("Tea", 1100, null, 4))
        assertEquals(1100L, result.merged.getLong("salePriceMinorUnits"))
        assertTrue(result.clashes.isEmpty())
        assertFalse(result.needsPush)
    }

    @Test
    fun `clearing a field locally is a real change, not a missing value`() {
        val base = product("Tea", 1000, "890123", 1)
        val result = merge(base, product("Tea", 1000, null, 5), product("Tea", 1000, "890123", 1))
        assertTrue(result.merged.isNull("barcode"))
        assertTrue(result.needsPush)
    }

    @Test
    fun `no base - every differing field is a clash decided by timestamp`() {
        val result = merge(null, product("Tea", 1200, null, 2), product("Tea", 1100, null, 8))
        assertEquals(1100L, result.merged.getLong("salePriceMinorUnits"))
        assertEquals(1, result.clashes.size)
    }

    @Test
    fun `a placeholder with a zero timestamp never overwrites real data`() {
        val placeholder = product("Restored product", 0, null, 0)
        val real = product("Tea", 1000, "890123", 7)
        val result = merge(null, placeholder, real)
        assertEquals("Tea", result.merged.getString("name"))
        assertFalse(result.needsPush)
    }

    @Test
    fun `bookkeeping fields are never compared`() {
        val base = product("Tea", 1000, null, 1)
        val local = product("Tea", 1000, null, 1).put("syncStatus", "PENDING").put("_schemaVersion", 1)
        val result = merge(base, local, product("Tea", 1000, null, 1).put("syncStatus", "SYNCED"))
        assertTrue(result.clashes.isEmpty())
        assertFalse(result.needsPush)
    }

    @Test
    fun `numbers compare by value across int long and double`() {
        assertTrue(ConflictPolicy.same(0, 0.0))
        assertTrue(ConflictPolicy.same(1000L, 1000))
        assertTrue(ConflictPolicy.same(JSONObject.NULL, null))
        assertFalse(ConflictPolicy.same("a", null))
    }

    // ---- legacy document repair ------------------------------------------------------------

    @Test
    fun `stock rows that add up to the bill lines are left alone`() {
        assertFalse(ConflictPolicy.childTotalsMismatch(mapOf("p1" to -2L, "p2" to -1L), mapOf("p1" to -2L, "p2" to -1L)))
    }

    @Test
    fun `both edits' stock rows present is a mismatch`() {
        // Winner sold 2 of p1; the losing edit's rows (1 of p1, 3 of p2) were also pushed.
        assertTrue(ConflictPolicy.childTotalsMismatch(mapOf("p1" to -2L), mapOf("p1" to -3L, "p2" to -3L)))
    }

    @Test
    fun `a product missing on either side is a mismatch`() {
        assertTrue(ConflictPolicy.childTotalsMismatch(mapOf("p1" to -2L), emptyMap()))
        assertTrue(ConflictPolicy.childTotalsMismatch(emptyMap(), mapOf("p1" to -2L)))
    }

    @Test
    fun `old revision credit rows are recognised by their id`() {
        assertTrue(ConflictPolicy.isStaleRevisionRow("sale-settlement:bill1:0", "sale-settlement", "bill1", 1L))
        assertFalse(ConflictPolicy.isStaleRevisionRow("sale-settlement:bill1:1", "sale-settlement", "bill1", 1L))
        assertFalse(ConflictPolicy.isStaleRevisionRow("sale-settlement:bill2:0", "sale-settlement", "bill1", 1L))
        assertFalse(ConflictPolicy.isStaleRevisionRow("5f1c2e", "sale-settlement", "bill1", 1L))
    }

    // ---- queue folding -----------------------------------------------------------------------

    @Test
    fun `every queued write is stamped with its edit time`() {
        assertEquals(1234L, JSONObject(SyncManager.stampEditTime("""{"name":"A"}""", 1234L)).getLong(SyncManager.EDITED_AT))
        assertEquals("not json", SyncManager.stampEditTime("not json", 1L))
    }

    private fun fold(existingOp: String, existing: String, incomingOp: String, incoming: String) =
        SyncManager.foldQueuedOperation(existingOp, existing, incomingOp, incoming)

    @Test
    fun `editing a bill before its first sync keeps it an insert with the new content`() {
        val folded = fold("INSERT", """{"total":100}""", "UPDATE", """{"total":150}""")!!
        assertEquals("INSERT", folded.first)
        assertEquals(150, JSONObject(folded.second).getInt("total"))
    }

    @Test
    fun `a full update after a partial one is kept, not dropped`() {
        val folded = fold("PARTIAL_UPDATE", """{"name":"A"}""", "UPDATE", """{"name":"B","price":5}""")!!
        assertEquals("UPDATE", folded.first)
        assertEquals("B", JSONObject(folded.second).getString("name"))
    }

    @Test
    fun `partial updates accumulate onto whatever is queued`() {
        val onInsert = fold("INSERT", """{"name":"A","price":1}""", "PARTIAL_UPDATE", """{"price":2}""")!!
        assertEquals("INSERT", onInsert.first)
        assertEquals("A", JSONObject(onInsert.second).getString("name"))
        assertEquals(2, JSONObject(onInsert.second).getInt("price"))

        val onPartial = fold("PARTIAL_UPDATE", """{"name":"A"}""", "PARTIAL_UPDATE", """{"price":2}""")!!
        assertEquals("PARTIAL_UPDATE", onPartial.first)
        assertEquals(setOf("name", "price"), JSONObject(onPartial.second).keys().asSequence().toSet())

        val onUpdate = fold("UPDATE", """{"name":"A","price":1}""", "PARTIAL_UPDATE", """{"price":2}""")!!
        assertEquals("UPDATE", onUpdate.first)
        assertEquals("A", JSONObject(onUpdate.second).getString("name"))
    }

    @Test
    fun `a queued delete stands and a later delete replaces anything`() {
        assertNull(fold("DELETE", "{}", "UPDATE", """{"name":"A"}"""))
        assertNull(fold("DELETE", "{}", "INSERT", """{"name":"A"}"""))
        assertEquals("DELETE", fold("INSERT", """{"name":"A"}""", "DELETE", """{"name":"A"}""")!!.first)
        assertEquals("DELETE", fold("PARTIAL_UPDATE", """{"name":"A"}""", "DELETE", "{}")!!.first)
    }
}
