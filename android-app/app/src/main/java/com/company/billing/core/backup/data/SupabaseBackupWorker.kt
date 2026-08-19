package com.company.billing.core.backup.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth

class SupabaseBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BackupWorkerEntryPoint {
        fun supabaseBackupManager(): SupabaseBackupManager
        fun supabase(): SupabaseClient
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            BackupWorkerEntryPoint::class.java
        )
        val supabaseBackupManager = entryPoint.supabaseBackupManager()
        val supabase = entryPoint.supabase()

        // Check if user is authenticated in Supabase
        val session = supabase.auth.currentSessionOrNull()
        if (session == null) {
            // Not logged in online, do not backup
            return Result.success()
        }

        // Perform the upload
        val success = supabaseBackupManager.uploadBackupToSupabase()
        return if (success) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
