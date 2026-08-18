package com.company.billing.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit

class SyncScheduler(private val context: Context) {
    fun request() = WorkManager.getInstance(context).enqueueUniqueWork(
        "billing-sync",
        ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED)).build(),
    )

    fun schedulePeriodicGoogleDriveBackup() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val backupRequest = PeriodicWorkRequestBuilder<com.company.billing.core.backup.data.GoogleDriveBackupWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "google-drive-backup",
            ExistingPeriodicWorkPolicy.KEEP,
            backupRequest
        )
    }
}
