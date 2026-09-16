package com.kadaikutty.pos.core.export.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import android.text.Layout
import com.kadaikutty.pos.core.export.domain.PdfExporter
import com.kadaikutty.pos.feature.reports.domain.ReportData
import java.io.ByteArrayOutputStream

class AndroidPdfExporter : PdfExporter {
    override fun export(data: ReportData): ByteArray {
        val document = PdfDocument()
        val pageWidth = 595 // A4 width in points
        val pageHeight = 842 // A4 height in points
        val margin = 36f
        
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = TextPaint().apply {
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.BLACK
        }
        val subTitlePaint = TextPaint().apply {
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            color = Color.DKGRAY
        }
        val headerPaint = TextPaint().apply {
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.WHITE
        }
        val cellPaint = TextPaint().apply {
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            color = Color.BLACK
        }
        val boldCellPaint = TextPaint().apply {
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.BLACK
        }
        val borderPaint = Paint().apply {
            strokeWidth = 0.5f
            style = Paint.Style.STROKE
            color = Color.LTGRAY
        }
        val headerBgPaint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#424242")
        }
        val altRowBgPaint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#F5F5F5")
        }

        var y = margin
        
        // Draw title
        canvas.drawText(data.title, margin, y + 16f, titlePaint)
        y += 24f

        val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
        val generatedStr = "Generated on: ${dateFormat.format(java.util.Date(data.generatedAtEpochMs))}"
        val periodStr = if (data.fromEpochMs != null || data.toEpochMs != null) {
            val fromStr = data.fromEpochMs?.let { dateFormat.format(java.util.Date(it)) } ?: "Beginning"
            val toStr = data.toEpochMs?.let { dateFormat.format(java.util.Date(it)) } ?: "Now"
            "Period: $fromStr  to  $toStr"
        } else {
            "Period: All Time"
        }
        canvas.drawText("$periodStr  |  $generatedStr", margin, y + 10f, subTitlePaint)
        y += 22f

        val columnsCount = data.columns.size
        val usableWidth = pageWidth - (margin * 2)
        val colWidths = FloatArray(columnsCount)
        var fixedWidths = 0f
        var dynamicCols = 0
        
        for (i in 0 until columnsCount) {
            val colName = data.columns[i].lowercase()
            if (colName == "s.no" || colName == "qty" || colName == "unit") {
                colWidths[i] = 30f
                fixedWidths += 30f
            } else if (colName == "qty sold") {
                colWidths[i] = 45f
                fixedWidths += 45f
            } else if (colName == "last activity" || colName == "date & time") {
                colWidths[i] = 80f
                fixedWidths += 80f
            } else {
                dynamicCols++
            }
        }
        
        val dynamicWidth = if (dynamicCols > 0) (usableWidth - fixedWidths) / dynamicCols else 50f
        for (i in 0 until columnsCount) {
            if (colWidths[i] == 0f) {
                colWidths[i] = dynamicWidth
            }
        }

        fun drawHeaders(c: Canvas): Float {
            c.drawRect(margin, y, pageWidth - margin, y + 25f, headerBgPaint)
            var x = margin
            for (i in 0 until columnsCount) {
                val text = data.columns[i]
                c.drawText(text, x + 4f, y + 17f, headerPaint)
                x += colWidths[i]
            }
            return y + 25f
        }

        y = drawHeaders(canvas)
        
        var rowIndex = 0

        for (row in data.rows) {
            val isTotalRow = row.firstOrNull() == "" && row.getOrNull(1)?.startsWith("---") == false && row.getOrNull(1)?.isNotBlank() == true
            val isDivider = row.getOrNull(1)?.startsWith("---") == true
            
            if (isDivider) {
                y += 5f
                canvas.drawLine(margin, y, pageWidth - margin, y, borderPaint)
                y += 5f
                continue
            }

            val currentPaint = if (isTotalRow) boldCellPaint else cellPaint
            
            var maxRowHeight = 20f
            val layouts = arrayOfNulls<StaticLayout>(columnsCount)
            
            for (i in 0 until columnsCount) {
                val cellText = row.getOrNull(i) ?: ""
                val textWidth = (colWidths[i] - 8f).toInt()
                if (textWidth > 0 && cellText.isNotBlank()) {
                    val align = if ((cellText.contains("₹") || cellText.matches(Regex(".*[0-9].*"))) && i > 1) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
                    val layout = StaticLayout.Builder.obtain(cellText, 0, cellText.length, currentPaint, textWidth)
                        .setAlignment(align)
                        .setLineSpacing(0f, 1f)
                        .setIncludePad(false)
                        .build()
                    layouts[i] = layout
                    if (layout.height + 10f > maxRowHeight) {
                        maxRowHeight = layout.height + 10f
                    }
                }
            }

            if (y + maxRowHeight > pageHeight - margin) {
                document.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = margin
                y = drawHeaders(canvas)
            }

            if (!isTotalRow && rowIndex % 2 == 0) {
                canvas.drawRect(margin, y, pageWidth - margin, y + maxRowHeight, altRowBgPaint)
            }
            
            canvas.drawRect(margin, y, pageWidth - margin, y + maxRowHeight, borderPaint)
            
            var x = margin
            for (i in 0 until columnsCount) {
                if (i > 0) {
                    canvas.drawLine(x, y, x, y + maxRowHeight, borderPaint)
                }
                val layout = layouts[i]
                if (layout != null) {
                    canvas.save()
                    canvas.translate(x + 4f, y + 5f)
                    layout.draw(canvas)
                    canvas.restore()
                }
                x += colWidths[i]
            }
            
            y += maxRowHeight
            if (!isTotalRow) {
                rowIndex++
            }
        }

        document.finishPage(page)
        val bos = ByteArrayOutputStream()
        document.writeTo(bos)
        document.close()
        return bos.toByteArray()
    }
}
