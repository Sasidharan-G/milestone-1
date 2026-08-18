package com.company.billing.feature.purchase.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
    var message by remember { mutableStateOf("") }

    val purchaseTotal = lines.fold(Money.Zero) { sum, line -> sum + line.total }

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
                modifier = modifier,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Draft Purchase Order", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

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

                    HorizontalDivider()

                    // 2. Select Product & Cost Details
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

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = { quantityText = it },
                            label = { Text("Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = costText,
                            onValueChange = { costText = it },
                            label = { Text("Unit Cost") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(2f)
                        )
                    }

                    Button(
                        onClick = {
                            val qty = quantityText.toLongOrNull() ?: 1L
                            val costDouble = costText.toDoubleOrNull()
                            if (selectedProductId.isNotBlank() && qty > 0 && costDouble != null) {
                                viewModel.addLine(selectedProductId, qty, Money((costDouble * 100).toLong()))
                                selectedProductId = ""
                                quantityText = "1"
                                costText = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Purchase Line")
                    }

                    HorizontalDivider()

                    // Draft Items list
                    Text("Purchase Items", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(lines) { line ->
                            val prodName = products.find { it.id == line.productId }?.name ?: "Unknown Product"
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Row(
                                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(3f)) {
                                        Text(prodName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Qty: ${line.quantity} × ${line.unitValue}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                    Text(line.total.toString(), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1.5f))
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
                        Text("Total Purchase Value:", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(purchaseTotal.toString(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    Button(
                        onClick = {
                            viewModel.save(onSuccess = {
                                message = "Purchase saved successfully!"
                            }, onError = {
                                message = "Error: ${it.message}"
                            })
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save & Add to Stock", fontWeight = FontWeight.Bold)
                    }

                    if (message.isNotBlank()) {
                        Text(message, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        val purchaseHistoryColumn: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier = modifier) {
                Text("Purchase History", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(purchases) { purchase ->
                        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(purchase.createdAtEpochMs))
                        val supplierName = suppliers.find { it.id == purchase.supplierId }?.name ?: "Unknown Supplier"
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Order ID: ${purchase.id.take(8)}...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(Money(purchase.totalMinorUnits).toString(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
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
            val isMobile = maxWidth < 600.dp
            
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
