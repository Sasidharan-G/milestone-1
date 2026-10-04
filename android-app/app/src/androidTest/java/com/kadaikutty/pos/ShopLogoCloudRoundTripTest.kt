package com.kadaikutty.pos

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kadaikutty.pos.core.auth.Session
import com.kadaikutty.pos.core.branding.LogoImages
import com.kadaikutty.pos.core.branding.ShopLogoSync.Outcome
import com.kadaikutty.pos.core.branding.ShopLogoSyncer
import com.kadaikutty.pos.core.network.BackendApiClient
import com.kadaikutty.pos.core.network.BackendApiException
import com.kadaikutty.pos.core.security.Permission
import com.kadaikutty.pos.support.PosTestFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Random
import java.util.UUID

/**
 * The shop logo's whole trip through the app's real code (BackendApiClient, ShopLogoSyncer, the
 * phone's preferences and files) and a real copy of the server (local providers) running on this PC.
 * It is skipped unless started on purpose with `-e localServer true` while that server listens on
 * port 3000, and it refuses to run when the build points anywhere but the emulator's host
 * (10.0.2.2), so it can never touch the real backend.
 */
@RunWith(AndroidJUnit4::class)
class ShopLogoCloudRoundTripTest {
    private var fixture: PosTestFixture? = null
    private lateinit var api: BackendApiClient
    private lateinit var syncer: ShopLogoSyncer
    private lateinit var token: String
    private val prefs get() = fixture!!.appPreferences
    private val logosDirectory get() = File(fixture!!.context.filesDir, "logos")

    @Before fun setUp() {
        assumeTrue("start the local server and pass -e localServer true", InstrumentationRegistry.getArguments().getString("localServer") == "true")
        assumeTrue("only ever against a server on the emulator's host", BuildConfig.BACKEND_BASE_URL.startsWith("http://10.0.2.2"))
        val shop = PosTestFixture().also { fixture = it }
        api = BackendApiClient(shop.sessions, shop.appPreferences)
        syncer = ShopLogoSyncer(shop.context, api, shop.sessions, shop.appPreferences)
        runBlocking {
            val login = api.login("9876540100", "123456")
            val user = login.getJSONObject("user")
            val tokens = login.getJSONObject("tokens")
            token = tokens.getString("accessToken")
            val registered = api.registerSession(token, "e2e-" + UUID.randomUUID(), "E2E test phone")
            shop.sessions.save(Session(
                userId = user.getString("userId"), displayName = "E2E Owner", permissions = Permission.ALL_ACTIVE,
                companyId = user.getString("companyId"), role = "ADMIN", accessToken = token,
                refreshToken = tokens.optString("refreshToken").ifBlank { null },
                sessionToken = registered.getJSONObject("session").getString("sessionId")))
            api.deleteShopLogo(token) // every test starts from a shop with no logo in the cloud
        }
    }

    @After fun tearDown() {
        fixture?.let { logosDirectory.listFiles()?.forEach { file -> file.delete() } }
        fixture?.close()
    }

    private fun picture(width: Int, height: Int, seed: Long): ByteArray {
        val random = Random(seed)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val row = IntArray(width)
        for (y in 0 until height) {
            for (x in 0 until width) row[x] = Color.rgb((x * 255 / width), (y * 255 / height), random.nextInt(60))
            bitmap.setPixels(row, 0, width, 0, y, width, 1)
        }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray().also { bitmap.recycle() }
    }

    private fun noise(width: Int, height: Int): ByteArray {
        val random = Random(3)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val row = IntArray(width)
        for (y in 0 until height) {
            for (x in 0 until width) row[x] = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
            bitmap.setPixels(row, 0, width, 0, y, width, 1)
        }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray().also { bitmap.recycle() }
    }

    /** The owner picks [bytes] and presses Save: the file is on the phone and the shop details are saved with it. */
    private suspend fun choose(bytes: ByteArray, name: String = "shop_logo_${System.nanoTime()}.png"): File {
        logosDirectory.mkdirs()
        val file = File(logosDirectory, name).apply { writeBytes(bytes) }
        prefs.saveShopDetails("E2E Stores", "E2E Owner", "", "", "", "", file.absolutePath)
        return file
    }

    private suspend fun profile(): JSONObject = api.currentLicense(token).getJSONObject("shopProfile")
    private suspend fun sync(): Outcome = syncer.sync(profile())

    @Test fun aChosenLogoReachesTheCloudAndComesBackOnAFreshInstall() = runBlocking {
        val logo = picture(300, 200, seed = 1)
        val file = choose(logo)

        assertEquals(Outcome.UPLOADED, sync())

        val saved = profile()
        assertTrue("the profile now says where the logo is", saved.optString("logoObjectKey").isNotBlank())
        val version = saved.getLong("logoUpdatedAtEpochMs")
        assertTrue(version > 0)
        assertEquals("the phone remembers what the cloud holds", file.absolutePath, prefs.shopLogoUploadedPath.first())
        assertEquals(version, prefs.shopLogoVersion.first())
        assertArrayEquals("the cloud holds exactly the picture", logo, api.downloadShopLogo(token))
        assertEquals("nothing left to do", Outcome.IN_SYNC, sync())

        // Uninstall and reinstall: the phone has nothing, the owner signs in.
        prefs.clearShopDetails()
        logosDirectory.listFiles()?.forEach { it.delete() }
        assertEquals("", prefs.shopLogoPath.first())

        assertEquals(Outcome.DOWNLOADED, sync())

        val restored = File(prefs.shopLogoPath.first())
        assertTrue("the logo is back on the phone", restored.isFile)
        assertArrayEquals(logo, restored.readBytes())
        assertEquals("and needs no upload", restored.absolutePath, prefs.shopLogoUploadedPath.first())
        assertEquals(version, prefs.shopLogoVersion.first())
        assertEquals(Outcome.IN_SYNC, sync())
    }

    @Test fun removingTheLogoRemovesItFromTheCloudAndFromTheOtherDevices() = runBlocking {
        choose(picture(200, 200, seed = 2))
        assertEquals(Outcome.UPLOADED, sync())
        assertTrue(api.downloadShopLogo(token) != null)

        // The owner presses Remove and Save.
        prefs.saveShopDetails("E2E Stores", "E2E Owner", "", "", "", "", "")
        assertEquals(Outcome.REMOVED_FROM_CLOUD, sync())

        assertNull("the cloud has no logo any more", api.downloadShopLogo(token))
        assertTrue("and the profile says so", profile().optString("logoObjectKey").isBlank())
        assertEquals("", prefs.shopLogoUploadedPath.first())
        assertEquals(Outcome.IN_SYNC, sync())

        // A device that still shows the old logo hears about the removal from the profile.
        val old = picture(200, 200, seed = 2)
        val stale = File(logosDirectory, "stale.png").apply { writeBytes(old) }
        prefs.saveShopLogoSync(stale.absolutePath, stale.absolutePath, 1L)
        assertEquals(Outcome.CLEARED_HERE, sync())
        assertEquals("", prefs.shopLogoPath.first())
        assertFalse("its file is cleaned up", stale.exists())
    }

    @Test fun aLogoReplacedOnAnotherDeviceIsDownloadedHere() = runBlocking {
        choose(picture(240, 160, seed = 4))
        assertEquals(Outcome.UPLOADED, sync())

        val replacement = picture(180, 180, seed = 5)
        Thread.sleep(5) // the server's clock moves on: that is what tells the devices apart
        api.uploadShopLogo(token, replacement) // another device of the shop saved a new logo

        assertEquals(Outcome.DOWNLOADED, sync())
        assertArrayEquals(replacement, File(prefs.shopLogoPath.first()).readBytes())
        assertEquals(Outcome.IN_SYNC, sync())
    }

    @Test fun aLogoTooBigForTheCloudIsSentSmallAndTheOriginalStaysOnThePhone() = runBlocking {
        val big = noise(640, 640)
        assertTrue("test picture is over the limit: ${big.size}", big.size > LogoImages.MAX_UPLOAD_BYTES)
        val file = choose(big)

        assertEquals(Outcome.UPLOADED, sync())

        val sent = api.downloadShopLogo(token)!!
        assertTrue("the cloud copy is small: ${sent.size}", sent.size <= LogoImages.MAX_UPLOAD_BYTES)
        assertEquals("a JPEG", 0xFF.toByte(), sent[0])
        assertArrayEquals("this phone keeps its own picture", big, file.readBytes())
        assertEquals(Outcome.IN_SYNC, sync())
    }

    @Test fun aMissingLogoIsNullButARefusedRequestIsAnError() = runBlocking {
        assertNull("no logo saved is a normal answer", api.downloadShopLogo(token))
        try {
            api.downloadShopLogo("not-a-valid-token")
            fail("a refused request must not look like 'no logo'")
        } catch (expected: BackendApiException) {
            assertEquals(401, expected.statusCode)
        }
    }

}
