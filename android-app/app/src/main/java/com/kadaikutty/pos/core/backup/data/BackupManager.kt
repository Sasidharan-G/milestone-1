package com.kadaikutty.pos.core.backup.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.kadaikutty.pos.core.database.BillingDatabase
import com.kadaikutty.pos.core.backup.domain.BackupResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class BackupManager(
    private val context: Context,
    private val database: BillingDatabase
) {

    private val SYSTEM_TABLES = setOf(
        "android_metadata",
        "room_master_table",
        "sqlite_sequence",
        "sqlite_stat1",
        "room_schema_version"
    )

    /**
     * Dynamically fetch all user tables from the active SQLite database
     */
    private fun getUserTables(db: androidx.sqlite.db.SupportSQLiteDatabase): List<String> {
        val tables = mutableListOf<String>()
        try {
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%'").use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    if (!SYSTEM_TABLES.contains(name) && !name.startsWith("sqlite_") && !name.startsWith("room_")) {
                        tables.add(name)
                    }
                }
            }
        } catch (e: Exception) {
            return listOf(
                "users", "categories", "products", "customers", "suppliers", "expenses",
                "sales", "sale_items", "purchases", "purchase_items",
                "customer_credits", "supplier_credits", "stock_movements",
                "draft_cart_items", "shifts", "sync_queue", "sync_dead_letter"
            )
        }
        return if (tables.isNotEmpty()) tables else listOf(
            "users", "categories", "products", "customers", "suppliers", "expenses",
            "sales", "sale_items", "purchases", "purchase_items",
            "customer_credits", "supplier_credits", "stock_movements",
            "draft_cart_items", "shifts"
        )
    }

    private fun calculateSha256(bytes: ByteArray): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun createBackup(): BackupResult = withContext(Dispatchers.IO) {
        try {
            // 1. Checkpoint SQLite WAL for consistent snapshot
            try {
                database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            } catch (ignored: Exception) {}

            // 2. Query all tables dynamically directly from database
            val rawDb = database.openHelper.readableDatabase
            val tables = getUserTables(rawDb)

            val backupJson = JSONObject()
            backupJson.put("version", 18)
            val timestamp = System.currentTimeMillis()
            backupJson.put("timestamp", timestamp)

            val tableRowCounts = JSONObject()
            var totalRecords = 0

            for (table in tables) {
                val array = JSONArray()
                try {
                    rawDb.query("SELECT * FROM $table").use { cursor ->
                        val colNames = cursor.columnNames
                        while (cursor.moveToNext()) {
                            val rowObj = JSONObject()
                            for (col in colNames) {
                                val idx = cursor.getColumnIndex(col)
                                when (cursor.getType(idx)) {
                                    android.database.Cursor.FIELD_TYPE_INTEGER -> rowObj.put(col, cursor.getLong(idx))
                                    android.database.Cursor.FIELD_TYPE_FLOAT -> rowObj.put(col, cursor.getDouble(idx))
                                    android.database.Cursor.FIELD_TYPE_STRING -> rowObj.put(col, cursor.getString(idx))
                                    android.database.Cursor.FIELD_TYPE_BLOB -> rowObj.put(col, cursor.getBlob(idx)?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) })
                                    else -> rowObj.put(col, JSONObject.NULL)
                                }
                            }
                            array.put(rowObj)
                        }
                    }
                } catch (ignored: Exception) {}
                backupJson.put(table, array)
                tableRowCounts.put(table, array.length())
                totalRecords += array.length()
            }

            val jsonBytes = backupJson.toString(2).toByteArray(Charsets.UTF_8)
            val jsonChecksum = calculateSha256(jsonBytes)

            val metadataJson = JSONObject().apply {
                put("timestampEpochMs", timestamp)
                put("dbVersion", 18)
                put("tablesCount", tables.size)
                put("totalRecords", totalRecords)
                put("dataChecksumSha256", jsonChecksum)
                put("tableRowCounts", tableRowCounts)
                put("app", "Kadaikutty POS")
            }.toString(2).toByteArray(Charsets.UTF_8)

            // 3. Package into ZIP
            val bos = ByteArrayOutputStream()
            ZipOutputStream(bos).use { zos ->
                zos.putNextEntry(ZipEntry("backup_data.json"))
                zos.write(jsonBytes)
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("metadata.json"))
                zos.write(metadataJson)
                zos.closeEntry()
            }

            BackupResult.Success(bos.toByteArray())
        } catch (e: Exception) {
            BackupResult.Failure(e)
        }
    }

    suspend fun restoreBackup(zipBytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            if (zipBytes.isEmpty()) return@withContext false

            var jsonContent: String? = null
            var metadataContent: String? = null
            var legacyDbBytes: ByteArray? = null

            // 1. Extract and inspect ZIP entries
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when {
                        entry.name.endsWith("backup_data.json") -> {
                            jsonContent = String(zis.readBytes(), Charsets.UTF_8)
                        }
                        entry.name.endsWith("metadata.json") -> {
                            metadataContent = String(zis.readBytes(), Charsets.UTF_8)
                        }
                        entry.name.endsWith(".db") -> {
                            legacyDbBytes = zis.readBytes()
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // Path A: Restore from structured JSON (Ultra Reliable with Checksum Verification)
            if (!jsonContent.isNullOrBlank()) {
                if (!metadataContent.isNullOrBlank()) {
                    try {
                        val metaObj = JSONObject(metadataContent!!)
                        if (metaObj.has("dataChecksumSha256")) {
                            val expectedChecksum = metaObj.getString("dataChecksumSha256")
                            val actualChecksum = calculateSha256(jsonContent!!.toByteArray(Charsets.UTF_8))
                            if (expectedChecksum != actualChecksum) {
                                return@withContext false
                            }
                        }
                    } catch (ignored: Exception) {}
                }

                return@withContext restoreFromJson(JSONObject(jsonContent!!))
            }

            // Path B: Restore from legacy SQLite / SQLCipher DB file
            if (legacyDbBytes != null && legacyDbBytes!!.isNotEmpty()) {
                return@withContext restoreFromSqliteBytes(legacyDbBytes!!)
            }

            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun restoreFromJson(json: JSONObject): Boolean {
        val db = database.openHelper.writableDatabase
        
        try {
            try {
                db.execSQL("PRAGMA foreign_keys = OFF")
            } catch (ignored: Exception) {}

            db.beginTransaction()
            try {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val table = keys.next()
                    if (table == "version" || table == "timestamp") continue

                    val array = json.optJSONArray(table) ?: continue
                    
                    try {
                        db.execSQL("DELETE FROM $table")
                    } catch (ignored: Exception) {}

                    for (i in 0 until array.length()) {
                        val row = array.getJSONObject(i)
                        val cv = android.content.ContentValues()
                        val colKeys = row.keys()
                        while (colKeys.hasNext()) {
                            val col = colKeys.next()
                            val value = row.get(col)
                            if (value == JSONObject.NULL) {
                                cv.putNull(col)
                            } else when (value) {
                                is Long -> cv.put(col, value)
                                is Int -> cv.put(col, value)
                                is Double -> cv.put(col, value)
                                is Boolean -> cv.put(col, if (value) 1 else 0)
                                is String -> cv.put(col, value)
                            }
                        }
                        try {
                            db.insert(table, SQLiteDatabase.CONFLICT_REPLACE, cv)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                db.setTransactionSuccessful()
                return true
            } finally {
                db.endTransaction()
                try {
                    db.execSQL("PRAGMA foreign_keys = ON")
                } catch (ignored: Exception) {}
                
                try {
                    database.invalidationTracker.refreshVersionsAsync()
                } catch (ignored: Exception) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun restoreFromSqliteBytes(dbBytes: ByteArray): Boolean {
        val tempFile = File(context.cacheDir, "legacy_restore_temp.db")
        try {
            if (tempFile.exists()) tempFile.delete()
            FileOutputStream(tempFile).use { it.write(dbBytes) }

            val legacyDb = SQLiteDatabase.openDatabase(tempFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val currentDb = database.openHelper.writableDatabase
            val tables = getUserTables(currentDb)

            try {
                currentDb.execSQL("PRAGMA foreign_keys = OFF")
            } catch (ignored: Exception) {}

            currentDb.beginTransaction()
            try {
                for (table in tables) {
                    try {
                        legacyDb.rawQuery("SELECT * FROM $table", null).use { cursor ->
                            try {
                                currentDb.execSQL("DELETE FROM $table")
                            } catch (ignored: Exception) {}

                            val colNames = cursor.columnNames
                            while (cursor.moveToNext()) {
                                val cv = android.content.ContentValues()
                                for (col in colNames) {
                                    val idx = cursor.getColumnIndex(col)
                                    when (cursor.getType(idx)) {
                                        android.database.Cursor.FIELD_TYPE_INTEGER -> cv.put(col, cursor.getLong(idx))
                                        android.database.Cursor.FIELD_TYPE_FLOAT -> cv.put(col, cursor.getDouble(idx))
                                        android.database.Cursor.FIELD_TYPE_STRING -> cv.put(col, cursor.getString(idx))
                                        android.database.Cursor.FIELD_TYPE_BLOB -> cv.put(col, cursor.getBlob(idx))
                                        else -> cv.putNull(col)
                                    }
                                }
                                try {
                                    currentDb.insert(table, SQLiteDatabase.CONFLICT_REPLACE, cv)
                                } catch (ignored: Exception) {}
                            }
                        }
                    } catch (ignored: Exception) {}
                }
                currentDb.setTransactionSuccessful()
                return true
            } finally {
                currentDb.endTransaction()
                legacyDb.close()
                try {
                    currentDb.execSQL("PRAGMA foreign_keys = ON")
                    database.invalidationTracker.refreshVersionsAsync()
                } catch (ignored: Exception) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            tempFile.delete()
        }
    }

    suspend fun restoreFromCloud(companyId: String, firestore: FirebaseFirestore): BackupResult = withContext(Dispatchers.IO) {
        try {
            val backupJson = JSONObject()
            val salesArray = JSONArray()
            val saleItemsArray = JSONArray()
            val purchasesArray = JSONArray()
            val purchaseItemsArray = JSONArray()
            
            val simpleCollections = listOf(
                "categories", "products", "customers", "suppliers", "expenses",
                "customer_credits", "supplier_credits"
            )

            for (collection in simpleCollections) {
                val array = JSONArray()
                try {
                    val snapshot = firestore.collection("users").document(companyId).collection(collection).get().await()
                    for (doc in snapshot.documents) {
                        doc.data?.let { array.put(JSONObject(it)) }
                    }
                } catch (e: Exception) {
                    // Ignore errors for individual collections
                }
                backupJson.put(collection, array)
            }
            
            // Fetch staff separately to map to users table
            try {
                val staffArray = JSONArray()
                val staffSnapshot = firestore.collection("users").document(companyId).collection("staff").get().await()
                for (doc in staffSnapshot.documents) {
                    doc.data?.let { staffArray.put(JSONObject(it)) }
                }
                backupJson.put("users", staffArray)
            } catch (e: Exception) { }

            // Fetch Sales and nested items
            try {
                val salesSnapshot = firestore.collection("users").document(companyId).collection("sales").get().await()
                for (doc in salesSnapshot.documents) {
                    val data = doc.data ?: continue
                    salesArray.put(JSONObject(data))
                    
                    val itemsSnapshot = doc.reference.collection("items").get().await()
                    for (itemDoc in itemsSnapshot.documents) {
                        itemDoc.data?.let { saleItemsArray.put(JSONObject(it)) }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            backupJson.put("sales", salesArray)
            backupJson.put("sale_items", saleItemsArray)

            // Fetch Purchases and nested items
            try {
                val purchasesSnapshot = firestore.collection("users").document(companyId).collection("purchases").get().await()
                for (doc in purchasesSnapshot.documents) {
                    val data = doc.data ?: continue
                    purchasesArray.put(JSONObject(data))
                    
                    val itemsSnapshot = doc.reference.collection("items").get().await()
                    for (itemDoc in itemsSnapshot.documents) {
                        itemDoc.data?.let { purchaseItemsArray.put(JSONObject(it)) }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            backupJson.put("purchases", purchasesArray)
            backupJson.put("purchase_items", purchaseItemsArray)

            // Restore from the constructed JSON
            val success = restoreFromJson(backupJson)
            if (success) {
                BackupResult.Success(ByteArray(0)) // Success with empty byte array
            } else {
                BackupResult.Failure(Exception("Failed to restore from cloud data"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            BackupResult.Failure(e)
        }
    }
}

