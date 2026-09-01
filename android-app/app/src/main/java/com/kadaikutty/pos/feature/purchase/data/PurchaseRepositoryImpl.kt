package com.kadaikutty.pos.feature.purchase.data

import com.kadaikutty.pos.core.common.AppResult
import com.kadaikutty.pos.core.common.AppError
import com.kadaikutty.pos.core.sync.SyncStatus
import com.kadaikutty.pos.core.common.newRecordId
import com.kadaikutty.pos.feature.billing.data.StockMovementEntity
import com.kadaikutty.pos.feature.purchase.domain.PurchaseDraft
import com.kadaikutty.pos.feature.purchase.domain.PurchaseRepository
import com.kadaikutty.pos.feature.stock.domain.ProductStock
import kotlinx.coroutines.flow.Flow

import com.kadaikutty.pos.core.sync.SyncManager
import kotlinx.coroutines.flow.first

class PurchaseRepositoryImpl(
    private val purchaseDao: PurchaseDao,
    private val syncManager: SyncManager,
    private val sessionStore: com.kadaikutty.pos.core.auth.SessionStore,
    private val appPreferences: com.kadaikutty.pos.core.preferences.AppPreferences
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
                // Split by '-' and get the last part which is the number (e.g. XYZ-01)
                val cleanDigits = o?.substringAfterLast("-")?.filter { it.isDigit() }?.toIntOrNull()
                if (cleanDigits != null && cleanDigits > maxSeq) {
                    maxSeq = cleanDigits
                }
            }
            val nextSeq = maxSeq + 1

            // Get or generate device prefix
            var prefix = appPreferences.devicePrefix.first()
            if (prefix == null) {
                val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                prefix = (1..3).map { chars.random() }.joinToString("")
                appPreferences.saveDevicePrefix(prefix)
            }
            
            val orderNumber = String.format(java.util.Locale.US, "%s-%04d", prefix, nextSeq)

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
            
            var supplierCredit: com.kadaikutty.pos.feature.masters.data.SupplierCreditEntity? = null
            if (draft.creditApplied.minorUnits > 0) {
                val billDesc = if (!draft.invoiceNumber.isNullOrBlank()) "Bill #${draft.invoiceNumber}" else "Order #$orderNumber"
                supplierCredit = com.kadaikutty.pos.feature.masters.data.SupplierCreditEntity(
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
            movements.forEach { movement ->
                syncManager.enqueueStockMovement(movement)
            }
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

    override suspend fun deletePurchase(purchaseId: String, orderOrInvoice: String): AppResult<Unit> {
        return try {
            val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
            purchaseDao.deletePurchaseCascade(session.companyId, purchaseId, orderOrInvoice)
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unexpected(e.message ?: "Failed to delete purchase"))
        }
    }

    override suspend fun getPurchaseItemsList(purchaseId: String): List<PurchaseItemEntity> {
        val session = sessionStore.activeSession.first() ?: return emptyList()
        return purchaseDao.getPurchaseItemsList(session.companyId, purchaseId)
    }
}
