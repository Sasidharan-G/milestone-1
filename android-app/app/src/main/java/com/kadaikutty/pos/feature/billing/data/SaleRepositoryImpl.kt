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
    private val saleDao: SaleDao,
    private val syncManager: SyncManager,
    private val sessionStore: com.kadaikutty.pos.core.auth.SessionStore,
    private val appPreferences: com.kadaikutty.pos.core.preferences.AppPreferences,
    private val database: BillingDatabase
) : SaleRepository {
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
                val cost = (unitCost * line.quantity / if (line.unitType in listOf("KG", "LITER")) 1000 else 1).toLong()
                SaleItemEntity(company, id, line.productId, line.quantity, line.unitPrice.minorUnits,
                    line.lineTotal.minorUnits, line.discount.minorUnits, line.unitType, line.productName, cost, line.lineTotal.minorUnits - discounts[index])
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
                val credit = CustomerCreditEntity(id = "sale-credit:$id:${sale.revision}", companyId = company, customerId = customer!!.id,
                    amountMinorUnits = draft.creditApplied.minorUnits, reason = "Bill #$number", dateEpochMs = now,
                    referenceId = id, syncStatus = SyncStatus.LOCAL_ONLY)
                database.masterDao().insertCustomerCredit(credit)
                syncManager.enqueueCustomerCredit(credit, "INSERT")
            }
            if (draft.previousDue > 0) {
                val settlement = CustomerCreditEntity(id = "sale-settlement:$id:${sale.revision}", companyId = company, customerId = customer!!.id,
                    amountMinorUnits = -draft.previousDue, reason = "Previous due settled in Bill $number", dateEpochMs = now,
                    referenceId = id, syncStatus = SyncStatus.LOCAL_ONLY)
                database.masterDao().insertCustomerCredit(settlement)
                syncManager.enqueueCustomerCredit(settlement, "INSERT")
            }
            syncManager.enqueueSale(sale, items)
            movements.forEach { syncManager.enqueueStockMovement(it) }
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

    override suspend fun deleteSale(saleId: String, billNumber: String): AppResult<Unit> = try {
        val session = sessionStore.activeSession.first() ?: error("Sign in first")
        require(session.role in listOf("ADMIN", "SUPER_ADMIN")) { "Only an administrator can cancel bills" }
        database.withTransaction {
            val sale = saleDao.getSaleById(session.companyId, saleId) ?: error("Bill no longer exists")
            saleDao.movementsFor(session.companyId, saleId).forEach { syncManager.enqueueStockMovement(it, "DELETE") }
            saleDao.creditsFor(session.companyId, saleId).forEach { syncManager.enqueueCustomerCredit(it, "DELETE") }
            saleDao.deleteSaleCascade(session.companyId, saleId, sale.billNumber)
            syncManager.enqueueSale(sale, emptyList(), "DELETE")
            database.auditLogDao().insertAuditLog(AuditLogEntity(newRecordId(), session.companyId,
                "BILL_CANCEL", sale.billNumber, sale.totalMinorUnits, "Bill cancelled", session.userId, session.displayName, System.currentTimeMillis()))
        }
        AppResult.Success(Unit)
    } catch (e: CancellationException) { throw e
    } catch (e: Exception) { AppResult.Failure(AppError.Unexpected(e.message ?: "Unable to cancel bill")) }
}
