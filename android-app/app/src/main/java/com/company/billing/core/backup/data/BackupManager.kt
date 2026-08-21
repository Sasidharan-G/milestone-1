package com.company.billing.core.backup.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import com.company.billing.core.database.BillingDatabase
import com.company.billing.core.backup.domain.BackupResult
import com.company.billing.core.security.SecurityShield
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
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
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }

            val dbFile = context.getDatabasePath("billing.db")
            if (!dbFile.exists()) {
                return@withContext BackupResult.Failure(IOException("Database file not found"))
            }

            // 2. Export to a temporary unencrypted (plaintext) database
            val tempPlaintext = File(context.cacheDir, "plaintext_backup.db")
            if (tempPlaintext.exists()) tempPlaintext.delete()

            val db = database.openHelper.readableDatabase
            db.execSQL("ATTACH DATABASE '${tempPlaintext.absolutePath}' AS plaintext KEY '';")
            db.query("SELECT sqlcipher_export('plaintext');").use { it.moveToFirst() }
            db.execSQL("DETACH DATABASE plaintext;")

            // 3. Read plaintext DB bytes and compute md5
            val dbBytes = tempPlaintext.readBytes()
            tempPlaintext.delete() // Clean up temp file

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
                zos.putNextEntry(ZipEntry("billing.db"))
                zos.write(dbBytes)
                zos.closeEntry()

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
                        "billing.db" -> dbBytes = zis.readBytes()
                        "metadata.json" -> metadataStr = String(zis.readBytes(), Charsets.UTF_8)
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

            // 3. Close active database connections and delete existing encrypted files
            database.close()
            val encryptedDbFile = context.getDatabasePath("billing.db")
            File(encryptedDbFile.path + "-shm").delete()
            File(encryptedDbFile.path + "-wal").delete()
            encryptedDbFile.delete()

            // 4. Write unencrypted bytes to a temp file
            val tempRestoreFile = File(context.cacheDir, "plaintext_restore.db")
            if (tempRestoreFile.exists()) tempRestoreFile.delete()
            FileOutputStream(tempRestoreFile).use { fos ->
                fos.write(extractedDbBytes)
            }

            // 5. Open the unencrypted database via SQLCipher (empty password)
            val factory = net.sqlcipher.database.SupportFactory("".toByteArray())
            val plaintextHelper = factory.create(
                androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(tempRestoreFile.absolutePath)
                    .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {}
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                    })
                    .build()
            )
            val plaintextDb = plaintextHelper.writableDatabase

            // 6. Attach a new encrypted database using the current device's key
            val keyBytes = SecurityShield.getOrCreateDatabaseKey(context)
            val keyHex = keyBytes.joinToString("") { "%02x".format(it) }
            
            plaintextDb.execSQL("ATTACH DATABASE '${encryptedDbFile.absolutePath}' AS encrypted KEY 'x''$keyHex''';")
            plaintextDb.query("SELECT sqlcipher_export('encrypted');").use { it.moveToFirst() }
            plaintextDb.execSQL("DETACH DATABASE encrypted;")
            
            // 7. Cleanup
            plaintextDb.close()
            plaintextHelper.close()
            tempRestoreFile.delete()

            // 8. Re-open standard database connection
            database.openHelper.writableDatabase
            true
        } catch (e: Exception) {
            e.printStackTrace()
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
