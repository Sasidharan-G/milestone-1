package com.company.billing.core.backup.data

import android.content.Context
import com.company.billing.core.backup.domain.BackupResult
import com.company.billing.core.preferences.AppPreferences
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

class GoogleDriveBackupManager(
    private val context: Context,
    private val appPreferences: AppPreferences,
    private val backupManager: BackupManager
) {
    suspend fun uploadBackupToDrive(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Get the current signed-in Google account
            val signInAccount: GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)
            if (signInAccount == null) {
                return@withContext false
            }

            // 2. Initialize Google Drive Service with the AppData scope
            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                Collections.singleton(DriveScopes.DRIVE_APPDATA)
            ).apply {
                selectedAccount = signInAccount.account
            }

            val driveService = Drive.Builder(
                com.google.api.client.http.javanet.NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("Client Billing System").build()

            // 3. Create the ZIP backup bytes
            val backupResult = backupManager.createBackup()
            if (backupResult !is BackupResult.Success) {
                return@withContext false
            }
            val zipBytes = backupResult.zipBytes

            // 4. Construct file metadata for AppData folder
            val fileMetadata = File().apply {
                name = "billing_backup_${System.currentTimeMillis()}.zip"
                parents = Collections.singletonList("appDataFolder")
            }

            val mediaContent = ByteArrayContent("application/zip", zipBytes)

            // 5. Upload file to Google Drive
            val driveFile = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()

            driveFile != null && driveFile.id != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
