package com.kadaikutty.pos.core.sync

import com.kadaikutty.pos.core.backup.data.BackupManager
import com.kadaikutty.pos.core.backup.domain.BackupResult
import com.kadaikutty.pos.core.network.BackendApiClient
import com.kadaikutty.pos.core.preferences.AppPreferences

// Uploads a fresh cloud backup snapshot and prunes older ones beyond maxRetained. Extracted out of
// BackupWorker so the create -> upload -> prune sequence, and its ordering guarantee (never delete
// an old backup before a new one is confirmed uploaded), is unit-testable without a real
// WorkManager/Hilt runtime.
object CloudBackupRunner {
    const val DEFAULT_MAX_RETAINED_BACKUPS = 7

    suspend fun run(
        token: String,
        backupManager: BackupManager,
        backendApi: BackendApiClient,
        appPreferences: AppPreferences,
        maxRetained: Int = DEFAULT_MAX_RETAINED_BACKUPS,
    ) {
        val result = backupManager.createBackup()
        val zipBytes: ByteArray
        val schemaVersion: Int
        when (result) {
            is BackupResult.Success -> {
                zipBytes = result.zipBytes
                schemaVersion = result.schemaVersion
            }
            is BackupResult.Failure -> throw result.exception
        }

        backendApi.uploadBackup(token, "billing_backup_${System.currentTimeMillis()}.zip", schemaVersion, zipBytes)
        appPreferences.saveLastBackupTimestamp(System.currentTimeMillis())

        // Retention runs only after the new backup is confirmed uploaded above, so an interrupted
        // or failing prune step never leaves a shop with zero cloud backups.
        val backups = backendApi.listBackups(token)
        if (backups.length() > maxRetained) {
            for (index in maxRetained until backups.length()) {
                val backupId = backups.getJSONObject(index).getString("backupId")
                runCatching { backendApi.deleteBackup(token, backupId) }
            }
        }
    }
}
