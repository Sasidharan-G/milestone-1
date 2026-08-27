package com.company.billing.feature.billing.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.company.billing.core.sync.SyncStatus
import com.company.billing.feature.masters.data.ProductEntity

@Entity(tableName = "sales", indices = [Index(value = ["companyId", "billNumber"], unique = true), Index("companyId"), Index(value = ["companyId", "createdAtEpochMs"])])
data class SaleEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val billNumber: String,
    val totalMinorUnits: Long,
    val createdAtEpochMs: Long,
    val syncStatus: SyncStatus,
    val customerId: String? = null,
    val paymentMode: String = "CASH",
    val paidCashMinorUnits: Long = 0L,
    val paidUpiMinorUnits: Long = 0L,
    val creditAppliedMinorUnits: Long = 0L,
    val discountMinorUnits: Long = 0L
)

@Entity(
    tableName = "sale_items",
    primaryKeys = ["saleId", "productId"],
    foreignKeys = [
        ForeignKey(entity = SaleEntity::class, parentColumns = ["id"], childColumns = ["saleId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("productId"), Index("companyId"), Index(value = ["companyId", "saleId"])]
)
data class SaleItemEntity(
    val companyId: String,
    val saleId: String,
    val productId: String,
    val quantity: Long,
    val unitPriceMinorUnits: Long,
    val lineTotalMinorUnits: Long,
    val discountMinorUnits: Long = 0L
)

@Entity(tableName = "stock_movements", indices = [Index("companyId", "productId"), Index("companyId", "referenceId"), Index("companyId"), Index(value = ["companyId", "productId", "createdAtEpochMs"])])
data class StockMovementEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val productId: String,
    val quantityDelta: Long,
    val type: String,
    val referenceId: String,
    val createdAtEpochMs: Long
)

