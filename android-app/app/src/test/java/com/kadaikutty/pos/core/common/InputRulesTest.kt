package com.kadaikutty.pos.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InputRulesTest {
    @Test
    fun `a phone field keeps only ten digits`() {
        assertEquals("9876543210", InputRules.phone("98765abc43210999"))
        assertNull(InputRules.checkPhone("9876543210"))
        assertNotNull(InputRules.checkPhone("1234567890"))
        assertNotNull(InputRules.checkPhone("98765"))
        assertNull(InputRules.checkPhone(""))
        assertNotNull(InputRules.checkPhone("", required = true))
    }

    @Test
    fun `money keeps one point and two decimals`() {
        assertEquals("12.34", InputRules.money("12.345"))
        assertEquals("12.3", InputRules.money("12.3."))
        assertEquals("150", InputRules.money("-150"))
        assertNotNull(InputRules.checkMoney("0"))
        assertNull(InputRules.checkMoney("0", allowZero = true))
        assertNull(InputRules.checkMoney("", required = false))
    }

    @Test
    fun `quantity allows decimals only for weighed items`() {
        assertEquals("5", InputRules.quantity("5.5", allowDecimals = false))
        assertEquals("0.125", InputRules.quantity("0.1256", allowDecimals = true))
        assertNotNull(InputRules.checkQuantity("0"))
    }

    @Test
    fun `gstin is uppercased and checked against the real format`() {
        assertEquals("33ABCDE1234F1Z5", InputRules.gstin("33abcde1234f1z5"))
        assertNull(InputRules.checkGstin("33ABCDE1234F1Z5"))
        assertNotNull(InputRules.checkGstin("33ABCDE1234F1X5"))
        assertNotNull(InputRules.checkGstin("123456789012345"))
        assertNull(InputRules.checkGstin(""))
    }

    @Test
    fun `names need letters and emails need a domain`() {
        assertNotNull(InputRules.checkName("  "))
        assertNotNull(InputRules.checkName("123"))
        assertNull(InputRules.checkName("Ravi Stores"))
        assertNull(InputRules.checkEmail("shop@example.in"))
        assertNotNull(InputRules.checkEmail("shop@example"))
    }
}
