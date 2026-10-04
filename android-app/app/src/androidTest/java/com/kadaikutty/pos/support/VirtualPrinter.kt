package com.kadaikutty.pos.support

import android.graphics.Bitmap
import android.graphics.Color

/**
 * A printer that exists only to prove what a real one would do. It reads ESC/POS bytes the way a
 * printer reads them - command by command, refusing anything it does not understand - and rebuilds
 * the paper from the raster bands it is sent. If a receipt looks right here and every byte is a
 * command a printer knows, it prints right on any ESC/POS printer that supports raster images.
 */
class VirtualPrinter(bytes: ByteArray) {
    class Band(val widthBytes: Int, val rows: Int, val data: ByteArray)

    /** What the printer was told to do, in order: "INIT", "ALIGN 1", "RASTER 48x128", "LF", "CUT". */
    val trace = mutableListOf<String>()
    val bands = mutableListOf<Band>()

    init {
        var i = 0
        fun byteAt(index: Int): Int {
            require(index < bytes.size) { "the data stops in the middle of a command (at byte $index of ${bytes.size})" }
            return bytes[index].toInt() and 0xFF
        }
        while (i < bytes.size) {
            val b = byteAt(i)
            when {
                b == 0x1B && byteAt(i + 1) == 0x40 -> { trace += "INIT"; i += 2 }
                b == 0x1B && byteAt(i + 1) == 0x61 -> { trace += "ALIGN ${byteAt(i + 2)}"; i += 3 }
                b == 0x1D && byteAt(i + 1) == 0x76 && byteAt(i + 2) == 0x30 -> {
                    val mode = byteAt(i + 3)
                    require(mode == 0) { "raster mode $mode is not the normal-density mode" }
                    val widthBytes = byteAt(i + 4) + byteAt(i + 5) * 256
                    val rows = byteAt(i + 6) + byteAt(i + 7) * 256
                    val length = widthBytes * rows
                    require(widthBytes > 0 && rows > 0) { "an empty raster band" }
                    require(i + 8 + length <= bytes.size) { "raster band of ${widthBytes}x$rows runs past the end of the data" }
                    bands += Band(widthBytes, rows, bytes.copyOfRange(i + 8, i + 8 + length))
                    trace += "RASTER ${widthBytes}x$rows"
                    i += 8 + length
                }
                b == 0x1D && byteAt(i + 1) == 0x56 && byteAt(i + 2) == 0x41 -> { trace += "CUT"; i += 4 }
                b == 0x0A -> { trace += "LF"; i += 1 }
                else -> throw IllegalArgumentException("unknown printer command 0x%02X at byte %d".format(b, i))
            }
        }
    }

    val widthDots: Int get() = (bands.firstOrNull()?.widthBytes ?: 0) * 8
    val heightRows: Int get() = bands.sumOf { it.rows }

    /** The paper as it would come out: black where the head burns, white elsewhere. */
    fun paper(): Bitmap {
        require(bands.isNotEmpty()) { "nothing was printed" }
        require(bands.all { it.widthBytes * 8 == widthDots }) { "bands of different widths" }
        val bitmap = Bitmap.createBitmap(widthDots, heightRows, Bitmap.Config.ARGB_8888)
        var top = 0
        for (band in bands) {
            for (y in 0 until band.rows) {
                for (xb in 0 until band.widthBytes) {
                    val value = band.data[y * band.widthBytes + xb].toInt() and 0xFF
                    for (bit in 0..7) {
                        bitmap.setPixel(xb * 8 + bit, top + y, if (value and (0x80 shr bit) != 0) Color.BLACK else Color.WHITE)
                    }
                }
            }
            top += band.rows
        }
        return bitmap
    }
}
