package com.kadaikutty.pos.core.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.kadaikutty.pos.core.auth.SessionStore
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.feature.masters.data.CategoryEntity
import com.kadaikutty.pos.feature.masters.data.ProductEntity
import com.kadaikutty.pos.feature.masters.data.CustomerEntity
import com.kadaikutty.pos.feature.masters.data.SupplierEntity
import com.kadaikutty.pos.feature.masters.data.ExpenseEntity
import com.kadaikutty.pos.feature.masters.data.CustomerCreditEntity
import com.kadaikutty.pos.feature.masters.data.SupplierCreditEntity
import com.kadaikutty.pos.feature.billing.data.SaleEntity
import com.kadaikutty.pos.feature.billing.data.SaleItemEntity
import com.kadaikutty.pos.feature.purchase.data.PurchaseEntity
import com.kadaikutty.pos.feature.purchase.data.PurchaseItemEntity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

class PullWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PullEntryPoint {
        fun database(): BillingDatabase
        fun sessionStore(): SessionStore
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            PullEntryPoint::class.java,
        )
        val database = entryPoint.database()
        val sessionStore = entryPoint.sessionStore()

        val activeSession = sessionStore.activeSession.first() ?: return Result.success()
        val companyId = activeSession.companyId

        val sharedPrefs = applicationContext.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        val lastPullTimestamp = sharedPrefs.getLong("last_pull_timestamp_$companyId", 0L)

        val firestore = FirebaseFirestore.getInstance()

        try {
            val masterDao = database.masterDao()
            val saleDao = database.saleDao()
            val purchaseDao = database.purchaseDao()

            var newTimestamp = lastPullTimestamp
            val now = System.currentTimeMillis()

            val deleteOrder = listOf(
                "stock_movements", "sales", "purchases", "customer_credits", "supplier_credits", 
                "products", "categories", "customers", "suppliers", "expenses"
            )

            val insertOrder = listOf(
                "categories", "customers", "suppliers", "products", 
                "customer_credits", "supplier_credits", "sales", "purchases", "stock_movements", "expenses"
            )

            // PASS 1: Deletions (Bottom-Up)
            for (colName in deleteOrder) {
                try {
                    var lastDoc: com.google.firebase.firestore.DocumentSnapshot? = null
                    while (true) {
                        var query = firestore.collection("users")
                            .document(companyId)
                            .collection(colName)
                            .whereGreaterThan("updatedAtEpochMs", lastPullTimestamp)
                            .orderBy("updatedAtEpochMs")
                            .limit(500)

                        if (lastDoc != null) {
                            query = query.startAfter(lastDoc)
                        }

                        val snapshot = query.get().await()
                        if (snapshot.isEmpty) break

                        for (doc in snapshot.documents) {
                            val data = doc.data ?: continue
                            val isDeleted = data["isDeleted"] as? Boolean ?: false
                            if (!isDeleted) continue

                            val id = data["id"] as? String ?: doc.id
                            when (colName) {
                                "categories" -> masterDao.deleteCategoryById(companyId, id)
                                "products" -> masterDao.deleteProductById(companyId, id)
                                "customers" -> masterDao.deleteCustomerById(companyId, id)
                                "suppliers" -> masterDao.deleteSupplierById(companyId, id)
                                "expenses" -> masterDao.deleteExpenseById(companyId, id)
                                "customer_credits" -> masterDao.deleteCustomerCreditById(companyId, id)
                                "supplier_credits" -> masterDao.deleteSupplierCreditById(companyId, id)
                                "sales" -> saleDao.deleteSale(companyId, id)
                                "purchases" -> purchaseDao.deletePurchase(companyId, id)
                                "stock_movements" -> saleDao.deleteStockMovementById(companyId, id)
                            }
                        }
                        lastDoc = snapshot.documents.last()
                    }
                } catch (e: Exception) {
                    Log.e("PullWorker", "Failed pulling deletes for $colName", e)
                }
            }

            // PASS 2: Insertions/Updates (Top-Down)
            for (colName in insertOrder) {
                try {
                    var lastDoc: com.google.firebase.firestore.DocumentSnapshot? = null
                    while (true) {
                        var query = firestore.collection("users")
                            .document(companyId)
                            .collection(colName)
                            .whereGreaterThan("updatedAtEpochMs", lastPullTimestamp)
                            .orderBy("updatedAtEpochMs")
                            .limit(500)

                        if (lastDoc != null) {
                            query = query.startAfter(lastDoc)
                        }

                        val snapshot = query.get().await()
                        if (snapshot.isEmpty) break

                        for (doc in snapshot.documents) {
                            val data = doc.data ?: continue
                            val isDeleted = data["isDeleted"] as? Boolean ?: false
                            if (isDeleted) continue

                        val updatedAt = when (val timeField = data["updatedAtEpochMs"]) {
                            is Number -> timeField.toLong()
                            is com.google.firebase.Timestamp -> timeField.toDate().time
                            else -> 0L
                        }
                        if (updatedAt > newTimestamp) {
                            newTimestamp = updatedAt
                        }

                        val id = data["id"] as? String ?: doc.id

                        when (colName) {
                            "categories" -> masterDao.insertCategory(
                                CategoryEntity(
                                    id = id,
                                    companyId = companyId,
                                    name = data["name"] as? String ?: "",
                                    createdAtEpochMs = (data["createdAtEpochMs"] as? Number)?.toLong() ?: 0L,
                                    updatedAtEpochMs = updatedAt,
                                    syncStatus = SyncStatus.SYNCED
                                )
                            )
                            "products" -> masterDao.insertProduct(
                                ProductEntity(
                                    id = id,
                                    companyId = companyId,
                                    name = data["name"] as? String ?: "",
                                    categoryId = data["categoryId"] as? String ?: "",
                                    purchasePriceMinorUnits = (data["purchasePriceMinorUnits"] as? Number)?.toLong() ?: 0L,
                                    salePriceMinorUnits = (data["salePriceMinorUnits"] as? Number)?.toLong() ?: 0L,
                                    unitType = data["unitType"] as? String ?: "PIECE",
                                    barcode = data["barcode"] as? String,
                                    minStockLevel = (data["minStockLevel"] as? Number)?.toDouble() ?: 0.0,
                                    createdAtEpochMs = (data["createdAtEpochMs"] as? Number)?.toLong() ?: 0L,
                                    updatedAtEpochMs = updatedAt,
                                    syncStatus = SyncStatus.SYNCED
                                )
                            )
                            "customers" -> masterDao.insertCustomer(
                                CustomerEntity(
                                    id = id,
                                    companyId = companyId,
                                    name = data["name"] as? String ?: "",
                                    phone = data["phone"] as? String,
                                    address = data["address"] as? String,
                                    creditLimitMinorUnits = (data["creditLimitMinorUnits"] as? Number)?.toLong() ?: 0L,
                                    createdAtEpochMs = (data["createdAtEpochMs"] as? Number)?.toLong() ?: 0L,
                                    updatedAtEpochMs = updatedAt,
                                    syncStatus = SyncStatus.SYNCED
                                )
                            )
                            "suppliers" -> masterDao.insertSupplier(
                                SupplierEntity(
                                    id = id,
                                    companyId = companyId,
                                    name = data["name"] as? String ?: "",
                                    phone = data["phone"] as? String,
                                    address = data["address"] as? String,
                                    createdAtEpochMs = (data["createdAtEpochMs"] as? Number)?.toLong() ?: 0L,
                                    updatedAtEpochMs = updatedAt,
                                    syncStatus = SyncStatus.SYNCED
                                )
                            )
                            "expenses" -> masterDao.insertExpense(
                                ExpenseEntity(
                                    id = id,
                                    companyId = companyId,
                                    amountMinorUnits = (data["amountMinorUnits"] as? Number)?.toLong() ?: 0L,
                                    description = data["description"] as? String ?: "",
                                    createdAtEpochMs = (data["createdAtEpochMs"] as? Number)?.toLong() ?: 0L,
                                    updatedAtEpochMs = updatedAt,
                                    syncStatus = SyncStatus.SYNCED
                                )
                            )
                            "customer_credits" -> masterDao.insertCustomerCredit(
                                CustomerCreditEntity(
                                    id = id,
                                    companyId = companyId,
                                    customerId = data["customerId"] as? String ?: "",
                                    amountMinorUnits = (data["amountMinorUnits"] as? Number)?.toLong() ?: 0L,
                                    reason = data["reason"] as? String ?: "",
                                    dateEpochMs = (data["dateEpochMs"] as? Number)?.toLong() ?: 0L,
                                    syncStatus = SyncStatus.SYNCED
                                )
                            )
                            "supplier_credits" -> masterDao.insertSupplierCredit(
                                SupplierCreditEntity(
                                    id = id,
                                    companyId = companyId,
                                    supplierId = data["supplierId"] as? String ?: "",
                                    amountMinorUnits = (data["amountMinorUnits"] as? Number)?.toLong() ?: 0L,
                                    terms = data["terms"] as? String ?: "",
                                    dueDateEpochMs = (data["dueDateEpochMs"] as? Number)?.toLong() ?: 0L,
                                    dateEpochMs = (data["dateEpochMs"] as? Number)?.toLong() ?: 0L,
                                    syncStatus = SyncStatus.SYNCED
                                )
                            )
                            "sales" -> {
                                saleDao.insertSale(
                                    SaleEntity(
                                        id = id,
                                        companyId = companyId,
                                        billNumber = data["billNumber"] as? String ?: "",
                                        totalMinorUnits = (data["totalMinorUnits"] as? Number)?.toLong() ?: 0L,
                                        createdAtEpochMs = (data["createdAtEpochMs"] as? Number)?.toLong() ?: 0L,
                                        customerId = data["customerId"] as? String,
                                        paymentMode = data["paymentMode"] as? String ?: "CASH",
                                        paidCashMinorUnits = (data["paidCashMinorUnits"] as? Number)?.toLong() ?: 0L,
                                        paidUpiMinorUnits = (data["paidUpiMinorUnits"] as? Number)?.toLong() ?: 0L,
                                        creditAppliedMinorUnits = (data["creditAppliedMinorUnits"] as? Number)?.toLong() ?: 0L,
                                        discountMinorUnits = (data["discountMinorUnits"] as? Number)?.toLong() ?: 0L,
                                        syncStatus = SyncStatus.SYNCED
                                    )
                                )
                                val itemsList = data["items"] as? List<Map<String, Any>>
                                if (itemsList != null) {
                                    val saleItems = itemsList.map { itemMap ->
                                        SaleItemEntity(
                                            companyId = companyId,
                                            saleId = id,
                                            productId = itemMap["productId"] as? String ?: "",
                                            quantity = (itemMap["quantity"] as? Number)?.toLong() ?: 0L,
                                            unitPriceMinorUnits = (itemMap["unitPriceMinorUnits"] as? Number)?.toLong() ?: 0L,
                                            lineTotalMinorUnits = (itemMap["lineTotalMinorUnits"] as? Number)?.toLong() ?: 0L,
                                            discountMinorUnits = (itemMap["discountMinorUnits"] as? Number)?.toLong() ?: 0L
                                        )
                                    }
                                    saleDao.insertItems(saleItems)
                                }
                            }
                            "purchases" -> {
                                purchaseDao.insertPurchase(
                                    PurchaseEntity(
                                        id = id,
                                        companyId = companyId,
                                        supplierId = data["supplierId"] as? String ?: "",
                                        totalMinorUnits = (data["totalMinorUnits"] as? Number)?.toLong() ?: 0L,
                                        createdAtEpochMs = (data["createdAtEpochMs"] as? Number)?.toLong() ?: 0L,
                                        invoiceNumber = data["invoiceNumber"] as? String,
                                        notes = data["notes"] as? String,
                                        paymentMode = data["paymentMode"] as? String ?: "CASH",
                                        paidCashMinorUnits = (data["paidCashMinorUnits"] as? Number)?.toLong() ?: 0L,
                                        paidUpiMinorUnits = (data["paidUpiMinorUnits"] as? Number)?.toLong() ?: 0L,
                                        creditAppliedMinorUnits = (data["creditAppliedMinorUnits"] as? Number)?.toLong() ?: 0L,
                                        orderNumber = data["orderNumber"] as? String,
                                        syncStatus = SyncStatus.SYNCED
                                    )
                                )
                                val itemsList = data["items"] as? List<Map<String, Any>>
                                if (itemsList != null) {
                                    val purchaseItems = itemsList.map { itemMap ->
                                        PurchaseItemEntity(
                                            companyId = companyId,
                                            purchaseId = id,
                                            productId = itemMap["productId"] as? String ?: "",
                                            quantity = (itemMap["quantity"] as? Number)?.toLong() ?: 0L,
                                            unitValueMinorUnits = (itemMap["unitValueMinorUnits"] as? Number)?.toLong() ?: 0L,
                                            lineTotalMinorUnits = (itemMap["lineTotalMinorUnits"] as? Number)?.toLong() ?: 0L
                                        )
                                    }
                                    purchaseDao.insertItems(purchaseItems)
                                }
                            }
                            "stock_movements" -> {
                                saleDao.insertStockMovements(listOf(
                                    com.kadaikutty.pos.feature.billing.data.StockMovementEntity(
                                        id = id,
                                        companyId = companyId,
                                        productId = data["productId"] as? String ?: "",
                                        quantityDelta = (data["quantityDelta"] as? Number)?.toLong() ?: 0L,
                                        type = data["type"] as? String ?: "",
                                        referenceId = data["referenceId"] as? String ?: "",
                                        createdAtEpochMs = (data["createdAtEpochMs"] as? Number)?.toLong() ?: 0L
                                    )
                                ))
                            }
                        }
                        }
                        lastDoc = snapshot.documents.last()
                    }
                } catch (e: Exception) {
                    Log.e("PullWorker", "Failed pulling inserts for $colName", e)
                }
            }
            
            sharedPrefs.edit().putLong("last_pull_timestamp_$companyId", now).apply()
            return Result.success()

        } catch (e: Exception) {
            Log.e("PullWorker", "Exception during pull: ${e.message}")
            return Result.retry()
        }
    }
}
