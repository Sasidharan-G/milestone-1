package com.company.billing.feature.purchase.domain

import com.company.billing.core.common.AppResult
import com.company.billing.feature.purchase.data.PurchaseEntity
import com.company.billing.feature.purchase.data.PurchaseItemEntity
import com.company.billing.feature.stock.domain.ProductStock
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
