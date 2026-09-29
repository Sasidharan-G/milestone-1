package com.kadaikutty.pos.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class GstMathTest {
    @Test
    fun `an inclusive price splits into taxable value and equal CGST and SGST`() {
        // Rs 118 at 18%: Rs 100 taxable + Rs 18 tax (9 + 9).
        val s = GstMath.splitInclusive(11_800, 1800)
        assertEquals(10_000L, s.taxable)
        assertEquals(900L, s.cgst)
        assertEquals(900L, s.sgst)
        // Rs 105 at 5%.
        assertEquals(10_000L, GstMath.splitInclusive(10_500, 500).taxable)
        // Exempt goods carry no tax.
        assertEquals(GstMath.Split(4_200, 0), GstMath.splitInclusive(4_200, 0))
    }

    @Test
    fun `every paisa is accounted for, odd tax goes to SGST`() {
        for (amount in listOf(1L, 99L, 7_777L, 123_457L)) for (rate in GstMath.RATES_BPS) {
            val s = GstMath.splitInclusive(amount, rate)
            assertEquals(amount, s.taxable + s.cgst + s.sgst)
            assertEquals(true, s.sgst - s.cgst in 0..1)
        }
    }

    @Test
    fun `the rate summary adds back up to the bill`() {
        val lines = listOf(5_200L to 500, 11_800L to 1800, 3_000L to 0, 2_100L to 500)
        val summary = GstMath.summarize(lines)
        assertEquals(listOf(0, 500, 1800), summary.map { it.rateBps })
        assertEquals(lines.sumOf { it.first }, summary.sumOf { it.taxable + it.tax })
    }

    @Test
    fun `rates print as percentages`() {
        assertEquals("18%", GstMath.label(1800))
        assertEquals("0.25%", GstMath.label(25))
        assertEquals("0%", GstMath.label(0))
    }
}
