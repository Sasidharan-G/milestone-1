package com.kadaikutty.pos.core.printer.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogoBinarizerTest {

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private val white = argb(255, 255, 255, 255)
    private val black = argb(255, 0, 0, 0)
    private val navy = argb(255, 10, 30, 130)
    private val clear = argb(0, 0, 0, 0)

    private fun picture(width: Int, height: Int, background: Int, vararg boxes: Triple<IntRange, IntRange, Int>): IntArray {
        val pixels = IntArray(width * height) { background }
        for ((xs, ys, colour) in boxes) for (y in ys) for (x in xs) pixels[y * width + x] = colour
        return pixels
    }

    @Test fun transparent_pixels_count_as_white_paper() {
        assertEquals(listOf(255, 255), LogoBinarizer.luminance(intArrayOf(clear, argb(0, 10, 10, 10))).toList())
    }

    @Test fun half_transparent_black_is_mid_grey() {
        val grey = LogoBinarizer.luminance(intArrayOf(argb(128, 0, 0, 0)))[0]
        assertTrue("was $grey", grey in 120..135)
    }

    @Test fun otsu_puts_the_cut_between_dark_and_light() {
        val gray = IntArray(100) { if (it < 50) 20 else 230 }
        assertTrue(LogoBinarizer.otsuThreshold(gray) in 20..229)
    }

    @Test fun a_picture_of_one_tone_gets_the_middle_cut() {
        assertEquals(128, LogoBinarizer.otsuThreshold(IntArray(64) { 90 }))
    }

    @Test fun a_logo_on_a_dark_background_is_printed_inverted_so_the_paper_stays_clean() {
        // navy badge with a white mark in the middle, like a launcher icon screenshot
        val w = 40; val h = 40
        val pixels = picture(w, h, navy, Triple(15..24, 15..24, white))
        val analysis = LogoBinarizer.analyze(pixels, w, h)
        assertTrue(analysis.invert)
        assertArrayEquals(intArrayOf(15, 15, 25, 25), analysis.bounds)

        val dots = LogoBinarizer.toDots(pixels, w, h, analysis.invert)
        assertTrue("the mark burns", dots[20 * w + 20])
        assertFalse("the background stays white", dots[2 * w + 2])
        assertTrue("under a tenth of the paper is burnt", dots.count { it } * 10 < dots.size)
    }

    @Test fun a_dark_logo_on_white_is_not_inverted() {
        val w = 40; val h = 40
        val pixels = picture(w, h, white, Triple(10..29, 10..29, black))
        val analysis = LogoBinarizer.analyze(pixels, w, h)
        assertFalse(analysis.invert)
        val dots = LogoBinarizer.toDots(pixels, w, h, analysis.invert)
        assertTrue(dots[20 * w + 20])
        assertFalse(dots[0])
    }

    @Test fun a_dark_logo_on_a_transparent_background_is_not_inverted() {
        val w = 30; val h = 30
        val pixels = picture(w, h, clear, Triple(8..21, 8..21, black))
        assertFalse(LogoBinarizer.analyze(pixels, w, h).invert)
    }

    @Test fun empty_margins_around_the_artwork_are_found() {
        val pixels = picture(40, 30, clear, Triple(5..14, 8..13, black))
        assertArrayEquals(intArrayOf(5, 8, 15, 14), LogoBinarizer.analyze(pixels, 40, 30).bounds)
    }

    @Test fun a_blank_picture_has_no_artwork() {
        val analysis = LogoBinarizer.analyze(IntArray(25 * 25) { white }, 25, 25)
        assertNull(analysis.bounds)
        val transparent = LogoBinarizer.analyze(IntArray(25 * 25) { clear }, 25, 25)
        assertNull(transparent.bounds)
    }

    @Test fun flat_artwork_is_cut_cleanly_with_no_stray_dots() {
        val w = 20; val h = 20
        val pixels = picture(w, h, white, Triple(5..14, 5..14, black))
        val dots = LogoBinarizer.toDots(pixels, w, h, false)
        for (y in 0 until h) for (x in 0 until w) {
            val inside = x in 5..14 && y in 5..14
            assertEquals("pixel $x,$y", inside, dots[y * w + x])
        }
    }

    @Test fun shaded_artwork_is_dithered_so_the_shading_survives() {
        val w = 64; val h = 16
        val pixels = IntArray(w * h) { index ->
            val grey = (index % w) * 255 / (w - 1)
            argb(255, grey, grey, grey)
        }
        val dots = LogoBinarizer.toDots(pixels, w, h, false)
        val burnt = dots.count { it }
        assertTrue("about half burnt, was $burnt of ${dots.size}", burnt in dots.size * 35 / 100..dots.size * 65 / 100)
        // in the middle of the ramp both dark and light dots occur in the same column band
        val middle = (24 until 40).flatMap { x -> (0 until h).map { y -> dots[y * w + x] } }
        assertTrue(middle.any { it })
        assertTrue(middle.any { !it })
    }

    @Test fun the_same_picture_always_gives_the_same_dots() {
        val w = 32; val h = 32
        val pixels = IntArray(w * h) { argb(255, (it * 7) % 256, (it * 13) % 256, (it * 29) % 256) }
        assertArrayEquals(LogoBinarizer.toDots(pixels, w, h, false), LogoBinarizer.toDots(pixels, w, h, false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun a_pixel_array_that_does_not_match_the_size_is_refused() {
        LogoBinarizer.analyze(IntArray(10), 4, 4)
    }

    @Test fun a_one_pixel_picture_does_not_crash() {
        val analysis = LogoBinarizer.analyze(intArrayOf(black), 1, 1)
        assertNotNull(analysis)
        assertEquals(1, LogoBinarizer.toDots(intArrayOf(black), 1, 1, false).size)
    }
}
