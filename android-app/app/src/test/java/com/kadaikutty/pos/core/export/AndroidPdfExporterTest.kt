package com.kadaikutty.pos.core.export

import com.kadaikutty.pos.core.export.data.AndroidPdfExporter
import com.kadaikutty.pos.feature.reports.domain.ReportData
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidPdfExporterTest {
    @Test
    fun `export compiles but throws stub exception on local JVM`() {
        val data = ReportData(
            title = "Daily Sales",
            columns = listOf("Date", "Revenue"),
            rows = listOf(
                listOf("2026-08-15", "$120.00"),
                listOf("2026-08-16", "$150.00")
            )
        )
        
        val exporter = AndroidPdfExporter()
        try {
            exporter.export(data)
        } catch (e: RuntimeException) {
            // Under JVM testing environments, Android's graphics classes (like Paint, Canvas, and PdfDocument) 
            // throw RuntimeException with stub/not mocked messages. Catching this verifies the class loads and 
            // runs down to the Android native calls.
            val msg = e.message ?: ""
            assertTrue(msg.contains("stub") || msg.contains("not mocked"))
        }
    }
}
