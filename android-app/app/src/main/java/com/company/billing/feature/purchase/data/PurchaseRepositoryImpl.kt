package com.company.billing.feature.purchase.data

import com.company.billing.core.common.AppResult
import com.company.billing.core.common.AppError
import com.company.billing.core.sync.SyncStatus
import com.company.billing.core.common.newRecordId
import com.company.billing.feature.billing.data.StockMovementEntity
import com.company.billing.feature.purchase.domain.PurchaseDraft
import com.company.billing.feature.purchase.domain.PurchaseRepository
import com.company.billing.feature.stock.domain.ProductStock
import kotlinx.coroutines.flow.Flow

import com.company.billing.core.sync.SyncManager
import kotlinx.coroutines.flow.first

class PurchaseRepositoryImpl(
    private val purchaseDao: PurchaseDao,
    private val syncManager: SyncManager,
    private val sessionStore: com.company.billing.core.auth.SessionStore
) : PurchaseRepository {
    override suspend fun save(draft: PurchaseDraft): AppResult<String> {
        return try {
            val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
            val companyId = session.companyId
            val purchaseId = newRecordId()
            val epochMs = System.currentTimeMillis()
            
            // Generate clean sequential order numbers (01, 02, 03...)
            val existingOrders = purchaseDao.getAllOrderNumbers(companyId)
            var maxSeq = 0
            for (o in existingOrders) {
                val cleanDigits = o?.filter { it.isDigit() }?.toIntOrNull()
                if (cleanDigits != null && cleanDigits in 1..99999 && cleanDigits > maxSeq) {
                    maxSeq = cleanDigits
                }
            }
            val nextSeq = maxSeq + 1
            val orderNumber = String.format(java.util.Locale.US, "%02d", nextSeq)

            val purchase = PurchaseEntity(
                id = purchaseId,
                companyId = companyId,
                supplierId = draft.supplierId,
                totalMinorUnits = draft.total.minorUnits,
                createdAtEpochMs = epochMs,
                syncStatus = SyncStatus.LOCAL_ONLY,
                invoiceNumber = draft.invoiceNumber,
                notes = draft.notes,
                paymentMode = draft.paymentMode,
                paidCashMinorUnits = draft.paidCash.minorUnits,
                paidUpiMinorUnits = draft.paidUpi.minorUnits,
                creditAppliedMinorUnits = draft.creditApplied.minorUnits,
                orderNumber = orderNumber
            )
            
            val items = draft.lines.map { line ->
                PurchaseItemEntity(
                    companyId = companyId,
                    purchaseId = purchaseId,
                    productId = line.productId,
                    quantity = line.quantity,
                    unitValueMinorUnits = line.unitValue.minorUnits,
                    lineTotalMinorUnits = line.total.minorUnits
                )
            }
            
            val movements = draft.lines.map { line ->
                StockMovementEntity(
                    id = newRecordId(),
                    companyId = companyId,
                    productId = line.productId,
                    quantityDelta = line.quantity, // Positive for purchases to add stock
                    type = "PURCHASE",
                    referenceId = purchaseId,
                    createdAtEpochMs = epochMs
                )
            }
            
            var supplierCredit: com.company.billing.feature.masters.data.SupplierCreditEntity? = null
            if (draft.creditApplied.minorUnits > 0) {
                val billDesc = if (!draft.invoiceNumber.isNullOrBlank()) "Bill #${draft.invoiceNumber}" else "Order #$orderNumber"
                supplierCredit = com.company.billing.feature.masters.data.SupplierCreditEntity(
                    id = newRecordId(),
                    companyId = companyId,
                    supplierId = draft.supplierId,
                    amountMinorUnits = draft.creditApplied.minorUnits,
                    terms = "Purchase $billDesc (${draft.paymentMode})",
                    dueDateEpochMs = epochMs + (30L * 24 * 60 * 60 * 1000),
                    dateEpochMs = epochMs,
                    syncStatus = SyncStatus.LOCAL_ONLY
                )
            }
            
            purchaseDao.savePurchase(purchase, items, movements, supplierCredit)
            if (supplierCredit != null) {
                syncManager.enqueueSupplierCredit(supplierCredit, "INSERT")
            }
            syncManager.enqueuePurchase(purchase, items)
            AppResult.Success(purchaseId)
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unexpected(e.message ?: "Unexpected error"))
        }
    }

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
}
