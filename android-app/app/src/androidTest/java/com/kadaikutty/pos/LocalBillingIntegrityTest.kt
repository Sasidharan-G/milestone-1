package com.kadaikutty.pos

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.kadaikutty.pos.core.auth.*
import com.kadaikutty.pos.core.common.*
import com.kadaikutty.pos.core.database.*
import com.kadaikutty.pos.core.preferences.AppPreferences
import com.kadaikutty.pos.core.security.Permission
import com.kadaikutty.pos.core.sync.*
import com.kadaikutty.pos.feature.billing.data.*
import com.kadaikutty.pos.feature.billing.domain.*
import com.kadaikutty.pos.feature.purchase.data.*
import com.kadaikutty.pos.feature.purchase.domain.*
import com.kadaikutty.pos.feature.masters.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LocalBillingIntegrityTest {
    private lateinit var db: BillingDatabase
    private lateinit var sales: SaleRepositoryImpl
    private lateinit var purchases: PurchaseRepositoryImpl
    private lateinit var scope: CoroutineScope
    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, BillingDatabase::class.java).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefs = PreferenceDataStoreFactory.create(scope = scope) { File(context.cacheDir, "test-${UUID.randomUUID()}.preferences_pb") }
        val securePrefs = context.getSharedPreferences("test-secure-${UUID.randomUUID()}", android.content.Context.MODE_PRIVATE)
        val sessions = SessionStore(prefs, securePrefs)
        sessions.save(Session("owner", "Owner", Permission.ALL_ACTIVE, companyId = "shop", role = "ADMIN"))
        val appPrefs = AppPreferences(prefs)
        val manager = SyncManager(db, SyncScheduler(context), sessions, scheduleEnabled = false)
        sales = SaleRepositoryImpl(db.saleDao(), manager, sessions, appPrefs, db)
        purchases = PurchaseRepositoryImpl(db.purchaseDao(), manager, sessions, appPrefs, db)
        db.masterDao().insertCategory(CategoryEntity("cat", "shop", "General", 0, 0, SyncStatus.LOCAL_ONLY))
        db.masterDao().insertProduct(ProductEntity("p", "shop", "Rice", "cat", 8000, 10000, "KG", createdAtEpochMs = 0, updatedAtEpochMs = 0, syncStatus = SyncStatus.LOCAL_ONLY))
        db.masterDao().insertSupplier(SupplierEntity("supplier", "shop", "Supplier", createdAtEpochMs = 0, updatedAtEpochMs = 0, syncStatus = SyncStatus.LOCAL_ONLY))
        db.masterDao().insertCustomer(CustomerEntity("customer", "shop", "Customer", createdAtEpochMs = 0, updatedAtEpochMs = 0, syncStatus = SyncStatus.LOCAL_ONLY))
        db.saleDao().insertStockMovements(listOf(StockMovementEntity("opening", "shop", "p", 10000, "OPENING", "opening", 0)))
    }
    @After fun cleanup() { db.close(); scope.cancel() }
    private fun draft(request: String = UUID.randomUUID().toString()) = SaleDraft(listOf(SaleLine("p", "Rice", 1000, Money(10000), "KG")), paidCash = Money(10000), requestId = request)

    @Test fun retryDoesNotDuplicateSaleOrStock() = runBlocking {
        val draft = draft("same-request")
        val first = sales.save(draft)
        assertTrue(first is AppResult.Success)
        assertEquals(first, sales.save(draft))
        assertEquals(1, db.saleDao().getSales("shop").first().size)
        assertEquals(9000, db.saleDao().stock("shop", "p"))
    }
    @Test fun failedEditPreservesOriginalAndValidEditPreservesIdentity() = runBlocking {
        sales.save(draft())
        val old = db.saleDao().getSales("shop").first().single()
        val invalid = draft().copy(editingSaleId = old.id, expectedRevision = old.revision, paidCash = Money(1))
        assertTrue(sales.save(invalid) is AppResult.Failure)
        assertEquals(old, db.saleDao().getSaleById("shop", old.id))
        assertEquals(9000, db.saleDao().stock("shop", "p"))
        val valid = draft().copy(editingSaleId = old.id, expectedRevision = old.revision,
            lines = listOf(SaleLine("p", "Rice", 500, Money(10000), "KG")), paidCash = Money(5000))
        assertTrue(sales.save(valid) is AppResult.Success)
        assertEquals(old.billNumber, db.saleDao().getSaleById("shop", old.id)!!.billNumber)
        assertEquals(9500, db.saleDao().stock("shop", "p"))
        assertTrue(sales.save(valid.copy(requestId = "stale-edit")) is AppResult.Failure)
    }
    @Test fun discountAndFrozenCostReconcile() = runBlocking {
        assertTrue(sales.save(draft().copy(globalDiscount = Money(1000), paidCash = Money(9000))) is AppResult.Success)
        val report = db.reportDao().getProfitReportRaw("shop", null, null).single()
        assertEquals(9000, report.totalRevenue)
        assertEquals(8000L, report.recordedCost)
        val p = db.masterDao().getProductById("shop", "p")!!
        db.masterDao().updateProduct(p.copy(purchasePriceMinorUnits = 100))
        assertEquals(8000L, db.reportDao().getProfitReportRaw("shop", null, null).single().recordedCost)
    }
    @Test fun multiSupplierFailureRollsBackEverything() = runBlocking {
        val good = PurchaseDraft("supplier", listOf(PurchaseLine("p", 1000, Money(8000), "KG")), paidCash = Money(8000))
        val bad = good.copy(supplierId = "missing", requestId = "bad")
        assertTrue(purchases.saveBatch(listOf(good, bad)) is AppResult.Failure)
        assertTrue(db.purchaseDao().getPurchases("shop").first().isEmpty())
        assertEquals(10000, db.saleDao().stock("shop", "p"))
    }
    @Test fun numberIsNotReusedAfterCancellation() = runBlocking {
        sales.save(draft())
        val old = db.saleDao().getSales("shop").first().single()
        assertTrue(sales.deleteSale(old.id, old.billNumber) is AppResult.Success)
        sales.save(draft())
        // A cancelled bill stays in history as VOID, so the list now holds it and the new bill.
        val numbers = db.saleDao().getSales("shop").first().map { it.billNumber }
        assertEquals(2, numbers.size)
        assertEquals("the new bill must not reuse the cancelled bill's number", 2, numbers.toSet().size)
        assertTrue(old.billNumber in numbers)
    }
    @Test fun ledgerDeletionUsesReferenceNotInvoiceSubstring() = runBlocking {
        db.masterDao().insertSupplierCredit(SupplierCreditEntity("a", "shop", "supplier", 100, "Purchase Bill #12 (CREDIT)", 0, 0, "purchase-12", SyncStatus.LOCAL_ONLY))
        db.masterDao().insertSupplierCredit(SupplierCreditEntity("b", "shop", "supplier", 100, "Purchase Bill #123 (CREDIT)", 0, 0, "purchase-123", SyncStatus.LOCAL_ONLY))
        db.purchaseDao().deleteSupplierCreditsByTerms("shop", "purchase-12")
        assertEquals(listOf("b"), db.masterDao().getAllSupplierCredits("shop").map { it.id })
    }

    @Test fun purchaseEditRetainsKgAndOriginalUntilCommit() = runBlocking {
        val draft = PurchaseDraft("supplier", listOf(PurchaseLine("p", 1000, Money(8000), "KG")), paidCash = Money(8000))
        assertTrue(purchases.save(draft) is AppResult.Success)
        val original = db.purchaseDao().getPurchases("shop").first().single()
        val item = db.purchaseDao().getPurchaseItemsList("shop", original.id).single()
        assertEquals("KG", item.unitType)
        assertTrue(purchases.save(draft.copy(requestId = "edit-purchase", editingPurchaseId = original.id, expectedRevision = original.revision)) is AppResult.Success)
        assertEquals(8000, db.purchaseDao().getById("shop", original.id)!!.totalMinorUnits)
        assertEquals(11000, db.saleDao().stock("shop", "p"))
    }
    @Test fun queueFailureRollsBackSaleStockAndNumber() = runBlocking {
        db.openHelper.writableDatabase.execSQL("DROP TABLE sync_queue")
        assertTrue(sales.save(draft()) is AppResult.Failure)
        assertTrue(db.saleDao().getSales("shop").first().isEmpty())
        assertEquals(10000, db.saleDao().stock("shop", "p"))
    }
    @Test fun previousDueCollectionAndCreditLimitAreAtomic() = runBlocking {
        db.masterDao().insertCustomerCredit(CustomerCreditEntity("due", "shop", "customer", 5000, "Opening due", 0, syncStatus = SyncStatus.LOCAL_ONLY))
        assertTrue(sales.save(draft().copy(customerId = "customer", previousDue = 5000, paidCash = Money(15000))) is AppResult.Success)
        assertEquals(0L, db.masterDao().getCustomerCreditBalance("shop", "customer").first())
        assertEquals(10000, db.saleDao().getSales("shop").first().single().totalMinorUnits)
        val customer = db.masterDao().getCustomerById("shop", "customer")!!
        db.masterDao().updateCustomer(customer.copy(creditLimitMinorUnits = 100))
        assertTrue(sales.save(draft().copy(customerId = "customer", paidCash = Money.Zero, creditApplied = Money(10000))) is AppResult.Failure)
        assertEquals(1, db.saleDao().getSales("shop").first().size)
    }
    @Test fun heldCartUnitsAndBackupRoundTrip() = runBlocking {
        db.draftCartDao().insertItems(listOf(DraftCartItemEntity("cart", "shop", "p", "Rice", 1000, 10000, "KG", parkId = "held", customerId = "customer", checkoutId = "checkout", cartDiscountMinorUnits = 500)))
        assertEquals(9500, db.draftCartDao().getHeldCartsSummary("shop").first().single().totalAmountMinorUnits)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val backup = com.kadaikutty.pos.core.backup.data.BackupManager(context, db)
        val result = backup.createBackup()
        assertTrue(result is com.kadaikutty.pos.core.backup.domain.BackupResult.Success)
        val zip = (result as com.kadaikutty.pos.core.backup.domain.BackupResult.Success).zipBytes
        assertTrue(backup.restoreBackup(zip))
        assertEquals("customer", db.draftCartDao().getItemsByParkId("shop", "held").single().customerId)
        // Invalid archive must leave existing records untouched.
        assertFalse(backup.restoreBackup("invalid backup".toByteArray()))
        assertNotNull(db.masterDao().getProductById("shop", "p"))
    }
}
