package com.company.billing.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.company.billing.feature.masters.data.CategoryEntity
import com.company.billing.feature.masters.data.CustomerEntity
import com.company.billing.feature.masters.data.ExpenseEntity
import com.company.billing.feature.masters.data.MasterDao
import com.company.billing.feature.masters.data.ProductEntity
import com.company.billing.feature.masters.data.SupplierEntity
import com.company.billing.feature.billing.data.SaleDao
import com.company.billing.feature.billing.data.SaleEntity
import com.company.billing.feature.billing.data.SaleItemEntity
import com.company.billing.feature.billing.data.StockMovementEntity
import com.company.billing.feature.purchase.data.PurchaseDao
import com.company.billing.feature.purchase.data.PurchaseEntity
import com.company.billing.feature.purchase.data.PurchaseItemEntity

import com.company.billing.core.auth.UserEntity
import com.company.billing.core.auth.UserDao
import com.company.billing.feature.masters.data.CustomerCreditEntity
import com.company.billing.feature.masters.data.SupplierCreditEntity
import com.company.billing.feature.billing.data.DraftCartItemEntity
import com.company.billing.feature.billing.data.DraftCartDao
import com.company.billing.feature.billing.data.ShiftEntity
import com.company.billing.feature.billing.data.ShiftDao

@Database(entities = [SyncQueueEntity::class, SyncDeadLetterEntity::class, CategoryEntity::class, ProductEntity::class, CustomerEntity::class, SupplierEntity::class, ExpenseEntity::class, SaleEntity::class, SaleItemEntity::class, StockMovementEntity::class, PurchaseEntity::class, PurchaseItemEntity::class, UserEntity::class, CustomerCreditEntity::class, SupplierCreditEntity::class, DraftCartItemEntity::class, ShiftEntity::class], version = 18, exportSchema = true)
@TypeConverters(SyncStatusConverter::class)
abstract class BillingDatabase : RoomDatabase() {
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun syncDeadLetterDao(): SyncDeadLetterDao
    abstract fun masterDao(): MasterDao
    abstract fun saleDao(): SaleDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun reportDao(): ReportDao
    abstract fun userDao(): UserDao
    abstract fun draftCartDao(): DraftCartDao
    abstract fun shiftDao(): ShiftDao
}
