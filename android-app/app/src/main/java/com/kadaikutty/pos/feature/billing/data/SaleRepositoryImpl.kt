package com.kadaikutty.pos.feature.billing.data

import com.kadaikutty.pos.core.common.AppResult
import com.kadaikutty.pos.core.common.AppError
import com.kadaikutty.pos.core.sync.SyncStatus
import com.kadaikutty.pos.core.common.newRecordId
import com.kadaikutty.pos.feature.billing.domain.SaleDraft
import com.kadaikutty.pos.feature.billing.domain.SaleRepository

import com.kadaikutty.pos.core.sync.SyncManager
import kotlinx.coroutines.flow.first

class SaleRepositoryImpl(
    private val saleDao: SaleDao,
    private val syncManager: SyncManager,
    private val sessionStore: com.kadaikutty.pos.core.auth.SessionStore
) : SaleRepository {
    override suspend fun save(draft: SaleDraft): AppResult<String> {
        return try {
            val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
            val companyId = session.companyId
            val saleId = newRecordId()
            val epochMs = System.currentTimeMillis()
            
            // Generate clean sequential bill numbers starting from 01, 02, 03...
            val existingBills = saleDao.getAllBillNumbers(companyId)
            var maxSeq = 0
            for (b in existingBills) {
                val cleanDigits = b.filter { it.isDigit() }.toIntOrNull()
                // Ignore legacy timestamp based huge numbers
                if (cleanDigits != null && cleanDigits in 1..99999 && cleanDigits > maxSeq) {
                    maxSeq = cleanDigits
                }
            }
            val nextSeq = maxSeq + 1
            val billNumber = String.format(java.util.Locale.US, "%02d", nextSeq)
            
            val sale = SaleEntity(
                id = saleId,
                companyId = companyId,
                billNumber = billNumber,
                totalMinorUnits = draft.total.minorUnits,
                createdAtEpochMs = epochMs,
                syncStatus = SyncStatus.LOCAL_ONLY,
                customerId = draft.customerId,
                paymentMode = draft.paymentMode,
                paidCashMinorUnits = draft.paidCash.minorUnits,
                paidUpiMinorUnits = draft.paidUpi.minorUnits,
                creditAppliedMinorUnits = draft.creditApplied.minorUnits,
                discountMinorUnits = draft.globalDiscount.minorUnits
            )
            
            val items = draft.lines.map { line ->
                SaleItemEntity(
                    companyId = companyId,
                    saleId = saleId,
                    productId = line.productId,
                    quantity = line.quantity,
                    unitPriceMinorUnits = line.unitPrice.minorUnits,
                    lineTotalMinorUnits = line.lineTotal.minorUnits,
                    discountMinorUnits = line.discount.minorUnits
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

            var customerCredit: com.kadaikutty.pos.feature.masters.data.CustomerCreditEntity? = null
            if (draft.creditApplied.minorUnits > 0 && draft.customerId != null) {
                customerCredit = com.kadaikutty.pos.feature.masters.data.CustomerCreditEntity(
                    id = newRecordId(),
                    companyId = companyId,
                    customerId = draft.customerId,
                    amountMinorUnits = draft.creditApplied.minorUnits, // Positive amount means customer owes money
                    reason = "Bill #$billNumber",
                    dateEpochMs = epochMs,
                    syncStatus = SyncStatus.LOCAL_ONLY
                )
            }
            
            saleDao.saveSale(sale, items, movements, customerCredit)
            syncManager.enqueueSale(sale, items)
            AppResult.Success(billNumber)
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unexpected(e.message ?: "Unexpected error"))
        }
    }

    override suspend fun deleteSale(saleId: String, billNumber: String): AppResult<Unit> {
        return try {
            val session = sessionStore.activeSession.first() ?: throw IllegalStateException("No active session")
            val companyId = session.companyId
            saleDao.deleteSaleCascade(companyId, saleId, billNumber)
            // Enqueue delete for cloud sync
            val dummySale = SaleEntity(
                id = saleId,
                companyId = companyId,
                billNumber = billNumber,
                totalMinorUnits = 0L,
                createdAtEpochMs = 0L,
                syncStatus = SyncStatus.LOCAL_ONLY
            )
            syncManager.enqueueSale(dummySale, emptyList(), "DELETE")
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unexpected(e.message ?: "Unexpected error"))
        }
    }
}
