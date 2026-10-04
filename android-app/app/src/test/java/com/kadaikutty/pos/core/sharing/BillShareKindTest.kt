package com.kadaikutty.pos.core.sharing

import org.junit.Assert.assertEquals
import org.junit.Test

class BillShareKindTest {

    @Test
    fun receipt_format_goes_out_as_a_picture() {
        val kind = BillShareKind.forFormat("RECEIPT")
        assertEquals(BillShareKind.IMAGE, kind)
        assertEquals("image/png", kind.mimeType)
        assertEquals("png", kind.extension)
    }

    @Test
    fun a4_format_goes_out_as_a_pdf() {
        val kind = BillShareKind.forFormat("A4")
        assertEquals(BillShareKind.PDF, kind)
        assertEquals("application/pdf", kind.mimeType)
        assertEquals("pdf", kind.extension)
    }

    @Test
    fun an_unknown_or_missing_format_falls_back_to_the_pdf() {
        assertEquals(BillShareKind.PDF, BillShareKind.forFormat(""))
        assertEquals(BillShareKind.PDF, BillShareKind.forFormat("something else"))
    }
}
