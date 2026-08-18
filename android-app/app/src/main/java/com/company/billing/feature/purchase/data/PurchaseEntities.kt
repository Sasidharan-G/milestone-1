package com.company.billing.feature.purchase.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.company.billing.core.sync.SyncStatus

@Entity(tableName = "purchases")
data class PurchaseEntity(@PrimaryKey val id: String, val supplierId: String, val totalMinorUnits: Long, val createdAtEpochMs: Long, val syncStatus: SyncStatus)
@Entity(tableName = "purchase_items", primaryKeys = ["purchaseId", "productId"])
data class PurchaseItemEntity(val purchaseId: String, val productId: String, val quantity: Long, val unitValueMinorUnits: Long, val lineTotalMinorUnits: Long)
