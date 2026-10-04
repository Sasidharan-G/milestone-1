package com.kadaikutty.pos.core.printer.data

/**
 * Turns a shop logo into the black dots a thermal head burns. A thermal printer has no grey and no
 * colour, so a logo straight from a phone gallery prints as a black block: a dark blue badge, a
 * screenshot, a photo with a coloured background. These functions look at the picture itself and
 * decide how to print it:
 *
 *  - transparent areas count as white paper;
 *  - a picture whose own background is dark is printed inverted, so the background stays unburnt
 *    paper (and the receipt is not a slab of black that is slow, hot and wastes the head);
 *  - empty margins around the artwork are found so they can be cropped away;
 *  - the black/white cut-off is chosen from the picture (Otsu), not fixed;
 *  - flat artwork is cut cleanly; shaded artwork (many mid-greys) is dithered so the shading survives.
 *
 * Pure functions on pixel arrays (ARGB ints, row by row), so they are unit-tested without a phone.
 */
object LogoBinarizer {

    /** What was learnt about a picture: whether to print it inverted and where its artwork sits. */
    class Analysis(
        val invert: Boolean,
        /** left, top, right (exclusive), bottom (exclusive) of the artwork; null for a blank picture. */
        val bounds: IntArray?
    )

    /** Brightness 0 (black) to 255 (white) of each pixel; transparent parts count as white paper. */
    fun luminance(argb: IntArray): IntArray = IntArray(argb.size) { index ->
        val p = argb[index]
        val a = p ushr 24
        var r = (p shr 16) and 0xFF
        var g = (p shr 8) and 0xFF
        var b = p and 0xFF
        if (a < 255) {
            r = (r * a + 255 * (255 - a)) / 255
            g = (g * a + 255 * (255 - a)) / 255
            b = (b * a + 255 * (255 - a)) / 255
        }
        (299 * r + 587 * g + 114 * b) / 1000
    }

    /** The grey level that best splits dark from light (Otsu's method); 128 for a one-tone picture. */
    fun otsuThreshold(gray: IntArray): Int {
        if (gray.isEmpty()) return 128
        val histogram = IntArray(256)
        gray.forEach { histogram[it.coerceIn(0, 255)]++ }
        val total = gray.size.toLong()
        var sumAll = 0L
        for (level in 0..255) sumAll += level.toLong() * histogram[level]
        var weightBackground = 0L
        var sumBackground = 0L
        var bestVariance = -1.0
        var best = 128
        for (level in 0..255) {
            weightBackground += histogram[level]
            if (weightBackground == 0L) continue
            val weightForeground = total - weightBackground
            if (weightForeground == 0L) break
            sumBackground += level.toLong() * histogram[level]
            val meanBackground = sumBackground.toDouble() / weightBackground
            val meanForeground = (sumAll - sumBackground).toDouble() / weightForeground
            val variance = weightBackground.toDouble() * weightForeground.toDouble() * (meanBackground - meanForeground) * (meanBackground - meanForeground)
            if (variance > bestVariance) { bestVariance = variance; best = level }
        }
        return if (bestVariance <= 0.0) 128 else best
    }

    private fun ringIndices(width: Int, height: Int): IntArray {
        if (width <= 0 || height <= 0) return IntArray(0)
        val list = ArrayList<Int>(2 * (width + height))
        for (x in 0 until width) { list.add(x); list.add((height - 1) * width + x) }
        for (y in 1 until height - 1) { list.add(y * width); list.add(y * width + width - 1) }
        return list.toIntArray()
    }

    /**
     * The picture as it will be burnt, 0 = black dot and 255 = bare paper: inverted when asked, and
     * every transparent pixel is bare paper whatever the inversion does to the colour under it.
     */
    private fun paperLevels(argb: IntArray, invert: Boolean): IntArray {
        val gray = luminance(argb)
        for (i in gray.indices) {
            gray[i] = if ((argb[i] ushr 24) < TRANSPARENT_BELOW) 255 else if (invert) 255 - gray[i] else gray[i]
        }
        return gray
    }

    /**
     * Decides what to do with a picture: invert it when its own background (the outer ring of pixels)
     * is dark, and find the artwork's bounding box on the background that results.
     */
    fun analyze(argb: IntArray, width: Int, height: Int): Analysis {
        require(argb.size == width * height) { "pixel count does not match the size" }
        if (width <= 0 || height <= 0) return Analysis(false, null)
        val gray = luminance(argb)
        val threshold = otsuThreshold(gray)
        val ring = ringIndices(width, height)
        val darkRing = ring.count { gray[it] <= threshold }
        val invert = ring.isNotEmpty() && darkRing * 2 > ring.size
        val level = paperLevels(argb, invert)
        val background = ring.map { level[it] }.sorted().let { it[it.size / 2] }
        var left = width; var top = height; var right = -1; var bottom = -1
        for (y in 0 until height) for (x in 0 until width) {
            val v = level[y * width + x]
            if (kotlin.math.abs(v - background) > CONTENT_DIFFERENCE) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        val bounds = if (right < left || bottom < top) null else intArrayOf(left, top, right + 1, bottom + 1)
        return Analysis(invert, bounds)
    }

    /** True where a black dot is to be burnt. [invert] comes from [analyze] of the whole, uncropped picture. */
    fun toDots(argb: IntArray, width: Int, height: Int, invert: Boolean): BooleanArray {
        require(argb.size == width * height) { "pixel count does not match the size" }
        val gray = paperLevels(argb, invert)
        val threshold = otsuThreshold(gray)
        val midtones = gray.count { kotlin.math.abs(it - threshold) < MIDTONE_BAND }
        val shaded = gray.isNotEmpty() && midtones * 100 > gray.size * SHADED_PERCENT
        return if (shaded) dither(gray, width, height) else BooleanArray(gray.size) { gray[it] <= threshold }
    }

    /** Floyd-Steinberg error diffusion: keeps shading as a pattern of dots. */
    private fun dither(gray: IntArray, width: Int, height: Int): BooleanArray {
        val work = gray.copyOf()
        val dots = BooleanArray(gray.size)
        for (y in 0 until height) for (x in 0 until width) {
            val index = y * width + x
            val old = work[index]
            val black = old < 128
            dots[index] = black
            val error = old - if (black) 0 else 255
            if (x + 1 < width) work[index + 1] += error * 7 / 16
            if (y + 1 < height) {
                if (x > 0) work[index + width - 1] += error * 3 / 16
                work[index + width] += error * 5 / 16
                if (x + 1 < width) work[index + width + 1] += error / 16
            }
        }
        return dots
    }

    private const val CONTENT_DIFFERENCE = 40
    private const val TRANSPARENT_BELOW = 48
    private const val MIDTONE_BAND = 48
    private const val SHADED_PERCENT = 30
}
