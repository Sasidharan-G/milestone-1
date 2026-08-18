package com.company.billing.core.backup.domain

data class BackupMetadata(
    val timestampEpochMs: Long,
    val dbVersion: Int,
    val checksumMd5: String,
    val fileSize: Long
)

sealed interface BackupResult {
    data class Success(val zipBytes: ByteArray) : BackupResult
    data class Failure(val exception: Throwable) : BackupResult
}
