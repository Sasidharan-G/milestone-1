package com.company.billing.core.backup.data

import android.content.Context
import com.company.billing.core.database.BillingDatabase
import com.company.billing.core.backup.domain.BackupMetadata
import com.company.billing.core.backup.domain.BackupResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManager(
    private val context: Context,
    private val database: BillingDatabase
) {

    suspend fun createBackup(): BackupResult = withContext(Dispatchers.IO) {
        try {
            // 1. Checkpoint database to flush WAL frames
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)")

            // 2. Fetch SQLite DB path
            val dbFile = context.getDatabasePath("billing.db")
            if (!dbFile.exists()) {
                return@withContext BackupResult.Failure(IOException("Database file not found"))
            }

            // 3. Read DB bytes and compute md5
            val dbBytes = dbFile.readBytes()
            val md5 = computeMd5(dbBytes)
            val timestamp = System.currentTimeMillis()
            val version = database.openHelper.readableDatabase.version

            // 4. Construct metadata JSON
            val metadataJson = """
                {
                  "timestampEpochMs": $timestamp,
                  "dbVersion": $version,
                  "checksumMd5": "$md5",
                  "fileSize": ${dbBytes.size}
                }
            """.trimIndent()

            // 5. ZIP database and metadata
            val bos = ByteArrayOutputStream()
            ZipOutputStream(bos).use { zos ->
                // Write DB entry
                zos.putNextEntry(ZipEntry("billing.db"))
                zos.write(dbBytes)
                zos.closeEntry()

                // Write Metadata entry
                zos.putNextEntry(ZipEntry("metadata.json"))
                zos.write(metadataJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            BackupResult.Success(bos.toByteArray())
        } catch (e: Exception) {
            BackupResult.Failure(e)
        }
    }

    suspend fun restoreBackup(zipBytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            var dbBytes: ByteArray? = null
            var metadataStr: String? = null

            // 1. Extract ZIP entries
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        "billing.db" -> {
                            dbBytes = zis.readBytes()
                        }
                        "metadata.json" -> {
                            metadataStr = String(zis.readBytes(), Charsets.UTF_8)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val extractedDbBytes = dbBytes ?: return@withContext false
            val extractedMetadata = metadataStr ?: return@withContext false

            // 2. Validate metadata values (extract MD5)
            val expectedMd5 = parseJsonField(extractedMetadata, "checksumMd5") ?: return@withContext false
            val computedMd5 = computeMd5(extractedDbBytes)
            if (expectedMd5 != computedMd5) {
                return@withContext false
            }

            // 3. Close active database connections
            database.close()

            // 4. Copy database file back
            val dbFile = context.getDatabasePath("billing.db")
            
            val shmFile = File(dbFile.path + "-shm")
            val walFile = File(dbFile.path + "-wal")
            shmFile.delete()
            walFile.delete()

            FileOutputStream(dbFile).use { fos ->
                fos.write(extractedDbBytes)
            }

            // 5. Trigger reopening database connection
            database.openHelper.writableDatabase
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun computeMd5(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun parseJsonField(json: String, field: String): String? {
        val regex = "\"$field\"\\s*:\\s*\"?([^\",\\s}]+)\"?".toRegex()
        val match = regex.find(json)
        return match?.groupValues?.get(1)?.replace("\"", "")
    }
}
