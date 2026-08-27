package com.kadaikutty.pos.feature.billing.domain

import com.kadaikutty.pos.core.common.AppResult

interface SaleRepository {
    suspend fun save(draft: SaleDraft): AppResult<String>
    suspend fun deleteSale(saleId: String, billNumber: String): AppResult<Unit>
}

