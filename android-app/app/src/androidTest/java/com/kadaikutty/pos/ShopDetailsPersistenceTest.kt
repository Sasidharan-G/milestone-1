package com.kadaikutty.pos

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kadaikutty.pos.support.PosTestFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Found on a real phone: the shop logo was gone after the app was reopened. On every app start
 * LicenseManager wiped the saved shop details (the logo included) because it only remembered the
 * signed-in company in memory. The wipe now happens only when the details belong to a different company.
 */
@RunWith(AndroidJUnit4::class)
class ShopDetailsPersistenceTest {
    private lateinit var fixture: PosTestFixture

    @Before fun setUp() { fixture = PosTestFixture() }
    @After fun tearDown() { fixture.close() }

    private val prefs get() = fixture.appPreferences

    private suspend fun saveShop(logo: String = "/data/app/files/logos/shop_logo_1.png") =
        prefs.saveShopDetails("Kavi Stores", "Kavitha", "27ABCDE1234F2Z5", "12 Main Road", "8754511678", "kavi@shop.in", logo)

    @Test fun logoAndDetailsSurviveTheAppBeingReopened() = runBlocking {
        saveShop()
        assertFalse("first sign-in", prefs.claimShopDetailsFor("company-a"))
        // App closed and opened again: the same company signs in once more.
        assertFalse("reopened", prefs.claimShopDetailsFor("company-a"))
        assertFalse("reopened again", prefs.claimShopDetailsFor("company-a"))

        assertEquals("/data/app/files/logos/shop_logo_1.png", prefs.shopLogoPath.first())
        assertEquals("Kavi Stores", prefs.shopName.first())
        assertEquals("27ABCDE1234F2Z5", prefs.gstNumber.first())
    }

    @Test fun aDifferentCompanyNeverSeesTheOldShopsDetails() = runBlocking {
        saveShop()
        prefs.saveShopLogoSync("/data/app/files/logos/shop_logo_1.png", "/data/app/files/logos/shop_logo_1.png", 777L)
        prefs.claimShopDetailsFor("company-a")

        assertTrue("another shop signed in on the same phone", prefs.claimShopDetailsFor("company-b"))

        assertEquals("", prefs.shopLogoPath.first())
        // The cloud-sync memory goes too: otherwise shop B would think shop A's logo was already uploaded for it.
        assertEquals("", prefs.shopLogoUploadedPath.first())
        assertEquals(0L, prefs.shopLogoVersion.first())
        assertEquals("", prefs.shopName.first())
        assertEquals("", prefs.gstNumber.first())
        assertEquals("", prefs.shopAddress.first())
        // ...and company B's own details are kept from then on.
        prefs.saveShopDetails("B Traders", "B", "", "", "", "", "/logo-b.png")
        assertFalse(prefs.claimShopDetailsFor("company-b"))
        assertEquals("/logo-b.png", prefs.shopLogoPath.first())
    }

    @Test fun aBackgroundRefreshOfTheDetailsLeavesTheLogoAlone() = runBlocking {
        saveShop()
        prefs.saveShopLogoSync("/logo-synced.png", "/logo-synced.png", 5L)

        // LicenseManager refreshes name, GSTIN... from the server with no logo of its own to write.
        prefs.saveShopDetails("Kavi Stores 2", "Kavitha", "", "", "", "", null)

        assertEquals("Kavi Stores 2", prefs.shopName.first())
        assertEquals("/logo-synced.png", prefs.shopLogoPath.first())
        assertEquals("/logo-synced.png", prefs.shopLogoUploadedPath.first())
        assertEquals(5L, prefs.shopLogoVersion.first())
    }

    @Test fun signingOutClearsEverythingSoTheNextShopStartsClean() = runBlocking {
        saveShop()
        prefs.saveShopLogoSync("/logo.png", "/logo.png", 9L)
        prefs.claimShopDetailsFor("company-a")

        prefs.clearShopDetails()

        assertEquals("", prefs.shopLogoUploadedPath.first())
        assertEquals(0L, prefs.shopLogoVersion.first())
        assertEquals("", prefs.shopLogoPath.first())
        assertEquals("", prefs.shopName.first())
        assertFalse("nothing left to remove for the next shop", prefs.claimShopDetailsFor("company-b"))
    }

    @Test fun detailsSavedBeforeTheMarkerExistedAreTakenAsTheSignedInShops() = runBlocking {
        saveShop() // an app updated from a version that had no marker
        assertFalse(prefs.claimShopDetailsFor("company-a"))
        assertEquals("/data/app/files/logos/shop_logo_1.png", prefs.shopLogoPath.first())
    }
}
