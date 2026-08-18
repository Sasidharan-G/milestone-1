package com.company.billing.core.sharing

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ShareManager(private val context: Context) {

    companion object {
        const val PACKAGE_WHATSAPP = "com.whatsapp"
        const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    }

    fun shareText(text: String, packageId: String? = null): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }

        if (packageId != null && isAppInstalled(packageId)) {
            intent.setPackage(packageId)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return true
        }

        return try {
            val chooser = Intent.createChooser(intent, "Share via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun shareFile(fileBytes: ByteArray, filename: String, mimeType: String, packageId: String? = null): Boolean {
        val file = saveToCache(fileBytes, filename) ?: return false
        val uri: Uri = try {
            FileProvider.getUriForFile(context, "com.company.billing.fileprovider", file)
        } catch (e: IllegalArgumentException) {
            return false
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        if (packageId != null && isAppInstalled(packageId)) {
            intent.setPackage(packageId)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return true
        }

        return try {
            val chooser = Intent.createChooser(intent, "Share Document").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isAppInstalled(packageId: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageId, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun saveToCache(bytes: ByteArray, filename: String): File? {
        val cacheDir = context.cacheDir
        val file = File(cacheDir, filename)
        return try {
            FileOutputStream(file).use { fos ->
                fos.write(bytes)
            }
            file
        } catch (e: IOException) {
            null
        }
    }
}
