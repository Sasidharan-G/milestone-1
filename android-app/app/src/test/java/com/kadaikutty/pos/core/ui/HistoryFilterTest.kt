package com.kadaikutty.pos.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class HistoryFilterTest {
    private val zone = TimeZone.getTimeZone("Asia/Kolkata")
    private fun at(year: Int, month: Int, day: Int, hour: Int = 0) = Calendar.getInstance(zone).apply {
        clear(); set(year, month - 1, day, hour, 0)
    }.timeInMillis

    // 22 Sep 2026, 15:00 in India.
    private val now = at(2026, 9, 22, 15)

    @Test
    fun `today and yesterday are whole local days`() {
        assertEquals(at(2026, 9, 22) to at(2026, 9, 23), HistoryFilter(preset = DateRangePreset.TODAY).bounds(now, zone))
        assertEquals(at(2026, 9, 21) to at(2026, 9, 22), HistoryFilter(preset = DateRangePreset.YESTERDAY).bounds(now, zone))
    }

    @Test
    fun `last seven days includes today and this month starts on the first`() {
        assertEquals(at(2026, 9, 16) to at(2026, 9, 23), HistoryFilter(preset = DateRangePreset.LAST_7_DAYS).bounds(now, zone))
        assertEquals(at(2026, 9, 1) to at(2026, 9, 23), HistoryFilter(preset = DateRangePreset.THIS_MONTH).bounds(now, zone))
    }

    @Test
    fun `a custom range includes its last day`() {
        val filter = HistoryFilter(preset = DateRangePreset.CUSTOM, customFromEpochMs = at(2026, 8, 1), customToEpochMs = at(2026, 8, 31))
        assertEquals(at(2026, 8, 1) to at(2026, 9, 1), filter.bounds(now, zone))
    }
}
