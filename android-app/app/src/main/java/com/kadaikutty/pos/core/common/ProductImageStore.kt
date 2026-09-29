package com.kadaikutty.pos.core.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Where a product's photo lives on this device, and how a picked/captured image gets there.
 *
 * A product photo is never kept as a full-resolution camera file: a modern phone camera produces
 * 10-40MB images, and decoding one straight to a [Bitmap] on the wrong thread is exactly the kind
 * of thing that freezes the UI for several seconds. Every function here that touches image bytes
 * runs on [Dispatchers.IO] and downsamples before it ever allocates a full bitmap.
 *
 * The saved file doubles as the "is this synced yet" signal: [ProductImageUploadWorker] uploads it
 * and then the product's `imageUrl` column is set, so there is no separate "pending" flag to keep
 * in sync with reality.
 */
object ProductImageStore {
    private const val MAX_DIMENSION_PX = 1024
    private const val JPEG_QUALITY = 82
    private const val DIRECTORY = "product_images"

    fun localFile(context: Context, productId: String): File =
        File(context.filesDir, "$DIRECTORY/$productId.jpg")

    /** A scratch file for a photo not yet attached to a saved product (the create-product flow). */
    fun newTempFile(context: Context): File {
        val dir = File(context.cacheDir, DIRECTORY)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "pending_${System.currentTimeMillis()}_${(0..9999).random()}.jpg")
    }

    /** Downsamples and orientation-corrects [source] into [destination], overwriting it. */
    suspend fun compressInto(context: Context, source: Uri, destination: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext false

            var sampleSize = 1
            while (bounds.outWidth / (sampleSize * 2) >= MAX_DIMENSION_PX && bounds.outHeight / (sampleSize * 2) >= MAX_DIMENSION_PX) {
                sampleSize *= 2
            }

            val decoded = context.contentResolver.openInputStream(source)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            } ?: return@withContext false

            val rotationDegrees = runCatching {
                context.contentResolver.openInputStream(source)?.use { ExifInterface(it) }
                    ?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrNull()

            val upright = if (rotationDegrees != null && rotationDegrees != ExifInterface.ORIENTATION_NORMAL) {
                val degrees = when (rotationDegrees) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                if (degrees == 0f) decoded else {
                    val matrix = Matrix().apply { postRotate(degrees) }
                    val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                    if (rotated !== decoded) decoded.recycle()
                    rotated
                }
            } else decoded

            val scale = MAX_DIMENSION_PX.toFloat() / maxOf(upright.width, upright.height)
            val final = if (scale < 1f) {
                val scaled = Bitmap.createScaledBitmap(upright, (upright.width * scale).toInt().coerceAtLeast(1), (upright.height * scale).toInt().coerceAtLeast(1), true)
                if (scaled !== upright) upright.recycle()
                scaled
            } else upright

            destination.parentFile?.mkdirs()
            val temp = File(destination.parentFile, "${destination.name}.tmp")
            FileOutputStream(temp).use { out -> final.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
            final.recycle()
            temp.renameTo(destination)
            true
        } catch (e: Exception) {
            android.util.Log.e("ProductImageStore", "Failed to process product photo", e)
            false
        }
    }

    /** Moves a temp file created for the create-product flow to its permanent, id-keyed home. */
    suspend fun commitTemp(context: Context, temp: File, productId: String): Boolean = withContext(Dispatchers.IO) {
        if (!temp.exists()) return@withContext false
        val dest = localFile(context, productId)
        dest.parentFile?.mkdirs()
        val moved = temp.renameTo(dest)
        if (!moved) {
            // Cross-volume renameTo can fail; fall back to copy+delete.
            runCatching { temp.copyTo(dest, overwrite = true) }.onSuccess { temp.delete() }.isSuccess
        } else true
    }

    suspend fun delete(context: Context, productId: String) = withContext(Dispatchers.IO) {
        localFile(context, productId).delete()
    }
}
