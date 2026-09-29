package com.kadaikutty.pos.feature.billing.data

import androidx.room.withTransaction
import com.kadaikutty.pos.core.common.*
import com.kadaikutty.pos.core.database.*
import com.kadaikutty.pos.core.security.Permission
import com.kadaikutty.pos.core.sync.*
import com.kadaikutty.pos.feature.billing.domain.*
import com.kadaikutty.pos.feature.masters.data.CustomerCreditEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class SaleRepositoryImpl(
    private val tenantDatabaseManager: com.kadaikutty.pos.core.database.TenantDatabaseManager?,
    private val syncManager: SyncManager,
    private val sessionStore: com.kadaikutty.pos.core.auth.SessionStore,
    private val appPreferences: com.kadaikutty.pos.core.preferences.AppPreferences,
    private val fallbackDatabase: BillingDatabase? = null,
    private val fallbackSaleDao: SaleDao? = null,
) : SaleRepository {
    constructor(
        tenantDatabaseManager: com.kadaikutty.pos.core.database.TenantDatabaseManager,
        syncManager: SyncManager,
        sessionStore: com.kadaikutty.pos.core.auth.SessionStore,
        appPreferences: com.kadaikutty.pos.core.preferences.AppPreferences,
    ) : this(tenantDatabaseManager, syncManager, sessionStore, appPreferences, null, null)

    constructor(
        saleDao: SaleDao,
        syncManager: SyncManager,
        sessionStore: com.kadaikutty.pos.core.auth.SessionStore,
        appPreferences: com.kadaikutty.pos.core.preferences.AppPreferences,
        database: BillingDatabase,
    ) : this(null, syncManager, sessionStore, appPreferences, database, saleDao)

    private val database: BillingDatabase
        get() = tenantDatabaseManager?.getDatabase() ?: fallbackDatabase ?: error("No database available")

    private val saleDao: SaleDao
        get() = tenantDatabaseManager?.getDatabase()?.saleDao() ?: fallbackSaleDao ?: database.saleDao()

    override suspend fun save(draft: SaleDraft): AppResult<String> = try {
        val session = sessionStore.activeSession.first() ?: error("Sign in before billing")
        require(Permission.SALE_CREATE in session.permissions && Permission.ACCOUNT_INACTIVE !in session.permissions) { "Sale permission required" }
        val company = session.companyId
        require(company.isNotBlank())
        val prefix = appPreferences.getOrCreateInstallationDeviceId().take(8).uppercase()
        val result = database.withTransaction {
            val receiptKey = "sale:${draft.requestId}"
            database.localOperationDao().get(company, receiptKey)?.let { return@withTransaction it }
            val old = draft.editingSaleId?.let { saleDao.getSaleById(company, it) ?: error("Original bill no longer exists") }
            require(old?.status != SaleStatus.VOID) { "This bill was cancelled and can no longer be edited" }
            if (old != null) {
                require(session.role == "ADMIN" || session.role == "SUPER_ADMIN") { "Only an administrator can edit completed bills" }
                require(saleDao.creditsFor(company, old.id).none { it.amountMinorUnits < 0 }) { "Bills containing previous-due collections cannot be edited. Use cancellation and a new bill." }
                require(old.revision == draft.expectedRevision) { "Bill changed. Reopen it before editing" }
            }
            val id = old?.id ?: draft.requestId
            val number = old?.billNumber ?: database.nextDocumentNumber(company, "sale", prefix)
            require(draft.globalDiscount.minorUnits in 0..draft.subtotal.minorUnits) { "Discount exceeds the bill subtotal" }
            require(draft.lines.map { it.productId }.distinct().size == draft.lines.size) { "Duplicate product lines" }
            require(draft.previousDue >= 0)
            CheckoutMath.validate(Math.addExact(draft.total.minorUnits, draft.previousDue), draft.paidCash.minorUnits, draft.paidUpi.minorUnits, draft.creditApplied.minorUnits)
            require(draft.creditApplied.minorUnits <= draft.total.minorUnits) { "Previous dues must be paid, not moved to credit again" }
            val customer = draft.customerId?.takeUnless { it == "online" }?.let {
                database.masterDao().getCustomerById(company, it) ?: error("Customer no longer exists")
            }
            if (draft.creditApplied.minorUnits > 0 || draft.previousDue > 0) require(customer != null) { "Select a registered customer" }
            if (customer != null) {
                val balance = database.masterDao().getCustomerCreditBalance(company, customer.id).first() ?: 0L
                require(draft.previousDue <= maxOf(0, balance)) { "Customer due changed. Reopen checkout" }
                val replacedCredit = if (old?.customerId == customer.id) old.creditAppliedMinorUnits else 0
                require(customer.creditLimitMinorUnits <= 0 || balance - replacedCredit - draft.previousDue + draft.creditApplied.minorUnits <= customer.creditLimitMinorUnits) { "Customer credit limit exceeded" }
            }
            val oldItems = old?.let { saleDao.getSaleItemsList(company, it.id) }.orEmpty().associateBy { it.productId }
            val discounts = CheckoutMath.allocate(draft.globalDiscount.minorUnits, draft.lines.map { it.lineTotal.minorUnits })
            val now = System.currentTimeMillis()
            val items = draft.lines.mapIndexed { index, line ->
                val product = database.masterDao().getProductById(company, line.productId) ?: error("Product no longer exists")
                require(product.unitType == line.unitType) { "Product unit changed. Reload the cart" }
                val available = saleDao.stock(company, line.productId) + (oldItems[line.productId]?.quantity ?: 0)
                require(available >= line.quantity) { "Insufficient stock for ${line.productName}" }
                val unitCost = database.purchaseDao().getAveragePurchasePrice(company, line.productId) ?: product.purchasePriceMinorUnits.toDouble()
                val cost = Math.round(unitCost * line.quantity / if (line.unitType in listOf("KG", "LITER")) 1000.0 else 1.0)
                SaleItemEntity(company, id, line.productId, line.quantity, line.unitPrice.minorUnits,
                    line.lineTotal.minorUnits, line.discount.minorUnits, line.unitType, line.productName, cost, line.lineTotal.minorUnits - discounts[index],
                    gstRateBps = product.gstRateBps, hsnCode = product.hsnCode)
            }
            val sale = SaleEntity(id, company, number, draft.total.minorUnits, old?.createdAtEpochMs ?: now,
                SyncStatus.LOCAL_ONLY, draft.customerId, draft.paymentMode, draft.paidCash.minorUnits,
                draft.paidUpi.minorUnits, draft.creditApplied.minorUnits, draft.globalDiscount.minorUnits,
                (old?.revision ?: -1) + 1)
            if (old == null) saleDao.createSale(sale) else {
                saleDao.movementsFor(company, id).forEach { syncManager.enqueueStockMovement(it, "DELETE") }
                saleDao.creditsFor(company, id).forEach { syncManager.enqueueCustomerCredit(it, "DELETE") }
                saleDao.deleteSaleItems(company, id)
                saleDao.deleteSaleStockMovements(company, id)
                saleDao.deleteCustomerCreditsByReason(company, id)
                saleDao.updateSale(sale)
            }
            saleDao.insertItems(items)
            val movements = items.map { StockMovementEntity(newRecordId(), company, it.productId, -it.quantity, "SALE", id, now) }
            saleDao.insertStockMovements(movements)
            if (draft.creditApplied.minorUnits > 0) {
                val credit = CustomerCreditEntity(id = newRecordId(), companyId = company, customerId = customer!!.id,
                    amountMinorUnits = draft.creditApplied.minorUnits, reason = "Bill #$number", dateEpochMs = now,
                    referenceId = id, syncStatus = SyncStatus.LOCAL_ONLY)
                database.masterDao().insertCustomerCredit(credit)
                syncManager.enqueueCustomerCredit(credit, "INSERT")
            }
            if (draft.previousDue > 0) {
                val settlement = CustomerCreditEntity(id = newRecordId(), companyId = company, customerId = customer!!.id,
                    amountMinorUnits = -draft.previousDue, reason = "Previous due settled in Bill $number", dateEpochMs = now,
                    referenceId = id, syncStatus = SyncStatus.LOCAL_ONLY)
                database.masterDao().insertCustomerCredit(settlement)
                syncManager.enqueueCustomerCredit(settlement, "INSERT")
            }
            // An edit is an UPDATE so the server checks it against the version this device last
            // saw; sent as INSERT it skipped that check and overwrote edits from other devices.
            syncManager.enqueueSale(sale, items, if (old == null) "INSERT" else "UPDATE")
            movements.forEach { syncManager.enqueueStockMovement(it) }
            // If sync was guarding this bill's stock/credit rows after a conflict, this edit's rows
            // are now the valid ones.
            if (old != null) ConflictResolver(database, syncManager).onLocalDocumentEdit(company, "Sale", id)
            database.draftCartDao().removePurchased(company, items.map { it.productId })
            database.draftCartDao().rekeyActive(company, draft.nextCartRequestId)
            database.localOperationDao().put(LocalOperationEntity(company, receiptKey, number))
            if (old != null) database.auditLogDao().insertAuditLog(AuditLogEntity(newRecordId(), company,
                "BILL_EDIT", number, sale.totalMinorUnits, "Bill corrected", session.userId, session.displayName, now))
            number
        }
        AppResult.Success(result)
    } catch (e: CancellationException) { throw e
    } catch (e: Exception) { AppResult.Failure(AppError.Unexpected(e.message ?: "Unable to save bill")) }

    override suspend fun deleteSale(saleId: String, billNumber: String, reason: String): AppResult<Unit> = try {
        val session = sessionStore.activeSession.first() ?: error("Sign in first")
        require(session.role in listOf("ADMIN", "SUPER_ADMIN")) { "Only an administrator can cancel bills" }
        database.withTransaction {
            // Reports rows carry only the bill number (they are formatted display rows, not the
            // sale's own row ID), so resolve the sale from whichever identifier the caller has.
            val sale = saleDao.getSaleById(session.companyId, saleId)
                ?: saleDao.getSaleByBillNumber(session.companyId, billNumber)
                ?: error("Bill no longer exists")
            val id = sale.id
            require(sale.status != SaleStatus.VOID) { "Bill ${sale.billNumber} is already cancelled" }
            // Voided, not deleted: the bill, its lines and its number stay for the record (audit,
            // GST), while its stock and credit rows are reversed exactly as a delete did.
            saleDao.movementsFor(session.companyId, id).forEach { syncManager.enqueueStockMovement(it, "DELETE") }
            saleDao.creditsFor(session.companyId, id).forEach { syncManager.enqueueCustomerCredit(it, "DELETE") }
            saleDao.deleteSaleStockMovements(session.companyId, id)
            saleDao.deleteCustomerCreditsByReason(session.companyId, id)
            val voided = sale.copy(status = SaleStatus.VOID, revision = sale.revision + 1, syncStatus = SyncStatus.LOCAL_ONLY)
            saleDao.updateSale(voided)
            syncManager.enqueueSale(voided, saleDao.getSaleItemsList(session.companyId, id), "UPDATE")
            database.auditLogDao().insertAuditLog(AuditLogEntity(newRecordId(), session.companyId,
                "BILL_CANCEL", sale.billNumber, sale.totalMinorUnits, reason.ifBlank { "Bill cancelled" }, session.userId, session.displayName, System.currentTimeMillis()))
        }
        AppResult.Success(Unit)
    } catch (e: CancellationException) { throw e
    } catch (e: Exception) { AppResult.Failure(AppError.Unexpected(e.message ?: "Unable to cancel bill")) }
}
