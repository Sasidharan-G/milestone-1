package com.kadaikutty.pos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kadaikutty.pos.core.printer.data.ReceiptEncoder
import com.kadaikutty.pos.core.printer.data.ReceiptRaster
import com.kadaikutty.pos.core.printer.domain.BillItem
import com.kadaikutty.pos.core.printer.domain.BillReceipt
import com.kadaikutty.pos.core.printer.domain.PaperWidth
import com.kadaikutty.pos.core.printer.domain.PrintDocument
import com.kadaikutty.pos.core.printer.domain.PrintLine
import com.kadaikutty.pos.support.VirtualPrinter
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Thermal printing, proven without a printer. Every receipt is encoded to ESC/POS bytes, read back
 * by a [VirtualPrinter] that rebuilds the paper, and checked for what would go wrong on a real
 * roll: a command no printer knows, a picture wider than the roll, ink in the margin (clipped by
 * the head), one line drawn over another, a logo off-centre or a black slab, a crash on a bad
 * logo or a very long bill. Run for 58, 80 and 112 mm. The paper pictures are left in the app's
 * cache (receipt-print-*.png) for looking at.
 */
@RunWith(AndroidJUnit4::class)
class ReceiptPrintingTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val made = mutableListOf<File>()
    private val rolls = listOf(32, 48, 64)

    @After fun removeTempFiles() { made.forEach { it.delete() } }

    // ---------------------------------------------------------------- helpers

    private fun logoFile(name: String, width: Int, height: Int, draw: (Canvas, Int, Int) -> Unit): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap), width, height)
        val file = File(context.cacheDir, name).also { made += it }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file.absolutePath
    }

    private val bannerLogo get() = logoFile("print-banner.png", 1200, 300) { c, w, h ->
        c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        c.drawRoundRect(RectF(40f, 40f, w - 40f, h - 40f), 40f, 40f, p)
        p.color = Color.WHITE; p.textSize = 150f; p.textAlign = Paint.Align.CENTER
        c.drawText("KAVI STORES", w / 2f, h / 2f + 50f, p)
    }

    private val darkBadgeLogo get() = logoFile("print-dark.png", 512, 512) { c, w, h ->
        c.drawColor(Color.rgb(10, 30, 130))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        c.drawCircle(w / 2f, h / 2f, 120f, p)
    }

    private val transparentLogo get() = logoFile("print-clear.png", 600, 400) { c, w, h ->
        // fully transparent except a black ring in the middle of a lot of empty margin
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 30f }
        c.drawCircle(w / 2f, h / 2f, 90f, p)
    }

    private fun typicalDoc(columns: Int, items: Int = 4, logo: String = "", cut: Boolean = false): PrintDocument {
        val lines = (1..items).map { BillItem("Product number $it", "$it", "₹${10 * it}.50", "₹${it * 10 * it}.50") }
        return BillReceipt.document(
            shopName = "Kavi Stores", shopAddress = "12 Main Road, Anna Nagar, Chennai 600040",
            billNumber = "2B445C7A-0004", date = "03/10/26 11:02", customerName = "kavitha",
            items = lines, subtotal = "₹1,220.00", discount = "₹20.00", grandTotal = "₹1,200.00",
            paperWidth = columns, shopPhone = "8754511678", gstNumber = "27ABCDE1234F2Z5", paymentMode = "CASH",
            gstRows = listOf("GST 5% on ₹1,142.86" to "₹57.14"), logoPath = logo
        ).copy(cutPaper = cut)
    }

    private fun keep(name: String, printer: VirtualPrinter) {
        val paper = printer.paper()
        File(context.cacheDir, "receipt-print-$name.png").outputStream().use { paper.compress(Bitmap.CompressFormat.PNG, 100, it) }
        paper.recycle()
    }

    private fun isInk(bitmap: Bitmap, x: Int, y: Int) = Color.red(bitmap.getPixel(x, y)) == 0

    /** Everything that must hold for ANY receipt, ANY roll. */
    private fun assertNeat(doc: PrintDocument, label: String): VirtualPrinter {
        val bytes = ReceiptEncoder.encode(doc)
        val printer = VirtualPrinter(bytes) // throws on any command a printer would not know
        val columns = doc.paperWidth.takeIf { it in 24..64 } ?: 32
        val dots = PaperWidth.printableDots(columns)

        assertEquals("$label: starts by resetting the printer", "INIT", printer.trace.first())
        assertTrue("$label: ends by feeding paper", printer.trace.takeLast(if (doc.cutPaper) 5 else 4).let { tail ->
            tail.count { it == "LF" } >= 3 && (!doc.cutPaper || tail.last() == "CUT")
        })
        assertTrue("$label: a band is wider than the paper", printer.bands.all { it.widthBytes * 8 == dots })
        assertTrue("$label: a band is too tall", printer.bands.all { it.rows in 1..ReceiptRaster.BAND_ROWS })
        assertEquals("$label: width in dots", dots, printer.widthDots)
        assertEquals("$label: whole bytes", 0, printer.widthDots % 8)

        val page = ReceiptRaster.layout(doc, dots)
        val paper = printer.paper()
        try {
            assertEquals("$label: the paper is as tall as the layout", page.height, printer.heightRows)
            val margin = ReceiptRaster.MARGIN
            val ordered = page.items.sortedBy { it.top }
            ordered.zipWithNext { a, b -> assertTrue("$label: line at ${a.top} runs into the next at ${b.top}", a.bottom <= b.top) }
            page.items.forEach {
                assertTrue("$label: item at ${it.top} leaves the margins", it.left >= margin && it.left + it.width <= dots - margin)
                assertTrue("$label: item at ${it.top} leaves the page", it.top >= 0 && it.bottom <= page.height)
            }
            // The head cannot burn the very edge reliably: nothing may be drawn there.
            for (y in 0 until paper.height) {
                for (x in 0 until margin - 1) assertFalse("$label: ink in the left margin at $x,$y", isInk(paper, x, y))
                for (x in dots - margin + 1 until dots) assertFalse("$label: ink in the right margin at $x,$y", isInk(paper, x, y))
            }
            // Nothing is drawn outside the space its own line was given: that is what an overlap is.
            val covered = BooleanArray(paper.height)
            page.items.forEach { for (y in it.top until it.bottom) covered[y] = true }
            for (y in 0 until paper.height) {
                if (covered[y]) continue
                for (x in 0 until dots) assertFalse("$label: ink at $x,$y between lines (a line spilled out of its space)", isInk(paper, x, y))
            }
            // Every line that has something to say actually put ink on the paper.
            page.items.filter { it.kind != ReceiptRaster.Kind.RULE }.forEach { item ->
                var any = false
                loop@ for (y in item.top until item.bottom) for (x in item.left until item.left + item.width) if (isInk(paper, x, y)) { any = true; break@loop }
                assertTrue("$label: the ${item.kind} at ${item.top} printed nothing", any)
            }
        } finally {
            paper.recycle()
            page.recycle()
        }
        return printer
    }

    // ---------------------------------------------------------------- tests

    @Test fun aTypicalBillPrintsNeatlyOnEveryRoll() {
        for (columns in rolls) keep("bill-${PaperWidth.millimetres(columns)}mm", assertNeat(typicalDoc(columns), "bill $columns"))
    }

    @Test fun theWidthIsExactlyWhatEachRollCanPrint() {
        assertEquals(384, VirtualPrinter(ReceiptEncoder.encode(typicalDoc(32))).widthDots)
        assertEquals(576, VirtualPrinter(ReceiptEncoder.encode(typicalDoc(48))).widthDots)
        assertEquals(768, VirtualPrinter(ReceiptEncoder.encode(typicalDoc(64))).widthDots)
    }

    @Test fun onlyTheFourInchRollAsksThePrinterToCentre() {
        fun alignments(columns: Int) = VirtualPrinter(ReceiptEncoder.encode(typicalDoc(columns))).trace.filter { it.startsWith("ALIGN") }
        assertEquals("58 mm: the picture is as wide as the head, no command needed", emptyList<String>(), alignments(32))
        assertEquals("80 mm: same", emptyList<String>(), alignments(48))
        assertEquals("112 mm: centre, then back to normal", listOf("ALIGN 1", "ALIGN 0"), alignments(64))
    }

    @Test fun theHeightIsAWholeNumberOfRowGroups() {
        for (columns in rolls) {
            val printer = VirtualPrinter(ReceiptEncoder.encode(typicalDoc(columns, items = 30)))
            assertEquals("height ${printer.heightRows} on $columns", 0, printer.heightRows % 24)
            assertEquals("a band is a whole number of groups too", 0, ReceiptRaster.BAND_ROWS % 24)
        }
    }

    @Test fun anUnknownPaperSizeFallsBackToTheNarrowRoll() {
        assertEquals(384, VirtualPrinter(ReceiptEncoder.encode(typicalDoc(99))).widthDots)
        assertEquals(384, VirtualPrinter(ReceiptEncoder.encode(typicalDoc(0))).widthDots)
    }

    @Test fun theLogoIsCentredAtTheTopOnEveryRoll() {
        val logo = bannerLogo
        for (columns in rolls) {
            val doc = typicalDoc(columns, logo = logo)
            val printer = assertNeat(doc, "logo $columns")
            keep("logo-${PaperWidth.millimetres(columns)}mm", printer)
            val dots = PaperWidth.printableDots(columns)
            val page = ReceiptRaster.layout(doc, dots)
            try {
                val first = page.items.minByOrNull { it.top }!!
                assertEquals("the logo comes first", ReceiptRaster.Kind.LOGO, first.kind)
                assertTrue("centred: left ${first.left} width ${first.width} on $dots", Math.abs(first.left + first.width / 2 - dots / 2) <= 1)
                assertTrue("not wider than 62% of the paper", first.width <= (dots - 2 * ReceiptRaster.MARGIN) * 62 / 100)
                assertTrue("not taller than 200 dots", first.height <= 200)
                val next = page.items.filter { it.top > first.top }.minByOrNull { it.top }!!
                assertTrue("a gap under the logo", next.top - first.bottom >= 4)
            } finally { page.recycle() }
        }
    }

    @Test fun aLogoOnADarkBackgroundIsPrintedInvertedNotAsABlackSlab() {
        // A white disc on a dark blue square (like a launcher icon): the blue is the background, so it must
        // stay bare paper and the disc is what burns - not the whole square.
        val doc = typicalDoc(48, logo = darkBadgeLogo)
        val printer = assertNeat(doc, "dark logo")
        keep("logo-dark-80mm", printer)
        val page = ReceiptRaster.layout(doc, PaperWidth.printableDots(48))
        val paper = printer.paper()
        try {
            val logo = page.items.first { it.kind == ReceiptRaster.Kind.LOGO }
            val cx = logo.left + logo.width / 2
            val cy = logo.top + logo.height / 2
            assertTrue("the disc burns in the middle", isInk(paper, cx, cy))
            for ((x, y) in listOf(logo.left + 1 to logo.top + 1, logo.left + logo.width - 2 to logo.top + 1, logo.left + 1 to logo.top + logo.height - 2, logo.left + logo.width - 2 to logo.top + logo.height - 2)) {
                assertFalse("the corner ($x,$y) is the dark background and must stay paper", isInk(paper, x, y))
            }
        } finally { paper.recycle(); page.recycle() }
    }

    @Test fun aTransparentLogoIsCroppedToItsArtwork() {
        val doc = typicalDoc(48, logo = transparentLogo)
        assertNeat(doc, "transparent logo")
        val page = ReceiptRaster.layout(doc, 576)
        try {
            val logo = page.items.first { it.kind == ReceiptRaster.Kind.LOGO }
            // 600x400 with only a 210-dot ring: the empty margin is cropped, so the ring fills the logo box.
            assertTrue("cropped to a roughly square ring, was ${logo.width}x${logo.height}", Math.abs(logo.width - logo.height) <= 6)
        } finally { page.recycle() }
    }

    @Test fun veryLargeAndVerySmallLogosAreHandled() {
        val huge = logoFile("print-huge.png", 3000, 3000) { c, w, h ->
            c.drawColor(Color.WHITE)
            c.drawCircle(w / 2f, h / 2f, 1200f, Paint().apply { color = Color.BLACK })
        }
        val tiny = logoFile("print-tiny.png", 12, 12) { c, _, _ ->
            c.drawColor(Color.WHITE)
            c.drawCircle(6f, 6f, 4f, Paint().apply { color = Color.BLACK })
        }
        for (path in listOf(huge, tiny)) for (columns in rolls) {
            val doc = typicalDoc(columns, logo = path)
            assertNeat(doc, "size ${File(path).name} $columns")
            val page = ReceiptRaster.layout(doc, PaperWidth.printableDots(columns))
            try {
                val logo = page.items.first { it.kind == ReceiptRaster.Kind.LOGO }
                assertTrue(logo.width <= (page.dots - 2 * ReceiptRaster.MARGIN) * 62 / 100 && logo.height <= 200)
                if (path == tiny) assertTrue("a tiny logo grows at most to double", logo.width <= 24)
            } finally { page.recycle() }
        }
    }

    @Test fun aMissingOrBrokenLogoNeverStopsTheBillPrinting() {
        val notAPicture = File(context.cacheDir, "print-broken.png").also { made += it; it.writeText("this is not a picture") }
        val empty = File(context.cacheDir, "print-empty.png").also { made += it; it.writeBytes(ByteArray(0)) }
        val blank = logoFile("print-blank.png", 200, 200) { c, _, _ -> c.drawColor(Color.WHITE) }
        for (path in listOf("", File(context.cacheDir, "no-such-file.png").absolutePath, notAPicture.absolutePath, empty.absolutePath, blank)) {
            val doc = typicalDoc(32, logo = path)
            assertNeat(doc, "bad logo '$path'")
            val page = ReceiptRaster.layout(doc, 384)
            try { assertTrue("no logo item for '$path'", page.items.none { it.kind == ReceiptRaster.Kind.LOGO }) } finally { page.recycle() }
        }
    }

    @Test fun aSolidColourPictureHasNothingToPrintSoNoLogoSpaceIsWasted() {
        val solidBlack = logoFile("print-solid-black.png", 40, 40) { c, _, _ -> c.drawColor(Color.BLACK) }
        val solidNavy = logoFile("print-solid-navy.png", 40, 40) { c, _, _ -> c.drawColor(Color.rgb(10, 30, 130)) }
        for (path in listOf(solidBlack, solidNavy)) {
            val doc = typicalDoc(32, logo = path)
            assertNeat(doc, "solid ${File(path).name}")
            val page = ReceiptRaster.layout(doc, 384)
            try { assertTrue("a picture with nothing in it prints no logo", page.items.none { it.kind == ReceiptRaster.Kind.LOGO }) } finally { page.recycle() }
        }
    }

    @Test fun longNamesLongAmountsAndTamilNeverClipOrOverlap() {
        val unbroken = "A".repeat(140)
        val hugeAmount = "₹" + "9".repeat(36) + ".99"
        val tamil = "தமிழ் நாடு பாரம்பரிய அரிசி மற்றும் மசாலா பொருட்கள் ஒரு கிலோ"
        for (columns in rolls) {
            val doc = PrintDocument(
                title = "ஸ்ரீ கடைக்குட்டி மளிகை மற்றும் பல்பொருள் அங்காடி " + "Kavi Stores Supermarket and Departmental",
                headers = listOf("12 Main Road, Anna Nagar, Chennai 600040, Tamil Nadu, India, Near the Big Temple", "GSTIN: 27ABCDE1234F2Z5", "Customer: " + tamil),
                lines = listOf(
                    PrintLine(unbroken, 1, "₹1.00", "₹1.00", "1"),
                    PrintLine(tamil, 2, "₹120.00", "₹240.00", "2"),
                    PrintLine("Rice", 1, hugeAmount, hugeAmount, "12.500"),
                    PrintLine("Oil", 1, "₹5.00", "₹5.00", "1"),
                ),
                totals = listOf("Subtotal" to hugeAmount, "A very long label that goes on and on and on" to "₹1.00", "TOTAL" to "₹1,200.00"),
                footer = "நன்றி! மீண்டும் வருக! Thank you, please visit again and again and again",
                paperWidth = columns
            )
            keep("stress-${PaperWidth.millimetres(columns)}mm", assertNeat(doc, "stress $columns"))
        }
    }

    @Test fun aBillWithHundredsOfItemsIsCutIntoBandsAndCompletes() {
        val doc = typicalDoc(48, items = 400)
        val started = System.currentTimeMillis()
        val printer = assertNeat(doc, "400 items")
        val seconds = (System.currentTimeMillis() - started) / 1000.0
        assertTrue("took ${seconds}s", seconds < 40)
        assertTrue("many bands", printer.bands.size > 20)
        assertEquals("every band but the last is full", true, printer.bands.dropLast(1).all { it.rows == ReceiptRaster.BAND_ROWS })
    }

    @Test fun anEmptyReceiptStillPrintsInsteadOfFailing() {
        val doc = PrintDocument("", emptyList(), emptyList(), emptyList(), "", paperWidth = 48)
        val printer = VirtualPrinter(ReceiptEncoder.encode(doc))
        assertTrue(printer.heightRows > 0)
    }

    @Test fun theSameBillAlwaysGivesTheSameBytes() {
        val logo = bannerLogo
        assertArrayEquals(ReceiptEncoder.encode(typicalDoc(48, logo = logo)), ReceiptEncoder.encode(typicalDoc(48, logo = logo)))
    }

    @Test fun theCutterIsOnlyUsedWhenAsked() {
        assertNull(VirtualPrinter(ReceiptEncoder.encode(typicalDoc(32, cut = false))).trace.firstOrNull { it == "CUT" })
        assertNotNull(VirtualPrinter(ReceiptEncoder.encode(typicalDoc(32, cut = true))).trace.firstOrNull { it == "CUT" })
    }

    @Test fun controlCharactersInTextCannotDriveThePrinter() {
        val doc = PrintDocument("Shop\u001b@\u001dV\u0000", listOf("Name\u0007\u0000"), listOf(PrintLine("Item\u001b@", 1, "₹1.00", "₹1.00")), listOf("TOTAL" to "₹1.00"), "Bye\u001b", paperWidth = 32)
        // every byte is part of a raster band or a known command: nothing the shop typed became a command
        assertNeat(doc, "control characters")
    }
}
