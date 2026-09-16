package com.kadaikutty.pos.core.export.data

import com.kadaikutty.pos.core.export.domain.ExcelExporter
import com.kadaikutty.pos.feature.reports.domain.ReportData
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class CsvExcelExporter : ExcelExporter {
    override fun export(data: ReportData): ByteArray {
        val bos = ByteArrayOutputStream()
        val writer = OutputStreamWriter(bos, StandardCharsets.UTF_8)
        
        val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
        val generatedStr = "Generated on: ${dateFormat.format(java.util.Date(data.generatedAtEpochMs))}"
        val periodStr = if (data.fromEpochMs != null || data.toEpochMs != null) {
            val fromStr = data.fromEpochMs?.let { dateFormat.format(java.util.Date(it)) } ?: "Beginning"
            val toStr = data.toEpochMs?.let { dateFormat.format(java.util.Date(it)) } ?: "Now"
            "Period: $fromStr to $toStr"
        } else {
            "Period: All Time"
        }

        // Write title & metadata header lines
        writer.write(escapeCsv(data.title) + "\r\n")
        writer.write(escapeCsv(periodStr) + "\r\n")
        writer.write(escapeCsv(generatedStr) + "\r\n\r\n")

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
