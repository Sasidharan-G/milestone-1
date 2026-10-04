package com.kadaikutty.pos.support

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kadaikutty.pos.core.analytics.AnalyticsManager
import com.kadaikutty.pos.core.auth.Session
import com.kadaikutty.pos.core.auth.SessionStore
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.preferences.AppPreferences
import com.kadaikutty.pos.core.printer.data.PrinterManager
import com.kadaikutty.pos.core.printer.domain.PrintDocument
import com.kadaikutty.pos.core.printer.domain.PrinterDriver
import com.kadaikutty.pos.core.printer.domain.PrinterError
import com.kadaikutty.pos.core.printer.domain.PrinterResult
import com.kadaikutty.pos.core.security.Permission
import com.kadaikutty.pos.core.sharing.ShareManager
import com.kadaikutty.pos.core.sync.SyncManager
import com.kadaikutty.pos.core.sync.SyncScheduler
import com.kadaikutty.pos.core.sync.SyncStatus
import com.kadaikutty.pos.feature.billing.data.SaleRepositoryImpl
import com.kadaikutty.pos.feature.billing.data.StockMovementEntity
import com.kadaikutty.pos.feature.billing.presentation.BillingViewModel
import com.kadaikutty.pos.feature.masters.data.CategoryEntity
import com.kadaikutty.pos.feature.masters.data.CustomerEntity
import com.kadaikutty.pos.feature.masters.data.ProductEntity
import com.kadaikutty.pos.feature.masters.data.SupplierEntity
import com.kadaikutty.pos.feature.purchase.data.PurchaseRepositoryImpl
import com.kadaikutty.pos.feature.purchase.presentation.PurchaseViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID

/**
 * A throwaway shop for UI tests: in-memory database, a signed-in admin, three products with stock,
 * one customer and one supplier. It never touches the network, so a test can click through Billing
 * and Purchase without risking real shop data.
 */
class PosTestFixture {
    val context: Context = ApplicationProvider.getApplicationContext()
    val db: BillingDatabase = Room.inMemoryDatabaseBuilder(context, BillingDatabase::class.java).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefsStore = PreferenceDataStoreFactory.create(scope = scope) { File(context.cacheDir, "fixture-${UUID.randomUUID()}.preferences_pb") }
    val appPreferences = AppPreferences(prefsStore)
    val sessions = SessionStore(prefsStore, context.getSharedPreferences("fixture-secure-${UUID.randomUUID()}", Context.MODE_PRIVATE))
    private val syncScheduler = SyncScheduler(context)
    private val syncManager = SyncManager(db, syncScheduler, sessions, scheduleEnabled = false)

    init {
        runBlocking {
            sessions.save(Session("owner", "Owner", Permission.ALL_ACTIVE, companyId = COMPANY, role = "ADMIN"))
            db.masterDao().insertCategory(CategoryEntity("cat", COMPANY, "General", 0, 0, SyncStatus.LOCAL_ONLY))
            listOf(
                Triple("turmeric", "turmeric powder", 12000L),
                Triple("milk", "dairy milk", 5000L),
                Triple("soap", "hamam soap", 5000L),
            ).forEach { (id, name, price) ->
                db.masterDao().insertProduct(ProductEntity(id, COMPANY, name, "cat", price / 2, price, "PIECE", createdAtEpochMs = 0, updatedAtEpochMs = 0, syncStatus = SyncStatus.LOCAL_ONLY))
                db.saleDao().insertStockMovements(listOf(StockMovementEntity("open-$id", COMPANY, id, 100, "OPENING", "open-$id", 0)))
            }
            db.masterDao().insertCustomer(CustomerEntity("customer", COMPANY, "kavitha", createdAtEpochMs = 0, updatedAtEpochMs = 0, syncStatus = SyncStatus.LOCAL_ONLY))
            db.masterDao().insertSupplier(SupplierEntity("supplier", COMPANY, "Test Supplier", createdAtEpochMs = 0, updatedAtEpochMs = 0, syncStatus = SyncStatus.LOCAL_ONLY))
        }
    }

    private object NoPrinter : PrinterDriver {
        private val failure = PrinterResult.Failure(PrinterError.DeviceNotFound("no printer in tests"))
        override suspend fun connect(deviceId: String): PrinterResult = failure
        override suspend fun print(document: PrintDocument): PrinterResult = failure
        override suspend fun disconnect() {}
    }

    fun billingViewModel(): BillingViewModel = BillingViewModel(
        db,
        SaleRepositoryImpl(db.saleDao(), syncManager, sessions, appPreferences, db),
        ShareManager(context),
        appPreferences,
        sessions,
        syncManager,
        syncScheduler,
        AnalyticsManager(),
        PrinterManager(NoPrinter, NoPrinter),
    )

    fun purchaseViewModel(): PurchaseViewModel = PurchaseViewModel(
        db,
        PurchaseRepositoryImpl(db.purchaseDao(), syncManager, sessions, appPreferences, db),
        sessions,
        appPreferences,
    )

    fun close() {
        db.close()
        scope.cancel()
    }

    companion object {
        const val COMPANY = "shop"
    }
}
