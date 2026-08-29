package com.kadaikutty.pos.core.license

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LicenseDao {
    @Query("SELECT * FROM company_licenses WHERE companyId = :companyId LIMIT 1")
    fun getLicenseFlow(companyId: String): Flow<LicenseEntity?>

    @Query("SELECT * FROM company_licenses WHERE companyId = :companyId LIMIT 1")
    suspend fun getLicense(companyId: String): LicenseEntity?

    @Query("SELECT * FROM company_licenses LIMIT 1")
    fun getActiveLicenseFlow(): Flow<LicenseEntity?>

    @Query("SELECT * FROM company_licenses LIMIT 1")
    suspend fun getActiveLicense(): LicenseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLicense(license: LicenseEntity)

    @Update
    suspend fun updateLicense(license: LicenseEntity)

    @Query("DELETE FROM company_licenses WHERE companyId = :companyId")
    suspend fun deleteLicense(companyId: String)
}
