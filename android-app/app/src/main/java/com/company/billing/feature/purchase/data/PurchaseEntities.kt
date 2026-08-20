package com.company.billing.feature.purchase.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.company.billing.core.sync.SyncStatus

@Entity(tableName = "purchases", indices = [Index("companyId")])
data class PurchaseEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val supplierId: String,
    val totalMinorUnits: Long,
    val createdAtEpochMs: Long,
    val syncStatus: SyncStatus
)

@Entity(tableName = "purchase_items", primaryKeys = ["purchaseId", "productId"], indices = [Index("companyId")])
data class PurchaseItemEntity(
    val companyId: String,
    val purchaseId: String,
    val productId: String,
    val quantity: Long,
    val unitValueMinorUnits: Long,
    val lineTotalMinorUnits: Long
)

