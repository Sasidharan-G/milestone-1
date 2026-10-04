package com.kadaikutty.pos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kadaikutty.pos.core.branding.LogoImages
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Random

/**
 * The logo pipeline on a real Android image stack: a picture the owner picks is scaled down safely
 * (a phone photo is tens of megapixels), and the copy sent to the cloud is always small enough for
 * any proxy on the way.
 */
@RunWith(AndroidJUnit4::class)
class LogoImagesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scratch = File(context.cacheDir, "logo-images-test")

    @Before fun setUp() { scratch.deleteRecursively(); scratch.mkdirs() }
    @After fun tearDown() {
        scratch.deleteRecursively()
        File(context.filesDir, "logos").listFiles()?.filter { it.name.startsWith("shop_logo_") }?.forEach { it.delete() }
    }

    /** Smooth colour with a little noise, so a JPEG/PNG of it is not trivially tiny. */
    private fun picture(width: Int, height: Int, noise: Int = 40): Bitmap {
        val random = Random(7)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val row = IntArray(width)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = (x * 255 / width + random.nextInt(noise)).coerceIn(0, 255)
                val g = (y * 255 / height + random.nextInt(noise)).coerceIn(0, 255)
                val b = ((x + y) * 255 / (width + height) + random.nextInt(noise)).coerceIn(0, 255)
                row[x] = Color.rgb(r, g, b)
            }
            bitmap.setPixels(row, 0, width, 0, y, width, 1)
        }
        return bitmap
    }

    /** Pure random colour (a PNG of it hardly compresses), with the first [transparentColumns] columns see-through. */
    private fun noisy(width: Int, height: Int, transparentColumns: Int): Bitmap {
        val random = Random(11)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val row = IntArray(width)
        for (y in 0 until height) {
            for (x in 0 until width) {
                row[x] = if (x < transparentColumns) Color.TRANSPARENT else Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
            }
            bitmap.setPixels(row, 0, width, 0, y, width, 1)
        }
        return bitmap
    }

    private fun file(name: String, bitmap: Bitmap, format: Bitmap.CompressFormat): Uri {
        val target = File(scratch, name)
        target.outputStream().use { bitmap.compress(format, 95, it) }
        return Uri.fromFile(target)
    }

    private fun sizeOf(path: String): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        return bounds.outWidth to bounds.outHeight
    }

    @Test fun aPhonePhotoIsScaledDownToTheLogoSize() {
        val photo = picture(4000, 3000, noise = 20)
        val uri = file("photo.jpg", photo, Bitmap.CompressFormat.JPEG)
        photo.recycle()

        val saved = LogoImages.saveScaled(context, uri)

        assertNotNull(saved)
        val (width, height) = sizeOf(saved!!)
        assertEquals("long side is the logo size", LogoImages.MAX_SIDE, maxOf(width, height))
        assertEquals("shape is kept (4:3)", 4.0 / 3.0, width.toDouble() / height, 0.02)
        assertTrue("saved under the app's logos folder", File(saved).parentFile!!.name == "logos")
    }

    @Test fun aSmallLogoIsStillSavedAndFitsTheLogoSize() {
        val small = picture(120, 90)
        val uri = file("small.png", small, Bitmap.CompressFormat.PNG)
        small.recycle()

        val saved = LogoImages.saveScaled(context, uri)

        assertNotNull(saved)
        val (width, height) = sizeOf(saved!!)
        assertEquals(LogoImages.MAX_SIDE, maxOf(width, height))
        assertTrue(width > 0 && height > 0)
    }

    @Test fun aVeryThinPictureDoesNotBreakTheScaling() {
        val thin = picture(2000, 3, noise = 10)
        val uri = file("thin.png", thin, Bitmap.CompressFormat.PNG)
        thin.recycle()

        val saved = LogoImages.saveScaled(context, uri)

        assertNotNull(saved)
        val (width, height) = sizeOf(saved!!)
        assertEquals(LogoImages.MAX_SIDE, width)
        assertTrue("never zero pixels high", height >= 1)
    }

    @Test fun somethingThatIsNotAPictureGivesNullNotACrash() {
        val notAPicture = File(scratch, "notes.png").apply { writeText("this is not an image at all") }
        assertNull(LogoImages.saveScaled(context, Uri.fromFile(notAPicture)))
        assertNull(LogoImages.saveScaled(context, Uri.fromFile(File(scratch, "missing.png"))))
    }

    private fun png(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    @Test fun aSmallLogoIsSentToTheCloudExactlyAsItIs() {
        val bitmap = picture(200, 200, noise = 5)
        val bytes = png(bitmap)
        bitmap.recycle()
        assertTrue(bytes.size < LogoImages.MAX_UPLOAD_BYTES)
        assertSame(bytes, LogoImages.fitForUpload(bytes))
    }

    @Test fun aLogoThatIsTooBigForTheCloudIsSentAsASmallerJpeg() {
        val bitmap = noisy(640, 640, transparentColumns = 32) // random colour does not compress: the PNG is about 1.2 MB
        val bytes = png(bitmap)
        bitmap.recycle()
        assertTrue("test picture must be over the limit, was ${bytes.size}", bytes.size > LogoImages.MAX_UPLOAD_BYTES)

        val sent = LogoImages.fitForUpload(bytes)

        assertTrue("fits: ${sent.size}", sent.size <= LogoImages.MAX_UPLOAD_BYTES)
        assertEquals("a JPEG", 0xFF.toByte(), sent[0])
        assertEquals(0xD8.toByte(), sent[1])
        val decoded = BitmapFactory.decodeByteArray(sent, 0, sent.size)
        assertNotNull("the server and the app can both read it back", decoded)
        assertTrue("still a usable logo: ${decoded.width}x${decoded.height}", decoded.width >= 256 && decoded.height >= 256)
        // The see-through strip was flattened on white, which is how a logo prints anyway.
        val pixel = decoded.getPixel(3, decoded.height / 2)
        assertTrue("white, was #${Integer.toHexString(pixel)}", Color.red(pixel) >= 240 && Color.green(pixel) >= 240 && Color.blue(pixel) >= 240)
        decoded.recycle()
    }

    @Test fun anUnreadablePictureIsPassedOnUnchangedForTheServerToJudge() {
        val junk = ByteArray(LogoImages.MAX_UPLOAD_BYTES + 10) { (it % 251).toByte() }
        assertArrayEquals(junk, LogoImages.fitForUpload(junk))
    }
}
