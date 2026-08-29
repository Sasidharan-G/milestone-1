package com.kadaikutty.pos.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kadaikutty.pos.feature.masters.data.CategoryEntity
import com.kadaikutty.pos.feature.masters.data.CustomerEntity
import com.kadaikutty.pos.feature.masters.data.ExpenseEntity
import com.kadaikutty.pos.feature.masters.data.MasterDao
import com.kadaikutty.pos.feature.masters.data.ProductEntity
import com.kadaikutty.pos.feature.masters.data.SupplierEntity
import com.kadaikutty.pos.feature.billing.data.SaleDao
import com.kadaikutty.pos.feature.billing.data.SaleEntity
import com.kadaikutty.pos.feature.billing.data.SaleItemEntity
import com.kadaikutty.pos.feature.billing.data.StockMovementEntity
import com.kadaikutty.pos.feature.purchase.data.PurchaseDao
import com.kadaikutty.pos.feature.purchase.data.PurchaseEntity
import com.kadaikutty.pos.feature.purchase.data.PurchaseItemEntity

import com.kadaikutty.pos.core.auth.UserEntity
import com.kadaikutty.pos.core.auth.UserDao
import com.kadaikutty.pos.core.license.LicenseEntity
import com.kadaikutty.pos.core.license.LicenseDao
import com.kadaikutty.pos.feature.masters.data.CustomerCreditEntity
import com.kadaikutty.pos.feature.masters.data.SupplierCreditEntity
import com.kadaikutty.pos.feature.billing.data.DraftCartItemEntity
import com.kadaikutty.pos.feature.billing.data.DraftCartDao
import com.kadaikutty.pos.feature.billing.data.ShiftEntity
import com.kadaikutty.pos.feature.billing.data.ShiftDao

@Database(entities = [SyncQueueEntity::class, SyncDeadLetterEntity::class, CategoryEntity::class, ProductEntity::class, CustomerEntity::class, SupplierEntity::class, ExpenseEntity::class, SaleEntity::class, SaleItemEntity::class, StockMovementEntity::class, PurchaseEntity::class, PurchaseItemEntity::class, UserEntity::class, CustomerCreditEntity::class, SupplierCreditEntity::class, DraftCartItemEntity::class, ShiftEntity::class, LicenseEntity::class], version = 19, exportSchema = true)
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
    abstract fun licenseDao(): LicenseDao
}

