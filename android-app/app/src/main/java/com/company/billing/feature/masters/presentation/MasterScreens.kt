package com.company.billing.feature.masters.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
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
    val tabs = listOf("Categories", "Products", "Customers", "Suppliers", "Expenses")

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
                .padding(paddingValues)
        ) {
            ScrollableTabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = activeTab == index,
                        onClick = { activeTab = index },
                        text = { Text(title, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                when (activeTab) {
                    0 -> CategoryTabScreen(categoryVm)
                    1 -> ProductTabScreen(productVm)
                    2 -> CustomerTabScreen(customerVm)
                    3 -> SupplierTabScreen(supplierVm)
                    4 -> ExpenseTabScreen(expenseVm)
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
        val isMobile = maxWidth < 600.dp
        
        if (isMobile) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Create Category", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Category Name") },
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
                                shape = RoundedCornerShape(8.dp)
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
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }

                items(categories) { category ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(category.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(category.syncStatus.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(
                    modifier = Modifier.weight(1.2f).fillMaxHeight(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Create Category", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Category Name") },
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
                            shape = RoundedCornerShape(8.dp)
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
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories) { category ->
                            Card(modifier = Modifier.fillMaxWidth()) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductTabScreen(viewModel: ProductViewModel) {
    val products by viewModel.products.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var name by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isMobile = maxWidth < 600.dp
        
        if (isMobile) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Create Product", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Product Name") },
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

                            Button(
                                onClick = {
                                    if (name.isNotBlank() && selectedCategoryId.isNotBlank()) {
                                        viewModel.addProduct(name, selectedCategoryId, onSuccess = {
                                            name = ""
                                            selectedCategoryId = ""
                                            message = "Product added successfully"
                                        }, onError = {
                                            message = "Error: ${it.message}"
                                        })
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
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
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }

                items(products) { product ->
                    val catName = categories.find { it.id == product.categoryId }?.name ?: "Unknown Category"
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(catName, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            Text(product.syncStatus.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(
                    modifier = Modifier.weight(1.2f).fillMaxHeight(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Create Product", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Product Name") },
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

                        Button(
                            onClick = {
                                if (name.isNotBlank() && selectedCategoryId.isNotBlank()) {
                                    viewModel.addProduct(name, selectedCategoryId, onSuccess = {
                                        name = ""
                                        selectedCategoryId = ""
                                        message = "Product added successfully"
                                    }, onError = {
                                        message = "Error: ${it.message}"
                                    })
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
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
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(products) { product ->
                            val catName = categories.find { it.id == product.categoryId }?.name ?: "Unknown Category"
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                        Text(catName, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
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

@Composable
fun CustomerTabScreen(viewModel: CustomerViewModel) {
    val customers by viewModel.customers.collectAsState()
    var name by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isMobile = maxWidth < 600.dp
        
        if (isMobile) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Create Customer", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Customer Name") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    if (name.isNotBlank()) {
                                        viewModel.addCustomer(name, onSuccess = {
                                            name = ""
                                            message = "Customer added successfully"
                                        }, onError = {
                                            message = "Error: ${it.message}"
                                        })
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
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
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }

                items(customers) { customer ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(customer.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(customer.syncStatus.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(
                    modifier = Modifier.weight(1.2f).fillMaxHeight(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Create Customer", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Customer Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                if (name.isNotBlank()) {
                                    viewModel.addCustomer(name, onSuccess = {
                                        name = ""
                                        message = "Customer added successfully"
                                    }, onError = {
                                        message = "Error: ${it.message}"
                                    })
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
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
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(customers) { customer ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(customer.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
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

@Composable
fun SupplierTabScreen(viewModel: SupplierViewModel) {
    val suppliers by viewModel.suppliers.collectAsState()
    var name by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isMobile = maxWidth < 600.dp
        
        if (isMobile) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Create Supplier", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Supplier Name") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    if (name.isNotBlank()) {
                                        viewModel.addSupplier(name, onSuccess = {
                                            name = ""
                                            message = "Supplier added successfully"
                                        }, onError = {
                                            message = "Error: ${it.message}"
                                        })
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
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
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }

                items(suppliers) { supplier ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(supplier.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(supplier.syncStatus.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(
                    modifier = Modifier.weight(1.2f).fillMaxHeight(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Create Supplier", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Supplier Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                if (name.isNotBlank()) {
                                    viewModel.addSupplier(name, onSuccess = {
                                        name = ""
                                        message = "Supplier added successfully"
                                    }, onError = {
                                        message = "Error: ${it.message}"
                                    })
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
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
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(suppliers) { supplier ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(supplier.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
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

@Composable
fun ExpenseTabScreen(viewModel: ExpenseViewModel) {
    val expenses by viewModel.expenses.collectAsState()
    var description by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isMobile = maxWidth < 600.dp
        
        if (isMobile) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Create Expense", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = { Text("Description") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { amountText = it },
                                label = { Text("Amount (e.g. 150.00)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                                shape = RoundedCornerShape(8.dp)
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

                items(expenses) { expense ->
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    val dateStr = dateFormat.format(Date(expense.createdAtEpochMs))
                    Card(modifier = Modifier.fillMaxWidth()) {
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
        } else {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(
                    modifier = Modifier.weight(1.2f).fillMaxHeight(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Create Expense", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it },
                            label = { Text("Amount (e.g. 150.00)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                            shape = RoundedCornerShape(8.dp)
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
                        items(expenses) { expense ->
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            val dateStr = dateFormat.format(Date(expense.createdAtEpochMs))
                            Card(modifier = Modifier.fillMaxWidth()) {
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
