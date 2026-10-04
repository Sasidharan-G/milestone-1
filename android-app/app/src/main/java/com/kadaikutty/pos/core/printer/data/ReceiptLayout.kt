package com.kadaikutty.pos.core.printer.data

/** Keeps every printable character; removes device-control characters from user input. */
object ReceiptLayout {
    /** The text with device-control characters removed (they could drive the printer) and tabs as spaces; line breaks stay. */
    fun sanitize(value: String): String =
        value.replace("\r\n", "\n").replace('\t', ' ').filter { it == '\n' || !it.isISOControl() }

    fun wrap(value: String, width: Int): List<String> {
        require(width in 24..64)
        return sanitize(value)
            .split('\n').flatMap { paragraph ->
                val result = mutableListOf<String>()
                var remaining = paragraph
                while (remaining.length > width) {
                    val space = remaining.lastIndexOf(' ', width)
                    val characters = java.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT)
                    characters.setText(remaining)
                    val end = if (space > 0) space else characters.preceding(width + 1).takeIf { it > 0 } ?: width
                    result += remaining.substring(0, end)
                    remaining = remaining.substring(end).trimStart(' ')
                }
                result += remaining
                result
            }
    }

    fun columns(left: String, right: String, width: Int): List<String> {
        val l = wrap(left, width)
        val r = wrap(right, width)
        return if (l.size == 1 && r.size == 1 && l[0].length + r[0].length <= width) {
            listOf(l[0] + " ".repeat(width - l[0].length - r[0].length) + r[0])
        } else l + r.map { it.padStart(width) }
    }
}
