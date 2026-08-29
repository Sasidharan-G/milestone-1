package com.kadaikutty.pos.feature.purchase.presentation

import com.kadaikutty.pos.feature.masters.data.ProductEntity
import com.kadaikutty.pos.feature.masters.data.SupplierEntity
import com.kadaikutty.pos.feature.purchase.data.PurchaseEntity
import com.kadaikutty.pos.feature.stock.domain.ProductStock
import com.kadaikutty.pos.feature.purchase.domain.PurchaseLine

data class PurchaseUiState(
    val products: List<ProductEntity> = emptyList(),
    val suppliers: List<SupplierEntity> = emptyList(),
    val purchases: List<PurchaseEntity> = emptyList(),
    val stocks: List<ProductStock> = emptyList(),
    val lines: List<PurchaseLine> = emptyList(),
    val selectedSupplierId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
