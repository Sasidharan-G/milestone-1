package com.kadaikutty.pos.feature.reports.domain

import com.kadaikutty.pos.core.common.Money

/** REQUIRES_CLIENT_CONFIRMATION: PROFIT_COSTING_METHOD. */
interface CostingStrategy {
    suspend fun getProductCost(productId: String, quantity: Long): Money
}
