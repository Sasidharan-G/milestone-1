package com.kadaikutty.pos.core.printer.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.kadaikutty.pos.core.printer.domain.PrintDocument
import java.io.ByteArrayOutputStream

/** Unicode is rasterized so Tamil/product names do not depend on printer code pages. */
object ReceiptEncoder {
    fun encode(doc: PrintDocument): ByteArray {
        val content = listOf(doc.title, doc.footer) + doc.headers +
            doc.lines.flatMap { listOf(it.name, it.quantityText, it.price, it.total) } +
            doc.totals.flatMap { listOf(it.first, it.second) }
        require(content.sumOf { it.length.toLong() } <= 200_000) { "Receipt too large; split the print job" }
        // Unreachable in practice today: Money.toString() always prefixes the rupee sign, which is
        // not ASCII, so any receipt carrying a total or a price fails this check and rasterizes.
        // Reaching the faster text path would mean printing amounts as "Rs." instead, which changes
        // what every receipt looks like and needs a real printer to sign off - so it is left alone
        // rather than quietly switched.
        if (content.all { text -> text.all { it.code < 128 } }) return EscPosFormatter().format(doc)
        val columns = doc.paperWidth.takeIf { it in 24..64 } ?: 32
        // Snapped down to a whole byte: the raster packing below walks the row in steps of 8 and
        // reads pixels[x + 7], so an odd column count (33 -> 396 dots) would run past the array.
        val dots = (columns * 12) / 8 * 8
        val output = ByteArrayOutputStream()
        output.write(EscPosFormatter.INIT)
        val paint = TextPaint().apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }
        fun line(text: String, bold: Boolean = false, center: Boolean = false) {
            paint.typeface = if (bold) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.MONOSPACE
            ReceiptLayout.wrap(text, columns).forEach { part ->
                val layout = StaticLayout.Builder.obtain(part.ifEmpty { " " }, 0, part.ifEmpty { " " }.length, paint, dots - 16)
                    .setAlignment(if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(true).build()
                val bitmap = Bitmap.createBitmap(dots, layout.height + 4, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    canvas.translate(8f, 0f)
                    layout.draw(canvas)
                    val rowBytes = dots / 8
                    val height = bitmap.height
                    output.write(byteArrayOf(0x1D, 0x76, 0x30, 0,
                        (rowBytes and 255).toByte(), (rowBytes shr 8).toByte(),
                        (height and 255).toByte(), (height shr 8).toByte()))
                    val pixels = IntArray(dots)
                    for (y in 0 until height) {
                        bitmap.getPixels(pixels, 0, dots, 0, y, dots, 1)
                        for (x in 0 until dots step 8) {
                            var bits = 0
                            for (bit in 0..7) {
                                if (Color.red(pixels[x + bit]) < 160) bits = bits or (0x80 shr bit)
                            }
                            output.write(bits)
                        }
                    }
                } finally { bitmap.recycle() }
            }
        }
        line(doc.title, bold = true, center = true)
        doc.headers.forEach { line(it) }
        line("-".repeat(columns))
        doc.lines.forEach {
            line(it.name, bold = true)
            ReceiptLayout.columns("${it.quantityText} x ${it.price}", it.total, columns).forEach { row -> line(row) }
        }
        line("-".repeat(columns))
        doc.totals.forEach { (label, value) ->
            ReceiptLayout.columns(label, value, columns).forEach { line(it, bold = true) }
        }
        line(doc.footer, center = true)
        output.write(byteArrayOf(10, 10, 10))
        if (doc.cutPaper) output.write(EscPosFormatter.FEED_AND_CUT)
        return output.toByteArray()
    }
}
