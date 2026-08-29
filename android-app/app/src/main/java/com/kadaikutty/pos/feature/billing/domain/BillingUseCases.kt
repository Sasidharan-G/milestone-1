package com.kadaikutty.pos.feature.billing.domain

import com.kadaikutty.pos.core.common.AppResult
import com.kadaikutty.pos.core.common.Money
import com.kadaikutty.pos.core.common.newRecordId
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.feature.masters.data.CustomerCreditEntity
import com.kadaikutty.pos.feature.masters.data.CustomerEntity
import javax.inject.Inject

class AddQuickCustomerUseCase @Inject constructor(
    private val database: BillingDatabase
) {
    suspend operator fun invoke(
        companyId: String,
        name: String,
        phone: String?,
        address: String?,
        openingDueMinorUnits: Long
    ): AppResult<String> {
        return try {
            val customerId = newRecordId()
            val customer = CustomerEntity(
                id = customerId,
                companyId = companyId,
                name = name,
                phone = phone,
                address = address,
                creditLimitMinorUnits = 0L,
                createdAtEpochMs = System.currentTimeMillis(),
                updatedAtEpochMs = System.currentTimeMillis(),
                syncStatus = com.kadaikutty.pos.core.sync.SyncStatus.PENDING
            )
            val masterDao = database.masterDao()
            masterDao.insertCustomer(customer)

            if (openingDueMinorUnits != 0L) {
                val creditRecord = CustomerCreditEntity(
                    id = newRecordId(),
                    companyId = companyId,
                    customerId = customerId,
                    amountMinorUnits = openingDueMinorUnits,
                    reason = "OPENING",
                    dateEpochMs = System.currentTimeMillis(),
                    syncStatus = com.kadaikutty.pos.core.sync.SyncStatus.PENDING
                )
                masterDao.insertCustomerCredit(creditRecord)
            }
            AppResult.Success(customerId)
        } catch (e: Exception) {
            AppResult.Failure(com.kadaikutty.pos.core.common.AppError.Database)
        }
    }
}

class SettleCreditUseCase @Inject constructor(
    private val database: BillingDatabase
) {
    suspend operator fun invoke(
        companyId: String,
        customerId: String,
        amount: Money,
        notes: String
    ): AppResult<Unit> {
        return try {
            if (amount.minorUnits <= 0) return AppResult.Failure(com.kadaikutty.pos.core.common.AppError.Validation("Amount must be greater than zero"))
            
            val masterDao = database.masterDao()
            val creditRecord = CustomerCreditEntity(
                id = newRecordId(),
                companyId = companyId,
                customerId = customerId,
                amountMinorUnits = amount.minorUnits,
                reason = "SETTLEMENT",
                dateEpochMs = System.currentTimeMillis(),
                syncStatus = com.kadaikutty.pos.core.sync.SyncStatus.PENDING
            )
            masterDao.insertCustomerCredit(creditRecord)
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(com.kadaikutty.pos.core.common.AppError.Database)
        }
    }
}
