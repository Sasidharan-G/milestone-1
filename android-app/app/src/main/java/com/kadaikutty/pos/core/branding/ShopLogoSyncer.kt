package com.kadaikutty.pos.core.branding

import android.content.Context
import com.kadaikutty.pos.core.auth.SessionStore
import com.kadaikutty.pos.core.network.BackendApiClient
import com.kadaikutty.pos.core.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's one entry point for keeping the shop logo in step with the cloud: the real cloud API
 * and this phone's preferences and files plugged into [ShopLogoSync].
 */
@Singleton
class ShopLogoSyncer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backendApi: BackendApiClient,
    private val sessionStore: SessionStore,
    private val appPreferences: AppPreferences,
) {
    private val sync = ShopLogoSync(CloudApi(), LocalFiles())

    /** [profile] is the "shopProfile" object the server returned (null when it returned none). */
    suspend fun sync(profile: JSONObject?): ShopLogoSync.Outcome {
        val key = profile?.optString("logoObjectKey").orEmpty()
        val hasLogo = key.isNotBlank() && key != "null"
        return sync.sync(profile?.optLong("logoUpdatedAtEpochMs", 0L) ?: 0L, hasLogo)
    }

    private inner class CloudApi : ShopLogoCloud {
        private suspend fun token(): String =
            sessionStore.activeSession.first()?.accessToken?.takeIf { it.isNotBlank() } ?: throw IOException("Sign in again to sync the shop logo")

        override suspend fun upload(picture: ByteArray): Long {
            // The copy in the cloud is kept small (see LogoImages.MAX_UPLOAD_BYTES); the file on the phone is not touched.
            val toSend = withContext(Dispatchers.Default) { LogoImages.fitForUpload(picture) }
            return versionOf(backendApi.uploadShopLogo(token(), toSend))
        }

        override suspend fun download(): ByteArray? = backendApi.downloadShopLogo(token())

        override suspend fun remove(): Long = versionOf(backendApi.deleteShopLogo(token()))

        private fun versionOf(reply: JSONObject): Long = reply.optJSONObject("shopProfile")?.optLong("logoUpdatedAtEpochMs", 0L) ?: 0L
    }

    private inner class LocalFiles : ShopLogoLocal {
        private val directory get() = File(context.filesDir, "logos")

        override suspend fun state() = ShopLogoState(
            path = appPreferences.shopLogoPath.first(),
            uploadedPath = appPreferences.shopLogoUploadedPath.first(),
            version = appPreferences.shopLogoVersion.first()
        )

        override suspend fun read(path: String): ByteArray? = withContext(Dispatchers.IO) {
            runCatching { File(path).takeIf { it.isFile && it.length() > 0 }?.readBytes() }.getOrNull()
        }

        override suspend fun adopt(picture: ByteArray, version: Long) {
            val before = state()
            val file = withContext(Dispatchers.IO) {
                directory.mkdirs()
                val target = File(directory, "shop_logo_cloud_$version.png")
                val temporary = File(directory, "${target.name}.tmp")
                temporary.writeBytes(picture)
                if (!temporary.renameTo(target)) { target.delete(); check(temporary.renameTo(target)) { "Could not save the logo" } }
                target
            }
            appPreferences.saveShopLogoSync(file.absolutePath, file.absolutePath, version)
            deleteLogoFiles(before.path, before.uploadedPath, except = file.absolutePath)
        }

        override suspend fun markUploaded(path: String, version: Long) {
            val before = state()
            appPreferences.markShopLogoUploaded(path, version)
            deleteLogoFiles(before.uploadedPath, except = path)
        }

        override suspend fun clear(version: Long) {
            val before = state()
            appPreferences.saveShopLogoSync("", "", version)
            deleteLogoFiles(before.path, before.uploadedPath, except = "")
        }

        /**
         * A replaced or removed logo's old file is no use to anyone; do not let them pile up. Only the
         * files this phone knew about are touched, so a picture the owner has just chosen but not
         * saved yet can never be deleted from under them.
         */
        private suspend fun deleteLogoFiles(vararg paths: String, except: String) = withContext(Dispatchers.IO) {
            paths.filter { it.isNotBlank() && it != except }.map { File(it) }
                .filter { it.parentFile?.absolutePath == directory.absolutePath }
                .forEach { it.delete() }
        }
    }
}
