package com.company.billing.feature.billing.data

import com.company.billing.core.common.AppResult
import com.company.billing.core.common.AppError
import com.company.billing.core.sync.SyncStatus
import com.company.billing.core.common.newRecordId
import com.company.billing.feature.billing.domain.SaleDraft
import com.company.billing.feature.billing.domain.SaleRepository

import com.company.billing.core.sync.SyncManager
import kotlinx.coroutines.flow.first

class SaleRepositoryImpl(
    private val saleDao: SaleDao,
    private val syncManager: SyncManager,
    private val sessionStore: com.company.billing.core.auth.SessionStore
) : SaleRepository {
    override suspend fun save(draft: SaleDraft): AppResult<String> {
        return try {
            val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
            val companyId = session.companyId
            val saleId = newRecordId()
            val epochMs = System.currentTimeMillis()
            
            // In a production app, the bill number would use a configurable strategy.
            // For now, we generate a unique bill number based on timestamp & random digits.
            val billNumber = "BILL-${epochMs}-${(100..999).random()}"
            
            val sale = SaleEntity(
                id = saleId,
                companyId = companyId,
                billNumber = billNumber,
                totalMinorUnits = draft.total.minorUnits,
                createdAtEpochMs = epochMs,
                syncStatus = SyncStatus.LOCAL_ONLY,
                customerId = draft.customerId
            )
            
            val items = draft.lines.map { line ->
                SaleItemEntity(
                    companyId = companyId,
                    saleId = saleId,
                    productId = line.productId,
                    quantity = line.quantity,
                    unitPriceMinorUnits = line.unitPrice.minorUnits,
                    lineTotalMinorUnits = line.lineTotal.minorUnits
                )
            }
            
            val movements = draft.lines.map { line ->
                StockMovementEntity(
                    id = newRecordId(),
                    companyId = companyId,
                    productId = line.productId,
                    quantityDelta = -line.quantity, // Negative for sales to reduce stock
                    type = "SALE",
                    referenceId = saleId,
                    createdAtEpochMs = epochMs
                )
            }
            
            saleDao.saveSale(sale, items, movements)
            syncManager.enqueueSale(sale, items)
            AppResult.Success(billNumber)
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unexpected(e.message ?: "Unexpected error"))
        }
    }
}
