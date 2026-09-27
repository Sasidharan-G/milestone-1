package com.kadaikutty.pos.feature.stock.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** What a bulk-import file's free-text Unit column is mapped to. */
class UnitTypeNormalizationTest {
    @Test
    fun `the five original unit types pass through unchanged, case-insensitively`() {
        assertEquals("PIECE", normalizeUnitType("piece"))
        assertEquals("KG", normalizeUnitType("kg"))
        assertEquals("LITER", normalizeUnitType("Liter"))
        assertEquals("BOX", normalizeUnitType("box"))
        assertEquals("PACK", normalizeUnitType("Pack"))
    }

    @Test
    fun `common shorthand a shop actually writes maps to the right type`() {
        assertEquals("KG", normalizeUnitType("Kgs"))
        assertEquals("KG", normalizeUnitType("Kilo"))
        assertEquals("LITER", normalizeUnitType("Ltr"))
        assertEquals("LITER", normalizeUnitType("Litres"))
        assertEquals("PIECE", normalizeUnitType("Pcs"))
        assertEquals("PIECE", normalizeUnitType("Nos"))
        assertEquals("PACK", normalizeUnitType("Pkt"))
        assertEquals("BOTTLE", normalizeUnitType("Bottles"))
        assertEquals("BAG", normalizeUnitType("Bags"))
    }

    @Test
    fun `products_100_bulk_import's own units (Bag, Bottle, Tub) are supported, not folded into Piece`() {
        assertEquals("BAG", normalizeUnitType("BAG"))
        assertEquals("BOTTLE", normalizeUnitType("BOTTLE"))
        assertEquals("TUB", normalizeUnitType("TUB"))
    }

    @Test
    fun `blank or unrecognised text defaults to Piece, never crashes`() {
        assertEquals("PIECE", normalizeUnitType(null))
        assertEquals("PIECE", normalizeUnitType(""))
        assertEquals("PIECE", normalizeUnitType("   "))
        assertEquals("PIECE", normalizeUnitType("XYZ garbage"))
    }
}
