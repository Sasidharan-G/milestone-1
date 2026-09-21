package com.kadaikutty.pos.core.database

import androidx.room.*

@Entity(tableName = "local_operations", primaryKeys = ["companyId", "key"])
data class LocalOperationEntity(val companyId: String, val key: String, val value: String)
@Dao
interface LocalOperationDao {
    @Query("SELECT value FROM local_operations WHERE companyId = :companyId AND `key` = :key")
    suspend fun get(companyId: String, key: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: LocalOperationEntity)
    @Query("SELECT * FROM local_operations WHERE companyId = :companyId AND `key` LIKE :prefix || '%'")
    suspend fun byPrefix(companyId: String, prefix: String): List<LocalOperationEntity>
    @Query("DELETE FROM local_operations WHERE companyId = :companyId AND `key` = :key")
    suspend fun delete(companyId: String, key: String)
}
