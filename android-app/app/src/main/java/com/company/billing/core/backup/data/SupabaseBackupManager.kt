package com.company.billing.core.backup.data

import android.content.Context
import com.company.billing.core.backup.domain.BackupResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.FileObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SupabaseBackupManager(
    private val context: Context,
    private val supabase: SupabaseClient,
    private val backupManager: BackupManager
) {
    suspend fun uploadBackupToSupabase(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Create the ZIP backup bytes
            val backupResult = backupManager.createBackup()
            if (backupResult !is BackupResult.Success) {
                return@withContext false
            }
            val zipBytes = backupResult.zipBytes
            val fileName = "billing_backup_${System.currentTimeMillis()}.zip"

            // 2. Upload with sequential bucket name fallbacks
            var success = false
            val buckets = listOf("backups", "Billing Datas", "billing-datas")
            for (bucketName in buckets) {
                try {
                    val bucket = supabase.storage.from(bucketName)
                    bucket.upload(fileName, zipBytes) {
                        upsert = true
                    }
                    success = true
                    break
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            success
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun listBackupsFromSupabase(): List<FileObject> = withContext(Dispatchers.IO) {
        val buckets = listOf("backups", "Billing Datas", "billing-datas")
        for (bucketName in buckets) {
            try {
                val bucket = supabase.storage.from(bucketName)
                val files = bucket.list()
                return@withContext files
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        emptyList()
    }

    suspend fun downloadBackupFromSupabase(fileName: String): ByteArray? = withContext(Dispatchers.IO) {
        val buckets = listOf("backups", "Billing Datas", "billing-datas")
        for (bucketName in buckets) {
            try {
                val bucket = supabase.storage.from(bucketName)
                val bytes = bucket.downloadAuthenticated(fileName)
                return@withContext bytes
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        null
    }
}
