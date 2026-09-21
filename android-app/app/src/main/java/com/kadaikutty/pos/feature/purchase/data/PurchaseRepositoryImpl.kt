package com.kadaikutty.pos.feature.purchase.data

import androidx.room.withTransaction
import com.kadaikutty.pos.core.common.*
import com.kadaikutty.pos.core.database.*
import com.kadaikutty.pos.core.security.Permission
import com.kadaikutty.pos.core.sync.*
import com.kadaikutty.pos.feature.billing.data.*
import com.kadaikutty.pos.feature.masters.data.SupplierCreditEntity
import com.kadaikutty.pos.feature.purchase.domain.*
import com.kadaikutty.pos.feature.stock.domain.ProductStock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*

class PurchaseRepositoryImpl(
    private val tenantDatabaseManager: com.kadaikutty.pos.core.database.TenantDatabaseManager?,
    private val syncManager: SyncManager,
    private val sessionStore: com.kadaikutty.pos.core.auth.SessionStore,
    private val appPreferences: com.kadaikutty.pos.core.preferences.AppPreferences,
    private val fallbackDatabase: BillingDatabase? = null,
    private val fallbackPurchaseDao: PurchaseDao? = null,
) : PurchaseRepository {
    constructor(
        tenantDatabaseManager: com.kadaikutty.pos.core.database.TenantDatabaseManager,
        syncManager: SyncManager,
        sessionStore: com.kadaikutty.pos.core.auth.SessionStore,
        appPreferences: com.kadaikutty.pos.core.preferences.AppPreferences,
    ) : this(tenantDatabaseManager, syncManager, sessionStore, appPreferences, null, null)

    constructor(
        purchaseDao: PurchaseDao,
        syncManager: SyncManager,
        sessionStore: com.kadaikutty.pos.core.auth.SessionStore,
        appPreferences: com.kadaikutty.pos.core.preferences.AppPreferences,
        database: BillingDatabase,
    ) : this(null, syncManager, sessionStore, appPreferences, database, purchaseDao)

    private val database: BillingDatabase
        get() = tenantDatabaseManager?.getDatabase() ?: fallbackDatabase ?: error("No database available")

    private val purchaseDao: PurchaseDao
        get() = tenantDatabaseManager?.getDatabase()?.purchaseDao() ?: fallbackPurchaseDao ?: database.purchaseDao()

    override suspend fun save(draft: PurchaseDraft): AppResult<String> = when (val result = saveBatch(listOf(draft))) {
        is AppResult.Success -> AppResult.Success(result.value.single())
        is AppResult.Failure -> result
    }
    override suspend fun saveBatch(drafts: List<PurchaseDraft>): AppResult<List<String>> = try {
        val session = sessionStore.activeSession.first() ?: error("Sign in first")
        require(Permission.PURCHASE_CREATE in session.permissions && Permission.ACCOUNT_INACTIVE !in session.permissions) { "Purchase permission required" }
        require(drafts.isNotEmpty())
        val company = session.companyId
        val prefix = appPreferences.getOrCreateInstallationDeviceId().take(8).uppercase()
        val ids = database.withTransaction {
            drafts.map { draft ->
                val key = "purchase:${draft.requestId}"
                val receipt = database.localOperationDao().get(company, key)
                if (receipt != null) return@map receipt
                CheckoutMath.validate(draft.total.minorUnits, draft.paidCash.minorUnits, draft.paidUpi.minorUnits, draft.creditApplied.minorUnits)
                require(database.masterDao().suppliers(company, "").first().any { it.id == draft.supplierId }) { "Supplier no longer exists" }
                require(draft.lines.map { it.productId }.distinct().size == draft.lines.size) { "Duplicate product lines" }
                val old = draft.editingPurchaseId?.let { purchaseDao.getById(company, it) ?: error("Original purchase no longer exists") }
                if (old != null) {
                    require(session.role in listOf("ADMIN", "SUPER_ADMIN")) { "Only an administrator can edit purchases" }
                    require(old.revision == draft.expectedRevision) { "Purchase changed. Reopen it" }
                    require(old.creditAppliedMinorUnits == 0L || purchaseDao.linkedCreditCount(company, old.id) == 1) { "Legacy credit needs review before editing this purchase" }
                }
                val id = old?.id ?: draft.requestId
                val now = System.currentTimeMillis()
                val number = old?.orderNumber ?: database.nextDocumentNumber(company, "purchase", prefix)
                val previous = old?.let { purchaseDao.getPurchaseItemsList(company, it.id) }.orEmpty()
                val replacement = draft.lines.associateBy { it.productId }
                previous.forEach {
                    require(database.saleDao().stock(company, it.productId) - it.quantity + (replacement[it.productId]?.quantity ?: 0) >= 0) { "Purchase stock has already been sold; quantity cannot be reduced this far" }
                }
                val items = draft.lines.map {
                    val product = database.masterDao().getProductById(company, it.productId) ?: error("Product no longer exists")
                    require(product.unitType == it.unitType) { "Product unit changed. Reload purchase" }
                    PurchaseItemEntity(company, id, it.productId, it.quantity, it.unitValue.minorUnits, it.total.minorUnits, it.unitType)
                }
                val purchase = PurchaseEntity(id, company, draft.supplierId, draft.total.minorUnits, old?.createdAtEpochMs ?: now,
                    SyncStatus.LOCAL_ONLY, draft.invoiceNumber, draft.notes, draft.paymentMode, draft.paidCash.minorUnits,
                    draft.paidUpi.minorUnits, draft.creditApplied.minorUnits, number, (old?.revision ?: -1) + 1)
                if (old == null) purchaseDao.createPurchase(purchase) else {
                    database.saleDao().movementsFor(company, id).forEach { syncManager.enqueueStockMovement(it, "DELETE") }
                    purchaseDao.creditsFor(company, id).forEach { syncManager.enqueueSupplierCredit(it, "DELETE") }
                    purchaseDao.deletePurchaseItems(company, id)
                    purchaseDao.deletePurchaseStockMovements(company, id)
                    purchaseDao.deleteSupplierCreditsByTerms(company, id)
                    purchaseDao.updatePurchase(purchase)
                }
                purchaseDao.insertItems(items)
                val movements = items.map { StockMovementEntity(newRecordId(), company, it.productId, it.quantity, "PURCHASE", id, now) }
                purchaseDao.insertStockMovements(movements)
                if (draft.creditApplied.minorUnits > 0) {
                    val credit = SupplierCreditEntity(id = newRecordId(), companyId = company, supplierId = draft.supplierId,
                        amountMinorUnits = draft.creditApplied.minorUnits, terms = "Purchase Order #$number", dueDateEpochMs = now + 2592000000L,
                        dateEpochMs = now, referenceId = id, syncStatus = SyncStatus.LOCAL_ONLY)
                    purchaseDao.insertSupplierCredit(credit)
                    syncManager.enqueueSupplierCredit(credit, "INSERT")
                }
                syncManager.enqueuePurchase(purchase, items, if (old == null) "INSERT" else "UPDATE")
                movements.forEach { syncManager.enqueueStockMovement(it) }
                if (old != null) ConflictResolver(database, syncManager).onLocalDocumentEdit(company, "Purchase", id)
                database.localOperationDao().put(LocalOperationEntity(company, key, id))
                if (old != null) database.auditLogDao().insertAuditLog(AuditLogEntity(newRecordId(), company,
                    "PURCHASE_EDIT", number, purchase.totalMinorUnits, "Purchase corrected", session.userId, session.displayName, now))
                id
            }
        }
        AppResult.Success(ids)
    } catch (e: CancellationException) { throw e
    } catch (e: Exception) { AppResult.Failure(AppError.Unexpected(e.message ?: "Unable to save purchase")) }

    override fun getPurchases(companyId: String): Flow<List<PurchaseEntity>> {
        return purchaseDao.getPurchases(companyId)
    }

    override fun getStockBalances(companyId: String): Flow<List<ProductStock>> {
        return purchaseDao.getStockBalances(companyId)
    }

    override fun getPurchaseItems(companyId: String, purchaseId: String): Flow<List<PurchaseItemEntity>> {
        return purchaseDao.getPurchaseItems(companyId, purchaseId)
    }

    override fun getPurchasesForSupplier(companyId: String, supplierId: String): Flow<List<PurchaseEntity>> {
        return purchaseDao.getPurchasesForSupplier(companyId, supplierId)
    }

    override suspend fun deletePurchase(purchaseId: String, orderOrInvoice: String): AppResult<Unit> = try {
        val session = sessionStore.activeSession.first() ?: error("Sign in first")
        require(session.role in listOf("ADMIN", "SUPER_ADMIN")) { "Only an administrator can cancel purchases" }
        database.withTransaction {
            val old = purchaseDao.getById(session.companyId, purchaseId) ?: error("Purchase no longer exists")
            require(old.creditAppliedMinorUnits == 0L || purchaseDao.linkedCreditCount(session.companyId, old.id) == 1) { "Legacy credit needs review before cancellation" }
            purchaseDao.getPurchaseItemsList(session.companyId, old.id).forEach {
                require(database.saleDao().stock(session.companyId, it.productId) >= it.quantity) { "Cannot cancel a purchase whose stock has already been sold" }
            }
            database.saleDao().movementsFor(session.companyId, old.id).forEach { syncManager.enqueueStockMovement(it, "DELETE") }
            purchaseDao.creditsFor(session.companyId, old.id).forEach { syncManager.enqueueSupplierCredit(it, "DELETE") }
            syncManager.enqueuePurchase(old, emptyList(), "DELETE")
            database.localOperationDao().put(LocalOperationEntity(session.companyId, "${com.kadaikutty.pos.core.sync.ConflictResolver.DOC_TOMBSTONE_PREFIX}Purchase:${old.id}", System.currentTimeMillis().toString()))
            purchaseDao.deletePurchaseCascade(session.companyId, old.id, old.orderNumber ?: old.id)
            database.auditLogDao().insertAuditLog(AuditLogEntity(newRecordId(), session.companyId, "PURCHASE_CANCEL",
                old.orderNumber ?: old.id, old.totalMinorUnits, "Purchase cancelled", session.userId, session.displayName, System.currentTimeMillis()))
        }
        AppResult.Success(Unit)
    } catch (e: CancellationException) { throw e
    } catch (e: Exception) { AppResult.Failure(AppError.Unexpected(e.message ?: "Unable to cancel purchase")) }

    override suspend fun getPurchaseItemsList(purchaseId: String): List<PurchaseItemEntity> {
        val session = sessionStore.activeSession.first() ?: return emptyList()
        return purchaseDao.getPurchaseItemsList(session.companyId, purchaseId)
    }
}
