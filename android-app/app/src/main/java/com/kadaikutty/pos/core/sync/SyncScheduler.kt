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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SyncScheduler(private val context: Context) {

    private val _manualDismissed = kotlinx.coroutines.flow.MutableStateFlow(true)

    fun dismissNotification() {
        _manualDismissed.value = true
    }

    fun cancelAllWork() {
        _manualDismissed.value = true
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork("billing-sync")
        workManager.cancelUniqueWork("billing-pull")
        // Also cancel periodic workers so they don't push deletions to cloud
        // or pull cloud data back into a deliberately wiped local database
        workManager.cancelUniqueWork("billing-periodic-sync")
        workManager.cancelUniqueWork("billing-periodic-pull")
        workManager.cancelUniqueWork("billing-periodic-live-backup-compaction")
        workManager.cancelUniqueWork("billing-periodic-cloud-backup")
    }

    val isSyncingFlow: Flow<Boolean> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow("billing-sync")
        .map { workInfos ->
            workInfos.any { it.state == androidx.work.WorkInfo.State.RUNNING }
        }

    val syncNotificationFlow: Flow<SyncNotificationState> = kotlinx.coroutines.flow.combine(
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow("billing-sync"),
        _manualDismissed
    ) { workInfos, dismissed ->
        if (dismissed) {
            SyncNotificationState.Idle
        } else if (workInfos.any { it.state == androidx.work.WorkInfo.State.RUNNING }) {
            SyncNotificationState.InProgress
        } else if (workInfos.isNotEmpty() && workInfos.any { it.state == androidx.work.WorkInfo.State.FAILED }) {
            val failedInfo = workInfos.firstOrNull { it.state == androidx.work.WorkInfo.State.FAILED }
            val reason = failedInfo?.outputData?.getString("error_reason")
                ?: "Network or server connection failed."
            val nextSteps = failedInfo?.outputData?.getString("error_next_steps")
                ?: "Please check your network and tap to retry."
            SyncNotificationState.Failed(reason, nextSteps)
        } else if (workInfos.isNotEmpty() && workInfos.all { it.state == androidx.work.WorkInfo.State.SUCCEEDED }) {
            SyncNotificationState.Success("Cloud sync completed")
        } else {
            SyncNotificationState.Idle
        }
    }

    /** Quiet background pull triggered by a realtime data_changed event; does not surface the sync banner. */
    fun requestPull() {
        val pullRequest = OneTimeWorkRequestBuilder<PullWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("billing-pull", ExistingWorkPolicy.KEEP, pullRequest)
    }

    fun request(replaceExisting: Boolean = false) {
        _manualDismissed.value = false
        val constraints = Constraints(requiredNetworkType = NetworkType.CONNECTED)
        
        val pullRequest = OneTimeWorkRequestBuilder<PullWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
            
        val pushRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
            
        val workManager = WorkManager.getInstance(context)

        // Upload and download are intentionally independent. A slow/failed pull
        // must never prevent locally-created bills, products, or masters from
        // reaching the backend sync API.
        workManager.enqueueUniqueWork(
                "billing-sync",
                if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                pushRequest
            )
        workManager.enqueueUniqueWork(
            "billing-pull",
            if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            pullRequest
        )
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

    fun schedulePeriodicLiveBackupCompaction() {
        val compactionRequest = PeriodicWorkRequestBuilder<LiveBackupCompactionWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "billing-periodic-live-backup-compaction",
            ExistingPeriodicWorkPolicy.KEEP,
            compactionRequest
        )
    }

    fun schedulePeriodicBackup() {
        val constraints = Constraints(requiredNetworkType = NetworkType.CONNECTED)
        val backupRequest = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "billing-periodic-cloud-backup",
            ExistingPeriodicWorkPolicy.KEEP,
            backupRequest
        )
    }
}
