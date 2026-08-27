package com.kadaikutty.pos.feature.billing.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "draft_cart_items", indices = [Index("companyId"), Index("productId")])
data class DraftCartItemEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val productId: String,
    val productName: String,
    val quantity: Long,
    val unitPriceMinorUnits: Long,
    val unitType: String
)

@Dao
interface DraftCartDao {
    @Query("SELECT * FROM draft_cart_items WHERE companyId = :companyId")
    fun getDraftCart(companyId: String): Flow<List<DraftCartItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<DraftCartItemEntity>)

    @Query("DELETE FROM draft_cart_items WHERE companyId = :companyId")
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
