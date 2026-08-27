package com.company.billing.feature.masters.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.company.billing.core.sync.SyncStatus

@Entity(tableName = "categories", indices = [Index(value = ["companyId", "name"], unique = true), Index(value = ["companyId", "syncStatus"])])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val name: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val syncStatus: SyncStatus
)

@Entity(
    tableName = "products",
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("categoryId"), Index(value = ["companyId", "name"], unique = true), Index(value = ["companyId", "syncStatus"])]
)
data class ProductEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val name: String,
    val categoryId: String,
    val purchasePriceMinorUnits: Long = 0L,
    val salePriceMinorUnits: Long = 0L,
    val unitType: String = "PIECE",
    val barcode: String? = null,
    val minStockLevel: Double = 0.0,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val syncStatus: SyncStatus
)

@Entity(tableName = "customers", indices = [Index(value = ["companyId", "name"]), Index(value = ["companyId"]), Index(value = ["companyId", "syncStatus"])])
data class CustomerEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val creditLimitMinorUnits: Long = 0L,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val syncStatus: SyncStatus
)

@Entity(tableName = "suppliers", indices = [Index(value = ["companyId", "name"]), Index(value = ["companyId"]), Index(value = ["companyId", "syncStatus"])])
data class SupplierEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val syncStatus: SyncStatus
)

@Entity(tableName = "expenses", indices = [Index("companyId")])
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val amountMinorUnits: Long,
    val description: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val syncStatus: SyncStatus
)

@Entity(tableName = "customer_credits", indices = [Index(value = ["companyId", "customerId"]), Index("companyId")])
data class CustomerCreditEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val customerId: String,
    val amountMinorUnits: Long,
    val reason: String,
    val dateEpochMs: Long,
    val syncStatus: SyncStatus
)

@Entity(tableName = "supplier_credits", indices = [Index(value = ["companyId", "supplierId"]), Index("companyId")])
data class SupplierCreditEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val supplierId: String,
    val amountMinorUnits: Long,
    val terms: String,
    val dueDateEpochMs: Long,
    val dateEpochMs: Long,
    val syncStatus: SyncStatus
)
