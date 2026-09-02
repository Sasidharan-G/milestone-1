package com.kadaikutty.pos.feature.billing.presentation

import com.kadaikutty.pos.core.ui.LocalLayoutMode

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.kadaikutty.pos.feature.billing.domain.SaleLine
import com.kadaikutty.pos.core.common.Money
import com.kadaikutty.pos.feature.billing.data.SaleEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import com.kadaikutty.pos.core.ui.CameraBarcodeScannerDialog
import androidx.compose.ui.platform.LocalContext
import com.kadaikutty.pos.feature.billing.presentation.components.AddCustomerDialog
import com.kadaikutty.pos.feature.billing.presentation.components.EditQuantityDialog
import com.kadaikutty.pos.feature.billing.presentation.components.PaymentCheckoutDialog
import com.kadaikutty.pos.feature.billing.presentation.components.SplitCartDialog
import com.kadaikutty.pos.feature.billing.presentation.components.SearchableProductSelectorDialog
import com.kadaikutty.pos.feature.billing.presentation.components.SearchableCustomerSelectorDialog
import com.kadaikutty.pos.feature.billing.presentation.components.HeldCartsDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(viewModel: BillingViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    val products = uiState.products
    val customers = uiState.customers
    val sales = uiState.sales
    val lines = uiState.lines
    val selectedCustomerId = uiState.selectedCustomerId
    val stockMap = uiState.stockBalances

    var selectedProductId by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("1") }
    var priceText by remember { mutableStateOf("") }

    var message by remember { mutableStateOf("") }
    var showCameraScanner by remember { mutableStateOf(value = false) }
    
    var showPaymentDialog by remember { mutableStateOf(value = false) }
    var discountInput by remember { mutableStateOf("") }
    var includePreviousDueInCheckout by remember { mutableStateOf(value = false) }
    
    var showSplitCartDialog by remember { mutableStateOf(value = false) }
    var splitCartSelectedItems by remember { mutableStateOf(setOf<String>()) }
    var checkoutMode by remember { mutableStateOf("ALL") } // "ALL" or "SPLIT_CART"
    var editingLineQuantity by remember { mutableStateOf<SaleLine?>(null) }
    
    val customerCreditDue = uiState.selectedCustomerCreditBalance
    var showAddCustomerDialog by remember { mutableStateOf(value = false) }
    var showProductSearchDialog by remember { mutableStateOf(value = false) }
    var showCustomerSearchDialog by remember { mutableStateOf(value = false) }
    var showHeldCartsDialog by remember { mutableStateOf(value = false) }
    val heldCarts by viewModel.heldCartsSummary.collectAsState()
    val heldCartsCount by viewModel.heldCartsCount.collectAsState()

    SearchableCustomerSelectorDialog(
        showDialog = showCustomerSearchDialog,
        customers = customers,
        onCustomerSelected = { customer ->
            viewModel.setCustomer(customer?.id)
        },
        onAddNewCustomer = {
            showAddCustomerDialog = true
        },
    ) { showCustomerSearchDialog = false }

    SearchableProductSelectorDialog(
        showDialog = showProductSearchDialog,
        products = products,
        stockMap = stockMap,
        onProductSelected = { prod ->
            selectedProductId = prod.id
        },
    ) { showProductSearchDialog = false }

    AddCustomerDialog(
        showDialog = showAddCustomerDialog,
        onDismiss = { showAddCustomerDialog = false },
        onSaveCustomer = { name, phone, address, openingDue ->
            viewModel.addQuickCustomer(
                name = name,
                phone = phone,
                address = address,
                openingDueMinorUnits = openingDue,
                onSuccess = {
                    showAddCustomerDialog = false
                    android.widget.Toast.makeText(context, "Customer added & selected!", android.widget.Toast.LENGTH_SHORT).show()
                },
            ) {
                android.widget.Toast.makeText(context, "Error: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    )

    if (editingLineQuantity != null) {
        val line = editingLineQuantity!!
        EditQuantityDialog(
            line = line,
            onDismiss = { editingLineQuantity = null },
            onUpdateQuantity = { productId, newQty ->
                viewModel.updateQuantity(productId, newQty)
                editingLineQuantity = null
            },
            onRemoveLine = { productId ->
                viewModel.removeLine(productId)
                editingLineQuantity = null
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
            quantityText = if ((prod.unitType == "KG") || (prod.unitType == "LITER")) "1.000" else "1"
        }
    }

    val activeLines = if (checkoutMode == "SPLIT_CART") lines.filter { splitCartSelectedItems.contains(it.productId) } else lines
    val activeBillSubtotal = activeLines.fold(Money.Zero) { sum, line -> sum + line.lineTotal }
    val globalDiscountMinorUnits = (discountInput.toDoubleOrNull() ?: 0.0).let { (it * 100).toLong() }
    val activeBillTotal = Money(maxOf(0L, activeBillSubtotal.minorUnits - globalDiscountMinorUnits))
    val selectedProduct = products.find { it.id == selectedProductId }
    val currentStockUnits = if (selectedProduct != null) stockMap[selectedProduct.id] ?: 0L else 0L

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(com.kadaikutty.pos.R.string.billing), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
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
                    shape = MaterialTheme.shapes.extraLarge
                ),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 1. Select Customer (Opens Fast Searchable Selector Dialog)
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
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clickable { showCustomerSearchDialog = true },
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, if (selectedCustomerId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (customerCreditDue > 0L) "Customer (Due: ${Money(customerCreditDue)})" else "Customer",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (customerCreditDue > 0L) MaterialTheme.colorScheme.error else if (selectedCustomerId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = selectedCustomerName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selectedCustomerId != null) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        color = if (selectedCustomerId != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                    )
                                }
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Search Customer",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        FilledTonalIconButton(
                            onClick = { showAddCustomerDialog = true },
                            modifier = Modifier.size(56.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Customer")
                        }
                    }

                    // 2. Select Product (Opens Fast Searchable Selector Dialog)
                    val selectedProductName = selectedProduct?.name ?: "Search & Select Product..."
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clickable { showProductSearchDialog = true },
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, if (selectedProduct != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Product",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selectedProduct != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = selectedProductName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selectedProduct != null) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        color = if (selectedProduct != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Search Product",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        FilledTonalIconButton(
                            onClick = { showCameraScanner = true },
                            modifier = Modifier.size(56.dp),
                            shape = MaterialTheme.shapes.medium
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
                                Text("⚠️ Out of Stock (0 available)", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            } else {
                                Text("Available in Stock: $formattedStock", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    if (products.isEmpty()) {
                        Text("No products found! Please create a product first.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    // Stepper Quantity, Price and Add Button in a single row
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Minus Button
                        FilledTonalIconButton(
                            onClick = {
                                val isDecimal = (selectedProduct?.unitType == "KG") || (selectedProduct?.unitType == "LITER")
                                quantityText = if (isDecimal) {
                                    val current = quantityText.toDoubleOrNull() ?: 1.0
                                    val next = maxOf(0.1, current - 1.0)
                                    String.format(Locale.US, "%.3f", next)
                                } else {
                                    val current = quantityText.toLongOrNull() ?: 1L
                                    val next = maxOf(1L, current - 1L)
                                    next.toString()
                                }
                            },
                            modifier = Modifier.size(44.dp).padding(top = 6.dp),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text("-", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }

                        // Quantity Input Box
                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = { quantityText = it },
                            label = { Text("Qty", style = MaterialTheme.typography.labelSmall) },
                            keyboardOptions = KeyboardOptions(keyboardType = if (selectedProduct?.unitType == "KG" || selectedProduct?.unitType == "LITER") KeyboardType.Decimal else KeyboardType.Number),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center, 
                                fontWeight = FontWeight.Bold
                            )
                        )

                        // Plus Button
                        FilledTonalIconButton(
                            onClick = {
                                val isDecimal = (selectedProduct?.unitType == "KG") || (selectedProduct?.unitType == "LITER")
                                quantityText = if (isDecimal) {
                                    val current = quantityText.toDoubleOrNull() ?: 0.0
                                    val next = current + 1.0
                                    String.format(Locale.US, "%.3f", next)
                                } else {
                                    val current = quantityText.toLongOrNull() ?: 0L
                                    val next = current + 1L
                                    next.toString()
                                }
                            },
                            modifier = Modifier.size(44.dp).padding(top = 6.dp),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text("+", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }

                        // Price Input Box
                        OutlinedTextField(
                            value = priceText,
                            onValueChange = { priceText = it },
                            label = { Text("Price", style = MaterialTheme.typography.labelSmall) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.weight(1.1f),
                            singleLine = true
                        )

                        // Add Button
                        Button(
                            onClick = {
                                val isDecimalUnit = (selectedProduct?.unitType == "KG") || (selectedProduct?.unitType == "LITER")
                                val parsedQty = if (isDecimalUnit) {
                                    val qty = quantityText.toDoubleOrNull()
                                    if (qty != null && qty > 0) (qty * 1000).toLong() else null
                                } else quantityText.toLongOrNull()
                                
                                val priceDouble = priceText.toDoubleOrNull()
                                
                                if (selectedProductId.isBlank() || selectedProduct == null) {
                                    message = "Please select a product"
                                } else if (parsedQty == null || parsedQty <= 0) {
                                    message = "Quantity must be > 0"
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
                            shape = MaterialTheme.shapes.small,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.weight(0.9f).height(54.dp).padding(top = 6.dp)
                        ) {
                            Text("ADD", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Draft Items list (Takes remaining space!)
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(lines, key = { it.productId }) { line ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                        shape = MaterialTheme.shapes.medium
                                    ),
                                shape = MaterialTheme.shapes.medium,
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
                                        Text(
                                            text = line.productName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${line.unitPrice} each",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                        )
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
                                            shape = CircleShape
                                        ) {
                                            Text("-", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        }

                                        Surface(
                                            modifier = Modifier
                                                .padding(horizontal = 6.dp)
                                                .clickable { editingLineQuantity = line },
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                        ) {
                                            val qtyLabel = when (line.unitType) {
                                                "KG" -> String.format(Locale.US, "%.3f Kg", line.quantity / 1000.0)
                                                "LITER" -> String.format(Locale.US, "%.3f L", line.quantity / 1000.0)
                                                else -> "${line.quantity} Pcs"
                                            }

                                            Text(
                                                text = qtyLabel,
                                                style = MaterialTheme.typography.bodySmall,
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
                                            shape = CircleShape
                                        ) {
                                            Text("+", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                                            style = MaterialTheme.typography.labelLarge,
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
                            label = { Text("Discount(₹)", style = MaterialTheme.typography.bodySmall) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                            shape = MaterialTheme.shapes.medium,
                            singleLine = true
                        )
                        Column(modifier = Modifier.weight(1.2f), horizontalAlignment = Alignment.End) {
                            Text("Sub: $activeBillSubtotal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            Text("Total: $activeBillTotal", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Action Buttons
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (heldCartsCount > 0) {
                            OutlinedButton(
                                onClick = { showHeldCartsDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD97706)),
                                border = BorderStroke(1.dp, Color(0xFFD97706)),
                                contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp)
                            ) {
                                Text("📑 Held ($heldCartsCount)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                if (lines.isEmpty()) message = "Cannot hold empty bill" else {
                                    viewModel.holdCurrentCart()
                                    message = "Bill placed on hold!"
                                }
                            },
                            modifier = Modifier.weight(0.9f),
                            shape = MaterialTheme.shapes.medium,
                            contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            Text("⏸️ Hold", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                if (lines.isEmpty()) message = "Cannot split empty bill" else showSplitCartDialog = true
                            },
                            modifier = Modifier.weight(0.9f),
                            shape = MaterialTheme.shapes.medium,
                            contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            Text("Split", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        
                        Button(
                            onClick = {
                                if (lines.isEmpty()) message = "Cannot save empty bill" else {
                                    checkoutMode = "ALL"
                                    showPaymentDialog = true
                                }
                            },
                            modifier = Modifier.weight(1.4f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = MaterialTheme.shapes.medium,
                            contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            Text("Pay All", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    HeldCartsDialog(
                        showDialog = showHeldCartsDialog,
                        heldCarts = heldCarts,
                        onResumeCart = { parkId ->
                            viewModel.resumeHeldCart(parkId)
                            message = "Held bill resumed!"
                        },
                        onDiscardCart = { parkId ->
                            viewModel.discardHeldCart(parkId)
                            message = "Held bill discarded"
                        },
                        onDismiss = { showHeldCartsDialog = false }
                    )

                    SplitCartDialog(
                        showDialog = showSplitCartDialog,
                        lines = lines,
                        selectedItems = splitCartSelectedItems,
                        onDismiss = { showSplitCartDialog = false },
                        onSelectionChange = { productId, isChecked ->
                            if (isChecked) {
                                splitCartSelectedItems += productId
                            } else {
                                splitCartSelectedItems -= productId
                            }
                        },
                        onProceedToPay = {
                            showSplitCartDialog = false
                            checkoutMode = "SPLIT_CART"
                            showPaymentDialog = true
                        }
                    )

                    val finalPayableTotal = activeBillTotal
                    val settleDueAmount = 0L

                    PaymentCheckoutDialog(
                        showDialog = showPaymentDialog,
                        checkoutMode = checkoutMode,
                        finalPayableTotal = finalPayableTotal,
                        customerCreditDue = customerCreditDue,
                        selectedCustomerId = selectedCustomerId,
                        includePreviousDueInCheckout = includePreviousDueInCheckout,
                        onIncludePreviousDueChange = { includePreviousDueInCheckout = it },
                        onDismiss = { showPaymentDialog = false },
                        onPerformSave = { pMode, pCash, pUpi, cApplied ->
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
                        },
                        onError = { errorMsg ->
                            message = errorMsg
                        }
                    )

                    if (message.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
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
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("WhatsApp", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { viewModel.printBill(context, billNum) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        Icon(painter = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_agenda), contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Print Bill", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        var deletingSale by remember { mutableStateOf<SaleEntity?>(null) }
        var activeMobileTab by remember { mutableIntStateOf(0) }

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
                Text("Sales History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sales, key = { it.id }) { sale ->
                        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(sale.createdAtEpochMs))
                        val customerName = customers.find { it.id == sale.customerId }?.name ?: "Walk-in"
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                    shape = MaterialTheme.shapes.medium
                                ),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp).fillMaxWidth()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Bill #${sale.billNumber}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(Money(sale.totalMinorUnits).toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                        
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
                                    Text("Customer: $customerName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            val isPortraitMobile = when (LocalLayoutMode.current) {
                "Mobile" -> true
                "Tablet" -> false
                else -> this.maxWidth < 700.dp && this.maxHeight > this.maxWidth
            }
            
            if (isPortraitMobile) {
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
