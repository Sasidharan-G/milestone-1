package com.company.billing.core.backup.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.company.billing.core.preferences.AppPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

class GoogleDriveBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BackupWorkerEntryPoint {
        fun googleDriveBackupManager(): GoogleDriveBackupManager
        fun appPreferences(): AppPreferences
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            BackupWorkerEntryPoint::class.java
        )
        val driveBackupManager = entryPoint.googleDriveBackupManager()
        val appPreferences = entryPoint.appPreferences()

        // 1. Check if user has linked their Google Account
        val email = appPreferences.googleAccount.first()
        if (email.isNullOrBlank()) {
            // No linked account, do not sync
            return Result.success()
        }

        // 2. Perform the database ZIP packaging and Google Drive upload
        val success = driveBackupManager.uploadBackupToDrive()
        return if (success) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
