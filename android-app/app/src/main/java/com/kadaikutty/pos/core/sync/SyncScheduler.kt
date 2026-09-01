package com.kadaikutty.pos.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import androidx.work.BackoffPolicy

class SyncScheduler(private val context: Context) {
    fun request() {
        val constraints = Constraints(requiredNetworkType = NetworkType.CONNECTED)
        
        val pullRequest = OneTimeWorkRequestBuilder<PullWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
            
        val pushRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
            
        WorkManager.getInstance(context)
            .beginUniqueWork("billing-sync", ExistingWorkPolicy.REPLACE, pullRequest)
            .then(pushRequest)
            .enqueue()
    }

    fun schedulePeriodicSync() {
        // Note: WorkManager does not support chained periodic work directly.
        // We will schedule them with the same interval, but PullWorker isn't periodic by default.
        // For periodic sync, we can just run a single PeriodicWorkRequest that does both,
        // or schedule both as periodic. Since SyncWorker is already periodic, we can also make PullWorker periodic.
        
        val constraints = Constraints(requiredNetworkType = NetworkType.CONNECTED)
        
        val pullRequest = PeriodicWorkRequestBuilder<PullWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
            
        val pushRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "billing-periodic-pull",
            ExistingPeriodicWorkPolicy.KEEP,
            pullRequest
        )
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "billing-periodic-sync",
            ExistingPeriodicWorkPolicy.KEEP,
            pushRequest
        )
    }
}
