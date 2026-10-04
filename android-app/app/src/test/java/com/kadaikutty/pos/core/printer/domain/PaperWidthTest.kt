package com.kadaikutty.pos.core.printer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PaperWidthTest {

    @Test
    fun the_three_rolls_a_shop_can_pick() {
        assertEquals(listOf(32, 48, 64), PaperWidth.all)
    }

    @Test
    fun each_roll_maps_to_its_paper_width_in_millimetres() {
        assertEquals(58, PaperWidth.millimetres(PaperWidth.MM_58))
        assertEquals(80, PaperWidth.millimetres(PaperWidth.MM_80))
        assertEquals(112, PaperWidth.millimetres(PaperWidth.MM_112))
    }

    @Test
    fun values_in_between_round_down_to_the_roll_they_fit() {
        assertEquals(58, PaperWidth.millimetres(24))
        assertEquals(58, PaperWidth.millimetres(47))
        assertEquals(80, PaperWidth.millimetres(63))
    }

    @Test
    fun labels_name_the_roll_in_millimetres_and_inches() {
        assertEquals("58 mm · 2 inch", PaperWidth.label(32))
        assertEquals("80 mm · 3 inch", PaperWidth.label(48))
        assertEquals("112 mm · 4 inch", PaperWidth.label(64))
    }
}
