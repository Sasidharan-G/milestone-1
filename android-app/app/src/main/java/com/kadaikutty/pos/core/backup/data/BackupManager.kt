package com.kadaikutty.pos.core.backup.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kadaikutty.pos.core.backup.domain.BackupResult
import com.kadaikutty.pos.core.database.BillingDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManager(
    private val context: Context,
    private val database: BillingDatabase,
) {
    companion object {
        private const val BACKUP_SCHEMA_VERSION = 22
        private const val MAX_BACKUP_BYTES = 64 * 1024 * 1024
        private const val DATABASE_ENTRY = "database.json"
        private const val METADATA_ENTRY = "metadata.json"
        private val excludedTables = setOf("android_metadata", "room_master_table", "sqlite_sequence")
        private val insertPriority = listOf(
            "company_licenses", "users", "categories", "customers", "suppliers", "products", "expenses",
            "sales", "purchases", "sale_items", "purchase_items", "stock_movements", "customer_credits",
            "supplier_credits", "draft_cart", "sync_queue", "sync_dead_letter", "local_operations",
        )
    }

    suspend fun createBackup(): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val sqlite = database.openHelper.writableDatabase
            sqlite.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
            val root = JSONObject()
            listTables(sqlite).forEach { table -> root.put(table, readTable(sqlite, table)) }
            val databaseBytes = root.toString().toByteArray(Charsets.UTF_8)
            val metadata = JSONObject()
                .put("format", "KADAIKUTTY_ROOM_JSON_V1")
                .put("schemaVersion", sqlite.version)
                .put("createdAtEpochMs", System.currentTimeMillis())
                .put("databaseSha256", sha256(databaseBytes))
            val output = ByteArrayOutputStream()
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry(METADATA_ENTRY)); zip.write(metadata.toString().toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry(DATABASE_ENTRY)); zip.write(databaseBytes); zip.closeEntry()
            }
            BackupResult.Success(output.toByteArray())
        }.getOrElse { BackupResult.Failure(it) }
    }

    suspend fun restoreBackup(zipBytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (zipBytes.isEmpty() || zipBytes.size > MAX_BACKUP_BYTES) return@withContext false
        runCatching {
            var metadataBytes: ByteArray? = null
            var databaseBytes: ByteArray? = null
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when (entry.name) {
                        METADATA_ENTRY -> metadataBytes = readBounded(zip)
                        DATABASE_ENTRY -> databaseBytes = readBounded(zip)
                    }
                    zip.closeEntry()
                }
            }
            val metadata = JSONObject(String(requireNotNull(metadataBytes)))
            val payload = requireNotNull(databaseBytes)
            require(metadata.getString("format") == "KADAIKUTTY_ROOM_JSON_V1")
            require(metadata.getInt("schemaVersion") <= database.openHelper.writableDatabase.version)
            require(MessageDigest.isEqual(metadata.getString("databaseSha256").toByteArray(), sha256(payload).toByteArray()))
            restoreJson(JSONObject(String(payload, Charsets.UTF_8)))
            true
        }.getOrDefault(false)
    }

    fun readBounded(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_BACKUP_BYTES) { "Backup exceeds the maximum supported size" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun listTables(database: SupportSQLiteDatabase): List<String> {
        val tables = mutableListOf<String>()
        database.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name").use { cursor ->
            while (cursor.moveToNext()) cursor.getString(0).takeIf { it !in excludedTables && safeIdentifier(it) }?.let(tables::add)
        }
        return tables
    }

    private fun readTable(database: SupportSQLiteDatabase, table: String): JSONArray {
        val rows = JSONArray()
        database.query("SELECT * FROM `$table`").use { cursor ->
            while (cursor.moveToNext()) {
                val row = JSONObject()
                for (index in 0 until cursor.columnCount) {
                    val value: Any? = when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                        Cursor.FIELD_TYPE_BLOB -> JSONObject().put("__blobBase64", Base64.encodeToString(cursor.getBlob(index), Base64.NO_WRAP))
                        else -> cursor.getString(index)
                    }
                    row.put(cursor.getColumnName(index), value)
                }
                rows.put(row)
            }
        }
        return rows
    }

    private suspend fun restoreJson(root: JSONObject) {
        val sqlite = database.openHelper.writableDatabase
        val available = listTables(sqlite).toSet()
        val included = root.keys().asSequence().filter { it in available && safeIdentifier(it) }.toSet()
        val ordered = insertPriority.filter(included::contains) + included.filterNot(insertPriority::contains).sorted()
        database.withTransaction {
            ordered.asReversed().forEach { sqlite.execSQL("DELETE FROM `$it`") }
            ordered.forEach { table ->
                val rows = root.optJSONArray(table) ?: JSONArray()
                val columns = tableColumns(sqlite, table)
                for (index in 0 until rows.length()) {
                    val row = rows.getJSONObject(index)
                    val values = ContentValues()
                    row.keys().forEach { column ->
                        if (column !in columns) return@forEach
                        val value = row.opt(column)
                        when (value) {
                            null, JSONObject.NULL -> values.putNull(column)
                            is Int -> values.put(column, value)
                            is Long -> values.put(column, value)
                            is Double -> values.put(column, value)
                            is Boolean -> values.put(column, if (value) 1 else 0)
                            is JSONObject -> if (value.has("__blobBase64")) values.put(column, Base64.decode(value.getString("__blobBase64"), Base64.NO_WRAP)) else values.put(column, value.toString())
                            else -> values.put(column, value.toString())
                        }
                    }
                    require(sqlite.insert(table, SQLiteDatabase.CONFLICT_REPLACE, values) != -1L) { "Failed restoring table $table" }
                }
            }
        }
        database.invalidationTracker.refreshVersionsAsync()
    }

    private fun tableColumns(database: SupportSQLiteDatabase, table: String): Set<String> {
        val columns = mutableSetOf<String>()
        database.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) columns += cursor.getString(nameIndex)
        }
        return columns
    }

    private fun safeIdentifier(value: String): Boolean = Regex("[A-Za-z_][A-Za-z0-9_]*").matches(value)
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

