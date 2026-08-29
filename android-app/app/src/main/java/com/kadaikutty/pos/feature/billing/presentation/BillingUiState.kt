package com.kadaikutty.pos.feature.billing.presentation

import com.kadaikutty.pos.feature.billing.domain.SaleLine
import com.kadaikutty.pos.feature.masters.data.CustomerEntity
import com.kadaikutty.pos.feature.masters.data.ProductEntity
import com.kadaikutty.pos.feature.billing.data.SaleEntity

data class BillingUiState(
    val products: List<ProductEntity> = emptyList(),
    val customers: List<CustomerEntity> = emptyList(),
    val sales: List<SaleEntity> = emptyList(),
    val stockBalances: Map<String, Long> = emptyMap(),
    val lines: List<SaleLine> = emptyList(),
    val selectedCustomerId: String? = null,
    val selectedCustomerCreditBalance: Long = 0L,
    val isLoading: Boolean = false,
    val error: String? = null
)
