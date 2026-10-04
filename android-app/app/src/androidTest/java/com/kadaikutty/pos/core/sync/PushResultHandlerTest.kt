package com.kadaikutty.pos.core.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kadaikutty.pos.core.auth.Session
import com.kadaikutty.pos.core.auth.SessionStore
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.database.SyncQueueEntity
import com.kadaikutty.pos.core.security.Permission
import com.kadaikutty.pos.feature.masters.data.CategoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** The server's answer to a pushed batch, applied to a real Room queue. */
@RunWith(AndroidJUnit4::class)
class PushResultHandlerTest {
    private lateinit var db: BillingDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var manager: SyncManager
    private lateinit var handler: PushResultHandler
    private val company = "shop"

    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, BillingDatabase::class.java).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefs = PreferenceDataStoreFactory.create(scope = scope) { File(context.cacheDir, "push-${UUID.randomUUID()}.preferences_pb") }
        val securePrefs = context.getSharedPreferences("test-secure-${UUID.randomUUID()}", android.content.Context.MODE_PRIVATE)
        val sessions = SessionStore(prefs, securePrefs)
        sessions.save(Session("owner", "Owner", Permission.ALL_ACTIVE, companyId = company, role = "ADMIN"))
        manager = SyncManager(db, SyncScheduler(context), sessions, scheduleEnabled = false)
        handler = PushResultHandler(db, ConflictResolver(db, manager))
    }

    @After fun cleanup() { db.close(); scope.cancel() }

    private suspend fun queued(): SyncQueueEntity = db.syncQueueDao().findPending(company, "Category", "cat")!!

    private suspend fun enqueueCategory(name: String) {
        val category = CategoryEntity("cat", company, name, 1, 1, SyncStatus.LOCAL_ONLY)
        db.masterDao().insertCategory(category)
        manager.enqueueCategory(category, "INSERT")
    }

    private fun results(vararg entries: JSONObject) = JSONArray().apply { entries.forEach { put(it) } }

    @Test fun appliedWriteIsClosedAndItsVersionRecorded() = runBlocking {
        enqueueCategory("Tea")
        val sent = queued()
        val outcome = handler.apply(company, listOf(sent), results(JSONObject().put("operationId", sent.id).put("status", "APPLIED").put("version", 1)))
        assertEquals(1, outcome.synced)
        assertEquals(SyncStatus.SYNCED, db.syncQueueDao().getById(sent.id)!!.status)
        assertEquals("1", db.localOperationDao().get(company, ConflictResolver.versionKey("Category", "cat")))
    }

    @Test fun anEditMadeWhileThePushWasInFlightIsNotLost() = runBlocking {
        enqueueCategory("Tea")
        val sent = queued()
        // The user renames the category while the request is on the wire; it folds into the same row.
        enqueueCategory("Green Tea")
        handler.apply(company, listOf(sent), results(JSONObject().put("operationId", sent.id).put("status", "APPLIED").put("version", 1)))

        val row = db.syncQueueDao().getById(sent.id)!!
        assertEquals("the newer edit must still be waiting to go out", SyncStatus.PENDING, row.status)
        assertTrue(row.payload.contains("Green Tea"))
        // ...and it goes out on top of the version the first write created.
        assertEquals("1", db.localOperationDao().get(company, ConflictResolver.versionKey("Category", "cat")))
    }

    @Test fun aRejectedWriteIsDeadLetteredAtOnceAndThePullRestarts() = runBlocking {
        enqueueCategory("Tea")
        db.localOperationDao().put(com.kadaikutty.pos.core.database.LocalOperationEntity(company, PullWorker.CURSOR_KEY, "42"))
        val sent = queued()
        val outcome = handler.apply(company, listOf(sent), results(JSONObject().put("operationId", sent.id).put("status", "REJECTED")
            .put("error", JSONObject().put("code", "SYNC_ENTITY_ID_INVALID").put("message", "bad id"))))
        assertEquals(1, outcome.rejected)
        val row = db.syncQueueDao().getById(sent.id)!!
        assertEquals(SyncStatus.FAILED, row.status)
        assertEquals(SyncWorker.MAX_SYNC_ATTEMPTS, row.attemptCount)
        assertEquals(1, db.syncDeadLetterDao().getDeadLetters(company, 10).size)
        assertEquals("0", db.localOperationDao().get(company, PullWorker.CURSOR_KEY))
        // Failed at the attempt limit: retryFailed must not bring it back.
        db.syncQueueDao().retryFailed(company, System.currentTimeMillis(), SyncWorker.MAX_SYNC_ATTEMPTS)
        assertEquals(SyncStatus.FAILED, db.syncQueueDao().getById(sent.id)!!.status)
    }

    @Test fun aConflictIsResolvedAgainstTheRowAsItIsNow() = runBlocking {
        enqueueCategory("Tea")
        val sent = queued()
        enqueueCategory("Green Tea")
        val server = JSONObject().put("companyId", company).put("entityType", "Category").put("entityId", "cat").put("version", 3)
            .put("deleted", false).put("updatedAtEpochMs", 1).put("payload", JSONObject().put("id", "cat").put("companyId", company).put("name", "Tea").put("createdAtEpochMs", 1).put("updatedAtEpochMs", 1))
        val outcome = handler.apply(company, listOf(sent), results(JSONObject().put("operationId", sent.id).put("status", "CONFLICT").put("record", server)))
        assertEquals(1, outcome.conflicts)
        // The in-flight rename is what the merge weighed, so it is what goes back to the cloud.
        val next = db.syncQueueDao().findPending(company, "Category", "cat")
        assertNotNull(next)
        assertTrue(next!!.payload.contains("Green Tea"))
        assertEquals("3", db.localOperationDao().get(company, ConflictResolver.versionKey("Category", "cat")))
        assertTrue(db.syncConflictDao().recent(company, 10).first().isNotEmpty())
    }

    @Test fun anUnknownStatusAsksForARetryAndLeavesTheRowAlone() = runBlocking {
        enqueueCategory("Tea")
        val sent = queued()
        val outcome = handler.apply(company, listOf(sent), results(JSONObject().put("operationId", sent.id).put("status", "SOMETHING_NEW")))
        assertTrue(outcome.retryNeeded)
        assertEquals(SyncStatus.PENDING, db.syncQueueDao().getById(sent.id)!!.status)
    }
}
