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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.company.billing.core.common.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(viewModel: BillingViewModel) {
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

    val billTotal = lines.fold(Money.Zero) { sum, line -> sum + line.lineTotal }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Billing / Sales Invoicing", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
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
                Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Draft Sales Bill", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                    // 1. Select Customer
                    val selectedCustomerName = when (selectedCustomerId) {
                        null -> "Walk-in Customer"
                        "online" -> "Online Customer"
                        else -> customers.find { it.id == selectedCustomerId }?.name ?: "Walk-in Customer"
                    }
                    ExposedDropdownMenuBox(
                        expanded = expandedCustomer,
                        onExpandedChange = { expandedCustomer = !expandedCustomer }
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = selectedCustomerName,
                            onValueChange = {},
                            label = { Text("Customer") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCustomer) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedCustomer,
                            onDismissRequest = { expandedCustomer = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Walk-in Customer") },
                                onClick = {
                                    viewModel.setCustomer(null)
                                    expandedCustomer = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Online Customer") },
                                onClick = {
                                    viewModel.setCustomer("online")
                                    expandedCustomer = false
                                }
                            )
                            customers.forEach { customer ->
                                DropdownMenuItem(
                                    text = { Text(customer.name) },
                                    onClick = {
                                        viewModel.setCustomer(customer.id)
                                        expandedCustomer = false
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // 2. Select Product & Input details
                    val selectedProduct = products.find { it.id == selectedProductId }
                    val selectedProductName = selectedProduct?.name ?: "Select Product"
                    ExposedDropdownMenuBox(
                        expanded = expandedProduct,
                        onExpandedChange = { expandedProduct = !expandedProduct }
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
                    if (products.isEmpty()) {
                        Text("No products found! Please create a product in Masters screen first.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = { quantityText = it },
                            label = { Text("Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = priceText,
                            onValueChange = { priceText = it },
                            label = { Text("Unit Price") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(2f)
                        )
                    }

                    Button(
                        onClick = {
                            val qty = quantityText.toLongOrNull()
                            val priceDouble = priceText.toDoubleOrNull()
                            if (selectedProductId.isBlank() || selectedProduct == null) {
                                message = "Validation Error: Please select a product first"
                            } else if (qty == null || qty <= 0) {
                                message = "Validation Error: Quantity must be a valid number greater than 0"
                            } else if (priceDouble == null || priceDouble <= 0.0) {
                                message = "Validation Error: Unit price must be a valid number greater than 0"
                            } else {
                                val priceMoney = Money((priceDouble * 100).toLong())
                                viewModel.addLine(selectedProductId, selectedProduct.name, qty, priceMoney)
                                selectedProductId = ""
                                quantityText = "1"
                                priceText = ""
                                message = "Product added successfully to invoice"
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add to Invoice")
                    }

                    HorizontalDivider()

                    // Draft Items list
                    Text("Bill Items", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(lines) { line ->
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
                                Row(
                                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(3f)) {
                                        Text(line.productName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Qty: ${line.quantity} × ${line.unitPrice}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                    Text(line.lineTotal.toString(), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1.5f))
                                    IconButton(
                                        onClick = { viewModel.removeLine(line.productId) },
                                        modifier = Modifier.weight(0.5f)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Total Amount:", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(billTotal.toString(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    Button(
                        onClick = {
                            viewModel.save(onSuccess = {
                                message = "Bill saved successfully: $it"
                            }, onError = {
                                message = "Error: ${it.message}"
                            })
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Generate & Save Bill", fontWeight = FontWeight.Bold)
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
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("WhatsApp Share", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.shareBill(billNum, false) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Share General", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
                            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(sale.billNumber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(Money(sale.totalMinorUnits).toString(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
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
                var activeMobileTab by remember { mutableStateOf(0) }
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
