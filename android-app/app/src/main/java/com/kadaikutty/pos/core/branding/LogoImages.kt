package com.kadaikutty.pos.core.branding

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/** Turns a picture the owner picked into the logo file kept on the phone, and into the copy sent to the cloud. */
object LogoImages {
    /** The longest side of the saved logo, in pixels: sharp on any bill, small to store and to send. */
    const val MAX_SIDE = 512

    /** The biggest picture sent to the cloud. Anything larger goes as a JPEG: a proxy on the way may refuse a body over 1 MB. */
    const val MAX_UPLOAD_BYTES = 700 * 1024

    /**
     * Reads the picture at [uri], scales it to fit [MAX_SIDE] and saves it as a PNG in the app's logos
     * folder. Returns the file's path, or null when the picture cannot be read.
     *
     * A phone photo can be tens of megapixels, so it is decoded already reduced (never below [MAX_SIDE]
     * on its long side): decoding it whole could run a low-memory phone out of memory.
     */
    fun saveScaled(context: Context, uri: Uri): String? {
        var source: Bitmap? = null
        var scaled: Bitmap? = null
        try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
            source = decoded
            val scale = minOf(MAX_SIDE.toFloat() / decoded.width, MAX_SIDE.toFloat() / decoded.height)
            val width = (decoded.width * scale).toInt().coerceAtLeast(1)
            val height = (decoded.height * scale).toInt().coerceAtLeast(1)
            val result = Bitmap.createScaledBitmap(decoded, width, height, true)
            scaled = result
            val directory = File(context.filesDir, "logos").apply { mkdirs() }
            val file = File(directory, "shop_logo_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { result.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return file.absolutePath
        } catch (e: Exception) {
            return null
        } catch (e: OutOfMemoryError) {
            return null
        } finally {
            if (scaled !== source) scaled?.recycle()
            source?.recycle()
        }
    }

    /**
     * [picture] as it is when it is small enough for the cloud, otherwise the same picture as a JPEG on
     * white (transparent areas print as white anyway), shrunk step by step until it fits. Falls back to
     * the original if it cannot be decoded, and lets the server have its say.
     */
    fun fitForUpload(picture: ByteArray): ByteArray {
        if (picture.size <= MAX_UPLOAD_BYTES) return picture
        var bitmap: Bitmap = try {
            BitmapFactory.decodeByteArray(picture, 0, picture.size)
        } catch (e: OutOfMemoryError) {
            null
        } ?: return picture
        try {
            repeat(8) {
                val flat = onWhite(bitmap)
                try {
                    for (quality in intArrayOf(90, 80, 70)) {
                        val out = ByteArrayOutputStream()
                        flat.compress(Bitmap.CompressFormat.JPEG, quality, out)
                        if (out.size() <= MAX_UPLOAD_BYTES) return out.toByteArray()
                    }
                } finally {
                    flat.recycle()
                }
                val smaller = Bitmap.createScaledBitmap(bitmap, (bitmap.width * 3 / 4).coerceAtLeast(1), (bitmap.height * 3 / 4).coerceAtLeast(1), true)
                if (smaller !== bitmap) bitmap.recycle()
                bitmap = smaller
            }
            return picture
        } catch (e: OutOfMemoryError) {
            return picture
        } finally {
            bitmap.recycle()
        }
    }

    private fun onWhite(source: Bitmap): Bitmap {
        val flat = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, 0f, 0f, null)
        }
        return flat
    }
}
