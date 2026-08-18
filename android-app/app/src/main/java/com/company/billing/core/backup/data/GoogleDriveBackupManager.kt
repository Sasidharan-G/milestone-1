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

            val fileMetadata = File().apply {
                name = "billing_backup_${System.currentTimeMillis()}.zip"
                parents = Collections.singletonList("appDataFolder")
            }
            val mediaContent = ByteArrayContent("application/zip", zipBytes)
            val driveFile = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()

            driveFile != null && driveFile.id != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun listBackupsFromDrive(): List<File> = withContext(Dispatchers.IO) {
        try {
            val signInAccount = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext emptyList()
            val credential = GoogleAccountCredential.usingOAuth2(context, Collections.singleton(DriveScopes.DRIVE_APPDATA)).apply {
                selectedAccount = signInAccount.account
            }
            val driveService = Drive.Builder(
                com.google.api.client.http.javanet.NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("Client Billing System").build()

            val result = driveService.files().list()
                .setSpaces("appDataFolder")
                .setFields("files(id, name, createdTime, size)")
                .execute()
            result.files ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun downloadBackupFromDrive(fileId: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val signInAccount = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext null
            val credential = GoogleAccountCredential.usingOAuth2(context, Collections.singleton(DriveScopes.DRIVE_APPDATA)).apply {
                selectedAccount = signInAccount.account
            }
            val driveService = Drive.Builder(
                com.google.api.client.http.javanet.NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("Client Billing System").build()

            val outputStream = java.io.ByteArrayOutputStream()
            driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            outputStream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
