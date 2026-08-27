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

    fun schedulePeriodicSupabaseBackup() {
        // Firebase Backup not implemented yet
    }
}
