package com.kadaikutty.pos.core.sharing

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.kadaikutty.pos.core.common.Money
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import android.graphics.BitmapFactory
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShareManager(private val context: Context) {

    companion object {
        const val PACKAGE_WHATSAPP = "com.whatsapp"
        const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    }

    fun shareText(text: String, packageId: String? = null): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }

        if (packageId != null && isAppInstalled(packageId)) {
            intent.setPackage(packageId)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return true
        }

        return try {
            val chooser = Intent.createChooser(intent, "Share via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun shareFile(fileBytes: ByteArray, filename: String, mimeType: String, packageId: String? = null): Boolean {
        val file = saveToCache(fileBytes, filename) ?: return false
        val uri: Uri = try {
            FileProvider.getUriForFile(context, "com.kadaikutty.pos.fileprovider", file)
        } catch (e: IllegalArgumentException) {
            return false
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        if (packageId != null && isAppInstalled(packageId)) {
            intent.setPackage(packageId)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return true
        }

        return try {
            val chooser = Intent.createChooser(intent, "Share Document").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isAppInstalled(packageId: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageId, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun saveToCache(bytes: ByteArray, filename: String): File? {
        val cacheDir = context.cacheDir
        val file = File(cacheDir, filename)
        return try {
            FileOutputStream(file).use { fos ->
                fos.write(bytes)
            }
            file
        } catch (e: IOException) {
            null
        }
    }

    /**
     * The thermal receipt as a PDF at the paper's own width (58 mm or 80 mm), same lines as the
     * printer gets, so a bill shared on WhatsApp or sent to a mobile printer app prints at receipt
     * size instead of an A4 page shrunk to a strip.
     */
    fun generateReceiptPdf(doc: com.kadaikutty.pos.core.printer.domain.PrintDocument): ByteArray {
        val columns = doc.paperWidth.takeIf { it in 24..64 } ?: 32
        val pageWidth = if (columns >= 48) 227 else 164 // 80 mm / 58 mm in PDF points
        val margin = 8f
        val mono = android.graphics.Typeface.MONOSPACE
        val paint = Paint().apply { color = Color.BLACK; isAntiAlias = true; typeface = mono }
        // Size the monospace font so exactly [columns] characters fill the printable width.
        paint.textSize = 10f
        paint.textSize = 10f * (pageWidth - 2 * margin) / paint.measureText("M".repeat(columns))
        val lineHeight = paint.textSize * 1.35f

        data class Row(val text: String, val bold: Boolean = false, val center: Boolean = false)
        val rows = mutableListOf<Row>()
        fun add(text: String, bold: Boolean = false, center: Boolean = false) =
            com.kadaikutty.pos.core.printer.data.ReceiptLayout.wrap(text, columns).forEach { rows += Row(it, bold, center) }
        add(doc.title, bold = true, center = true)
        doc.headers.forEach { add(it) }
        rows += Row("-".repeat(columns))
        doc.lines.forEach { line ->
            add(line.name, bold = true)
            com.kadaikutty.pos.core.printer.data.ReceiptLayout.columns("${line.quantityText} x ${line.price}", line.total, columns).forEach { rows += Row(it) }
        }
        rows += Row("-".repeat(columns))
        doc.totals.forEach { (label, value) ->
            com.kadaikutty.pos.core.printer.data.ReceiptLayout.columns(label, value, columns).forEach { rows += Row(it, bold = true) }
        }
        if (doc.footer.isNotBlank()) { rows += Row(""); add(doc.footer, center = true) }

        val pageHeight = (margin * 2 + lineHeight * (rows.size + 1)).toInt()
        val pdf = PdfDocument()
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)
        var y = margin + lineHeight
        rows.forEach { row ->
            paint.isFakeBoldText = row.bold
            val x = if (row.center) (pageWidth - paint.measureText(row.text)) / 2f else margin
            canvas.drawText(row.text, x, y, paint)
            y += lineHeight
        }
        pdf.finishPage(page)
        val out = java.io.ByteArrayOutputStream()
        pdf.writeTo(out)
        pdf.close()
        return out.toByteArray()
    }

    fun generatePdfInvoice(
        sale: com.kadaikutty.pos.feature.billing.data.SaleEntity,
        items: List<com.kadaikutty.pos.feature.billing.data.SaleItemEntity>,
        productsMap: Map<String, com.kadaikutty.pos.feature.masters.data.ProductEntity>,
        customerName: String,
        shopName: String,
        ownerName: String,
        gstNumber: String,
        shopAddress: String,
        shopPhone: String,
        shopEmail: String,
        cashierName: String,
        shopLogoPath: String = ""
    ): ByteArray {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint()
        
        // 1. Draw header (Shop Details)
        paint.color = Color.BLACK
        
        var y = 60f
        
        if (shopLogoPath.isNotBlank()) {
            try {
                val logoFile = File(shopLogoPath)
                if (logoFile.exists()) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    val bitmap = BitmapFactory.decodeFile(logoFile.absolutePath, opts)
                    if (bitmap != null) {
                        val maxDim = 80f
                        val scale = Math.min(maxDim / bitmap.width, maxDim / bitmap.height)
                        val dstWidth = bitmap.width * scale
                        val dstHeight = bitmap.height * scale
                        val destRect = android.graphics.RectF(40f, 40f, 40f + dstWidth, 40f + dstHeight)
                        canvas.drawBitmap(bitmap, null, destRect, paint)
                    }
                }
            } catch (ignored: Exception) {}
        }
        
        paint.textAlign = Paint.Align.CENTER
        
        // Shop Name
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText(shopName.ifBlank { "Client Billing System" }, 297.5f, y, paint)
        
        // Address & Phone
        paint.textSize = 10f
        paint.isFakeBoldText = false
        if (shopAddress.isNotBlank()) {
            y += 18f
            canvas.drawText(shopAddress, 297.5f, y, paint)
        }
        if (shopPhone.isNotBlank()) {
            y += 16f
            canvas.drawText("Phone: $shopPhone", 297.5f, y, paint)
        }
        if (shopEmail.isNotBlank()) {
            y += 16f
            canvas.drawText("Email: $shopEmail", 297.5f, y, paint)
        }
        
        // Owner Name & GST
        if (ownerName.isNotBlank() || gstNumber.isNotBlank()) {
            y += 16f
            val details = listOfNotNull(
                if (ownerName.isNotBlank()) "Proprietor: $ownerName" else null,
                if (gstNumber.isNotBlank()) "GSTIN: $gstNumber" else null
            ).joinToString("  |  ")
            canvas.drawText(details, 297.5f, y, paint)
        }
        
        // Make sure we have space under the logo if shop details were very short
        y = Math.max(y, 120f)
        
        // Divider
        y += 18f
        paint.strokeWidth = 1f
        canvas.drawLine(40f, y, 555f, y, paint)
        
        // 2. Bill & Customer Metadata
        y += 25f
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 11f
        
        // Left Column: Bill details
        val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
        val dateStr = sdf.format(Date(sale.createdAtEpochMs))
        // A GST-registered shop's bill is a tax invoice; the rate-wise tax is shown below the total.
        val gstRegistered = gstNumber.isNotBlank()
        // Tax needs each line's rate as it was on the day of the bill; bills made before GST
        // support have none, and inventing today's rates for them would misstate old tax.
        val gstLines = if (gstRegistered && items.isNotEmpty() && items.all { it.gstRateBps != null })
            items.map { (it.netRevenueMinorUnits ?: it.lineTotalMinorUnits) to it.gstRateBps!! } else null
        if (gstRegistered) {
            paint.isFakeBoldText = true
            canvas.drawText("TAX INVOICE", 40f, y, paint)
            paint.isFakeBoldText = false
            y += 16f
        }
        canvas.drawText("Bill No: ${sale.billNumber}", 40f, y, paint)
        y += 16f
        canvas.drawText("Date: $dateStr", 40f, y, paint)
        y += 16f
        canvas.drawText("Cashier: $cashierName", 40f, y, paint)
        
        // Right Column: Customer details
        val custY = y - 18f
        canvas.drawText("To: $customerName", 350f, custY, paint)
        
        // Divider
        y += 22f
        canvas.drawLine(40f, y, 555f, y, paint)
        
        // 3. Table Headers
        y += 25f
        paint.isFakeBoldText = true
        canvas.drawText("S.No", 40f, y, paint)
        canvas.drawText("Item Description", 80f, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Qty", 340f, y, paint)
        canvas.drawText("Rate", 440f, y, paint)
        canvas.drawText("Total", 550f, y, paint)
        
        // Table Header Divider
        y += 10f
        paint.strokeWidth = 1.5f
        canvas.drawLine(40f, y, 555f, y, paint)
        paint.strokeWidth = 1f
        paint.isFakeBoldText = false
        
        // 4. Draw Rows
        var serial = 1
        for (item in items) {
            y += 22f
            
            // Name and unit as saved on the bill, not the product's current ones: a later rename or
            // unit change must not rewrite an old invoice (1.500 kg reprinting as "1500 Pcs").
            val product = productsMap[item.productId]
            val productName = item.productName ?: product?.name ?: "Unknown Product"
            val unitType = item.unitType ?: product?.unitType ?: "PIECE"

            val qtyStr = com.kadaikutty.pos.feature.stock.domain.formatQuantity(item.quantity, unitType)
            val perUnit = if (com.kadaikutty.pos.feature.stock.domain.isThousandthsUnit(unitType)) "/${com.kadaikutty.pos.feature.stock.domain.unitShortLabel(unitType)}" else ""
            val rateStr = Money(item.unitPriceMinorUnits).toString() + perUnit
            val totalStr = Money(item.lineTotalMinorUnits).toString()
            
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(serial.toString(), 40f, y, paint)
            canvas.drawText(productName, 80f, y, paint)
            
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(qtyStr, 340f, y, paint)
            canvas.drawText(rateStr, 440f, y, paint)
            canvas.drawText(totalStr, 550f, y, paint)

            if (gstLines != null) {
                y += 12f
                paint.textAlign = Paint.Align.LEFT
                paint.textSize = 8.5f
                val hsn = item.hsnCode?.let { "HSN $it  ·  " } ?: ""
                canvas.drawText("${hsn}GST ${com.kadaikutty.pos.core.common.GstMath.label(item.gstRateBps ?: 0)} incl.", 80f, y, paint)
                paint.textSize = 11f
            }

            serial++
        }
        
        // Table End Divider
        y += 15f
        canvas.drawLine(40f, y, 555f, y, paint)
        
        // 5. Totals
        y += 25f
        paint.textAlign = Paint.Align.RIGHT
        
        // The tax split comes from each line's own GST rate (the summary under the total); bills
        // from before GST support carry no rates and just say prices include tax.
        val hasGst = gstRegistered
        val grandTotalMinor = sale.totalMinorUnits
        val subtotalMinor = items.sumOf { it.lineTotalMinorUnits }
        val discountMinor = sale.discountMinorUnits
        
        val itemCount = items.size
        var totalQtyPieces = 0L
        var totalQtyKg = 0.0
        var totalQtyLiters = 0.0
        for (item in items) {
            val unit = item.unitType ?: productsMap[item.productId]?.unitType
            if (unit == "KG") {
                totalQtyKg += (item.quantity / 1000.0)
            } else if (unit == "LITER") {
                totalQtyLiters += (item.quantity / 1000.0)
            } else {
                totalQtyPieces += item.quantity
            }
        }
        
        // Draw Item Counts on the left
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 10f
        paint.isFakeBoldText = false
        var countY = y
        canvas.drawText("Items Count: $itemCount", 40f, countY, paint)
        countY += 16f
        if (totalQtyPieces > 0) {
            canvas.drawText("Total Pcs: $totalQtyPieces", 40f, countY, paint)
            countY += 16f
        }
        if (totalQtyKg > 0.0) {
            canvas.drawText(String.format(Locale.US, "Total Wt: %.3f Kg", totalQtyKg), 40f, countY, paint)
            countY += 16f
        }
        if (totalQtyLiters > 0.0) {
            canvas.drawText(String.format(Locale.US, "Total Vol: %.3f Ltr", totalQtyLiters), 40f, countY, paint)
        }
        
        // Draw amounts on the right
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 11f
        if (discountMinor > 0L) {
            canvas.drawText("Subtotal: ", 450f, y, paint)
            canvas.drawText(Money(subtotalMinor).toString(), 550f, y, paint)

            y += 20f
            canvas.drawText("Discount: ", 450f, y, paint)
            canvas.drawText("- " + Money(discountMinor).toString(), 550f, y, paint)

            y += 25f
        }
        
        paint.isFakeBoldText = true
        paint.textSize = 13f
        canvas.drawText("Grand Total: ", 450f, y, paint)
        canvas.drawText(Money(grandTotalMinor).toString(), 550f, y, paint)
        if (gstLines != null) {
            y += 22f
            paint.isFakeBoldText = true
            paint.textSize = 9.5f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("GST", 300f, y, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Taxable", 400f, y, paint)
            canvas.drawText("CGST", 475f, y, paint)
            canvas.drawText("SGST", 550f, y, paint)
            paint.isFakeBoldText = false
            com.kadaikutty.pos.core.common.GstMath.summarize(gstLines).forEach { row ->
                y += 14f
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(com.kadaikutty.pos.core.common.GstMath.label(row.rateBps), 300f, y, paint)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(Money(row.taxable).toString(), 400f, y, paint)
                canvas.drawText(Money(row.cgst).toString(), 475f, y, paint)
                canvas.drawText(Money(row.sgst).toString(), 550f, y, paint)
            }
            paint.textSize = 13f
        }
        if (sale.status == com.kadaikutty.pos.feature.billing.data.SaleStatus.VOID) {
            // A cancelled bill is kept for the record; its copy must never pass for a live one.
            y += 18f
            paint.color = Color.rgb(200, 30, 30)
            canvas.drawText("*** CANCELLED BILL ***", 550f, y, paint)
            paint.color = Color.BLACK
        }
        if (hasGst) {
            y += 16f
            paint.isFakeBoldText = false
            paint.textSize = 9f
            canvas.drawText("(Inclusive of all taxes)", 550f, y, paint)
        }
        
        // 6. Terms & Footer
        y += 40f
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 9f
        paint.isFakeBoldText = true
        canvas.drawText("Terms & Conditions:", 40f, y, paint)
        paint.isFakeBoldText = false
        y += 14f
        canvas.drawText("1. Goods once sold will not be taken back or exchanged.", 40f, y, paint)
        y += 14f
        canvas.drawText("2. We are not responsible for any damage after goods leave the store.", 40f, y, paint)
        
        y += 30f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.textSize = 12f
        canvas.drawText("Thank You for Shopping!", 297.5f, y, paint)
        y += 18f
        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas.drawText("Please Visit Again", 297.5f, y, paint)
        
        pdfDocument.finishPage(page)
        
        val outputStream = ByteArrayOutputStream()
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
        
        return outputStream.toByteArray()
    }
}
