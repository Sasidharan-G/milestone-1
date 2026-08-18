package com.company.billing.core.export.data

import com.company.billing.core.export.domain.ExcelExporter
import com.company.billing.feature.reports.domain.ReportData
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class CsvExcelExporter : ExcelExporter {
    override fun export(data: ReportData): ByteArray {
        val bos = ByteArrayOutputStream()
        val writer = OutputStreamWriter(bos, StandardCharsets.UTF_8)
        
        // Write headers
        writer.write(data.columns.joinToString(",") { escapeCsv(it) })
        writer.write("\r\n")
        
        // Write rows
        for (row in data.rows) {
            writer.write(row.joinToString(",") { escapeCsv(it) })
            writer.write("\r\n")
        }
        
        writer.flush()
        return bos.toByteArray()
    }
    
    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) {
            "\"$escaped\""
        } else {
            escaped
        }
    }
}
