package com.kadaikutty.pos.feature.billing.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "draft_cart_items", indices = [Index("companyId"), Index("productId"), Index("parkId")])
data class DraftCartItemEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val productId: String,
    val productName: String,
    val quantity: Long,
    val unitPriceMinorUnits: Long,
    val unitType: String,
    val parkId: String = "active",
    val parkLabel: String = "",
    val parkedAtEpochMs: Long = 0L,
    val customerId: String? = null,
    val checkoutId: String? = null,
    val editingSaleId: String? = null,
    val editingRevision: Long? = null,
    @androidx.room.ColumnInfo(defaultValue = "0") val lineDiscountMinorUnits: Long = 0L,
    @androidx.room.ColumnInfo(defaultValue = "0") val cartDiscountMinorUnits: Long = 0L
)

data class HeldCartSummary(
    val parkId: String,
    val parkLabel: String,
    val parkedAtEpochMs: Long,
    val itemCount: Int,
    val totalAmountMinorUnits: Long
)

@Dao
interface DraftCartDao {
    @Query("UPDATE draft_cart_items SET checkoutId = :nextId, cartDiscountMinorUnits = 0 WHERE companyId = :companyId AND parkId = 'active'")
    suspend fun rekeyActive(companyId: String, nextId: String)

    @androidx.room.Transaction
    suspend fun replaceActive(companyId: String, items: List<DraftCartItemEntity>) {
        clearCart(companyId)
        insertItems(items)
    }
    @Query("DELETE FROM draft_cart_items WHERE companyId = :companyId AND parkId = 'active' AND productId IN (:productIds)")
    suspend fun removePurchased(companyId: String, productIds: List<String>)

    @Query("SELECT * FROM draft_cart_items WHERE companyId = :companyId AND parkId = 'active'")
    fun getDraftCart(companyId: String): Flow<List<DraftCartItemEntity>>

    @Query("SELECT * FROM draft_cart_items WHERE companyId = :companyId AND parkId = :parkId")
    suspend fun getItemsByParkId(companyId: String, parkId: String): List<DraftCartItemEntity>

    @Query("SELECT parkId, parkLabel, parkedAtEpochMs, COUNT(*) as itemCount, MAX(0, SUM(MAX(0, quantity * unitPriceMinorUnits / CASE WHEN unitType IN ('KG','LITER') THEN 1000 ELSE 1 END - lineDiscountMinorUnits)) - MAX(cartDiscountMinorUnits)) as totalAmountMinorUnits FROM draft_cart_items WHERE companyId = :companyId AND parkId != 'active' GROUP BY parkId ORDER BY parkedAtEpochMs DESC")
    fun getHeldCartsSummary(companyId: String): Flow<List<HeldCartSummary>>

    @Query("SELECT COUNT(DISTINCT parkId) FROM draft_cart_items WHERE companyId = :companyId AND parkId != 'active'")
    fun getHeldCartsCount(companyId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<DraftCartItemEntity>)

    @Query("DELETE FROM draft_cart_items WHERE companyId = :companyId AND parkId = :parkId")
    suspend fun clearCartByParkId(companyId: String, parkId: String)

    @Query("DELETE FROM draft_cart_items WHERE companyId = :companyId AND parkId = 'active'")
    suspend fun clearCart(companyId: String)
}

@Entity(tableName = "shifts", indices = [Index("companyId")])
data class ShiftEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val closedAtEpochMs: Long,
    val expectedCashMinorUnits: Long,
    val declaredCashMinorUnits: Long,
    val discrepancyMinorUnits: Long,
    val closedByUserId: String
)

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts WHERE companyId = :companyId ORDER BY closedAtEpochMs DESC LIMIT 1")
    fun getLastShift(companyId: String): Flow<ShiftEntity?>

    @Query("SELECT * FROM shifts WHERE companyId = :companyId ORDER BY closedAtEpochMs DESC")
    fun getAllShifts(companyId: String): Flow<List<ShiftEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShift(shift: ShiftEntity)
}
