package com.company.billing.feature.purchase.domain

import com.company.billing.core.common.AppResult

interface PurchaseRepository {
    suspend fun save(draft: PurchaseDraft): AppResult<String>
}
