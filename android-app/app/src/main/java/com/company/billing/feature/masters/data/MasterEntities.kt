package com.company.billing.feature.masters.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.company.billing.core.sync.SyncStatus

@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(@PrimaryKey val id: String, val name: String, val createdAtEpochMs: Long, val updatedAtEpochMs: Long, val syncStatus: SyncStatus)

@Entity(tableName = "products", foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("categoryId"), Index(value = ["name"], unique = true)])
data class ProductEntity(@PrimaryKey val id: String, val name: String, val categoryId: String, val createdAtEpochMs: Long, val updatedAtEpochMs: Long, val syncStatus: SyncStatus)

@Entity(tableName = "customers", indices = [Index("name")])
data class CustomerEntity(@PrimaryKey val id: String, val name: String, val createdAtEpochMs: Long, val updatedAtEpochMs: Long, val syncStatus: SyncStatus)

@Entity(tableName = "suppliers", indices = [Index("name")])
data class SupplierEntity(@PrimaryKey val id: String, val name: String, val createdAtEpochMs: Long, val updatedAtEpochMs: Long, val syncStatus: SyncStatus)

@Entity(tableName = "expenses")
data class ExpenseEntity(@PrimaryKey val id: String, val amountMinorUnits: Long, val description: String, val createdAtEpochMs: Long, val updatedAtEpochMs: Long, val syncStatus: SyncStatus)

