package com.kadaikutty.pos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kadaikutty.pos.core.printer.domain.BillItem
import com.kadaikutty.pos.core.printer.domain.BillReceipt
import com.kadaikutty.pos.core.printer.domain.PrintDocument
import com.kadaikutty.pos.core.sharing.ShareManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The shared receipt picture: a client wanted 58/80/112 mm bills to reach WhatsApp as a picture with the
 * shop logo on top (A4 stays a PDF). Run on a real Android runtime because it draws with Canvas.
 */
@RunWith(AndroidJUnit4::class)
class ReceiptImageTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val share = ShareManager(context)
    private val made = mutableListOf<File>()

    @Before
    fun clean() {
        made.clear()
    }

    @After
    fun removeTempFiles() {
        made.forEach { it.delete() }
    }

    private fun document(paperWidth: Int, items: List<BillItem> = defaultItems): PrintDocument = BillReceipt.document(
        shopName = "Kavi Stores",
        shopAddress = "12 Main Road, Anna Nagar, Chennai",
        billNumber = "2B445C7A-0004",
        date = "03/10/26 11:02",
        customerName = "kavitha",
        items = items,
        subtotal = "₹220.00",
        discount = "₹20.00",
        grandTotal = "₹200.00",
        paperWidth = paperWidth,
        shopPhone = "8754511678",
        paymentMode = "CASH",
    )

    private val defaultItems = listOf(
        BillItem("turmeric powder", "1", "₹120.00", "₹120.00"),
        BillItem("hamam soap", "1", "₹50.00", "₹50.00"),
        BillItem("dairy milk", "1", "₹50.00", "₹50.00"),
    )

    private fun logoFile(name: String, width: Int, height: Int, colour: Int): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(colour)
        val file = File(context.cacheDir, name).also { made += it }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file.absolutePath
    }

    private fun decode(bytes: ByteArray): Bitmap =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw AssertionError("not a readable picture")

    /** Saved where a person can look at it: `adb exec-out run-as com.kadaikutty.pos cat cache/<name>` (not removed by the test). */
    private fun keepForLooking(name: String, bytes: ByteArray) = File(context.cacheDir, name).writeBytes(bytes)

    private fun darkPixelsBelow(bitmap: Bitmap, fromY: Int): Int {
        var dark = 0
        for (y in fromY until bitmap.height) for (x in 0 until bitmap.width) {
            val p = bitmap.getPixel(x, y)
            if (Color.red(p) < 80 && Color.green(p) < 80 && Color.blue(p) < 80) dark++
        }
        return dark
    }

    @Test
    fun narrowReceiptIsA58mmPictureWithTheLogoOnTop() {
        val logo = logoFile("test-logo.png", 300, 150, Color.MAGENTA)
        val bytes = share.generateReceiptImage(document(paperWidth = 32), logo)
        keepForLooking("receipt-preview-58-logo.png", bytes)
        val picture = decode(bytes)

        assertEquals(560, picture.width)
        // 300x150 logo, centred, 24 px from the top: its middle is the middle of the logo.
        assertEquals(Color.MAGENTA, picture.getPixel(560 / 2, 24 + 75))
        // Just outside the logo's edge is paper.
        assertEquals(Color.WHITE, picture.getPixel(560 / 2 - 150 - 5, 24 + 75))
        // And the receipt text is under the logo.
        assertTrue("no receipt text under the logo", darkPixelsBelow(picture, 24 + 150 + 12) > 500)
    }

    @Test
    fun wideReceiptIsAn80mmPicture() {
        val bytes = share.generateReceiptImage(document(paperWidth = 48), logoFile("test-logo.png", 300, 150, Color.MAGENTA))
        keepForLooking("receipt-preview-80-logo.png", bytes)

        assertEquals(800, decode(bytes).width)
    }

    @Test
    fun fourInchReceiptIsA112mmPicture() {
        val bytes = share.generateReceiptImage(document(paperWidth = 64), logoFile("test-logo.png", 300, 150, Color.MAGENTA))
        keepForLooking("receipt-preview-112-logo.png", bytes)
        val picture = decode(bytes)

        assertEquals(1120, picture.width)
        assertEquals(Color.MAGENTA, picture.getPixel(1120 / 2, 24 + 75))
        assertTrue("no receipt text under the logo", darkPixelsBelow(picture, 24 + 150 + 12) > 500)
    }

    @Test
    fun withoutALogoThereIsNoGapAtTheTop() {
        val withLogo = decode(share.generateReceiptImage(document(32), logoFile("test-logo.png", 300, 150, Color.MAGENTA)))
        val bytesWithout = share.generateReceiptImage(document(32), "")
        keepForLooking("receipt-preview-58-nologo.png", bytesWithout)
        val without = decode(bytesWithout)

        // The logo adds its own height plus half a margin (12 px) and nothing else.
        assertEquals(150 + 12, withLogo.height - without.height)
    }

    @Test
    fun aMissingOrBrokenLogoFileIsSkippedNotAnError() {
        val plain = decode(share.generateReceiptImage(document(32), ""))
        val missing = decode(share.generateReceiptImage(document(32), File(context.cacheDir, "no-such-logo.png").absolutePath))
        val broken = File(context.cacheDir, "broken-logo.png").also { made += it; it.writeText("this is not a picture") }
        val brokenPicture = decode(share.generateReceiptImage(document(32), broken.absolutePath))

        assertEquals(plain.height, missing.height)
        assertEquals(plain.height, brokenPicture.height)
    }

    @Test
    fun aHugeLogoIsScaledDownToFitTheReceipt() {
        // 2400x1200 magenta logo: shown at most 60% of the 560 px width = 336 x 168, centred (left edge 112).
        val bytes = share.generateReceiptImage(document(32), logoFile("huge-logo.png", 2400, 1200, Color.MAGENTA))
        val picture = decode(bytes)

        assertEquals(Color.MAGENTA, picture.getPixel(560 / 2, 24 + 84))
        assertEquals(Color.MAGENTA, picture.getPixel(115, 24 + 84))
        assertEquals("the logo spilled past 336 px", Color.WHITE, picture.getPixel(105, 24 + 84))
    }

    @Test
    fun aLongBillGrowsTheRoll() {
        val short = decode(share.generateReceiptImage(document(32), ""))
        val many = (1..30).map { BillItem("item number $it with a fairly long product name", "2", "₹10.00", "₹20.00") }
        val long = decode(share.generateReceiptImage(document(32, many), ""))

        assertTrue("30 items should make a taller receipt", long.height > short.height + 30 * 40)
        assertEquals(560, long.width)
    }

    @Test
    fun theSharedFileIsARealPictureHandedThroughTheFileProvider() {
        val bytes = share.generateReceiptImage(document(32), logoFile("test-logo.png", 300, 150, Color.MAGENTA))
        // PNG signature: what WhatsApp needs to treat it as a picture.
        assertEquals(listOf(0x89, 0x50, 0x4E, 0x47), bytes.take(4).map { it.toInt() and 0xFF })

        assertTrue("share sheet could not be started for the picture", share.shareFile(bytes, "receipt-test.png", "image/png"))
        val cached = File(context.cacheDir, "receipt-test.png").also { made += it }
        assertTrue(cached.exists() && cached.length() == bytes.size.toLong())
    }

    @Test
    fun theReceiptPdfStillWorksForAnyoneStillUsingIt() {
        val pdf = share.generateReceiptPdf(document(32))
        assertEquals("%PDF-", String(pdf.copyOf(5)))
        assertTrue(pdf.size > 500)
    }
}
