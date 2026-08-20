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
import com.company.billing.feature.masters.data.CategoryEntity
import com.company.billing.feature.masters.data.ProductEntity
import com.company.billing.feature.masters.data.ExpenseEntity
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ExperimentalFoundationApi

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

    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var deletingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    if (editingCategory != null) {
        CategoryEditDialog(
            category = editingCategory!!,
            viewModel = viewModel,
            onDismiss = { editingCategory = null }
        )
    }

    if (deletingCategory != null) {
        DeleteConfirmationDialog(
            title = "Delete Category",
            message = "Are you sure you want to delete category \"${deletingCategory!!.name}\"? This action cannot be undone.",
            onConfirm = {
                val cat = deletingCategory!!
                deletingCategory = null
                viewModel.deleteCategory(cat, onSuccess = {
                    android.widget.Toast.makeText(context, "Category deleted successfully", android.widget.Toast.LENGTH_SHORT).show()
                }, onError = {
                    android.widget.Toast.makeText(context, "Cannot delete category: It might be referenced by products.", android.widget.Toast.LENGTH_LONG).show()
                })
            },
            onDismiss = { deletingCategory = null }
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
                            Row(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(category.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { editingCategory = category }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Category", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { deletingCategory = category }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Category", tint = MaterialTheme.colorScheme.error)
                                    }
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
                                    Row(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(category.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { editingCategory = category }) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit Category", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { deletingCategory = category }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete Category", tint = MaterialTheme.colorScheme.error)
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

    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var deletingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    if (editingProduct != null) {
        ProductEditDialog(
            product = editingProduct!!,
            categories = categories,
            viewModel = viewModel,
            onDismiss = { editingProduct = null }
        )
    }

    if (deletingProduct != null) {
        DeleteConfirmationDialog(
            title = "Delete Product",
            message = "Are you sure you want to delete product \"${deletingProduct!!.name}\"? This action cannot be undone.",
            onConfirm = {
                val prod = deletingProduct!!
                deletingProduct = null
                viewModel.deleteProduct(prod, onSuccess = {
                    android.widget.Toast.makeText(context, "Product deleted successfully", android.widget.Toast.LENGTH_SHORT).show()
                }, onError = {
                    android.widget.Toast.makeText(context, "Cannot delete product: It might be referenced by bills.", android.widget.Toast.LENGTH_LONG).show()
                })
            },
            onDismiss = { deletingProduct = null }
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
                                    value = if (unitType == "KG") "Kg / Grams" else if (unitType == "LITER") "Liters" else "Pieces",
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
                                    DropdownMenuItem(
                                        text = { Text("Liters") },
                                        onClick = {
                                            unitType = "LITER"
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
                            Row(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Text(catName, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                    val purText = Money(product.purchasePriceMinorUnits).toString()
                                    val saleText = Money(product.salePriceMinorUnits).toString()
                                    val unitLabel = if (product.unitType == "KG") "Kg" else if (product.unitType == "LITER") "Ltr" else "Piece"
                                    Text("$unitLabel • Sale: $saleText • Pur: $purText", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { editingProduct = product }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Product", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { deletingProduct = product }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Product", tint = MaterialTheme.colorScheme.error)
                                    }
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
                                value = if (unitType == "KG") "Kg / Grams" else if (unitType == "LITER") "Liters" else "Pieces",
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
                                DropdownMenuItem(
                                    text = { Text("Liters") },
                                    onClick = {
                                        unitType = "LITER"
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
                                    Row(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                            Text(catName, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                            val purText = Money(product.purchasePriceMinorUnits).toString()
                                            val saleText = Money(product.salePriceMinorUnits).toString()
                                            val unitLabel = if (product.unitType == "KG") "Kg" else if (product.unitType == "LITER") "Ltr" else "Piece"
                                            Text("$unitLabel • Sale: $saleText • Pur: $purText", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { editingProduct = product }) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit Product", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { deletingProduct = product }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete Product", tint = MaterialTheme.colorScheme.error)
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

    var editingCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var deletingCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    if (editingCustomer != null) {
        CustomerEditDialog(
            customer = editingCustomer!!,
            viewModel = viewModel,
            onDismiss = { editingCustomer = null }
        )
    }

    if (deletingCustomer != null) {
        DeleteConfirmationDialog(
            title = "Delete Customer",
            message = "Are you sure you want to delete customer \"${deletingCustomer!!.name}\"? This action cannot be undone.",
            onConfirm = {
                val cust = deletingCustomer!!
                deletingCustomer = null
                viewModel.deleteCustomer(cust, onSuccess = {
                    android.widget.Toast.makeText(context, "Customer deleted successfully", android.widget.Toast.LENGTH_SHORT).show()
                }, onError = {
                    android.widget.Toast.makeText(context, "Cannot delete customer: It might be referenced by bills.", android.widget.Toast.LENGTH_LONG).show()
                })
            },
            onDismiss = { deletingCustomer = null }
        )
    }

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
                        val balanceFlow = remember(customer.id) { viewModel.getCustomerBalance(customer.id) }
                        val balance by balanceFlow.collectAsState(initial = 0L)
                        val bal = balance ?: 0L
                        val isOverLimit = customer.creditLimitMinorUnits > 0L && bal > customer.creditLimitMinorUnits

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f).clickable { selectedCustomerForCredit = customer }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(customer.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                        if (isOverLimit) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "Credit Limit Exceeded",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { editingCustomer = customer }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Customer", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { deletingCustomer = customer }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Customer", tint = MaterialTheme.colorScheme.error)
                                    }
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
                                val balanceFlow = remember(customer.id) { viewModel.getCustomerBalance(customer.id) }
                                val balance by balanceFlow.collectAsState(initial = 0L)
                                val bal = balance ?: 0L
                                val isOverLimit = customer.creditLimitMinorUnits > 0L && bal > customer.creditLimitMinorUnits

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f).clickable { selectedCustomerForCredit = customer }) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(customer.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                                if (isOverLimit) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.Warning,
                                                        contentDescription = "Credit Limit Exceeded",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
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
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { editingCustomer = customer }) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit Customer", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { deletingCustomer = customer }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete Customer", tint = MaterialTheme.colorScheme.error)
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

    var editingSupplier by remember { mutableStateOf<SupplierEntity?>(null) }
    var deletingSupplier by remember { mutableStateOf<SupplierEntity?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    if (editingSupplier != null) {
        SupplierEditDialog(
            supplier = editingSupplier!!,
            viewModel = viewModel,
            onDismiss = { editingSupplier = null }
        )
    }

    if (deletingSupplier != null) {
        DeleteConfirmationDialog(
            title = "Delete Supplier",
            message = "Are you sure you want to delete supplier \"${deletingSupplier!!.name}\"? This action cannot be undone.",
            onConfirm = {
                val supp = deletingSupplier!!
                deletingSupplier = null
                viewModel.deleteSupplier(supp, onSuccess = {
                    android.widget.Toast.makeText(context, "Supplier deleted successfully", android.widget.Toast.LENGTH_SHORT).show()
                }, onError = {
                    android.widget.Toast.makeText(context, "Cannot delete supplier: It might be referenced by bills.", android.widget.Toast.LENGTH_LONG).show()
                })
            },
            onDismiss = { deletingSupplier = null }
        )
    }

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
                        val balanceFlow = remember(supplier.id) { viewModel.getSupplierBalance(supplier.id) }
                        val balance by balanceFlow.collectAsState(initial = 0L)
                        val bal = balance ?: 0L
                        
                        val creditsFlow = remember(supplier.id) { viewModel.getSupplierCredits(supplier.id) }
                        val credits by creditsFlow.collectAsState(initial = emptyList<SupplierCreditEntity>())
                        
                        val isOverdue = bal > 0L && credits.any { it.amountMinorUnits > 0L && it.dueDateEpochMs > 0L && it.dueDateEpochMs < System.currentTimeMillis() }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f).clickable { selectedSupplierForCredit = supplier }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(supplier.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                        if (isOverdue) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "Repayment Overdue",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { editingSupplier = supplier }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Supplier", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { deletingSupplier = supplier }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Supplier", tint = MaterialTheme.colorScheme.error)
                                    }
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
                                val balanceFlow = remember(supplier.id) { viewModel.getSupplierBalance(supplier.id) }
                                val balance by balanceFlow.collectAsState(initial = 0L)
                                val bal = balance ?: 0L
                                
                                val creditsFlow = remember(supplier.id) { viewModel.getSupplierCredits(supplier.id) }
                                val credits by creditsFlow.collectAsState(initial = emptyList<SupplierCreditEntity>())
                                
                                val isOverdue = bal > 0L && credits.any { it.amountMinorUnits > 0L && it.dueDateEpochMs > 0L && it.dueDateEpochMs < System.currentTimeMillis() }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f).clickable { selectedSupplierForCredit = supplier }) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(supplier.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                                if (isOverdue) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.Warning,
                                                        contentDescription = "Repayment Overdue",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
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
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { editingSupplier = supplier }) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit Supplier", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { deletingSupplier = supplier }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete Supplier", tint = MaterialTheme.colorScheme.error)
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
}

@Composable
fun ExpenseTabScreen(viewModel: ExpenseViewModel) {
    val expenses by viewModel.expenses.collectAsState()
    var description by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    var editingExpense by remember { mutableStateOf<ExpenseEntity?>(null) }
    var deletingExpense by remember { mutableStateOf<ExpenseEntity?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    if (editingExpense != null) {
        ExpenseEditDialog(
            expense = editingExpense!!,
            viewModel = viewModel,
            onDismiss = { editingExpense = null }
        )
    }

    if (deletingExpense != null) {
        DeleteConfirmationDialog(
            title = "Delete Expense",
            message = "Are you sure you want to delete this expense? This action cannot be undone.",
            onConfirm = {
                val exp = deletingExpense!!
                deletingExpense = null
                viewModel.deleteExpense(exp, onSuccess = {
                    android.widget.Toast.makeText(context, "Expense deleted successfully", android.widget.Toast.LENGTH_SHORT).show()
                }, onError = {
                    android.widget.Toast.makeText(context, "Error: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
                })
            },
            onDismiss = { deletingExpense = null }
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
                            Row(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(expense.description, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Text(dateStr, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(Money(expense.amountMinorUnits).toString(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(onClick = { editingExpense = expense }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Expense", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { deletingExpense = expense }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Expense", tint = MaterialTheme.colorScheme.error)
                                    }
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
                                    Row(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(expense.description, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                            Text(dateStr, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(Money(expense.amountMinorUnits).toString(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.error)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            IconButton(onClick = { editingExpense = expense }) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit Expense", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { deletingExpense = expense }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete Expense", tint = MaterialTheme.colorScheme.error)
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
    val ledger by viewModel.getCustomerLedger(customer.id).collectAsState(initial = emptyList())
    val bal = ledger.firstOrNull()?.runningBalance ?: 0L
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
                            
                            ledger.reversed().forEach { entry ->
                                val sign = if (entry.debitMinorUnits > 0) "[SALE]" else "[PAYMENT]"
                                val amt = if (entry.debitMinorUnits > 0) entry.debitMinorUnits else entry.creditMinorUnits
                                sb.append("${df.format(Date(entry.dateEpochMs))}\n")
                                sb.append("  Type: $sign\n")
                                sb.append("  Amt: ${Money(amt)}\n")
                                sb.append("  Desc: ${entry.description}\n")
                                sb.append("  Running Bal: ${Money(entry.runningBalance)}\n")
                                sb.append("----------------------------------------\n")
                            }
                            auditReportText = sb.toString()
                        }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                            Text("Audit Report", fontSize = 11.sp)
                        }
                    }
                }

                Text("Ledger History", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                if (ledger.isEmpty()) {
                    Text("No transactions logged yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                } else {
                    ledger.forEach { entry ->
                        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        val isDebit = entry.debitMinorUnits > 0
                        val amt = if (isDebit) entry.debitMinorUnits else entry.creditMinorUnits
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(entry.description, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(
                                        text = if (isDebit) "+${Money(amt)}" else "-${Money(amt)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isDebit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(df.format(Date(entry.dateEpochMs)), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    Text("Running Bal: ${Money(entry.runningBalance)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
    val ledger by viewModel.getSupplierLedger(supplier.id).collectAsState(initial = emptyList())
    val bal = ledger.firstOrNull()?.runningBalance ?: 0L
    // Note: Due dates are stored in Purchase tables if applicable, but for overdue we check credits list.
    // For simplicity, overdue check remains based on raw credits if we still want it, but let's fetch credits just for this.
    val credits by viewModel.getSupplierCredits(supplier.id).collectAsState(initial = emptyList<com.company.billing.feature.masters.data.SupplierCreditEntity>())
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
                            
                            ledger.reversed().forEach { entry ->
                                val sign = if (entry.creditMinorUnits > 0) "[PURCHASE/CREDIT RECEIVED]" else "[PAYMENT MADE]"
                                val amt = if (entry.creditMinorUnits > 0) entry.creditMinorUnits else entry.debitMinorUnits
                                sb.append("${df.format(Date(entry.dateEpochMs))}\n")
                                sb.append("  Type: $sign\n")
                                sb.append("  Amt: ${Money(amt)}\n")
                                sb.append("  Desc: ${entry.description}\n")
                                sb.append("  Running Bal: ${Money(entry.runningBalance)}\n")
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
                if (ledger.isEmpty()) {
                    Text("No transactions logged yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                } else {
                    ledger.forEach { entry ->
                        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        val isCredit = entry.creditMinorUnits > 0
                        val amt = if (isCredit) entry.creditMinorUnits else entry.debitMinorUnits
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(entry.description, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(
                                        text = if (isCredit) "+${Money(amt)}" else "-${Money(amt)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isCredit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(df.format(Date(entry.dateEpochMs)), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    Text("Running Bal: ${Money(entry.runningBalance)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Content Above Search Bar (normal scrollable item)
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
        }

        // 2. Sticky Header (TabRow and Search Bar wrapper)
        stickyHeader {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TabRow(selectedTabIndex = viewMode, modifier = Modifier.fillMaxWidth()) {
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
            }
        }

        // 3. Content Below Search Bar (normal scrollable list items)
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

@Composable
fun DeleteConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CategoryEditDialog(
    category: CategoryEntity,
    viewModel: CategoryViewModel,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(category.name) }
    var error by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Category", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        error = "Name cannot be empty"
                    } else {
                        viewModel.updateCategory(category, name, onSuccess = {
                            android.widget.Toast.makeText(context, "Category updated successfully", android.widget.Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }, onError = {
                            error = "Error updating category: ${it.message}"
                        })
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditDialog(
    product: ProductEntity,
    categories: List<CategoryEntity>,
    viewModel: ProductViewModel,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(product.name) }
    var selectedCategoryId by remember { mutableStateOf(product.categoryId) }
    var purchasePrice by remember { mutableStateOf(Money(product.purchasePriceMinorUnits).toString()) }
    var salePrice by remember { mutableStateOf(Money(product.salePriceMinorUnits).toString()) }
    var unitType by remember { mutableStateOf(product.unitType) }
    
    var catExpanded by remember { mutableStateOf(false) }
    var unitExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Product", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                ExposedDropdownMenuBox(
                    expanded = catExpanded,
                    onExpandedChange = { catExpanded = it }
                ) {
                    val currentCatName = categories.find { it.id == selectedCategoryId }?.name ?: "Select Category"
                    OutlinedTextField(
                        value = currentCatName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = catExpanded,
                        onDismissRequest = { catExpanded = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    selectedCategoryId = cat.id
                                    catExpanded = false
                                }
                            )
                        }
                    }
                }
                
                OutlinedTextField(
                    value = purchasePrice,
                    onValueChange = { purchasePrice = it },
                    label = { Text("Purchase Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                OutlinedTextField(
                    value = salePrice,
                    onValueChange = { salePrice = it },
                    label = { Text("Sale Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                ExposedDropdownMenuBox(
                    expanded = unitExpanded,
                    onExpandedChange = { unitExpanded = it }
                ) {
                    OutlinedTextField(
                        value = unitType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Unit Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = unitExpanded,
                        onDismissRequest = { unitExpanded = false }
                    ) {
                        listOf("PIECE", "KG").forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    unitType = type
                                    unitExpanded = false
                                }
                            )
                        }
                    }
                }
                
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pPrice = purchasePrice.toDoubleOrNull()?.let { (it * 100).toLong() }
                    val sPrice = salePrice.toDoubleOrNull()?.let { (it * 100).toLong() }
                    if (name.isBlank() || selectedCategoryId.isBlank() || pPrice == null || sPrice == null) {
                        error = "Please fill in all fields correctly"
                    } else {
                        viewModel.updateProduct(
                            product = product,
                            newName = name,
                            newCategoryId = selectedCategoryId,
                            newPurchasePriceMinorUnits = pPrice,
                            newSalePriceMinorUnits = sPrice,
                            newUnitType = unitType,
                            onSuccess = {
                                android.widget.Toast.makeText(context, "Product updated successfully", android.widget.Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            onError = {
                                error = "Error: ${it.message}"
                            }
                        )
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CustomerEditDialog(
    customer: CustomerEntity,
    viewModel: CustomerViewModel,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(customer.name) }
    var phone by remember { mutableStateOf(customer.phone ?: "") }
    var address by remember { mutableStateOf(customer.address ?: "") }
    var error by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Customer", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Customer Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        error = "Name cannot be empty"
                    } else {
                        viewModel.updateCustomer(
                            customer = customer,
                            newName = name,
                            newPhone = phone.takeIf { it.isNotBlank() },
                            newAddress = address.takeIf { it.isNotBlank() },
                            onSuccess = {
                                android.widget.Toast.makeText(context, "Customer updated successfully", android.widget.Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            onError = {
                                error = "Error: ${it.message}"
                            }
                        )
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SupplierEditDialog(
    supplier: SupplierEntity,
    viewModel: SupplierViewModel,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(supplier.name) }
    var phone by remember { mutableStateOf(supplier.phone ?: "") }
    var address by remember { mutableStateOf(supplier.address ?: "") }
    var error by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Supplier", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Supplier Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        error = "Name cannot be empty"
                    } else {
                        viewModel.updateSupplier(
                            supplier = supplier,
                            newName = name,
                            newPhone = phone.takeIf { it.isNotBlank() },
                            newAddress = address.takeIf { it.isNotBlank() },
                            onSuccess = {
                                android.widget.Toast.makeText(context, "Supplier updated successfully", android.widget.Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            onError = {
                                error = "Error: ${it.message}"
                            }
                        )
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ExpenseEditDialog(
    expense: ExpenseEntity,
    viewModel: ExpenseViewModel,
    onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf(Money(expense.amountMinorUnits).toString()) }
    var description by remember { mutableStateOf(expense.description) }
    var error by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Expense", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull()?.let { (it * 100).toLong() }
                    if (amt == null || description.isBlank()) {
                        error = "Please fill in all fields correctly"
                    } else {
                        viewModel.updateExpense(
                            expense = expense,
                            newAmountMinorUnits = amt,
                            newDescription = description,
                            onSuccess = {
                                android.widget.Toast.makeText(context, "Expense updated successfully", android.widget.Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            onError = {
                                error = "Error: ${it.message}"
                            }
                        )
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
