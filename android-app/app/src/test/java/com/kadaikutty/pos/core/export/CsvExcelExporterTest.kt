package com.kadaikutty.pos.core.export

import com.kadaikutty.pos.core.export.data.CsvExcelExporter
import com.kadaikutty.pos.feature.reports.domain.ReportData
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.StandardCharsets

class CsvExcelExporterTest {
    @Test
    fun `export generates standard RFC 4180 csv formatting with proper escaping`() {
        val data = ReportData(
            title = "Test Report",
            columns = listOf("Col A", "Col,B", "Col \"C\""),
            rows = listOf(
                listOf("Val 1", "Val 2", "Val 3"),
                listOf("A, B", "Quote \"", "Line\nBreak")
            )
        )
        
        val exporter = CsvExcelExporter()
        val bytes = exporter.export(data)
        val csvString = String(bytes, StandardCharsets.UTF_8)
        
        val expected = "Col A,\"Col,B\",\"Col \"\"C\"\"\"\r\n" +
                "Val 1,Val 2,Val 3\r\n" +
                "\"A, B\",\"Quote \"\"\",\"Line\nBreak\"\r\n"
        
        assertEquals(expected, csvString)
    }
}
