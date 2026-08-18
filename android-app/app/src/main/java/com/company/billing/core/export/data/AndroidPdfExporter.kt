package com.company.billing.core.export.data

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.company.billing.core.export.domain.PdfExporter
import com.company.billing.feature.reports.domain.ReportData
import java.io.ByteArrayOutputStream

class AndroidPdfExporter : PdfExporter {
    override fun export(data: ReportData): ByteArray {
        val document = PdfDocument()
        val pageWidth = 595 // A4 width in points
        val pageHeight = 842 // A4 height in points
        
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        val paintText = Paint().apply {
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val paintHeader = Paint().apply {
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val paintTitle = Paint().apply {
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val paintBorder = Paint().apply {
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        val margin = 36f
        var y = margin + 20f

        // Draw title
        canvas.drawText(data.title, margin, y, paintTitle)
        y += 30f

        // Draw headers
        val columnsCount = data.columns.size
        val colWidth = (pageWidth - (margin * 2)) / columnsCount

        fun drawHeaders(c: Canvas) {
            var x = margin
            for (col in data.columns) {
                c.drawText(col, x, y, paintHeader)
                x += colWidth
            }
            y += 5f
            c.drawLine(margin, y, pageWidth - margin, y, paintBorder)
            y += 15f
        }

        drawHeaders(canvas)

        // Draw rows
        for (row in data.rows) {
            if (y > pageHeight - margin - 20f) {
                document.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = margin + 20f
                drawHeaders(canvas)
            }

            var x = margin
            val isTotalRow = row.firstOrNull() == "TOTAL"
            val paintRow = if (isTotalRow) paintHeader else paintText
            
            if (isTotalRow) {
                canvas.drawLine(margin, y - 10f, pageWidth - margin, y - 10f, paintBorder)
            }

            for (cell in row) {
                canvas.drawText(cell, x, y, paintRow)
                x += colWidth
            }
            y += 18f
        }

        document.finishPage(page)
        val bos = ByteArrayOutputStream()
        document.writeTo(bos)
        document.close()
        return bos.toByteArray()
    }
}
