package com.company.billing.feature.purchase.data

import com.company.billing.core.common.AppResult
import com.company.billing.core.common.AppError
import com.company.billing.core.sync.SyncStatus
import com.company.billing.core.common.newRecordId
import com.company.billing.feature.billing.data.StockMovementEntity
import com.company.billing.feature.purchase.domain.PurchaseDraft
import com.company.billing.feature.purchase.domain.PurchaseRepository

import com.company.billing.core.sync.SyncManager

class PurchaseRepositoryImpl(
    private val purchaseDao: PurchaseDao,
    private val syncManager: SyncManager
) : PurchaseRepository {
    override suspend fun save(draft: PurchaseDraft): AppResult<String> {
        return try {
            val purchaseId = newRecordId()
            val epochMs = System.currentTimeMillis()
            
            val purchase = PurchaseEntity(
                id = purchaseId,
                supplierId = draft.supplierId,
                totalMinorUnits = draft.total.minorUnits,
                createdAtEpochMs = epochMs,
                syncStatus = SyncStatus.LOCAL_ONLY
            )
            
            val items = draft.lines.map { line ->
                PurchaseItemEntity(
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
                    productId = line.productId,
                    quantityDelta = line.quantity, // Positive for purchases to add stock
                    type = "PURCHASE",
                    referenceId = purchaseId,
                    createdAtEpochMs = epochMs
                )
            }
            
            purchaseDao.savePurchase(purchase, items, movements)
            syncManager.enqueuePurchase(purchase, items)
            AppResult.Success(purchaseId)
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unexpected(e.message ?: "Unexpected error"))
        }
    }
}
