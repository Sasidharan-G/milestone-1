package com.kadaikutty.pos

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kadaikutty.pos.support.PosTestFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Paper size is saved by the printer settings, and on its own for a shop with no printer (shared
 * receipt pictures are cut to it). The 4-inch roll (64 characters) is accepted by both.
 */
@RunWith(AndroidJUnit4::class)
class PaperSettingsTest {
    private lateinit var fixture: PosTestFixture

    @Before fun setUp() { fixture = PosTestFixture() }
    @After fun tearDown() { fixture.close() }

    private val prefs get() = fixture.appPreferences

    @Test fun paperSizeIsSavedWithoutAPrinter() = runBlocking {
        assertEquals("58 mm is the starting size", 32, prefs.printerPaperWidth.first())
        prefs.savePaperWidth(64)
        assertEquals(64, prefs.printerPaperWidth.first())
        assertEquals("a printer was never chosen", null, prefs.printerDeviceId.first())
    }

    @Test fun printerSettingsAcceptEveryRoll() = runBlocking {
        for (width in listOf(32, 48, 64)) {
            prefs.savePrinterSettings("Bluetooth", "AA:BB:CC:DD:EE:FF", width)
            assertEquals(width, prefs.printerPaperWidth.first())
        }
        assertEquals("AA:BB:CC:DD:EE:FF", prefs.printerDeviceId.first())
    }

    @Test fun anUnknownPaperSizeIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { prefs.savePaperWidth(40) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { prefs.savePrinterSettings("Bluetooth", "AA:BB", 40) } }
    }

    @Test fun printerSettingsStillNeedAPrinter() {
        // The Settings screen checks this first and falls back to savePaperWidth; the rule itself stays.
        assertThrows(IllegalArgumentException::class.java) { runBlocking { prefs.savePrinterSettings("Bluetooth", "", 48) } }
    }
}
