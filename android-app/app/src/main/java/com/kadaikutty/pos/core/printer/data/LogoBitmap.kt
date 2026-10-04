package com.kadaikutty.pos.core.printer.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.io.File

/**
 * The shop logo as the pure black-and-white picture burnt onto the receipt: cropped to its
 * artwork, scaled to fit the box it is given, and converted with [LogoBinarizer]. Null when there
 * is no logo, it cannot be read, or it is blank - a receipt then just prints without it; a bad
 * logo file must never stop a bill from printing.
 */
internal object LogoBitmap {

    fun prepare(path: String, maxWidth: Int, maxHeight: Int): Bitmap? {
        if (path.isBlank() || maxWidth < MIN_SIZE || maxHeight < MIN_SIZE) return null
        return try {
            val file = File(path)
            if (!file.isFile) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            // A logo picked from a gallery can be a 12-megapixel photo; decode only what the print needs.
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxWidth * 2 && bounds.outHeight / (sample * 2) >= maxHeight * 2) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val source = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
            try { render(source, maxWidth, maxHeight) } finally { source.recycle() }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private fun render(source: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap? {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val analysis = LogoBinarizer.analyze(pixels, width, height)
        val box = analysis.bounds ?: return null
        val artworkWidth = box[2] - box[0]
        val artworkHeight = box[3] - box[1]
        // Fit the box without distorting; a very small logo may grow, but only to twice its size.
        val scale = minOf(maxWidth.toFloat() / artworkWidth, maxHeight.toFloat() / artworkHeight, MAX_GROWTH)
        val targetWidth = Math.round(artworkWidth * scale).coerceIn(1, maxWidth)
        val targetHeight = Math.round(artworkHeight * scale).coerceIn(1, maxHeight)

        // Alpha is kept (not painted onto white yet) so transparent areas stay bare paper after inversion.
        val scaled = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val dots: BooleanArray
        try {
            Canvas(scaled).drawBitmap(source, Rect(box[0], box[1], box[2], box[3]), Rect(0, 0, targetWidth, targetHeight), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            val scaledPixels = IntArray(targetWidth * targetHeight)
            scaled.getPixels(scaledPixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
            dots = LogoBinarizer.toDots(scaledPixels, targetWidth, targetHeight, analysis.invert)
        } finally {
            scaled.recycle()
        }
        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        result.setPixels(IntArray(dots.size) { if (dots[it]) Color.BLACK else Color.WHITE }, 0, targetWidth, 0, 0, targetWidth, targetHeight)
        return result
    }

    private const val MIN_SIZE = 16
    private const val MAX_GROWTH = 2f
}
