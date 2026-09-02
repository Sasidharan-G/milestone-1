package com.kadaikutty.pos.feature.billing.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "audit_logs", indices = [Index("companyId"), Index("timestampEpochMs"), Index("action")])
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val action: String, // "BILL_CANCEL", "BILL_EDIT", "PRICE_OVERRIDE"
    val billNumber: String,
    val amountMinorUnits: Long,
    val reason: String,
    val performedByUserId: String,
    val performedByUserName: String,
    val timestampEpochMs: Long
)

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs WHERE companyId = :companyId ORDER BY timestampEpochMs DESC")
    fun getAuditLogs(companyId: String): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)

    @Query("DELETE FROM audit_logs WHERE companyId = :companyId")
    suspend fun clearAuditLogs(companyId: String)
}
