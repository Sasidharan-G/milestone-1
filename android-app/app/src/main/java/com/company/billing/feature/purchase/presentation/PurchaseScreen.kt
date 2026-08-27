package com.company.billing.feature.purchase.presentation

import com.company.billing.core.ui.LocalLayoutMode

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.company.billing.core.common.Money
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.QrCodeScanner
import com.company.billing.core.ui.CameraBarcodeScannerDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.foundation.clickable
import com.company.billing.feature.purchase.domain.PurchaseLine
import com.company.billing.feature.purchase.data.PurchaseEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseScreen(viewModel: PurchaseViewModel) {
    val products by viewModel.products.collectAsState()
    val suppliers by viewModel.suppliers.collectAsState()
    val purchases by viewModel.purchases.collectAsState()
    val lines by viewModel.lines.collectAsState()
    val selectedSupplierId by viewModel.selectedSupplierId.collectAsState()

    var selectedProductId by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("1") }
    var costText by remember { mutableStateOf("") }

    var expandedProduct by remember { mutableStateOf(false) }
    var expandedSupplier by remember { mutableStateOf(false) }
    var supplierInvoiceNumber by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var showCameraScanner by remember { mutableStateOf(false) }
    var editingPurchaseLine by remember { mutableStateOf<PurchaseLine?>(null) }

    if (showCameraScanner) {
        CameraBarcodeScannerDialog(
            title = "Scan Product for Purchase",
            continuousScan = false,
            onBarcodeScanned = { barcode ->
                val matched = products.find { it.barcode == barcode }
                if (matched != null) {
                    selectedProductId = matched.id
                    costText = String.format(Locale.US, "%.2f", matched.purchasePriceMinorUnits / 100.0)
                    message = "Selected: ${matched.name}"
                } else {
                    message = "Barcode not found in stock: $barcode"
                }
                showCameraScanner = false
            },
            onDismiss = { showCameraScanner = false }
        )
    }

    if (editingPurchaseLine != null) {
        val line = editingPurchaseLine!!
        val prod = products.find { it.id == line.productId }
        val isDec = prod?.unitType == "KG" || prod?.unitType == "LITER"
        var inputQty by remember(line) {
            mutableStateOf(if (isDec) String.format(Locale.US, "%.3f", line.quantity / 1000.0) else line.quantity.toString())
        }

        AlertDialog(
            onDismissRequest = { editingPurchaseLine = null },
            title = { Text("Edit Quantity - ${prod?.name ?: "Product"}", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Unit Cost: ₹${line.unitValue}", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                    OutlinedTextField(
                        value = inputQty,
                        onValueChange = { inputQty = it },
                        label = { Text(if (isDec) "Quantity (Kg/L)" else "Quantity (Pieces)") },
                        keyboardOptions = KeyboardOptions(keyboardType = if (isDec) KeyboardType.Decimal else KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (!isDec) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1L, 2L, 5L, 10L).forEach { delta ->
                                FilledTonalButton(
                                    onClick = {
                                        val current = inputQty.toLongOrNull() ?: 0L
                                        inputQty = (current + delta).toString()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("+$delta", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedQty = if (isDec) {
                            val q = inputQty.toDoubleOrNull()
                            if (q != null && q > 0) (q * 1000).toLong() else null
                        } else {
                            inputQty.toLongOrNull()
                        }

                        if (parsedQty != null && parsedQty > 0) {
                            viewModel.updateQuantity(line.productId, parsedQty, line.supplierId)
                            editingPurchaseLine = null
                        }
                    }
                ) {
                    Text("Apply Quantity")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingPurchaseLine = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val purchaseTotal = lines.fold(Money.Zero) { sum, line -> sum + line.total }

    LaunchedEffect(selectedProductId) {
        val prod = products.find { it.id == selectedProductId }
        if (prod != null) {
            costText = String.format(Locale.US, "%.2f", prod.purchasePriceMinorUnits / 100.0)
            quantityText = if (prod.unitType == "KG" || prod.unitType == "LITER") "1.000" else "1"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Purchases & Stock Inward", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { paddingValues ->
        val draftPurchaseCard: @Composable (Modifier) -> Unit = { modifier ->
            Card(
                modifier = modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(20.dp)
                ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Draft Purchase Order", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                    // 1. Select Supplier
                    val selectedSupplierName = suppliers.find { it.id == selectedSupplierId }?.name ?: "Select Supplier"
                    ExposedDropdownMenuBox(
                        expanded = expandedSupplier,
                        onExpandedChange = { expandedSupplier = !expandedSupplier }
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = selectedSupplierName,
                            onValueChange = {},
                            label = { Text("Supplier") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSupplier) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedSupplier,
                            onDismissRequest = { expandedSupplier = false }
                        ) {
                            suppliers.forEach { supplier ->
                                DropdownMenuItem(
                                    text = { Text(supplier.name) },
                                    onClick = {
                                        viewModel.setSupplier(supplier.id)
                                        expandedSupplier = false
                                    }
                                )
                            }
                        }
                    }
                    if (suppliers.isEmpty()) {
                        Text("No suppliers found! Please create a supplier in Masters screen first.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }

                    // Supplier Invoice / Bill Number
                    OutlinedTextField(
                        value = supplierInvoiceNumber,
                        onValueChange = { supplierInvoiceNumber = it },
                        label = { Text("Supplier Bill / Invoice No. (Optional)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    HorizontalDivider()

                    // 2. Select Product & Cost Details
                    val selectedProduct = products.find { it.id == selectedProductId }
                    val selectedProductName = selectedProduct?.name ?: "Select Product"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = expandedProduct,
                            onExpandedChange = { expandedProduct = !expandedProduct },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                readOnly = true,
                                value = selectedProductName,
                                onValueChange = {},
                                label = { Text("Product") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProduct) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedProduct,
                                onDismissRequest = { expandedProduct = false }
                            ) {
                                products.forEach { product ->
                                    DropdownMenuItem(
                                        text = { Text(product.name) },
                                        onClick = {
                                            selectedProductId = product.id
                                            expandedProduct = false
                                        }
                                    )
                                }
                            }
                        }

                        FilledTonalIconButton(
                            onClick = { showCameraScanner = true },
                            modifier = Modifier.size(54.dp).padding(top = 6.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (products.isEmpty()) {
                        Text("No products found! Please create a product in Masters screen first.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }

                    // Quantity Stepper with [-] and [+] + Cost input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Minus Button
                        FilledTonalIconButton(
                            onClick = {
                                val isDecimal = selectedProduct?.unitType == "KG" || selectedProduct?.unitType == "LITER"
                                if (isDecimal) {
                                    val current = quantityText.toDoubleOrNull() ?: 1.0
                                    val next = maxOf(0.25, current - 1.0)
                                    quantityText = String.format(Locale.US, "%.3f", next)
                                } else {
                                    val current = quantityText.toLongOrNull() ?: 1L
                                    val next = maxOf(1L, current - 1L)
                                    quantityText = next.toString()
                                }
                            },
                            modifier = Modifier.size(44.dp).padding(top = 6.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = { quantityText = it },
                            label = { Text("Qty", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = if (selectedProduct?.unitType == "KG" || selectedProduct?.unitType == "LITER") KeyboardType.Decimal else KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        )

                        // Plus Button
                        FilledTonalIconButton(
                            onClick = {
                                val isDecimal = selectedProduct?.unitType == "KG" || selectedProduct?.unitType == "LITER"
                                if (isDecimal) {
                                    val current = quantityText.toDoubleOrNull() ?: 0.0
                                    val next = current + 1.0
                                    quantityText = String.format(Locale.US, "%.3f", next)
                                } else {
                                    val current = quantityText.toLongOrNull() ?: 0L
                                    val next = current + 1L
                                    quantityText = next.toString()
                                }
                            },
                            modifier = Modifier.size(44.dp).padding(top = 6.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedTextField(
                            value = costText,
                            onValueChange = { costText = it },
                            label = { Text("Unit Cost", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.4f),
                            singleLine = true
                        )
                    }

                    Button(
                        onClick = {
                            val isDecimalUnit = selectedProduct?.unitType == "KG" || selectedProduct?.unitType == "LITER"
                            val parsedQty = if (isDecimalUnit) {
                                val qty = quantityText.toDoubleOrNull()
                                if (qty != null && qty > 0) (qty * 1000).toLong() else null
                            } else {
                                quantityText.toLongOrNull()
                            }
                            val costDouble = costText.toDoubleOrNull()
                            if (selectedSupplierId.isNullOrBlank()) {
                                message = "Validation Error: Please select a supplier first before adding product"
                            } else if (selectedProductId.isBlank()) {
                                message = "Validation Error: Please select a product first"
                            } else if (parsedQty == null || parsedQty <= 0) {
                                message = "Validation Error: Quantity must be a valid number greater than 0"
                            } else if (costDouble == null || costDouble <= 0.0) {
                                message = "Validation Error: Unit cost must be a valid number greater than 0"
                            } else {
                                viewModel.addLine(selectedProductId, parsedQty, Money((costDouble * 100).toLong()), selectedSupplierId)
                                val suppName = suppliers.find { it.id == selectedSupplierId }?.name ?: "Supplier"
                                val prodName = selectedProduct?.name ?: "Product"
                                selectedProductId = ""
                                quantityText = "1"
                                costText = ""
                                message = "Added '$prodName' under $suppName"
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Purchase Line")
                    }

                    HorizontalDivider()

                    // Draft Items list with interactive Stepper [-] [ Qty ] [+]
                    Text("Purchase Items", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(lines) { line ->
                            val prod = products.find { it.id == line.productId }
                            val prodName = prod?.name ?: "Unknown Product"
                            val lineSuppName = suppliers.find { it.id == line.supplierId }?.name ?: suppliers.find { it.id == selectedSupplierId }?.name ?: "Supplier"
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Product Name & Supplier Badge
                                    Column(modifier = Modifier.weight(2.2f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(prodName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text("🏢 $lineSuppName", fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                        }
                                        Text("₹${line.unitValue} each", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    }

                                    // Quantity Stepper: [-] [ Qty ] [+]
                                    val isDecimal = prod?.unitType == "KG" || prod?.unitType == "LITER"
                                    val step = if (isDecimal) 250L else 1L

                                    Row(
                                        modifier = Modifier.weight(2.6f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        FilledTonalIconButton(
                                            onClick = {
                                                val nextQty = line.quantity - step
                                                if (nextQty <= 0) {
                                                    viewModel.removeLine(line.productId, line.supplierId)
                                                } else {
                                                    viewModel.updateQuantity(line.productId, nextQty, line.supplierId)
                                                }
                                            },
                                            modifier = Modifier.size(32.dp),
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        ) {
                                            Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Surface(
                                            modifier = Modifier
                                                .padding(horizontal = 6.dp)
                                                .clickable { editingPurchaseLine = line },
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                        ) {
                                            val qtyLabel = if (prod?.unitType == "KG") {
                                                String.format(Locale.US, "%.3f Kg", line.quantity / 1000.0)
                                            } else if (prod?.unitType == "LITER") {
                                                String.format(Locale.US, "%.3f L", line.quantity / 1000.0)
                                            } else "${line.quantity} Pcs"

                                            Text(
                                                text = qtyLabel,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                            )
                                        }

                                        FilledTonalIconButton(
                                            onClick = {
                                                viewModel.updateQuantity(line.productId, line.quantity + step, line.supplierId)
                                            },
                                            modifier = Modifier.size(32.dp),
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        ) {
                                            Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(line.total.toString(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        IconButton(
                                            onClick = { viewModel.removeLine(line.productId, line.supplierId) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    var showPaymentDialog by remember { mutableStateOf(false) }
                    var isSplitMode by remember { mutableStateOf(false) }
                    var splitCashText by remember { mutableStateOf("") }
                    var splitUpiText by remember { mutableStateOf("") }

                    if (showPaymentDialog) {
                        val cashVal = ((splitCashText.toDoubleOrNull() ?: 0.0) * 100).toLong()
                        val upiVal = ((splitUpiText.toDoubleOrNull() ?: 0.0) * 100).toLong()
                        val paidTotalMinor = cashVal + upiVal
                        val remainingCreditMinor = maxOf(0L, purchaseTotal.minorUnits - paidTotalMinor)

                        AlertDialog(
                            onDismissRequest = { showPaymentDialog = false; isSplitMode = false },
                            title = { Text(if (isSplitMode) "Split Supplier Payment" else "Select Payment Mode to Supplier", fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                    Text("Payable Amount: $purchaseTotal", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                                    
                                    if (!isSplitMode) {
                                        Button(
                                            onClick = {
                                                val invNum = supplierInvoiceNumber.trim().ifBlank { null }
                                                viewModel.save(
                                                    invoiceNumber = invNum,
                                                    paymentMode = "CASH",
                                                    paidCash = purchaseTotal,
                                                    onSuccess = {
                                                        showPaymentDialog = false
                                                        supplierInvoiceNumber = ""
                                                        message = "Purchase recorded & paid via Cash!"
                                                    },
                                                    onError = { message = "Error: ${it.message}" }
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Paid via Cash", fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = {
                                                val invNum = supplierInvoiceNumber.trim().ifBlank { null }
                                                viewModel.save(
                                                    invoiceNumber = invNum,
                                                    paymentMode = "UPI",
                                                    paidUpi = purchaseTotal,
                                                    onSuccess = {
                                                        showPaymentDialog = false
                                                        supplierInvoiceNumber = ""
                                                        message = "Purchase recorded & paid via UPI/Online!"
                                                    },
                                                    onError = { message = "Error: ${it.message}" }
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Paid via GPay / UPI", fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = {
                                                val invNum = supplierInvoiceNumber.trim().ifBlank { null }
                                                viewModel.save(
                                                    invoiceNumber = invNum,
                                                    paymentMode = "CREDIT",
                                                    creditApplied = purchaseTotal,
                                                    onSuccess = {
                                                        showPaymentDialog = false
                                                        supplierInvoiceNumber = ""
                                                        message = "Purchase recorded on Credit!"
                                                    },
                                                    onError = { message = "Error: ${it.message}" }
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Buy on Full Credit", fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = { isSplitMode = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Split Payment (Cash + UPI + Credit)", fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        OutlinedTextField(
                                            value = splitCashText,
                                            onValueChange = { splitCashText = it },
                                            label = { Text("Cash Paid (₹)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        OutlinedTextField(
                                            value = splitUpiText,
                                            onValueChange = { splitUpiText = it },
                                            label = { Text("UPI / Online Paid (₹)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text("Total Paid: ${Money(paidTotalMinor)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    "Supplier Credit / Debt: ${Money(remainingCreditMinor)}",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (remainingCreditMinor > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                val invNum = supplierInvoiceNumber.trim().ifBlank { null }
                                                viewModel.save(
                                                    invoiceNumber = invNum,
                                                    paymentMode = "SPLIT",
                                                    paidCash = Money(cashVal),
                                                    paidUpi = Money(upiVal),
                                                    creditApplied = Money(remainingCreditMinor),
                                                    onSuccess = {
                                                        showPaymentDialog = false
                                                        isSplitMode = false
                                                        supplierInvoiceNumber = ""
                                                        splitCashText = ""
                                                        splitUpiText = ""
                                                        message = "Split Purchase saved successfully!"
                                                    },
                                                    onError = { message = "Error: ${it.message}" }
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Confirm Purchase", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            },
                            confirmButton = {},
                            dismissButton = {
                                TextButton(onClick = { 
                                    if (isSplitMode) {
                                        isSplitMode = false
                                    } else {
                                        showPaymentDialog = false 
                                    }
                                }) {
                                    Text(if (isSplitMode) "Back" else "Cancel")
                                }
                            }
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Total Purchase Value:", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(purchaseTotal.toString(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    Button(
                        onClick = {
                            if (lines.isEmpty()) {
                                message = "Please add at least one item to purchase cart"
                            } else {
                                showPaymentDialog = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Pay & Save Purchase", fontWeight = FontWeight.Bold)
                    }

                    if (message.isNotBlank()) {
                        Text(message, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        var deletingPurchase by remember { mutableStateOf<PurchaseEntity?>(null) }
        var activeMobileTab by remember { mutableStateOf(0) }

        if (deletingPurchase != null) {
            val p = deletingPurchase!!
            val orderTitle = p.invoiceNumber ?: p.orderNumber ?: p.id.take(6)
            AlertDialog(
                onDismissRequest = { deletingPurchase = null },
                title = { Text("Delete Purchase Order", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to delete Purchase Order '$orderTitle'? This will automatically deduct inward items from stock inventory and reverse any supplier credit ledger.") },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            val targetPurchase = deletingPurchase!!
                            deletingPurchase = null
                            viewModel.deletePurchase(targetPurchase, onSuccess = {
                                message = "Purchase order deleted and stock deducted"
                            }, onError = {
                                message = "Failed to delete: ${it.message}"
                            })
                        }
                    ) {
                        Text("Delete & Deduct Stock")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deletingPurchase = null }) { Text("Cancel") }
                }
            )
        }

        val purchaseHistoryColumn: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier = modifier) {
                Text("Purchase History", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(purchases) { purchase ->
                        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(purchase.createdAtEpochMs))
                        val supplierName = suppliers.find { it.id == purchase.supplierId }?.name ?: "Unknown Supplier"
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp).fillMaxWidth()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        val orderTitle = when {
                                            !purchase.invoiceNumber.isNullOrBlank() -> "Bill: ${purchase.invoiceNumber}${purchase.orderNumber?.let { " (#$it)" } ?: ""}"
                                            !purchase.orderNumber.isNullOrBlank() -> "Order #${purchase.orderNumber}"
                                            else -> "Order #${purchase.id.take(4)}"
                                        }
                                        Text(orderTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Mode: ${purchase.paymentMode}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(Money(purchase.totalMinorUnits).toString(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                                        
                                        // Edit Purchase Button
                                        IconButton(
                                            onClick = {
                                                viewModel.loadPurchaseForEditing(purchase) { invNum ->
                                                    supplierInvoiceNumber = invNum ?: ""
                                                    activeMobileTab = 0
                                                    message = "Purchase loaded into draft for editing"
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Purchase",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        // Delete Purchase Button
                                        IconButton(
                                            onClick = { deletingPurchase = purchase },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Purchase",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Supplier: $supplierName", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                    Text(dateStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            val layoutMode = LocalLayoutMode.current
            val isMobile = when (layoutMode) {
                "Mobile" -> true
                "Tablet" -> false
                else -> maxWidth < 600.dp
            }
            
            if (isMobile) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TabRow(
                        selectedTabIndex = activeMobileTab,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Tab(
                            selected = activeMobileTab == 0,
                            onClick = { activeMobileTab = 0 },
                            text = { Text("New Order", fontWeight = FontWeight.SemiBold) }
                        )
                        Tab(
                            selected = activeMobileTab == 1,
                            onClick = { activeMobileTab = 1 },
                            text = { Text("Purchase History", fontWeight = FontWeight.SemiBold) }
                        )
                    }
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        if (activeMobileTab == 0) {
                            draftPurchaseCard(Modifier.fillMaxSize())
                        } else {
                            purchaseHistoryColumn(Modifier.fillMaxSize())
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    draftPurchaseCard(Modifier.weight(1.5f).fillMaxHeight())
                    purchaseHistoryColumn(Modifier.weight(1.5f).fillMaxHeight())
                }
            }
        }
    }
}
