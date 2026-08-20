package com.company.billing.feature.billing.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.company.billing.core.sync.SyncStatus
import com.company.billing.feature.masters.data.ProductEntity

@Entity(tableName = "sales", indices = [Index(value = ["companyId", "billNumber"], unique = true), Index("companyId")])
data class SaleEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val billNumber: String,
    val totalMinorUnits: Long,
    val createdAtEpochMs: Long,
    val syncStatus: SyncStatus,
    val customerId: String? = null
)

@Entity(
    tableName = "sale_items",
    primaryKeys = ["saleId", "productId"],
    foreignKeys = [
        ForeignKey(entity = SaleEntity::class, parentColumns = ["id"], childColumns = ["saleId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("productId"), Index("companyId")]
)
data class SaleItemEntity(
    val companyId: String,
    val saleId: String,
    val productId: String,
    val quantity: Long,
    val unitPriceMinorUnits: Long,
    val lineTotalMinorUnits: Long
)

@Entity(tableName = "stock_movements", indices = [Index("companyId", "productId"), Index("companyId", "referenceId"), Index("companyId")])
data class StockMovementEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val productId: String,
    val quantityDelta: Long,
    val type: String,
    val referenceId: String,
    val createdAtEpochMs: Long
)

