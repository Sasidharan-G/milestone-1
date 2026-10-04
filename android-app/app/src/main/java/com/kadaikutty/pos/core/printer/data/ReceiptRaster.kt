package com.kadaikutty.pos.core.printer.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.kadaikutty.pos.core.printer.domain.PaperWidth
import com.kadaikutty.pos.core.printer.domain.PrintDocument
import java.io.ByteArrayOutputStream

/**
 * Draws a receipt as ONE black-and-white picture exactly as wide as the printer can print, then
 * sends it to the printer in bands of rows. Nothing about the look depends on the printer's own
 * fonts, code pages or character count, so the same bill prints the same on every ESC/POS
 * printer - Bluetooth, USB or LAN, 58, 80 or 112 mm - including the rupee sign, Tamil names and
 * the shop logo.
 *
 * How it stays neat on any printer:
 *  - every line is measured in real pixels with the font in use, never assumed to be "N characters
 *    wide", so a font whose rupee sign or Tamil letters are wider than the rest cannot push an
 *    amount off the edge or onto the next line;
 *  - an amount is drawn against the right margin by its measured width (no padding with spaces),
 *    and when the name and the amount cannot share a line they go on separate lines;
 *  - line heights come from the fonts actually used (fallback fonts for Tamil included), plus a
 *    gap, so lines cannot overlap;
 *  - the picture keeps a margin on both sides, is a whole number of bytes wide, and is no wider than
 *    what the roll's printers can all print (see [PaperWidth.printableDots]);
 *  - it is cut into bands of [BAND_ROWS] rows, so no printer is handed one huge image.
 *
 * Assumes the usual receipt printer: 203 dots per inch. A printer of another resolution still
 * prints the receipt, only at a different physical size.
 */
object ReceiptRaster {
    // A multiple of 8 and of 24, the row grouping some printers work in, so no band ends mid-group.
    const val BAND_ROWS = 120
    const val MARGIN = 8
    private const val ROW_GAP = 3
    private const val SECTION_GAP = 8
    private const val TOP_PADDING = 6
    private const val BOTTOM_PADDING = 12
    private const val INK_BELOW = 150

    enum class Kind { LOGO, TEXT, RULE }

    /** Something on the page: its place, and how to paint it (top-left of the page is 0,0). */
    class Placed internal constructor(
        val left: Int, val top: Int, val width: Int, val height: Int, val kind: Kind,
        internal val paint: (Canvas) -> Unit
    ) {
        val bottom: Int get() = top + height
    }

    /** The finished page. [recycle] when done: the logo bitmap is held until then. */
    class Page internal constructor(val dots: Int, val height: Int, val items: List<Placed>, private val logo: Bitmap?) {
        fun recycle() { logo?.recycle() }
    }

    /** The ESC/POS bytes that print [doc]: raster bands for the whole receipt, a feed, and an optional cut. */
    fun encode(doc: PrintDocument): ByteArray {
        val columns = doc.paperWidth.takeIf { it in 24..64 } ?: 32
        val page = layout(doc, PaperWidth.printableDots(columns))
        try {
            return encodePage(page, doc.cutPaper)
        } finally {
            page.recycle()
        }
    }

    internal fun encodePage(page: Page, cut: Boolean): ByteArray {
        val dots = page.dots
        val rowBytes = dots / 8
        val output = ByteArrayOutputStream()
        output.write(EscPosFormatter.INIT)
        // On 58 and 80 mm the picture is as wide as the head, so no alignment command is sent at all.
        // A 4-inch head has more dots (832) than the safe width used here (768): ask the printer to
        // centre it; a printer that ignores the request prints from the left, which is also fine.
        val centred = dots > 576
        if (centred) output.write(EscPosFormatter.ALIGN_CENTER)
        val band = Bitmap.createBitmap(dots, BAND_ROWS, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(band)
            val pixels = IntArray(dots)
            var top = 0
            while (top < page.height) {
                val rows = minOf(BAND_ROWS, page.height - top)
                canvas.drawColor(Color.WHITE)
                canvas.save()
                canvas.translate(0f, -top.toFloat())
                for (item in page.items) if (item.bottom > top && item.top < top + rows) item.paint(canvas)
                canvas.restore()
                // GS v 0 m xL xH yL yH d1..dk: print a raster bit image, normal density.
                output.write(byteArrayOf(0x1D, 0x76, 0x30, 0,
                    (rowBytes and 255).toByte(), (rowBytes shr 8).toByte(),
                    (rows and 255).toByte(), (rows shr 8).toByte()))
                for (y in 0 until rows) {
                    band.getPixels(pixels, 0, dots, 0, y, dots, 1)
                    for (x in 0 until dots step 8) {
                        var bits = 0
                        for (bit in 0..7) {
                            if (Color.red(pixels[x + bit]) < INK_BELOW) bits = bits or (0x80 shr bit)
                        }
                        output.write(bits)
                    }
                }
                top += rows
            }
        } finally {
            band.recycle()
        }
        if (centred) output.write(EscPosFormatter.ALIGN_LEFT)
        output.write(byteArrayOf(10, 10, 10, 10))
        if (cut) output.write(EscPosFormatter.FEED_AND_CUT)
        return output.toByteArray()
    }

    /** Lays the receipt out on a page [dots] wide; nothing is drawn until the page is encoded. */
    fun layout(doc: PrintDocument, dots: Int): Page {
        require(dots % 8 == 0 && dots >= 128) { "printable width must be whole bytes and at least 128 dots" }
        val columns = doc.paperWidth.takeIf { it in 24..64 } ?: 32
        val contentWidth = dots - 2 * MARGIN
        val size = fittingTextSize(columns, contentWidth)
        val regular = textPaint(false, size)
        val bold = textPaint(true, size)
        val heading = textPaint(true, size * 1.2f)
        val items = ArrayList<Placed>()
        var y = TOP_PADDING

        fun block(text: String, paint: TextPaint, align: Layout.Alignment) {
            val clean = ReceiptLayout.sanitize(text)
            if (clean.isBlank()) return
            val layout = staticLayout(clean, paint, contentWidth, align)
            val top = y
            items += Placed(MARGIN, top, contentWidth, layout.height, Kind.TEXT) { canvas ->
                canvas.save(); canvas.translate(MARGIN.toFloat(), top.toFloat()); layout.draw(canvas); canvas.restore()
            }
            y += layout.height + ROW_GAP
        }

        // "name .......... amount": on one line when they fit side by side, otherwise stacked.
        fun pair(left: String, right: String, paint: TextPaint) {
            val l = ReceiptLayout.sanitize(left)
            val r = ReceiptLayout.sanitize(right)
            if (r.isBlank()) { block(l, paint, Layout.Alignment.ALIGN_NORMAL); return }
            if (l.isBlank()) { block(r, paint, Layout.Alignment.ALIGN_OPPOSITE); return }
            val rightWidth = Math.ceil(Layout.getDesiredWidth(r, paint).toDouble()).toInt() + 2
            val gap = (Math.ceil(paint.measureText(" ").toDouble()).toInt() * 2).coerceAtLeast(8)
            val room = contentWidth - rightWidth - gap
            if (rightWidth <= contentWidth * 6 / 10 && room >= contentWidth / 4) {
                val leftLayout = staticLayout(l, paint, room, Layout.Alignment.ALIGN_NORMAL)
                if (leftLayout.lineCount == 1) {
                    val rightLayout = staticLayout(r, paint, rightWidth, Layout.Alignment.ALIGN_OPPOSITE)
                    val height = maxOf(leftLayout.height, rightLayout.height)
                    val top = y
                    items += Placed(MARGIN, top, contentWidth, height, Kind.TEXT) { canvas ->
                        canvas.save(); canvas.translate(MARGIN.toFloat(), top.toFloat()); leftLayout.draw(canvas); canvas.restore()
                        canvas.save(); canvas.translate((MARGIN + contentWidth - rightWidth).toFloat(), top.toFloat()); rightLayout.draw(canvas); canvas.restore()
                    }
                    y += height + ROW_GAP
                    return
                }
            }
            block(l, paint, Layout.Alignment.ALIGN_NORMAL)
            block(r, paint, Layout.Alignment.ALIGN_OPPOSITE)
        }

        fun rule() {
            y += 2
            val top = y
            val line = Paint().apply { color = Color.BLACK; strokeWidth = 2f; isAntiAlias = false; style = Paint.Style.STROKE; pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f) }
            items += Placed(MARGIN, top, contentWidth, 8, Kind.RULE) { canvas ->
                canvas.drawLine(MARGIN.toFloat(), top + 4f, (MARGIN + contentWidth).toFloat(), top + 4f, line)
            }
            y += 8 + 2
        }

        // The logo: a good size on the paper without wasting paper or head on a giant picture.
        val logo = LogoBitmap.prepare(doc.logoPath, contentWidth * 62 / 100, minOf(contentWidth * 36 / 100, 200))
        if (logo != null) {
            val left = MARGIN + (contentWidth - logo.width) / 2
            val top = y
            items += Placed(left, top, logo.width, logo.height, Kind.LOGO) { canvas -> canvas.drawBitmap(logo, left.toFloat(), top.toFloat(), null) }
            y += logo.height + SECTION_GAP
        }

        block(doc.title, heading, Layout.Alignment.ALIGN_CENTER)
        y += ROW_GAP
        doc.headers.forEach { block(it, regular, Layout.Alignment.ALIGN_NORMAL) }
        rule()
        doc.lines.forEach {
            block(it.name, bold, Layout.Alignment.ALIGN_NORMAL)
            pair("${it.quantityText} x ${it.price}", it.total, regular)
        }
        rule()
        doc.totals.forEach { (label, value) -> pair(label, value, bold) }
        if (doc.footer.isNotBlank()) {
            y += SECTION_GAP
            block(doc.footer, regular, Layout.Alignment.ALIGN_CENTER)
        }
        y += BOTTOM_PADDING
        // A whole number of 24-row groups, like the bands, so the last band is as tidy as the others.
        return Page(dots, (y + 23) / 24 * 24, items, logo)
    }

    private fun textPaint(bold: Boolean, size: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        typeface = if (bold) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.MONOSPACE
        textSize = size
    }

    /**
     * The font size at which [columns] characters of the monospace font fill [width] pixels, so the
     * receipt keeps the familiar "32 / 48 / 64 characters across" look - measured with the real
     * font, and shrunk further if the bold face (or this phone's font) turns out wider.
     */
    internal fun fittingTextSize(columns: Int, width: Int): Float {
        val sample = "M".repeat(columns)
        val regular = textPaint(false, 20f)
        val bold = textPaint(true, 20f)
        var size = 20f
        repeat(6) {
            regular.textSize = size
            bold.textSize = size
            val measured = maxOf(regular.measureText(sample), bold.measureText(sample))
            if (measured <= 0f) return size
            size *= width / measured
        }
        size = (Math.floor(size * 2.0) / 2.0).toFloat().coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)
        while (size > MIN_TEXT_SIZE) {
            regular.textSize = size
            bold.textSize = size
            if (maxOf(regular.measureText(sample), bold.measureText(sample)) <= width) break
            size -= 0.5f
        }
        return size
    }

    private fun staticLayout(text: CharSequence, paint: TextPaint, width: Int, align: Layout.Alignment): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setAlignment(align)
            .setIncludePad(false)
            .setLineSpacing(0f, 1f)
            .apply {
                // Tamil and the rupee sign come from fallback fonts that are taller than the monospace
                // one; without this their lines are cut off or run into the line above.
                if (Build.VERSION.SDK_INT >= 28) setUseLineSpacingFromFallbacks(true)
            }
            .build()

    private const val MIN_TEXT_SIZE = 10f
    private const val MAX_TEXT_SIZE = 34f
}
