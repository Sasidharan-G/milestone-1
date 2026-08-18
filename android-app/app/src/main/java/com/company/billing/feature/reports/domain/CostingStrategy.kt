package com.company.billing.feature.reports.domain

import com.company.billing.core.common.Money

/** REQUIRES_CLIENT_CONFIRMATION: PROFIT_COSTING_METHOD. */
interface CostingStrategy {
    suspend fun getProductCost(productId: String, quantity: Long): Money
}
