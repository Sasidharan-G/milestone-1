package com.kadaikutty.pos.core.backup.domain

sealed interface BackupResult {
    data class Success(val zipBytes: ByteArray) : BackupResult
    data class Failure(val exception: Throwable) : BackupResult
}
