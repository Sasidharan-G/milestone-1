package com.kadaikutty.pos.core.printer.domain

/**
 * The thermal paper widths a shop can pick. A width is saved as the number of characters that fit
 * on one printed line (what the receipt layout works in); the picture and PDF a customer receives
 * are sized from the paper in millimetres.
 *
 * 112 mm is the "4 inch" roll. Its printable width is about 69 characters; 64 is used because
 * the receipt layout never goes past 64 and it leaves the edges clear.
 */
object PaperWidth {
    const val MM_58 = 32
    const val MM_80 = 48
    const val MM_112 = 64

    val all = listOf(MM_58, MM_80, MM_112)

    /** The paper for a saved [columns] value; anything unknown is treated as the narrowest roll. */
    fun millimetres(columns: Int): Int = when {
        columns >= MM_112 -> 112
        columns >= MM_80 -> 80
        else -> 58
    }

    /**
     * How many dots wide the printed picture is for a saved [columns] value. These are the widths
     * the common printers of each roll can all print: 384 (58 mm), 576 (80 mm) and 768 (112 mm, a
     * little under the 832 most 4-inch printers have, so it can never run past the edge). A
     * printer whose head is wider simply centres the picture; one narrower than this would clip,
     * which no standard roll does. Always a whole number of bytes (a multiple of 8).
     */
    fun printableDots(columns: Int): Int = when {
        columns >= MM_112 -> 768
        columns >= MM_80 -> 576
        columns >= MM_58 -> 384
        else -> ((columns.coerceAtLeast(16) * 12) / 8) * 8
    }

    /** "58 mm / 2 inch" - what the settings screens call a paper size. */
    fun label(columns: Int): String = when (millimetres(columns)) {
        112 -> "112 mm · 4 inch"
        80 -> "80 mm · 3 inch"
        else -> "58 mm · 2 inch"
    }
}
