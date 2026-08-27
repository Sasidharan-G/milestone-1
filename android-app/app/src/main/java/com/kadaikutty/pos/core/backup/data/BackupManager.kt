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

    suspend fun createBackup(): BackupResult = withContext(Dispatchers.IO) {
        try {
            // 1. Checkpoint SQLite WAL
            try {
                database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            } catch (ignored: Exception) {}

            // 2. Query all tables directly from database
            val rawDb = database.openHelper.readableDatabase

            val backupJson = JSONObject()
            backupJson.put("version", 18)
            backupJson.put("timestamp", System.currentTimeMillis())

            // Export all tables to JSON arrays
            val tables = listOf(
                "categories", "products", "customers", "suppliers", "expenses",
                "sales", "sale_items", "purchases", "purchase_items",
                "customer_credits", "supplier_credits", "stock_movements"
            )

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
            }

            val jsonBytes = backupJson.toString(2).toByteArray(Charsets.UTF_8)
            val metadataJson = """
                {
                  "timestampEpochMs": ${System.currentTimeMillis()},
                  "dbVersion": 18,
                  "tablesCount": ${tables.size}
                }
            """.trimIndent().toByteArray(Charsets.UTF_8)

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
            var jsonContent: String? = null
            var legacyDbBytes: ByteArray? = null

            // 1. Extract ZIP entries
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when {
                        entry.name.endsWith("backup_data.json") -> {
                            jsonContent = String(zis.readBytes(), Charsets.UTF_8)
                        }
                        entry.name.endsWith(".db") -> {
                            legacyDbBytes = zis.readBytes()
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // Path A: Restore from structured JSON (Ultra Reliable)
            if (!jsonContent.isNullOrBlank()) {
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
        try {
            val db = database.openHelper.writableDatabase

            db.beginTransaction()
            try {
                val tables = listOf(
                    "categories", "products", "customers", "suppliers", "expenses",
                    "sales", "sale_items", "purchases", "purchase_items",
                    "customer_credits", "supplier_credits", "stock_movements"
                )

                for (table in tables) {
                    val array = json.optJSONArray(table) ?: continue
                    for (i in 0 until array.length()) {
                        val row = array.getJSONObject(i)
                        val cv = android.content.ContentValues()
                        val keys = row.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val value = row.get(key)
                            if (value == JSONObject.NULL) {
                                cv.putNull(key)
                            } else when (value) {
                                is Long -> cv.put(key, value)
                                is Int -> cv.put(key, value)
                                is Double -> cv.put(key, value)
                                is Boolean -> cv.put(key, if (value) 1 else 0)
                                is String -> cv.put(key, value)
                            }
                        }
                        try {
                            db.insert(table, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, cv)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                db.setTransactionSuccessful()
                return true
            } finally {
                db.endTransaction()
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

            // Try opening as standard SQLite database
            val legacyDb = SQLiteDatabase.openDatabase(tempFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val currentDb = database.openHelper.writableDatabase

            currentDb.beginTransaction()
            try {
                val tables = listOf(
                    "categories", "products", "customers", "suppliers", "expenses",
                    "sales", "sale_items", "purchases", "purchase_items",
                    "customer_credits", "supplier_credits", "stock_movements"
                )

                for (table in tables) {
                    try {
                        legacyDb.rawQuery("SELECT * FROM $table", null).use { cursor ->
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
                                    currentDb.insert(table, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, cv)
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
                    e.printStackTrace()
                }
                backupJson.put(collection, array)
            }

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

