package com.kadaikutty.pos.core.common

/**
 * GST for a retail shop whose sale prices already include tax. Rates are basis points (500 = 5%).
 * A line's amount is split into its taxable value and the tax inside it; an intra-state sale
 * shows that tax as equal CGST and SGST halves.
 */
object GstMath {
    /** The GST slabs a product can carry, in basis points: 0, 0.25, 3, 5, 12, 18, 28 %. */
    val RATES_BPS = listOf(0, 25, 300, 500, 1200, 1800, 2800)

    fun label(rateBps: Int): String {
        val whole = rateBps / 100
        val frac = rateBps % 100
        return if (frac == 0) "$whole%" else "$whole.${frac.toString().padStart(2, '0').trimEnd('0')}%"
    }

    data class Split(val taxable: Long, val tax: Long) {
        val cgst: Long get() = tax / 2
        val sgst: Long get() = tax - cgst
    }

    /** [inclusiveMinorUnits] (paise, tax included) at [rateBps] -> taxable value + tax, rounded to the paisa. */
    fun splitInclusive(inclusiveMinorUnits: Long, rateBps: Int): Split {
        require(inclusiveMinorUnits >= 0 && rateBps in 0..10_000)
        if (rateBps == 0 || inclusiveMinorUnits == 0L) return Split(inclusiveMinorUnits, 0)
        val taxable = java.math.BigDecimal.valueOf(inclusiveMinorUnits)
            .multiply(java.math.BigDecimal.valueOf(10_000))
            .divide(java.math.BigDecimal.valueOf(10_000L + rateBps), 0, java.math.RoundingMode.HALF_UP)
            .toLong()
        return Split(taxable, inclusiveMinorUnits - taxable)
    }

    data class RateSummary(val rateBps: Int, val taxable: Long, val cgst: Long, val sgst: Long) {
        val tax: Long get() = cgst + sgst
    }

    /**
     * One row per rate for the invoice footer. [lines] are (amount actually charged for the line,
     * after any bill discount; rate). Each line is split on its own and the rows summed, so the rows
     * always add back up to the bill total to the paisa.
     */
    fun summarize(lines: List<Pair<Long, Int>>): List<RateSummary> =
        lines.groupBy({ it.second }, { splitInclusive(it.first, it.second) })
            .map { (rate, splits) -> RateSummary(rate, splits.sumOf { it.taxable }, splits.sumOf { it.cgst }, splits.sumOf { it.sgst }) }
            .sortedBy { it.rateBps }
}
