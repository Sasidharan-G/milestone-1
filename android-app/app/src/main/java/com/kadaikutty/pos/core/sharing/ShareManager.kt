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

    fun shareTextToWhatsApp(text: String, phoneNumber: String?): Boolean {
        if (!phoneNumber.isNullOrBlank()) {
            val formattedPhone = phoneNumber.filter { it.isDigit() }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?phone=$formattedPhone&text=${Uri.encode(text)}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                // If it fails, fallback to general sharing
                shareText(text)
            }
        }
        return shareText(text, PACKAGE_WHATSAPP)
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
                    val bitmap = BitmapFactory.decodeFile(logoFile.absolutePath)
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
        canvas.drawText("Qty", 360f, y, paint)
        canvas.drawText("Rate", 450f, y, paint)
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
            
            val product = productsMap[item.productId]
            val productName = product?.name ?: "Unknown Product"
            val unitType = product?.unitType ?: "PIECE"
            
            val qtyStr = if (unitType == "KG") {
                String.format(Locale.US, "%.3f Kg", item.quantity / 1000.0)
            } else if (unitType == "LITER") {
                String.format(Locale.US, "%.3f Ltr", item.quantity / 1000.0)
            } else {
                "${item.quantity} Pcs"
            }
            
            val rateStr = Money(item.unitPriceMinorUnits).toString()
            val totalStr = Money(item.lineTotalMinorUnits).toString()
            
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(serial.toString(), 40f, y, paint)
            canvas.drawText(productName, 80f, y, paint)
            
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(qtyStr, 360f, y, paint)
            canvas.drawText(rateStr, 450f, y, paint)
            canvas.drawText(totalStr, 550f, y, paint)
            
            serial++
        }
        
        // Table End Divider
        y += 15f
        canvas.drawLine(40f, y, 555f, y, paint)
        
        // 5. Totals
        y += 25f
        paint.textAlign = Paint.Align.RIGHT
        
        val hasGst = gstNumber.isNotBlank()
        val grandTotalMinor = sale.totalMinorUnits
        val subtotalMinor = if (hasGst) (grandTotalMinor * 100 / 118) else grandTotalMinor
        val cgstMinor = if (hasGst) ((grandTotalMinor - subtotalMinor) / 2) else 0L
        val sgstMinor = if (hasGst) (grandTotalMinor - subtotalMinor - cgstMinor) else 0L
        
        val itemCount = items.size
        var totalQtyPieces = 0L
        var totalQtyKg = 0.0
        var totalQtyLiters = 0.0
        for (item in items) {
            val p = productsMap[item.productId]
            if (p?.unitType == "KG") {
                totalQtyKg += (item.quantity / 1000.0)
            } else if (p?.unitType == "LITER") {
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
        if (hasGst) {
            canvas.drawText("Subtotal: ", 450f, y, paint)
            canvas.drawText(Money(subtotalMinor).toString(), 550f, y, paint)
            
            y += 20f
            canvas.drawText("CGST (9%): ", 450f, y, paint)
            canvas.drawText(Money(cgstMinor).toString(), 550f, y, paint)
            
            y += 20f
            canvas.drawText("SGST (9%): ", 450f, y, paint)
            canvas.drawText(Money(sgstMinor).toString(), 550f, y, paint)
            
            y += 25f
        }
        
        paint.isFakeBoldText = true
        paint.textSize = 13f
        canvas.drawText("Grand Total: ", 450f, y, paint)
        canvas.drawText(Money(grandTotalMinor).toString(), 550f, y, paint)
        
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
        canvas.drawText("Thank You for Shopping! \uD83D\uDE0A", 297.5f, y, paint)
        y += 18f
        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas.drawText("Please Visit Again \uD83D\uDECD️", 297.5f, y, paint)
        
        pdfDocument.finishPage(page)
        
        val outputStream = ByteArrayOutputStream()
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
        
        return outputStream.toByteArray()
    }
}
