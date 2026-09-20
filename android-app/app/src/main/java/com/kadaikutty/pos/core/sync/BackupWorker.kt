package com.kadaikutty.pos.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kadaikutty.pos.core.auth.SessionStore
import com.kadaikutty.pos.core.backup.data.BackupManager
import com.kadaikutty.pos.core.network.BackendApiClient
import com.kadaikutty.pos.core.preferences.AppPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

// Daily automatic cloud backup, scheduled once live local backup is set up (see
// LiveBackupWriter.setup / SettingsViewModel.setupLiveBackup). Gives every shop a second,
// independent off-device copy without relying on the user remembering to back up manually.
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BackupEntryPoint {
        fun backupManager(): BackupManager
        fun backendApiClient(): BackendApiClient
        fun sessionStore(): SessionStore
        fun appPreferences(): AppPreferences
    }

    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, BackupEntryPoint::class.java)
        val session = entry.sessionStore().activeSession.first() ?: return Result.success()
        var token = session.accessToken
        if (token.isNullOrBlank()) {
            val recovered = runCatching { entry.backendApiClient().autoRecoverSession(forceRefresh = false) }.getOrNull()
            token = recovered?.first
        }
        if (token.isNullOrBlank()) return Result.success()

        return try {
            CloudBackupRunner.run(token, entry.backupManager(), entry.backendApiClient(), entry.appPreferences())
            Result.success()
        } catch (error: java.io.IOException) {
            Result.retry()
        } catch (error: Exception) {
            Result.retry()
        }
    }
}
