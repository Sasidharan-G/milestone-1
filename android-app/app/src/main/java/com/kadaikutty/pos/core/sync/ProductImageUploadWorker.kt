package com.kadaikutty.pos.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kadaikutty.pos.core.auth.SessionStore
import com.kadaikutty.pos.core.common.ProductImageStore
import com.kadaikutty.pos.core.database.TenantDatabaseManager
import com.kadaikutty.pos.core.network.BackendApiClient
import com.kadaikutty.pos.core.network.BackendApiException
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * Uploads one product's locally-saved photo to cloud storage and records the resulting URL, so
 * every other device (and the cloud copy of this shop's data) picks it up on its next pull.
 *
 * This never runs on the UI thread and never blocks Add/Edit Product: [ProductViewModel] saves the
 * photo to disk and returns immediately, then just asks [SyncScheduler] to enqueue this worker.
 * WorkManager holds it until the network is back and retries with backoff on failure, the same way
 * [BackupWorker] and [SyncWorker] already do for their own uploads.
 */
class ProductImageUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ImageUploadEntryPoint {
        fun tenantDatabaseManager(): TenantDatabaseManager
        fun sessionStore(): SessionStore
        fun backendApiClient(): BackendApiClient
        fun syncManager(): SyncManager
    }

    override suspend fun doWork(): Result {
        val productId = inputData.getString(KEY_PRODUCT_ID) ?: return Result.failure()
        val entry = EntryPointAccessors.fromApplication(applicationContext, ImageUploadEntryPoint::class.java)
        val session = entry.sessionStore().activeSession.first() ?: return Result.success()
        val database = entry.tenantDatabaseManager().getDatabase(session.companyId)

        // The photo was removed (or the product deleted) before this worker got a turn - nothing
        // to upload, and not a failure.
        val localFile = ProductImageStore.localFile(applicationContext, productId)
        if (!localFile.exists()) return Result.success()
        val product = database.masterDao().getProductById(session.companyId, productId) ?: return Result.success()

        var token = session.accessToken
        if (token.isNullOrBlank()) {
            token = runCatching { entry.backendApiClient().autoRecoverSession(forceRefresh = false) }.getOrNull()?.first
        }
        if (token.isNullOrBlank()) return Result.retry()

        return try {
            val publicUrl = entry.backendApiClient().uploadProductImage(token, productId, localFile, "image/jpeg")
            // Re-read: the user may have edited the product while this upload was in flight.
            val latest = database.masterDao().getProductById(session.companyId, productId) ?: return Result.success()
            database.masterDao().updateProduct(latest.copy(imageUrl = publicUrl))
            entry.syncManager().enqueuePartialUpdate("Product", productId, mapOf("imageUrl" to publicUrl))
            Result.success()
        } catch (error: BackendApiException) {
            if (error.retryable || error.statusCode in 500..599 || error.statusCode == 408 || error.statusCode == 429) Result.retry()
            else Result.failure()
        } catch (error: java.io.IOException) {
            Result.retry()
        } catch (error: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val KEY_PRODUCT_ID = "productId"
    }
}
