package com.kadaikutty.pos.core.backup.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.kadaikutty.pos.core.backup.domain.BackupResult
import com.kadaikutty.pos.core.database.TenantDatabaseManager
import com.kadaikutty.pos.core.preferences.AppPreferences
import com.kadaikutty.pos.core.sync.RecordApplier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

// Mirrors every local data change into a user-chosen folder (via SAF persistable permission), so a
// destroyed/lost phone doesn't wipe out data that was never picked up by a cloud sync. The baseline
// zip + changelog are a convenience mirror only: the sync_queue table (Room, already durable) remains
// the source of truth, so a crash mid-flush never loses data, only delays when it reaches this folder.
@Singleton
class LiveBackupWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val backupManager: BackupManager,
    private val tenantDatabaseManager: TenantDatabaseManager,
) {
    companion object {
        const val ZIP_NAME = "billing_backup.zip"
        const val CHANGELOG_NAME = "billing_backup.changelog.jsonl"
        private const val FLUSH_INTERVAL_MS = 3000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bufferMutex = Mutex()
    private val buffer = mutableListOf<String>()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    init {
        scope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(FLUSH_INTERVAL_MS)
                flushNow()
            }
        }
    }

    suspend fun appendChange(entityType: String, entityId: String, operation: String, payloadJson: String) {
        if (appPreferences.liveBackupFolderUri.first().isNullOrBlank()) return
        val line = JSONObject()
            .put("entityType", entityType)
            .put("entityId", entityId)
            .put("operation", operation)
            .put("payload", runCatching { JSONObject(payloadJson) }.getOrElse { JSONObject() })
            .put("atEpochMs", System.currentTimeMillis())
            .toString()
        bufferMutex.withLock { buffer += line }
    }

    suspend fun flushNow(): Result<Unit> = withContext(Dispatchers.IO) {
        val folderUriStr = appPreferences.liveBackupFolderUri.first()
        if (folderUriStr.isNullOrBlank()) return@withContext Result.success(Unit)
        val linesToWrite = bufferMutex.withLock {
            if (buffer.isEmpty()) return@withContext Result.success(Unit)
            val copy = buffer.toList()
            buffer.clear()
            copy
        }
        runCatching {
            val dir = openFolder(folderUriStr) ?: error("Backup folder is no longer accessible")
            val changelogFile = dir.findFile(CHANGELOG_NAME) ?: dir.createFile("application/octet-stream", CHANGELOG_NAME) ?: error("Could not create changelog file")
            context.contentResolver.openOutputStream(changelogFile.uri, "wa")?.use { os ->
                linesToWrite.forEach { os.write((it + "\n").toByteArray(Charsets.UTF_8)) }
            } ?: error("Could not open changelog for append")
            appPreferences.saveLiveBackupLastWriteTimestamp(System.currentTimeMillis())
            _lastError.value = null
        }.onFailure { e ->
            // Put the lines back so the next flush retries them; sync_queue already has this data
            // durably, so worst case is a delayed mirror, not data loss.
            bufferMutex.withLock { buffer.addAll(0, linesToWrite) }
            _lastError.value = e.message ?: "Live local backup write failed"
            android.util.Log.w("LiveBackupWriter", "Live backup append failed", e)
        }
    }

    suspend fun setup(treeUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val dir = DocumentFile.fromTreeUri(context, treeUri) ?: error("Could not access the selected folder")
            writeBaselineSnapshot(dir)
            val changelogFile = dir.findFile(CHANGELOG_NAME) ?: dir.createFile("application/octet-stream", CHANGELOG_NAME) ?: error("Could not create changelog file")
            context.contentResolver.openOutputStream(changelogFile.uri, "wt")?.close() ?: error("Could not create changelog file")
            appPreferences.saveLiveBackupFolderUri(treeUri.toString())
            appPreferences.saveLiveBackupLastWriteTimestamp(System.currentTimeMillis())
            _lastError.value = null
        }
    }

    suspend fun compactNow(): Result<Unit> = withContext(Dispatchers.IO) {
        val folderUriStr = appPreferences.liveBackupFolderUri.first() ?: return@withContext Result.success(Unit)
        flushNow()
        runCatching {
            val dir = openFolder(folderUriStr) ?: error("Backup folder is no longer accessible")
            writeBaselineSnapshot(dir)
            val changelogFile = dir.findFile(CHANGELOG_NAME) ?: dir.createFile("application/octet-stream", CHANGELOG_NAME) ?: error("Could not create changelog file")
            context.contentResolver.openOutputStream(changelogFile.uri, "wt")?.close() ?: error("Could not truncate changelog file")
        }
    }

    // folderUriStr lets a caller restore from an arbitrary, not-yet-adopted folder (e.g. one shared
    // from another phone) without ever calling setup() on it first — setup() would overwrite that
    // folder's baseline with a snapshot of THIS device's (likely empty) database before the restore
    // could run. Omit it to restore from the folder this device already has configured.
    suspend fun restoreFrom(companyId: String, folderUriStr: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val resolvedFolderUriStr = folderUriStr ?: appPreferences.liveBackupFolderUri.first() ?: error("Live local backup is not set up")
            val dir = openFolder(resolvedFolderUriStr) ?: error("Backup folder is no longer accessible")
            val zipFile = dir.findFile(ZIP_NAME) ?: error("No baseline backup found in the selected folder")
            val zipBytes = context.contentResolver.openInputStream(zipFile.uri)?.use { it.readBytes() } ?: error("Could not read backup file")
            // Pinned to companyId: the changelog below is applied to this tenant, so the baseline
            // has to land in the same database even if the active company changes mid-restore.
            require(backupManager.restoreBackup(zipBytes, companyId)) { "Baseline backup is invalid or corrupted" }

            val changelogFile = dir.findFile(CHANGELOG_NAME)
            val lines = if (changelogFile != null) {
                context.contentResolver.openInputStream(changelogFile.uri)?.use { it.bufferedReader(Charsets.UTF_8).readLines() } ?: emptyList()
            } else emptyList()

            if (lines.isNotEmpty()) {
                val database = tenantDatabaseManager.getDatabase(companyId)
                database.withTransaction {
                    for (line in lines) {
                        if (line.isBlank()) continue
                        val entry = JSONObject(line)
                        val entityType = entry.getString("entityType")
                        val entityId = entry.getString("entityId")
                        val operation = entry.getString("operation")
                        val atEpochMs = entry.optLong("atEpochMs")
                        if (operation == "DELETE") {
                            RecordApplier.deleteRecord(database, companyId, entityType, entityId)
                        } else {
                            RecordApplier.upsertRecord(database, companyId, entityType, entityId, entry.optJSONObject("payload") ?: JSONObject(), atEpochMs)
                        }
                    }
                }
            }
        }
    }

    private suspend fun writeBaselineSnapshot(dir: DocumentFile) {
        val result = backupManager.createBackup()
        val zipBytes = when (result) {
            is BackupResult.Success -> result.zipBytes
            is BackupResult.Failure -> throw result.exception
        }
        val zipFile = dir.findFile(ZIP_NAME) ?: dir.createFile("application/zip", ZIP_NAME) ?: error("Could not create backup file")
        context.contentResolver.openOutputStream(zipFile.uri, "wt")?.use { it.write(zipBytes) } ?: error("Could not write backup file")
    }

    private fun openFolder(folderUriStr: String): DocumentFile? = DocumentFile.fromTreeUri(context, Uri.parse(folderUriStr))
}
