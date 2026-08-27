package com.kadaikutty.pos.feature.purchase.domain

import com.kadaikutty.pos.core.common.AppResult
import com.kadaikutty.pos.feature.purchase.data.PurchaseEntity
import com.kadaikutty.pos.feature.purchase.data.PurchaseItemEntity
import com.kadaikutty.pos.feature.stock.domain.ProductStock
import kotlinx.coroutines.flow.Flow

interface PurchaseRepository {
    suspend fun save(draft: PurchaseDraft): AppResult<String>
    
    fun getPurchases(companyId: String): Flow<List<PurchaseEntity>>
    
    fun getStockBalances(companyId: String): Flow<List<ProductStock>>
    
    fun getPurchaseItems(companyId: String, purchaseId: String): Flow<List<PurchaseItemEntity>>
    
    fun getPurchasesForSupplier(companyId: String, supplierId: String): Flow<List<PurchaseEntity>>

    suspend fun deletePurchase(purchaseId: String, orderOrInvoice: String): AppResult<Unit>

    suspend fun getPurchaseItemsList(purchaseId: String): List<PurchaseItemEntity>
}
