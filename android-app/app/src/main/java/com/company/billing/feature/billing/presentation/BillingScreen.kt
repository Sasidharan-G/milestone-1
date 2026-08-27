package com.company.billing.feature.billing.presentation

import com.company.billing.core.ui.LocalLayoutMode

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import com.company.billing.feature.billing.domain.SaleLine
import com.company.billing.core.common.Money
import com.company.billing.feature.billing.data.SaleEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material.icons.filled.QrCodeScanner
import com.company.billing.core.ui.CameraBarcodeScannerDialog
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(viewModel: BillingViewModel) {
    val context = LocalContext.current
    val products by viewModel.products.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val sales by viewModel.sales.collectAsState()
    val lines by viewModel.lines.collectAsState()
    val selectedCustomerId by viewModel.selectedCustomerId.collectAsState()

    var selectedProductId by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("1") }
    var priceText by remember { mutableStateOf("") }

    var expandedProduct by remember { mutableStateOf(false) }
    var expandedCustomer by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var showCameraScanner by remember { mutableStateOf(false) }
    
    var showPaymentDialog by remember { mutableStateOf(false) }
    var isSplitMode by remember { mutableStateOf(false) }
    var cashInput by remember { mutableStateOf("") }
    var upiInput by remember { mutableStateOf("") }
    var discountInput by remember { mutableStateOf("") }
    
    var showSplitCartDialog by remember { mutableStateOf(false) }
    var splitCartSelectedItems by remember { mutableStateOf(setOf<String>()) }
    var checkoutMode by remember { mutableStateOf("ALL") } // "ALL" or "SPLIT_CART"
    var editingLineQuantity by remember { mutableStateOf<SaleLine?>(null) }
    
    val customerCreditDue by viewModel.selectedCustomerCreditBalance.collectAsState()
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var newCustName by remember { mutableStateOf("") }
    var newCustPhone by remember { mutableStateOf("") }
    var newCustAddress by remember { mutableStateOf("") }
    var newCustOpeningDue by remember { mutableStateOf("") }
    var includePreviousDueInCheckout by remember { mutableStateOf(false) }

    if (showAddCustomerDialog) {
        AlertDialog(
            onDismissRequest = { showAddCustomerDialog = false },
            title = { Text("Add New Customer", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newCustName,
                        onValueChange = { newCustName = it },
                        label = { Text("Customer Name *") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newCustPhone,
                        onValueChange = { newCustPhone = it },
                        label = { Text("Phone Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newCustAddress,
                        onValueChange = { newCustAddress = it },
                        label = { Text("Address (Optional)") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newCustOpeningDue,
                        onValueChange = { newCustOpeningDue = it },
                        label = { Text("Previous / Opening Due (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCustName.isBlank()) {
                            android.widget.Toast.makeText(context, "Please enter customer name", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            val openingDueVal = ((newCustOpeningDue.toDoubleOrNull() ?: 0.0) * 100).toLong()
                            viewModel.addQuickCustomer(
                                name = newCustName,
                                phone = newCustPhone,
                                address = newCustAddress,
                                openingDueMinorUnits = openingDueVal,
                                onSuccess = {
                                    newCustName = ""
                                    newCustPhone = ""
                                    newCustAddress = ""
                                    newCustOpeningDue = ""
                                    showAddCustomerDialog = false
                                    android.widget.Toast.makeText(context, "Customer added & selected!", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                onError = {
                                    android.widget.Toast.makeText(context, "Error: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    }
                ) {
                    Text("Save & Select")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (editingLineQuantity != null) {
        val line = editingLineQuantity!!
        var inputQty by remember(line) {
            val isDec = line.unitType == "KG" || line.unitType == "LITER"
            mutableStateOf(if (isDec) String.format(Locale.US, "%.3f", line.quantity / 1000.0) else line.quantity.toString())
        }

        AlertDialog(
            onDismissRequest = { editingLineQuantity = null },
            title = { Text("Edit Quantity - ${line.productName}", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Unit Price: ₹${line.unitPrice}", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                    OutlinedTextField(
                        value = inputQty,
                        onValueChange = { inputQty = it },
                        label = { Text(if (line.unitType == "KG" || line.unitType == "LITER") "Quantity (Kg/L)" else "Quantity (Pieces)") },
                        keyboardOptions = KeyboardOptions(keyboardType = if (line.unitType == "KG" || line.unitType == "LITER") KeyboardType.Decimal else KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Quick Increment Buttons
                    if (line.unitType == "PIECE") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 2, 5, 10).forEach { addVal ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        val cur = inputQty.toLongOrNull() ?: 0L
                                        inputQty = (cur + addVal).toString()
                                    },
                                    label = { Text("+$addVal") }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val isDec = line.unitType == "KG" || line.unitType == "LITER"
                        val parsed = if (isDec) {
                            val d = inputQty.toDoubleOrNull()
                            if (d != null && d > 0) (d * 1000).toLong() else null
                        } else inputQty.toLongOrNull()

                        if (parsed != null && parsed > 0) {
                            viewModel.updateQuantity(line.productId, parsed)
                            editingLineQuantity = null
                        } else if (parsed == 0L) {
                            viewModel.removeLine(line.productId)
                            editingLineQuantity = null
                        }
                    }
                ) {
                    Text("Update Quantity")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingLineQuantity = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCameraScanner) {
        CameraBarcodeScannerDialog(
            title = "Fast Barcode Billing",
            continuousScan = true,
            onBarcodeScanned = { barcode ->
                viewModel.onBarcodeScanned(
                    barcode = barcode,
                    onProductFound = { prod ->
                        android.widget.Toast.makeText(context, "Scanned: ${prod.name} (+1 in Cart)", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onProductNotFound = {
                        message = "Product not found for barcode: $barcode"
                        android.widget.Toast.makeText(context, "Barcode not found in stock: $barcode", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
            },
            onDismiss = { showCameraScanner = false }
        )
    }

    LaunchedEffect(selectedProductId) {
        val prod = products.find { it.id == selectedProductId }
        if (prod != null) {
            priceText = String.format(Locale.US, "%.2f", prod.salePriceMinorUnits / 100.0)
            quantityText = if (prod.unitType == "KG" || prod.unitType == "LITER") "1.000" else "1"
        }
    }

    val billTotal = lines.fold(Money.Zero) { sum, line -> sum + line.lineTotal }
    val activeLines = if (checkoutMode == "SPLIT_CART") lines.filter { splitCartSelectedItems.contains(it.productId) } else lines
    val activeBillSubtotal = activeLines.fold(Money.Zero) { sum, line -> sum + line.lineTotal }
    val globalDiscountMinorUnits = (discountInput.toDoubleOrNull() ?: 0.0).let { (it * 100).toLong() }
    val activeBillTotal = Money(maxOf(0L, activeBillSubtotal.minorUnits - globalDiscountMinorUnits))

    val stockMap by viewModel.stockBalances.collectAsState()
    val selectedProduct = products.find { it.id == selectedProductId }
    val currentStockUnits = if (selectedProduct != null) stockMap[selectedProduct.id] ?: 0L else 0L

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Billing / Sales", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                actions = {
                    IconButton(onClick = { showCameraScanner = true }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Fast Barcode Scanner", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { paddingValues ->
        val draftInvoiceCard: @Composable (Modifier) -> Unit = { modifier ->
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
                Column(modifier = Modifier.padding(12.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 1. Select Customer
                    val selectedCustomerName = when (selectedCustomerId) {
                        null -> "Walk-in Customer"
                        "online" -> "Online Customer"
                        else -> customers.find { it.id == selectedCustomerId }?.name ?: "Walk-in Customer"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = expandedCustomer,
                            onExpandedChange = { expandedCustomer = !expandedCustomer },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                readOnly = true,
                                value = selectedCustomerName,
                                onValueChange = {},
                                label = { 
                                    Text(if (customerCreditDue > 0L) "Customer (Due: ₹${Money(customerCreditDue)})" else "Customer")
                                },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCustomer) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedCustomer,
                                onDismissRequest = { expandedCustomer = false }
                            ) {
                                DropdownMenuItem(text = { Text("Walk-in Customer") }, onClick = { viewModel.setCustomer(null); expandedCustomer = false })
                                customers.forEach { customer ->
                                    DropdownMenuItem(text = { Text(customer.name) }, onClick = { viewModel.setCustomer(customer.id); expandedCustomer = false })
                                }
                            }
                        }

                        FilledTonalIconButton(
                            onClick = { showAddCustomerDialog = true },
                            modifier = Modifier.size(54.dp).padding(top = 6.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Customer")
                        }
                    }

                    // 2. Select Product
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
                                    val pStock = stockMap[product.id] ?: 0L
                                    val pStockStr = if (product.unitType == "KG" || product.unitType == "LITER") {
                                        String.format(Locale.US, "%.3f", pStock / 1000.0)
                                    } else "$pStock"
                                    DropdownMenuItem(
                                        text = { 
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(product.name)
                                                Text(if (pStock <= 0) "Out of stock" else "Stock: $pStockStr", fontSize = 11.sp, color = if (pStock <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
                                            }
                                        }, 
                                        onClick = { selectedProductId = product.id; expandedProduct = false }
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
                    
                    // Stock display banner
                    if (selectedProduct != null) {
                        val isDecimal = selectedProduct.unitType == "KG" || selectedProduct.unitType == "LITER"
                        val formattedStock = if (isDecimal) {
                            String.format(Locale.US, "%.3f %s", currentStockUnits / 1000.0, if (selectedProduct.unitType == "KG") "Kg" else "Ltr")
                        } else {
                            "$currentStockUnits Pcs"
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            if (currentStockUnits <= 0) {
                                Text("⚠️ Out of Stock (0 available)", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text("Available in Stock: $formattedStock", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    if (products.isEmpty()) {
                        Text("No products found! Please create a product first.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }

                    // Stepper Quantity, Price and Add Button in a single row
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Minus Button
                        FilledTonalIconButton(
                            onClick = {
                                val isDecimal = selectedProduct?.unitType == "KG" || selectedProduct?.unitType == "LITER"
                                if (isDecimal) {
                                    val current = quantityText.toDoubleOrNull() ?: 1.0
                                    val next = maxOf(0.1, current - 1.0)
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
                            Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }

                        // Quantity Input Box
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

                        // Price Input Box
                        OutlinedTextField(
                            value = priceText,
                            onValueChange = { priceText = it },
                            label = { Text("Price", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.1f),
                            singleLine = true
                        )

                        // Add Button
                        Button(
                            onClick = {
                                val isDecimalUnit = selectedProduct?.unitType == "KG" || selectedProduct?.unitType == "LITER"
                                val parsedQty = if (isDecimalUnit) {
                                    val qty = quantityText.toDoubleOrNull()
                                    if (qty != null && qty > 0) (qty * 1000).toLong() else null
                                } else quantityText.toLongOrNull()
                                
                                val priceDouble = priceText.toDoubleOrNull()
                                
                                if (selectedProductId.isBlank() || selectedProduct == null) {
                                    message = "Please select a product"
                                } else if (currentStockUnits <= 0) {
                                    message = "Validation Error: ${selectedProduct.name} is Out of Stock (0 available)"
                                } else if (parsedQty == null || parsedQty <= 0) {
                                    message = "Quantity must be > 0"
                                } else if (parsedQty > currentStockUnits) {
                                    val isDecimal = selectedProduct.unitType == "KG" || selectedProduct.unitType == "LITER"
                                    val availStr = if (isDecimal) String.format(Locale.US, "%.3f", currentStockUnits / 1000.0) else "$currentStockUnits"
                                    message = "Cannot add: Only $availStr available in stock!"
                                } else if (priceDouble == null || priceDouble <= 0.0) {
                                    message = "Unit price must be > 0"
                                } else {
                                    val priceMoney = Money((priceDouble * 100).toLong())
                                    viewModel.addLine(selectedProductId, selectedProduct.name, parsedQty, priceMoney, selectedProduct.unitType)
                                    selectedProductId = ""
                                    quantityText = "1"
                                    priceText = ""
                                    message = "Added to invoice"
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.weight(0.9f).height(54.dp).padding(top = 6.dp)
                        ) {
                            Text("ADD", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Draft Items list (Takes remaining space!)
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(lines) { line ->
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
                                    // Product Name & Price
                                    Column(modifier = Modifier.weight(2.2f)) {
                                        Text(line.productName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("₹${line.unitPrice} each", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    }

                                    // Quantity Stepper: [-] [ Qty ] [+]
                                    val isDecimal = line.unitType == "KG" || line.unitType == "LITER"
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
                                                    viewModel.removeLine(line.productId)
                                                } else {
                                                    viewModel.updateQuantity(line.productId, nextQty)
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
                                                .clickable { editingLineQuantity = line },
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                        ) {
                                            val qtyLabel = if (line.unitType == "KG") {
                                                String.format(Locale.US, "%.3f Kg", line.quantity / 1000.0)
                                            } else if (line.unitType == "LITER") {
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
                                                viewModel.updateQuantity(line.productId, line.quantity + step)
                                            },
                                            modifier = Modifier.size(32.dp),
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        ) {
                                            Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // Line Total & Delete
                                    Row(
                                        modifier = Modifier.weight(1.8f),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            line.lineTotal.toString(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        IconButton(
                                            onClick = { viewModel.removeLine(line.productId) },
                                            modifier = Modifier.size(28.dp).padding(start = 4.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Bottom Summary area
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = discountInput,
                            onValueChange = { discountInput = it },
                            label = { Text("Discount(₹)", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Column(modifier = Modifier.weight(1.2f), horizontalAlignment = Alignment.End) {
                            Text("Sub: $activeBillSubtotal", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            Text("Total: $activeBillTotal", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Action Buttons
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (lines.isEmpty()) message = "Cannot split empty bill" else showSplitCartDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text("Split", fontWeight = FontWeight.Bold)
                        }
                        
                        Button(
                            onClick = {
                                if (lines.isEmpty()) message = "Cannot save empty bill" else {
                                    checkoutMode = "ALL"
                                    showPaymentDialog = true
                                }
                            },
                            modifier = Modifier.weight(1.5f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text("Checkout All", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (showSplitCartDialog) {
                        AlertDialog(
                            onDismissRequest = { showSplitCartDialog = false },
                            title = { Text("Select Items for Split Bill", fontWeight = FontWeight.Bold) },
                            text = {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    items(lines) { line ->
                                        val isChecked = splitCartSelectedItems.contains(line.productId)
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                            Checkbox(
                                                checked = isChecked, 
                                                onCheckedChange = { 
                                                    if (it) {
                                                        splitCartSelectedItems = splitCartSelectedItems + line.productId
                                                    } else {
                                                        splitCartSelectedItems = splitCartSelectedItems - line.productId
                                                    }
                                                }
                                            )
                                            Text(line.productName, modifier = Modifier.weight(1f), fontSize = 14.sp)
                                            Text(line.lineTotal.toString(), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    enabled = splitCartSelectedItems.isNotEmpty(),
                                    onClick = {
                                        showSplitCartDialog = false
                                        checkoutMode = "SPLIT_CART"
                                        showPaymentDialog = true
                                    }
                                ) { Text("Proceed to Pay") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showSplitCartDialog = false }) { Text("Cancel") }
                            }
                        )
                    }

                    if (showPaymentDialog) {
                        val finalPayableTotal = if (includePreviousDueInCheckout && customerCreditDue > 0L) {
                            Money(activeBillTotal.minorUnits + customerCreditDue)
                        } else {
                            activeBillTotal
                        }
                        val settleDueAmount = if (includePreviousDueInCheckout && customerCreditDue > 0L) customerCreditDue else 0L

                        val performSave: (String, Money, Money, Money) -> Unit = { pMode, pCash, pUpi, cApplied ->
                            val discountMoney = Money(globalDiscountMinorUnits)
                            if (checkoutMode == "SPLIT_CART") {
                                viewModel.checkoutSelectedItems(
                                    selectedProductIds = splitCartSelectedItems,
                                    paymentMode = pMode,
                                    paidCash = pCash,
                                    paidUpi = pUpi,
                                    creditApplied = cApplied,
                                    globalDiscount = discountMoney,
                                    settlePreviousCreditMinorUnits = settleDueAmount,
                                    onSuccess = { billNum ->
                                        message = "Bill saved successfully: $billNum"
                                        discountInput = ""
                                        includePreviousDueInCheckout = false
                                    },
                                    onError = { message = "Error: ${it.message}" }
                                )
                            } else {
                                viewModel.save(
                                    paymentMode = pMode,
                                    paidCash = pCash,
                                    paidUpi = pUpi,
                                    creditApplied = cApplied,
                                    globalDiscount = discountMoney,
                                    settlePreviousCreditMinorUnits = settleDueAmount,
                                    onSuccess = { billNum ->
                                        message = "Bill saved successfully: $billNum"
                                        discountInput = ""
                                        includePreviousDueInCheckout = false
                                    },
                                    onError = { message = "Error: ${it.message}" }
                                )
                            }
                        }

                        AlertDialog(
                            onDismissRequest = { 
                                showPaymentDialog = false
                                isSplitMode = false
                                cashInput = ""
                                upiInput = ""
                            },
                            title = { Text(if (checkoutMode == "SPLIT_CART") "Payment for Split Cart" else "Payment & Checkout", fontWeight = FontWeight.Bold) },
                            text = { 
                                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (customerCreditDue > 0L && selectedCustomerId != null && selectedCustomerId != "online") {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Previous Due: ₹${Money(customerCreditDue)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                                                    Text(if (includePreviousDueInCheckout) "Added to Total Bill" else "Skipped (Current Bill Only)", fontSize = 11.sp)
                                                }
                                                Switch(
                                                    checked = includePreviousDueInCheckout,
                                                    onCheckedChange = { includePreviousDueInCheckout = it }
                                                )
                                            }
                                        }
                                    }

                                    Text("Total Payable: $finalPayableTotal", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                                    
                                    if (!isSplitMode) {
                                        Text("Select Quick Checkout:", fontSize = 14.sp)
                                        Button(
                                            onClick = {
                                                showPaymentDialog = false
                                                performSave("CASH", finalPayableTotal, Money.Zero, Money.Zero)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF4CAF50))
                                        ) { Text("Full Cash (₹$finalPayableTotal)") }
                                        
                                        Button(
                                            onClick = {
                                                showPaymentDialog = false
                                                performSave("GPAY", Money.Zero, finalPayableTotal, Money.Zero)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF2196F3))
                                        ) { Text("Full GPay / UPI (₹$finalPayableTotal)") }
                                        
                                        Button(
                                            onClick = {
                                                if (selectedCustomerId == null || selectedCustomerId == "online") {
                                                    showPaymentDialog = false
                                                    message = "Validation Error: Credit can only be given to a registered customer. Please select a customer."
                                                } else {
                                                    showPaymentDialog = false
                                                    performSave("CREDIT", Money.Zero, Money.Zero, finalPayableTotal)
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                        ) { Text("Full Credit (₹$finalPayableTotal)") }

                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                        
                                        OutlinedButton(
                                            onClick = { isSplitMode = true },
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("Split / Partial Payment") }
                                        
                                    } else {
                                        val cInput = cashInput.toDoubleOrNull() ?: 0.0
                                        val uInput = upiInput.toDoubleOrNull() ?: 0.0
                                        val inputTotalMinor = ((cInput + uInput) * 100).toLong()
                                        val diff = finalPayableTotal.minorUnits - inputTotalMinor
                                        
                                        OutlinedTextField(
                                            value = cashInput, 
                                            onValueChange = { cashInput = it }, 
                                            label = { Text("Cash Amount Received") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = upiInput, 
                                            onValueChange = { upiInput = it }, 
                                            label = { Text("UPI/GPay Amount Received") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        
                                        if (diff > 0) {
                                            Text("Remaining Credit: ${Money(diff)}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                            if (selectedCustomerId == null || selectedCustomerId == "online") {
                                                Text("⚠️ Please select a customer first to assign credit.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                            }
                                        } else if (diff < 0) {
                                            Text("Change to Return: ${Money(-diff)}", color = androidx.compose.ui.graphics.Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                        } else {
                                            Text("Fully Paid! \uD83C\uDF89", color = androidx.compose.ui.graphics.Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                if (isSplitMode) {
                                    val cInput = cashInput.toDoubleOrNull() ?: 0.0
                                    val uInput = upiInput.toDoubleOrNull() ?: 0.0
                                    val inputTotalMinor = ((cInput + uInput) * 100).toLong()
                                    val diff = finalPayableTotal.minorUnits - inputTotalMinor
                                    val creditAmount = if (diff > 0) diff else 0L
                                    
                                    val canSubmit = creditAmount == 0L || (selectedCustomerId != null && selectedCustomerId != "online")
                                    
                                    Button(
                                        enabled = canSubmit,
                                        onClick = {
                                            showPaymentDialog = false
                                            isSplitMode = false
                                            cashInput = ""
                                            upiInput = ""
                                            
                                            val paymentModeStr = if (creditAmount > 0L) "PARTIAL" else "SPLIT"
                                            performSave(paymentModeStr, Money((cInput * 100).toLong()), Money((uInput * 100).toLong()), Money(creditAmount))
                                        }
                                    ) {
                                        Text("Confirm Split Payment")
                                    }
                                }
                            },
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

                    if (message.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(message, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            if (message.startsWith("Bill saved successfully: ")) {
                                val billNum = message.substringAfter("Bill saved successfully: ")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.shareBill(billNum, true) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF25D366)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { viewModel.printBill(context, billNum) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(painter = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_agenda), contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Print Bill", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        var deletingSale by remember { mutableStateOf<SaleEntity?>(null) }
        var activeMobileTab by remember { mutableStateOf(0) }

        if (deletingSale != null) {
            val s = deletingSale!!
            AlertDialog(
                onDismissRequest = { deletingSale = null },
                title = { Text("Delete Bill", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to delete Bill #${s.billNumber}? This will restore all sold items back into the inventory stock.") },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            val targetSale = deletingSale!!
                            deletingSale = null
                            viewModel.deleteSale(targetSale.id, targetSale.billNumber, onSuccess = {
                                message = "Bill #${targetSale.billNumber} deleted and stock restored"
                            }, onError = {
                                message = "Failed to delete: ${it.message}"
                            })
                        }
                    ) {
                        Text("Delete & Restore Stock")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deletingSale = null }) { Text("Cancel") }
                }
            )
        }

        val salesHistoryColumn: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier = modifier) {
                Text("Sales History", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sales) { sale ->
                        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(sale.createdAtEpochMs))
                        val customerName = customers.find { it.id == sale.customerId }?.name ?: "Walk-in"
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
                                    Text("Bill #${sale.billNumber}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(Money(sale.totalMinorUnits).toString(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                        
                                        // Edit Button
                                        IconButton(
                                            onClick = {
                                                viewModel.loadSaleForEditing(sale) {
                                                    activeMobileTab = 0
                                                    message = "Bill #${sale.billNumber} loaded for editing"
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        // Delete Button
                                        IconButton(
                                            onClick = { deletingSale = sale },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }

                                        // Share Button
                                        IconButton(
                                            onClick = { viewModel.shareBill(sale.billNumber, true) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "Share",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Customer: $customerName", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
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
                            text = { Text("Draft Invoice", fontWeight = FontWeight.SemiBold) }
                        )
                        Tab(
                            selected = activeMobileTab == 1,
                            onClick = { activeMobileTab = 1 },
                            text = { Text("Sales History", fontWeight = FontWeight.SemiBold) }
                        )
                    }
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        if (activeMobileTab == 0) {
                            draftInvoiceCard(Modifier.fillMaxSize())
                        } else {
                            salesHistoryColumn(Modifier.fillMaxSize())
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    draftInvoiceCard(Modifier.weight(1.5f).fillMaxHeight())
                    salesHistoryColumn(Modifier.weight(1.5f).fillMaxHeight())
                }
            }
        }
    }
}
