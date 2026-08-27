package com.kadaikutty.pos.feature.purchase.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kadaikutty.pos.core.sync.SyncStatus

@Entity(tableName = "purchases", indices = [Index("companyId"), Index(value = ["companyId", "createdAtEpochMs"])])
data class PurchaseEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val supplierId: String,
    val totalMinorUnits: Long,
    val createdAtEpochMs: Long,
    val syncStatus: SyncStatus,
    val invoiceNumber: String? = null,
    val notes: String? = null,
    val paymentMode: String = "CASH",
    val paidCashMinorUnits: Long = 0L,
    val paidUpiMinorUnits: Long = 0L,
    val creditAppliedMinorUnits: Long = 0L,
    val orderNumber: String? = null
)

@Entity(tableName = "purchase_items", primaryKeys = ["purchaseId", "productId"], indices = [Index("companyId"), Index(value = ["companyId", "purchaseId"])])
data class PurchaseItemEntity(
    val companyId: String,
    val purchaseId: String,
    val productId: String,
    val quantity: Long,
    val unitValueMinorUnits: Long,
    val lineTotalMinorUnits: Long
)

