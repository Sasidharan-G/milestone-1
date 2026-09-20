package com.kadaikutty.pos.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kadaikutty.pos.core.backup.data.LiveBackupWriter
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

// Keeps the live local backup's changelog bounded: folds it into a fresh baseline zip and truncates
// it back to empty. Runs daily; a no-op when live local backup hasn't been set up.
class LiveBackupCompactionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface LiveBackupCompactionEntryPoint {
        fun liveBackupWriter(): LiveBackupWriter
    }

    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, LiveBackupCompactionEntryPoint::class.java)
        val result = entry.liveBackupWriter().compactNow()
        return if (result.isSuccess) Result.success() else Result.retry()
    }
}
