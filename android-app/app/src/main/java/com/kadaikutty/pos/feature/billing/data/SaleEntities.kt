package com.kadaikutty.pos.feature.billing.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kadaikutty.pos.core.sync.SyncStatus
import com.kadaikutty.pos.feature.masters.data.ProductEntity

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
    val discountMinorUnits: Long = 0L,
    @androidx.room.ColumnInfo(defaultValue = "0") val revision: Long = 0L,
    /** ACTIVE, or VOID once cancelled: the bill stays for the record but counts nowhere. */
    @androidx.room.ColumnInfo(defaultValue = "'ACTIVE'") val status: String = SaleStatus.ACTIVE
)

object SaleStatus {
    const val ACTIVE = "ACTIVE"
    const val VOID = "VOID"
}

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
    val discountMinorUnits: Long = 0L,
    val unitType: String? = null,
    val productName: String? = null,
    val costTotalMinorUnits: Long? = null,
    val netRevenueMinorUnits: Long? = null,
    /** The product's GST rate and HSN when the bill was made (null on bills from before GST). */
    val gstRateBps: Int? = null,
    val hsnCode: String? = null
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

