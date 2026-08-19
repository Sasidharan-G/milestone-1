package com.company.billing.feature.masters.presentation

import com.company.billing.core.ui.LocalLayoutMode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.company.billing.core.common.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.company.billing.feature.masters.data.CustomerEntity
import com.company.billing.feature.masters.data.SupplierEntity
import com.company.billing.feature.masters.data.CustomerCreditEntity
import com.company.billing.feature.masters.data.SupplierCreditEntity
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

@Composable
fun MasterGridCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActiveColor: Boolean = false
) {
    val containerColor = if (isSelected) {
        if (isActiveColor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderModifier = if (isSelected) {
        Modifier
    } else {
        Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
            shape = RoundedCornerShape(12.dp)
        )
    }

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .then(borderModifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterScreens(
    categoryVm: CategoryViewModel,
    productVm: ProductViewModel,
    customerVm: CustomerViewModel,
    supplierVm: SupplierViewModel,
    expenseVm: ExpenseViewModel
) {
    var activeTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Master Data Management", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            // 2-column Interactive Grid Card Selector
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row 1: Categories & Products
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MasterGridCard(
                        title = "Categories",
                        icon = Icons.Default.List,
                        isSelected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    MasterGridCard(
                        title = "Products",
                        icon = Icons.Default.ShoppingCart,
                        isSelected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        modifier = Modifier.weight(1f),
                        isActiveColor = true
                    )
                }
                // Row 2: Customers & Suppliers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MasterGridCard(
                        title = "Customers",
                        icon = Icons.Default.Person,
                        isSelected = activeTab == 2,
                        onClick = { activeTab = 2 },
                        modifier = Modifier.weight(1f)
                    )
                    MasterGridCard(
                        title = "Suppliers",
                        icon = Icons.Default.Home,
                        isSelected = activeTab == 3,
                        onClick = { activeTab = 3 },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Row 3: Expenses & Credits
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MasterGridCard(
                        title = "Expenses",
                        icon = Icons.Default.Info,
                        isSelected = activeTab == 4,
                        onClick = { activeTab = 4 },
                        modifier = Modifier.weight(1f)
                    )
                    MasterGridCard(
                        title = "Credits & Ledger",
                        icon = Icons.Default.Star,
                        isSelected = activeTab == 5,
                        onClick = { activeTab = 5 },
                        modifier = Modifier.weight(1f),
                        isActiveColor = true
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp)) {
                when (activeTab) {
                    0 -> CategoryTabScreen(categoryVm)
                    1 -> ProductTabScreen(productVm)
                    2 -> CustomerTabScreen(customerVm)
                    3 -> SupplierTabScreen(supplierVm)
                    4 -> ExpenseTabScreen(expenseVm)
                    5 -> CreditLedgerTabScreen(customerVm, supplierVm)
                }
            }
        }
    }
}

@Composable
fun CategoryTabScreen(viewModel: CategoryViewModel) {
    val categories by viewModel.categories.collectAsState()
    var name by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layoutMode = LocalLayoutMode.current
        val isMobile = when (layoutMode) {
            "Mobile" -> true
            "Tablet" -> false
            else -> maxWidth < 600.dp
        }
        
        if (isMobile) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Create Category", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Category Name") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    if (name.isNotBlank()) {
                                        viewModel.addCategory(name, onSuccess = {
                                            name = ""
                                            message = "Category added successfully"
                                        }, onError = {
                                            message = "Error: ${it.message}"
                                        })
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add Category")
                            }
                            if (message.isNotBlank()) {
                                Text(message, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = search,
                        onValueChange = {
                            search = it
                            viewModel.updateSearch(it)
                        },
                        label = { Text("Search Categories") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }

                if (categories.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (search.isNotBlank()) "No categories found matching \"$search\"" else "No categories registered yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(categories) { category ->
                        Card(
                            modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(category.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(category.syncStatus.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(
                    modifier = Modifier.weight(1.2f).fillMaxHeight(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Create Category", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Category Name") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                if (name.isNotBlank()) {
                                    viewModel.addCategory(name, onSuccess = {
                                        name = ""
                                        message = "Category added successfully"
                                    }, onError = {
                                        message = "Error: ${it.message}"
                                    })
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add Category")
                        }
                        if (message.isNotBlank()) {
                            Text(message, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Column(modifier = Modifier.weight(1.8f).fillMaxHeight()) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = {
                            search = it
                            viewModel.updateSearch(it)
                        },
                        label = { Text("Search Categories") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (categories.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (search.isNotBlank()) "No categories found matching \"$search\"" else "No categories registered yet.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            items(categories) { category ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(category.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                        Text(category.syncStatus.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductTabScreen(viewModel: ProductViewModel) {
    val products by viewModel.products.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var name by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf("") }
    var purchasePrice by remember { mutableStateOf("") }
    var salePrice by remember { mutableStateOf("") }
    var unitType by remember { mutableStateOf("PIECE") }
    var unitTypeExpanded by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layoutMode = LocalLayoutMode.current
        val isMobile = when (layoutMode) {
            "Mobile" -> true
            "Tablet" -> false
            else -> maxWidth < 600.dp
        }
        
        if (isMobile) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Create Product", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Product Name") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            val selectedCategoryName = categories.find { it.id == selectedCategoryId }?.name ?: "Select Category"
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = !expanded }
                            ) {
                                OutlinedTextField(
                                    readOnly = true,
                                    value = selectedCategoryName,
                                    onValueChange = {},
                                    label = { Text("Category") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    categories.forEach { category ->
                                        DropdownMenuItem(
                                            text = { Text(category.name) },
                                            onClick = {
                                                selectedCategoryId = category.id
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = purchasePrice,
                                onValueChange = { purchasePrice = it },
                                label = { Text("Purchase Price (₹)") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )

                            OutlinedTextField(
                                value = salePrice,
                                onValueChange = { salePrice = it },
                                label = { Text("Sale Price (₹)") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )

                            ExposedDropdownMenuBox(
                                expanded = unitTypeExpanded,
                                onExpandedChange = { unitTypeExpanded = !unitTypeExpanded }
                            ) {
                                OutlinedTextField(
                                    readOnly = true,
                                    value = if (unitType == "KG") "Kg / Grams" else "Pieces",
                                    onValueChange = {},
                                    label = { Text("Unit Type") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitTypeExpanded) },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = unitTypeExpanded,
                                    onDismissRequest = { unitTypeExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Pieces") },
                                        onClick = {
                                            unitType = "PIECE"
                                            unitTypeExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Kg / Grams") },
                                        onClick = {
                                            unitType = "KG"
                                            unitTypeExpanded = false
                                        }
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    if (name.isNotBlank() && selectedCategoryId.isNotBlank()) {
                                        val purVal = ((purchasePrice.toDoubleOrNull() ?: 0.0) * 100).toLong()
                                        val saleVal = ((salePrice.toDoubleOrNull() ?: 0.0) * 100).toLong()
                                        viewModel.addProduct(name, selectedCategoryId, purVal, saleVal, unitType, onSuccess = {
                                            name = ""
                                            selectedCategoryId = ""
                                            purchasePrice = ""
                                            salePrice = ""
                                            unitType = "PIECE"
                                            message = "Product added successfully"
                                        }, onError = {
                                            message = "Error: ${it.message}"
                                        })
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add Product")
                            }
                            if (message.isNotBlank()) {
                                Text(message, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = search,
                        onValueChange = {
                            search = it
                            viewModel.updateSearch(it)
                        },
                        label = { Text("Search Products") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }

                if (products.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (search.isNotBlank()) "No products found matching \"$search\"" else "No products registered yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(products) { product ->
                        val catName = categories.find { it.id == product.categoryId }?.name ?: "Unknown Category"
                        Card(
                            modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Text(catName, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                    val purText = Money(product.purchasePriceMinorUnits).toString()
                                    val saleText = Money(product.salePriceMinorUnits).toString()
                                    val unitLabel = if (product.unitType == "KG") "Kg" else "Piece"
                                    Text("$unitLabel • Sale: $saleText • Pur: $purText", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(product.syncStatus.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(
                    modifier = Modifier.weight(1.2f).fillMaxHeight(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Create Product", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Product Name") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        val selectedCategoryName = categories.find { it.id == selectedCategoryId }?.name ?: "Select Category"
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                readOnly = true,
                                value = selectedCategoryName,
                                onValueChange = {},
                                label = { Text("Category") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                categories.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category.name) },
                                        onClick = {
                                            selectedCategoryId = category.id
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = purchasePrice,
                            onValueChange = { purchasePrice = it },
                            label = { Text("Purchase Price (₹)") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )

                        OutlinedTextField(
                            value = salePrice,
                            onValueChange = { salePrice = it },
                            label = { Text("Sale Price (₹)") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )

                        ExposedDropdownMenuBox(
                            expanded = unitTypeExpanded,
                            onExpandedChange = { unitTypeExpanded = !unitTypeExpanded }
                        ) {
                            OutlinedTextField(
                                readOnly = true,
                                value = if (unitType == "KG") "Kg / Grams" else "Pieces",
                                onValueChange = {},
                                label = { Text("Unit Type") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitTypeExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = unitTypeExpanded,
                                onDismissRequest = { unitTypeExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Pieces") },
                                    onClick = {
                                        unitType = "PIECE"
                                        unitTypeExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Kg / Grams") },
                                    onClick = {
                                        unitType = "KG"
                                        unitTypeExpanded = false
                                    }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (name.isNotBlank() && selectedCategoryId.isNotBlank()) {
                                    val purVal = ((purchasePrice.toDoubleOrNull() ?: 0.0) * 100).toLong()
                                    val saleVal = ((salePrice.toDoubleOrNull() ?: 0.0) * 100).toLong()
                                    viewModel.addProduct(name, selectedCategoryId, purVal, saleVal, unitType, onSuccess = {
                                        name = ""
                                        selectedCategoryId = ""
                                        purchasePrice = ""
                                        salePrice = ""
                                        unitType = "PIECE"
                                        message = "Product added successfully"
                                    }, onError = {
                                        message = "Error: ${it.message}"
                                    })
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add Product")
                        }
                        if (message.isNotBlank()) {
                            Text(message, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Column(modifier = Modifier.weight(1.8f).fillMaxHeight()) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = {
                            search = it
                            viewModel.updateSearch(it)
                        },
                        label = { Text("Search Products") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (products.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (search.isNotBlank()) "No products found matching \"$search\"" else "No products registered yet.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            items(products) { product ->
                                val catName = categories.find { it.id == product.categoryId }?.name ?: "Unknown Category"
                                Card(
                                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column {
                                            Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                            Text(catName, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                            val purText = Money(product.purchasePriceMinorUnits).toString()
                                            val saleText = Money(product.salePriceMinorUnits).toString()
                                            val unitLabel = if (product.unitType == "KG") "Kg" else "Piece"
                                            Text("$unitLabel • Sale: $saleText • Pur: $purText", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(product.syncStatus.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerTabScreen(viewModel: CustomerViewModel) {
    val customers by viewModel.customers.collectAsState()
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var selectedCustomerForCredit by remember { mutableStateOf<CustomerEntity?>(null) }

    if (selectedCustomerForCredit != null) {
        CustomerCreditDetailDialog(
            customer = selectedCustomerForCredit!!,
            viewModel = viewModel,
            onDismiss = { selectedCustomerForCredit = null }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layoutMode = LocalLayoutMode.current
        val isMobile = when (layoutMode) {
            "Mobile" -> true
            "Tablet" -> false
            else -> maxWidth < 600.dp
        }
        
        if (isMobile) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Create Customer", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Customer Name") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("Phone Number") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = address,
                                onValueChange = { address = it },
                                label = { Text("Address") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    if (name.isNotBlank()) {
                                        viewModel.addCustomer(
                                            name = name,
                                            phone = phone.trim().takeIf { it.isNotBlank() },
                                            address = address.trim().takeIf { it.isNotBlank() },
                                            onSuccess = {
                                                name = ""
                                                phone = ""
                                                address = ""
                                                message = "Customer added successfully"
                                            },
                                            onError = {
                                                message = "Error: ${it.message}"
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add Customer")
                            }
                            if (message.isNotBlank()) {
                                Text(message, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = search,
                        onValueChange = {
                            search = it
                            viewModel.updateSearch(it)
                        },
                        label = { Text("Search Customers") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }

                if (customers.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (search.isNotBlank()) "No customers found matching \"$search\"" else "No customers registered yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(customers) { customer ->
                        val balanceFlow = remember(customer.id) { viewModel.getCustomerCreditBalance(customer.id) }
                        val balance by balanceFlow.collectAsState(initial = 0L)
                        val bal = balance ?: 0L
                        val isOverLimit = customer.creditLimitMinorUnits > 0L && bal > customer.creditLimitMinorUnits

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCustomerForCredit = customer }
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(customer.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    if (bal != 0L) {
                                        Text(
                                            text = "Outstanding Balance: ${Money(bal)}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (!customer.phone.isNullOrBlank() || !customer.address.isNullOrBlank()) {
                                        Text(
                                            text = listOfNotNull(customer.phone, customer.address).joinToString(" | "),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                                if (isOverLimit) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Credit Limit Exceeded",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Text(customer.syncStatus.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(
                    modifier = Modifier.weight(1.2f).fillMaxHeight(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Create Customer", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Customer Name") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone Number") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Address") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                if (name.isNotBlank()) {
                                    viewModel.addCustomer(
                                        name = name,
                                        phone = phone.trim().takeIf { it.isNotBlank() },
                                        address = address.trim().takeIf { it.isNotBlank() },
                                        onSuccess = {
                                            name = ""
                                            phone = ""
                                            address = ""
                                            message = "Customer added successfully"
                                        },
                                        onError = {
                                            message = "Error: ${it.message}"
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add Customer")
                        }
                        if (message.isNotBlank()) {
                            Text(message, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Column(modifier = Modifier.weight(1.8f).fillMaxHeight()) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = {
                            search = it
                            viewModel.updateSearch(it)
                        },
                        label = { Text("Search Customers") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (customers.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (search.isNotBlank()) "No customers found matching \"$search\"" else "No customers registered yet.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            items(customers) { customer ->
                                val balanceFlow = remember(customer.id) { viewModel.getCustomerCreditBalance(customer.id) }
                                val balance by balanceFlow.collectAsState(initial = 0L)
                                val bal = balance ?: 0L
                                val isOverLimit = customer.creditLimitMinorUnits > 0L && bal > customer.creditLimitMinorUnits

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedCustomerForCredit = customer }
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(customer.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                            if (bal != 0L) {
                                                Text(
                                                    text = "Outstanding Balance: ${Money(bal)}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            if (!customer.phone.isNullOrBlank() || !customer.address.isNullOrBlank()) {
                                                Text(
                                                    text = listOfNotNull(customer.phone, customer.address).joinToString(" | "),
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }
                                        if (isOverLimit) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "Credit Limit Exceeded",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            Text(customer.syncStatus.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SupplierTabScreen(viewModel: SupplierViewModel) {
    val suppliers by viewModel.suppliers.collectAsState()
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var selectedSupplierForCredit by remember { mutableStateOf<SupplierEntity?>(null) }

    if (selectedSupplierForCredit != null) {
        SupplierCreditDetailDialog(
            supplier = selectedSupplierForCredit!!,
            viewModel = viewModel,
            onDismiss = { selectedSupplierForCredit = null }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layoutMode = LocalLayoutMode.current
        val isMobile = when (layoutMode) {
            "Mobile" -> true
            "Tablet" -> false
            else -> maxWidth < 600.dp
        }
        
        if (isMobile) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Create Supplier", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Supplier Name") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("Phone Number") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = address,
                                onValueChange = { address = it },
                                label = { Text("Address") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    if (name.isNotBlank()) {
                                        viewModel.addSupplier(
                                            name = name,
                                            phone = phone.trim().takeIf { it.isNotBlank() },
                                            address = address.trim().takeIf { it.isNotBlank() },
                                            onSuccess = {
                                                name = ""
                                                phone = ""
                                                address = ""
                                                message = "Supplier added successfully"
                                            },
                                            onError = {
                                                message = "Error: ${it.message}"
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add Supplier")
                            }
                            if (message.isNotBlank()) {
                                Text(message, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = search,
                        onValueChange = {
                            search = it
                            viewModel.updateSearch(it)
                        },
                        label = { Text("Search Suppliers") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }

                if (suppliers.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (search.isNotBlank()) "No suppliers found matching \"$search\"" else "No suppliers registered yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(suppliers) { supplier ->
                        val balanceFlow = remember(supplier.id) { viewModel.getSupplierCreditBalance(supplier.id) }
                        val balance by balanceFlow.collectAsState(initial = 0L)
                        val bal = balance ?: 0L
                        
                        val creditsFlow = remember(supplier.id) { viewModel.getSupplierCredits(supplier.id) }
                        val credits by creditsFlow.collectAsState(initial = emptyList<SupplierCreditEntity>())
                        
                        val isOverdue = bal > 0L && credits.any { it.amountMinorUnits > 0L && it.dueDateEpochMs > 0L && it.dueDateEpochMs < System.currentTimeMillis() }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedSupplierForCredit = supplier }
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(supplier.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    if (bal != 0L) {
                                        Text(
                                            text = "Outstanding Balance: ${Money(bal)}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (!supplier.phone.isNullOrBlank() || !supplier.address.isNullOrBlank()) {
                                        Text(
                                            text = listOfNotNull(supplier.phone, supplier.address).joinToString(" | "),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                                if (isOverdue) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Repayment Overdue",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Text(supplier.syncStatus.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(
                    modifier = Modifier.weight(1.2f).fillMaxHeight(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Create Supplier", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Supplier Name") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone Number") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Address") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                if (name.isNotBlank()) {
                                    viewModel.addSupplier(
                                        name = name,
                                        phone = phone.trim().takeIf { it.isNotBlank() },
                                        address = address.trim().takeIf { it.isNotBlank() },
                                        onSuccess = {
                                            name = ""
                                            phone = ""
                                            address = ""
                                            message = "Supplier added successfully"
                                        },
                                        onError = {
                                            message = "Error: ${it.message}"
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add Supplier")
                        }
                        if (message.isNotBlank()) {
                            Text(message, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Column(modifier = Modifier.weight(1.8f).fillMaxHeight()) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = {
                            search = it
                            viewModel.updateSearch(it)
                        },
                        label = { Text("Search Suppliers") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (suppliers.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (search.isNotBlank()) "No suppliers found matching \"$search\"" else "No suppliers registered yet.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            items(suppliers) { supplier ->
                                val balanceFlow = remember(supplier.id) { viewModel.getSupplierCreditBalance(supplier.id) }
                                val balance by balanceFlow.collectAsState(initial = 0L)
                                val bal = balance ?: 0L
                                
                                val creditsFlow = remember(supplier.id) { viewModel.getSupplierCredits(supplier.id) }
                                val credits by creditsFlow.collectAsState(initial = emptyList<SupplierCreditEntity>())
                                
                                val isOverdue = bal > 0L && credits.any { it.amountMinorUnits > 0L && it.dueDateEpochMs > 0L && it.dueDateEpochMs < System.currentTimeMillis() }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedSupplierForCredit = supplier }
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(supplier.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                            if (bal != 0L) {
                                                Text(
                                                    text = "Outstanding Balance: ${Money(bal)}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            if (!supplier.phone.isNullOrBlank() || !supplier.address.isNullOrBlank()) {
                                                Text(
                                                    text = listOfNotNull(supplier.phone, supplier.address).joinToString(" | "),
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }
                                        if (isOverdue) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "Repayment Overdue",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            Text(supplier.syncStatus.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpenseTabScreen(viewModel: ExpenseViewModel) {
    val expenses by viewModel.expenses.collectAsState()
    var description by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layoutMode = LocalLayoutMode.current
        val isMobile = when (layoutMode) {
            "Mobile" -> true
            "Tablet" -> false
            else -> maxWidth < 600.dp
        }
        
        if (isMobile) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Create Expense", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = { Text("Description") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { amountText = it },
                                label = { Text("Amount (e.g. 150.00)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    val amountDouble = amountText.toDoubleOrNull()
                                    if (description.isNotBlank() && amountDouble != null) {
                                        val minorUnits = (amountDouble * 100).toLong()
                                        viewModel.addExpense(minorUnits, description, onSuccess = {
                                            description = ""
                                            amountText = ""
                                            message = "Expense recorded successfully"
                                        }, onError = {
                                            message = "Error: ${it.message}"
                                        })
                                    } else {
                                        message = "Invalid amount or description"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Record Expense")
                            }
                            if (message.isNotBlank()) {
                                Text(message, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                item {
                    Text("Recorded Expenses", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                }

                if (expenses.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No expenses recorded yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(expenses) { expense ->
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        val dateStr = dateFormat.format(Date(expense.createdAtEpochMs))
                        Card(
                            modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(expense.description, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Text(dateStr, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                }
                                Text(Money(expense.amountMinorUnits).toString(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(
                    modifier = Modifier.weight(1.2f).fillMaxHeight(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Create Expense", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it },
                            label = { Text("Amount (e.g. 150.00)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                val amountDouble = amountText.toDoubleOrNull()
                                if (description.isNotBlank() && amountDouble != null) {
                                    val minorUnits = (amountDouble * 100).toLong()
                                    viewModel.addExpense(minorUnits, description, onSuccess = {
                                        description = ""
                                        amountText = ""
                                        message = "Expense recorded successfully"
                                    }, onError = {
                                        message = "Error: ${it.message}"
                                    })
                                } else {
                                    message = "Invalid amount or description"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Record Expense")
                        }
                        if (message.isNotBlank()) {
                            Text(message, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Column(modifier = Modifier.weight(1.8f).fillMaxHeight()) {
                    Text("Recorded Expenses", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (expenses.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No expenses recorded yet.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            items(expenses) { expense ->
                                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                val dateStr = dateFormat.format(Date(expense.createdAtEpochMs))
                                Card(
                                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column {
                                            Text(expense.description, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                            Text(dateStr, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                        }
                                        Text(Money(expense.amountMinorUnits).toString(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuditReportDialog(
    title: String,
    reportText: String,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = reportText,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Audit Report", reportText)
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(context, "Report copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy Report")
                }
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    )
}

@Composable
fun CustomerCreditDetailDialog(
    customer: CustomerEntity,
    viewModel: CustomerViewModel,
    onDismiss: () -> Unit
) {
    val credits by viewModel.getCustomerCredits(customer.id).collectAsState(initial = emptyList<CustomerCreditEntity>())
    val balance by viewModel.getCustomerCreditBalance(customer.id).collectAsState(initial = 0L)
    val bal = balance ?: 0L
    val isOverLimit = customer.creditLimitMinorUnits > 0L && bal > customer.creditLimitMinorUnits

    var amountText by remember { mutableStateOf("") }
    var reasonText by remember { mutableStateOf("") }
    var limitText by remember { mutableStateOf("") }
    
    var showAddCredit by remember { mutableStateOf(false) }
    var showReceivePayment by remember { mutableStateOf(false) }
    var showSetLimit by remember { mutableStateOf(false) }
    
    var auditReportText by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf("") }

    if (auditReportText != null) {
        AuditReportDialog(
            title = "Audit Report - ${customer.name}",
            reportText = auditReportText!!,
            onDismiss = { auditReportText = null }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(customer.name, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Customer Ledger Details", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (isOverLimit) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Outstanding Balance",
                            fontSize = 12.sp,
                            color = if (isOverLimit) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = Money(bal).toString(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Credit Limit: ${Money(customer.creditLimitMinorUnits)}",
                            fontSize = 12.sp,
                            color = if (isOverLimit) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        if (isOverLimit) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Warning: Exceeds predefined credit limit!", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (errorMessage.isNotBlank()) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                if (showAddCredit) {
                    Card(modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp)), shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Extend Credit Entry", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { amountText = it },
                                label = { Text("Amount") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = reasonText,
                                onValueChange = { reasonText = it },
                                label = { Text("Reason / Description") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { showAddCredit = false; amountText = ""; reasonText = "" }) { Text("Cancel") }
                                Spacer(modifier = Modifier.weight(1f))
                                Button(onClick = {
                                    val amtMinor = amountText.toDoubleOrNull()?.let { (it * 100).toLong() }
                                    if (amtMinor != null && amtMinor > 0 && reasonText.isNotBlank()) {
                                        viewModel.addCustomerCredit(customer.id, amtMinor, reasonText, onSuccess = {
                                            showAddCredit = false
                                            amountText = ""
                                            reasonText = ""
                                            errorMessage = ""
                                        }, onError = {
                                            errorMessage = "Error: ${it.message}"
                                        })
                                    } else {
                                        errorMessage = "Please enter valid amount and description"
                                    }
                                }) { Text("Record") }
                            }
                        }
                    }
                }

                if (showReceivePayment) {
                    Card(modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp)), shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Receive Payment (Settle Credit)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { amountText = it },
                                label = { Text("Payment Amount") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = reasonText,
                                onValueChange = { reasonText = it },
                                label = { Text("Notes (e.g. Receipt No, Cash/UPI)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { showReceivePayment = false; amountText = ""; reasonText = "" }) { Text("Cancel") }
                                Spacer(modifier = Modifier.weight(1f))
                                Button(onClick = {
                                    val amtMinor = amountText.toDoubleOrNull()?.let { (it * 100).toLong() }
                                    if (amtMinor != null && amtMinor > 0) {
                                        val reasonString = "Payment Received" + if (reasonText.isNotBlank()) " - $reasonText" else ""
                                        viewModel.addCustomerCredit(customer.id, -amtMinor, reasonString, onSuccess = {
                                            showReceivePayment = false
                                            amountText = ""
                                            reasonText = ""
                                            errorMessage = ""
                                        }, onError = {
                                            errorMessage = "Error: ${it.message}"
                                        })
                                    } else {
                                        errorMessage = "Please enter valid payment amount"
                                    }
                                }) { Text("Record Settle") }
                            }
                        }
                    }
                }

                if (showSetLimit) {
                    Card(modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp)), shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Adjust Credit Limit", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            OutlinedTextField(
                                value = limitText,
                                onValueChange = { limitText = it },
                                label = { Text("Max Credit Limit") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { showSetLimit = false; limitText = "" }) { Text("Cancel") }
                                Spacer(modifier = Modifier.weight(1f))
                                Button(onClick = {
                                    val limitMinor = limitText.toDoubleOrNull()?.let { (it * 100).toLong() }
                                    if (limitMinor != null && limitMinor >= 0) {
                                        viewModel.updateCustomerCreditLimit(customer.id, limitMinor, onSuccess = {
                                            showSetLimit = false
                                            limitText = ""
                                            errorMessage = ""
                                        }, onError = {
                                            errorMessage = "Error: ${it.message}"
                                        })
                                    } else {
                                        errorMessage = "Please enter a valid credit limit"
                                    }
                                }) { Text("Save Limit") }
                            }
                        }
                    }
                }

                if (!showAddCredit && !showReceivePayment && !showSetLimit) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showAddCredit = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                            Text("Extend Credit", fontSize = 11.sp)
                        }
                        Button(onClick = { showReceivePayment = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                            Text("Receive Pay", fontSize = 11.sp)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showSetLimit = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                            Text("Set Limit", fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = {
                            val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            val sb = StringBuilder()
                            sb.append("========================================\n")
                            sb.append("      CUSTOMER CREDIT RECONCILIATION\n")
                            sb.append("========================================\n")
                            sb.append("Customer: ${customer.name}\n")
                            sb.append("Phone: ${customer.phone ?: "N/A"}\n")
                            sb.append("Date generated: ${df.format(Date())}\n")
                            sb.append("----------------------------------------\n")
                            sb.append("Predefined Credit Limit: ${Money(customer.creditLimitMinorUnits)}\n")
                            sb.append("Outstanding Balance: ${Money(bal)}\n")
                            sb.append("Status: ${if (isOverLimit) "OVER CREDIT LIMIT (ALERT)" else "NORMAL"}\n")
                            sb.append("----------------------------------------\n")
                            sb.append("TRANSACTION HISTORY:\n\n")
                            
                            var running = 0L
                            credits.asReversed().forEach { c ->
                                running += c.amountMinorUnits
                                val sign = if (c.amountMinorUnits >= 0) "[CREDIT]" else "[PAYMENT]"
                                sb.append("${df.format(Date(c.dateEpochMs))}\n")
                                sb.append("  Type: $sign\n")
                                sb.append("  Amt: ${Money(Math.abs(c.amountMinorUnits))}\n")
                                sb.append("  Reason: ${c.reason}\n")
                                sb.append("  Running Bal: ${Money(running)}\n")
                                sb.append("----------------------------------------\n")
                            }
                            auditReportText = sb.toString()
                        }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                            Text("Audit Report", fontSize = 11.sp)
                        }
                    }
                }

                Text("Ledger History", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                if (credits.isEmpty()) {
                    Text("No transactions logged yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                } else {
                    var currentRunningBalance = 0L
                    val runningBalances = credits.asReversed().map {
                        currentRunningBalance += it.amountMinorUnits
                        currentRunningBalance
                    }.asReversed()

                    credits.forEachIndexed { index, cr ->
                        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        val crBal = runningBalances[index]
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(cr.reason, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(
                                        text = if (cr.amountMinorUnits >= 0) "+${Money(cr.amountMinorUnits)}" else Money(cr.amountMinorUnits).toString(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (cr.amountMinorUnits >= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(df.format(Date(cr.dateEpochMs)), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    Text("Running Bal: ${Money(crBal)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun SupplierCreditDetailDialog(
    supplier: SupplierEntity,
    viewModel: SupplierViewModel,
    onDismiss: () -> Unit
) {
    val credits by viewModel.getSupplierCredits(supplier.id).collectAsState(initial = emptyList<SupplierCreditEntity>())
    val balance by viewModel.getSupplierCreditBalance(supplier.id).collectAsState(initial = 0L)
    val bal = balance ?: 0L
    val isOverdue = bal > 0L && credits.any { it.amountMinorUnits > 0L && it.dueDateEpochMs > 0L && it.dueDateEpochMs < System.currentTimeMillis() }

    var amountText by remember { mutableStateOf("") }
    var termsText by remember { mutableStateOf("Net 30") }
    var repaymentDaysText by remember { mutableStateOf("30") }
    
    var showAddCredit by remember { mutableStateOf(false) }
    var showMakePayment by remember { mutableStateOf(false) }
    
    var auditReportText by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf("") }

    if (auditReportText != null) {
        AuditReportDialog(
            title = "Supplier Audit Report - ${supplier.name}",
            reportText = auditReportText!!,
            onDismiss = { auditReportText = null }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(supplier.name, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Supplier Ledger Details", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (isOverdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Outstanding Payable Balance",
                            fontSize = 12.sp,
                            color = if (isOverdue) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = Money(bal).toString(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        if (isOverdue) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Warning: Repayment is overdue!", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (errorMessage.isNotBlank()) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                if (showAddCredit) {
                    Card(modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp)), shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Record Received Credit", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { amountText = it },
                                label = { Text("Credit Amount") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = termsText,
                                onValueChange = { termsText = it },
                                label = { Text("Terms (e.g. Net 30, Cash on Del)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = repaymentDaysText,
                                onValueChange = { repaymentDaysText = it },
                                label = { Text("Repayment Due Days") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { showAddCredit = false; amountText = ""; termsText = "Net 30"; repaymentDaysText = "30" }) { Text("Cancel") }
                                Spacer(modifier = Modifier.weight(1f))
                                Button(onClick = {
                                    val amtMinor = amountText.toDoubleOrNull()?.let { (it * 100).toLong() }
                                    val days = repaymentDaysText.toLongOrNull() ?: 30L
                                    if (amtMinor != null && amtMinor > 0 && termsText.isNotBlank()) {
                                        val dueEpoch = System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000L)
                                        viewModel.addSupplierCredit(supplier.id, amtMinor, termsText, dueEpoch, onSuccess = {
                                            showAddCredit = false
                                            amountText = ""
                                            termsText = "Net 30"
                                            repaymentDaysText = "30"
                                            errorMessage = ""
                                        }, onError = {
                                            errorMessage = "Error: ${it.message}"
                                        })
                                    } else {
                                        errorMessage = "Please enter valid amount and terms"
                                    }
                                }) { Text("Record") }
                            }
                        }
                    }
                }

                if (showMakePayment) {
                    Card(modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp)), shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Deduct / Repay Supplier Credit", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { amountText = it },
                                label = { Text("Payment Amount") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = termsText,
                                onValueChange = { termsText = it },
                                label = { Text("Payment Reference / Notes") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { showMakePayment = false; amountText = ""; termsText = "" }) { Text("Cancel") }
                                Spacer(modifier = Modifier.weight(1f))
                                Button(onClick = {
                                    val amtMinor = amountText.toDoubleOrNull()?.let { (it * 100).toLong() }
                                    if (amtMinor != null && amtMinor > 0) {
                                        val termString = "Repayment Paid" + if (termsText.isNotBlank()) " - $termsText" else ""
                                        viewModel.addSupplierCredit(supplier.id, -amtMinor, termString, 0L, onSuccess = {
                                            showMakePayment = false
                                            amountText = ""
                                            termsText = ""
                                            errorMessage = ""
                                        }, onError = {
                                            errorMessage = "Error: ${it.message}"
                                        })
                                    } else {
                                        errorMessage = "Please enter valid payment amount"
                                    }
                                }) { Text("Record Payment") }
                            }
                        }
                    }
                }

                if (!showAddCredit && !showMakePayment) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showAddCredit = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                            Text("Record Credit Received", fontSize = 10.sp)
                        }
                        Button(onClick = { showMakePayment = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                            Text("Make Repayment", fontSize = 10.sp)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            val sb = StringBuilder()
                            sb.append("========================================\n")
                            sb.append("      SUPPLIER CREDIT RECONCILIATION\n")
                            sb.append("========================================\n")
                            sb.append("Supplier: ${supplier.name}\n")
                            sb.append("Phone: ${supplier.phone ?: "N/A"}\n")
                            sb.append("Date generated: ${df.format(Date())}\n")
                            sb.append("----------------------------------------\n")
                            sb.append("Outstanding Payable Balance: ${Money(bal)}\n")
                            sb.append("Status: ${if (isOverdue) "OVERDUE REPAYMENT ALERT" else "NORMAL"}\n")
                            sb.append("----------------------------------------\n")
                            sb.append("TRANSACTION HISTORY:\n\n")
                            
                            var running = 0L
                            credits.asReversed().forEach { c ->
                                running += c.amountMinorUnits
                                val sign = if (c.amountMinorUnits >= 0) "[CREDIT RECEIVED]" else "[PAYMENT MADE]"
                                sb.append("${df.format(Date(c.dateEpochMs))}\n")
                                sb.append("  Type: $sign\n")
                                sb.append("  Amt: ${Money(Math.abs(c.amountMinorUnits))}\n")
                                sb.append("  Terms: ${c.terms}\n")
                                if (c.amountMinorUnits > 0 && c.dueDateEpochMs > 0) {
                                    sb.append("  Due Date: ${df.format(Date(c.dueDateEpochMs))}\n")
                                }
                                sb.append("  Running Bal: ${Money(running)}\n")
                                sb.append("----------------------------------------\n")
                            }
                            auditReportText = sb.toString()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Generate Reconciliation Report")
                    }
                }

                Text("Ledger History", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                if (credits.isEmpty()) {
                    Text("No transactions logged yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                } else {
                    var currentRunningBalance = 0L
                    val runningBalances = credits.asReversed().map {
                        currentRunningBalance += it.amountMinorUnits
                        currentRunningBalance
                    }.asReversed()

                    credits.forEachIndexed { index, cr ->
                        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        val crBal = runningBalances[index]
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(cr.terms, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(
                                        text = if (cr.amountMinorUnits >= 0) "+${Money(cr.amountMinorUnits)}" else Money(cr.amountMinorUnits).toString(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (cr.amountMinorUnits >= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                if (cr.amountMinorUnits > 0 && cr.dueDateEpochMs > 0) {
                                    val dueStr = df.format(Date(cr.dueDateEpochMs))
                                    Text("Due Date: $dueStr", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(df.format(Date(cr.dateEpochMs)), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    Text("Running Bal: ${Money(crBal)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditLedgerTabScreen(customerVm: CustomerViewModel, supplierVm: SupplierViewModel) {
    val customers by customerVm.customers.collectAsState()
    val suppliers by supplierVm.suppliers.collectAsState()
    
    val totalRecFlow = remember { customerVm.getTotalCustomerCreditsReceivable() }
    val totalPayFlow = remember { supplierVm.getTotalSupplierCreditsPayable() }
    
    val totalRec by totalRecFlow.collectAsState(initial = 0L)
    val totalPay by totalPayFlow.collectAsState(initial = 0L)
    
    var viewMode by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf(0) }
    
    var selectedCustomerForCredit by remember { mutableStateOf<CustomerEntity?>(null) }
    var selectedSupplierForCredit by remember { mutableStateOf<SupplierEntity?>(null) }

    if (selectedCustomerForCredit != null) {
        CustomerCreditDetailDialog(
            customer = selectedCustomerForCredit!!,
            viewModel = customerVm,
            onDismiss = { selectedCustomerForCredit = null }
        )
    }

    if (selectedSupplierForCredit != null) {
        SupplierCreditDetailDialog(
            supplier = selectedSupplierForCredit!!,
            viewModel = supplierVm,
            onDismiss = { selectedSupplierForCredit = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Total Customer Credits Receivable", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(Money(totalRec ?: 0L).toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Total Supplier Credits Payable", fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(Money(totalPay ?: 0L).toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        TabRow(selectedTabIndex = viewMode) {
            Tab(selected = viewMode == 0, onClick = { viewMode = 0; statusFilter = 0; search = "" }, text = { Text("Customer Credits") })
            Tab(selected = viewMode == 1, onClick = { viewMode = 1; statusFilter = 0; search = "" }, text = { Text("Supplier Credits") })
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Search by name") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
            
            var dropdownExpanded by remember { mutableStateOf(false) }
            val filterLabel = when(statusFilter) {
                1 -> "With Balance"
                2 -> if (viewMode == 0) "Exceeded Limit" else "Overdue"
                else -> "All Balances"
            }
            
            Box {
                Button(
                    onClick = { dropdownExpanded = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text(filterLabel)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                    DropdownMenuItem(text = { Text("All Balances") }, onClick = { statusFilter = 0; dropdownExpanded = false })
                    DropdownMenuItem(text = { Text("With Balance") }, onClick = { statusFilter = 1; dropdownExpanded = false })
                    DropdownMenuItem(text = { Text(if (viewMode == 0) "Exceeded Limit" else "Overdue") }, onClick = { statusFilter = 2; dropdownExpanded = false })
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (viewMode == 0) {
                val filteredCustomers = customers.filter { customer ->
                    customer.name.contains(search, ignoreCase = true)
                }
                
                items(filteredCustomers) { customer ->
                    val balanceFlow = remember(customer.id) { customerVm.getCustomerCreditBalance(customer.id) }
                    val balance by balanceFlow.collectAsState(initial = 0L)
                    val bal = balance ?: 0L
                    val isOverLimit = customer.creditLimitMinorUnits > 0L && bal > customer.creditLimitMinorUnits
                    
                    val passesFilter = when(statusFilter) {
                        1 -> bal != 0L
                        2 -> isOverLimit
                        else -> true
                    }
                    
                    if (passesFilter) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCustomerForCredit = customer }
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(customer.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("Credit Limit: ${Money(customer.creditLimitMinorUnits)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = Money(bal).toString(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                    if (isOverLimit) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Over Limit", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val filteredSuppliers = suppliers.filter { supplier ->
                    supplier.name.contains(search, ignoreCase = true)
                }
                
                items(filteredSuppliers) { supplier ->
                    val balanceFlow = remember(supplier.id) { supplierVm.getSupplierCreditBalance(supplier.id) }
                    val balance by balanceFlow.collectAsState(initial = 0L)
                    val bal = balance ?: 0L
                    
                    val creditsFlow = remember(supplier.id) { supplierVm.getSupplierCredits(supplier.id) }
                    val credits by creditsFlow.collectAsState(initial = emptyList<SupplierCreditEntity>())
                    val isOverdue = bal > 0L && credits.any { it.amountMinorUnits > 0L && it.dueDateEpochMs > 0L && it.dueDateEpochMs < System.currentTimeMillis() }
                    
                    val passesFilter = when(statusFilter) {
                        1 -> bal != 0L
                        2 -> isOverdue
                        else -> true
                    }
                    
                    if (passesFilter) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedSupplierForCredit = supplier }
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(supplier.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    val nextDue = credits.filter { it.amountMinorUnits > 0L && it.dueDateEpochMs > 0L }.minByOrNull { it.dueDateEpochMs }
                                    if (nextDue != null && bal > 0L) {
                                        val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                        Text("Next Repayment Due: ${df.format(Date(nextDue.dueDateEpochMs))}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = Money(bal).toString(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                    if (isOverdue) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Repayment Overdue", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
