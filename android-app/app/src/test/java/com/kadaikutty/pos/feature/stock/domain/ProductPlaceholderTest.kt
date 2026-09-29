package com.kadaikutty.pos.feature.stock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Compares only against [ProductPlaceholder.GENERAL] and other calls to [ProductPlaceholder],
 * never a literal `R.drawable.*` written in this file: a unit test's own R class is resolved
 * separately from the app's main R class (this project has `android.nonTransitiveRClass=true`),
 * so the two do not reliably assign the same numeric id to the same resource. Comparing against
 * the app code's own constant sidesteps that entirely.
 */
class ProductPlaceholderTest {
    @Test
    fun `a product name picks a category-specific placeholder, distinct from other categories`() {
        val atta = ProductPlaceholder.forProduct("Aashirvaad Atta 1kg")
        val dairy = ProductPlaceholder.forProduct("Amul Milk 500ml")
        val cleaning = ProductPlaceholder.forProduct("Surf Excel Detergent")
        assertNotEquals(atta, dairy)
        assertNotEquals(dairy, cleaning)
        assertNotEquals(atta, cleaning)
        // Matching is case-insensitive and stable for the same product name.
        assertEquals(atta, ProductPlaceholder.forProduct("AASHIRVAAD ATTA 1KG"))
    }

    @Test
    fun `a longer, more specific phrase wins over a shorter generic keyword`() {
        // "hair oil" (personal care) must not fall through to the bare "oil" (edible oil) keyword.
        val hairOil = ProductPlaceholder.forProduct("Parachute Hair Oil")
        val edibleOil = ProductPlaceholder.forProduct("Sunflower Oil 1L")
        assertNotEquals(hairOil, edibleOil)
    }

    @Test
    fun `an unrecognised name falls back to the category, then to General`() {
        val dairyByName = ProductPlaceholder.forProduct("Amul Milk 500ml")
        val dairyByCategory = ProductPlaceholder.forProduct("XYZ Brand 500ml", "Dairy Products")
        assertEquals(dairyByName, dairyByCategory)
        assertEquals(ProductPlaceholder.GENERAL, ProductPlaceholder.forProduct("XYZ Brand 500ml", "General"))
        assertEquals(ProductPlaceholder.GENERAL, ProductPlaceholder.forProduct("Random Item"))
    }
}
