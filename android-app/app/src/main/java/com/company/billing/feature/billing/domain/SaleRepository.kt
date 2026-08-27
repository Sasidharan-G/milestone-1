package com.company.billing.feature.billing.domain

import com.company.billing.core.common.AppResult

interface SaleRepository {
    suspend fun save(draft: SaleDraft): AppResult<String>
    suspend fun deleteSale(saleId: String, billNumber: String): AppResult<Unit>
}

