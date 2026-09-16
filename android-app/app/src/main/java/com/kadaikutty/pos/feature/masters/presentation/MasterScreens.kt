package com.kadaikutty.pos.feature.masters.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.kadaikutty.pos.core.ui.CameraBarcodeScannerDialog
import com.kadaikutty.pos.core.ui.LocalLayoutMode
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.MenuBook
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
import com.kadaikutty.pos.core.common.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.kadaikutty.pos.feature.masters.data.CustomerEntity
import com.kadaikutty.pos.feature.masters.data.SupplierEntity
import com.kadaikutty.pos.feature.masters.data.CategoryEntity
import com.kadaikutty.pos.feature.masters.data.ProductEntity
import com.kadaikutty.pos.feature.masters.data.ExpenseEntity
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterScreens(
    categoryVm: CategoryViewModel,
    productVm: ProductViewModel,
    customerVm: CustomerViewModel,
    supplierVm: SupplierViewModel,
    expenseVm: ExpenseViewModel,
    settingsVm: com.kadaikutty.pos.feature.settings.presentation.SettingsViewModel,
    onBack: () -> Unit = {}
) {
    var activeTab by remember { mutableIntStateOf(0) }
    val userSession by settingsVm.activeSession.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Master Data Management", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
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
            // 2x3 Matrix Grid Header for the 5 Master Data Attributes with Unique Semantic Icons
            val masterTabs = listOf(
                Triple(0, "Categories", Icons.Default.Category),
                Triple(1, "Products", Icons.Default.Inventory2),
                Triple(2, "Customers", Icons.Default.Groups),
                Triple(3, "Suppliers", Icons.Default.LocalShipping),
                Triple(4, "Expenses", Icons.AutoMirrored.Filled.ReceiptLong),
                Triple(5, "Ledger", Icons.AutoMirrored.Filled.MenuBook)
            )

            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(masterTabs) { (index, title, icon) ->
                    val isSelected = activeTab == index
                    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { activeTab = index }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(bgColor)
                                .border(
                                    width = if (isSelected) 0.dp else 1.dp,
                                    color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                tint = contentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(animationSpec = tween(300)) { width -> width } + fadeIn(animationSpec = tween(300))).togetherWith(slideOutHorizontally(animationSpec = tween(300)) { width -> -width } + fadeOut(animationSpec = tween(300)))
                    } else {
                        (slideInHorizontally(animationSpec = tween(300)) { width -> -width } + fadeIn(animationSpec = tween(300))).togetherWith(slideOutHorizontally(animationSpec = tween(300)) { width -> width } + fadeOut(animationSpec = tween(300)))
                    }
                },
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                label = "tab_transition"
            ) { targetTab ->
                when (targetTab) {
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
    var isSubmitting by remember { mutableStateOf(false) }

    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var deletingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    if (editingCategory != null) {
        CategoryEditDialog(
            category = editingCategory!!,
            viewModel = viewModel
        ) { editingCategory = null }
    }

    if (deletingCategory != null) {
        DeleteConfirmationDialog(
            title = "Delete Category",
            message = "Are you sure you want to delete category \"${deletingCategory!!.name}\"? This action cannot be undone.",
            onConfirm = {
                val cat = deletingCategory!!
                deletingCategory = null
                viewModel.deleteCategory(
                    category = cat,
                    onSuccess = {
                        android.widget.Toast.makeText(context, "Category deleted successfully", android.widget.Toast.LENGTH_SHORT).show()
                    }
                ) {
                    android.widget.Toast.makeText(context, "Cannot delete category: It might be referenced by products.", android.widget.Toast.LENGTH_LONG).show()
                }
            },
        ) { deletingCategory = null }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isMobile = when (LocalLayoutMode.current) {
            "Mobile" -> true
            "Tablet" -> false
            else -> this.maxWidth < 600.dp
        }
        
        if (isMobile) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Create Category", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Category Name", fontSize = 13.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    val trimmed = name.trim()
                                    if (trimmed.isNotBlank() && !isSubmitting) {
                                        isSubmitting = true
                                        viewModel.addCategory(
                                            name = trimmed,
                                            onSuccess = {
                                                isSubmitting = false
                                                name = ""
                                                message = "Category added successfully"
                                            }
                                        ) {
                                            isSubmitting = false
                                            message = "Error: ${it.message}"
                                        }
                                    }
                                },
                                enabled = !isSubmitting && name.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Add Category", fontSize = 14.sp)
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
                        label = { Text("Search", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
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
                    items(categories, key = { it.id }) { category ->
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
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Create Category", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Category Name", fontSize = 13.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                val trimmed = name.trim()
                                if (trimmed.isNotBlank() && !isSubmitting) {
                                    isSubmitting = true
                                    viewModel.addCategory(trimmed, onSuccess = {
                                        isSubmitting = false
                                        name = ""
                                        message = "Category added successfully"
                                    }, onError = {
                                        isSubmitting = false
                                        message = "Error: ${it.message}"
                                    })
                                }
                            },
                            enabled = !isSubmitting && name.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add Category", fontSize = 14.sp)
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
                        label = { Text("Search", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
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
    val lowStockProducts by viewModel.lowStockProducts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val stockBalances by viewModel.stockBalances.collectAsState()
    var name by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf("") }
    var purchasePrice by remember { mutableStateOf("") }
    var salePrice by remember { mutableStateOf("") }
    var unitType by remember { mutableStateOf("PIECE") }
    var unitTypeExpanded by remember { mutableStateOf(value = false) }
    var barcode by remember { mutableStateOf("") }
    var minStockLevel by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(value = false) }
    var expandedProductId by remember { mutableStateOf<String?>(null) }
    var showBarcodeScanner by remember { mutableStateOf(value = false) }
    var isSubmitting by remember { mutableStateOf(false) }

    if (showBarcodeScanner) {
        CameraBarcodeScannerDialog(
            title = "Scan Product Barcode",
            continuousScan = false,
            onBarcodeScanned = {
                barcode = it
                showBarcodeScanner = false
            },
            onDismiss = { showBarcodeScanner = false }
        )
    }

    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var deletingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var adjustingStockProduct by remember { mutableStateOf<ProductEntity?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    var isImporting by remember { mutableStateOf(false) }
    var importProgressText by remember { mutableStateOf("") }
    var importSummary by remember { mutableStateOf<ProductImportSummary?>(null) }

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isImporting = true
            importProgressText = "Reading & streaming CSV..."
            viewModel.importProductsFromCsv(
                uri = uri,
                context = context,
                onProgress = { current, _ ->
                    importProgressText = "Processed $current products..."
                },
                onComplete = { summary ->
                    isImporting = false
                    importSummary = summary
                }
            )
        }
    }

    val templateExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            viewModel.exportSampleProductTemplate(uri, context) { success, err ->
                if (success) {
                    android.widget.Toast.makeText(context, "Sample CSV template downloaded successfully!", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    android.widget.Toast.makeText(context, err ?: "Failed to download template", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    if (isImporting) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Importing Products...", fontWeight = FontWeight.Bold) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    Text(importProgressText.ifBlank { "Streaming rows into database..." }, fontSize = 14.sp)
                }
            },
            confirmButton = {}
        )
    }

    if (importSummary != null) {
        val s = importSummary!!
        AlertDialog(
            onDismissRequest = { importSummary = null },
            title = {
                Text(
                    text = if (s.errorMessage == null) "Bulk Import Completed" else "Import Error",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.errorMessage != null) {
                        Text(s.errorMessage, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("Total Lines Read: ${s.totalRead}", fontWeight = FontWeight.SemiBold)
                        Text("Successfully Added: ${s.importedCount} items", color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                        if (s.updatedCount > 0) {
                            Text("Existing Products Updated: ${s.updatedCount} items", color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold)
                        }
                        if (s.skippedCount > 0) {
                            Text("Skipped / Empty Lines: ${s.skippedCount}", color = Color(0xFFF59E0B))
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { importSummary = null }) {
                    Text("Done")
                }
            }
        )
    }

    LaunchedEffect(categories) {
        if (selectedCategoryId.isBlank() && categories.isNotEmpty()) {
            selectedCategoryId = categories.first().id
        }
    }

    if (editingProduct != null) {
        ProductEditDialog(
            product = editingProduct!!,
            categories = categories,
            viewModel = viewModel,
            onDismiss = { editingProduct = null }
        )
    }

    if (adjustingStockProduct != null) {
        StockAdjustmentDialog(
            product = adjustingStockProduct!!,
            currentStock = stockBalances[adjustingStockProduct!!.id] ?: 0L,
            viewModel = viewModel,
            onDismiss = { adjustingStockProduct = null }
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
        val isMobile = when (LocalLayoutMode.current) {
            "Mobile" -> true
            "Tablet" -> false
            else -> this.maxWidth < 600.dp
        }
        
        if (isMobile) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { LowStockAlertsBanner(lowStockProducts) }
                
                // Bulk Import / Template Export Card
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { csvPickerLauncher.launch(arrayOf("*/*")) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Import CSV / Excel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { templateExportLauncher.launch("sample_products_template.csv") },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(4.dp))
                                Text("Template", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Create Product", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Product Name", fontSize = 13.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
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
                                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    categories.forEach { cat ->
                                        DropdownMenuItem(
                                            text = { Text(cat.name) },
                                            onClick = {
                                                selectedCategoryId = cat.id
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = purchasePrice,
                                    onValueChange = { purchasePrice = it },
                                    label = { Text("Pur. Price (₹)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = salePrice,
                                    onValueChange = { salePrice = it },
                                    label = { Text("Sale Price (₹)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = barcode,
                                    onValueChange = { barcode = it },
                                    label = { Text("Barcode / SKU (Optional)") },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                IconButton(
                                    onClick = { showBarcodeScanner = true },
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .size(48.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = "Scan Barcode",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = minStockLevel,
                                onValueChange = { minStockLevel = it },
                                label = { Text("Min Stock Level (Alerts)") },
                                singleLine = true,
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
                                    value = unitType,
                                    onValueChange = {},
                                    label = { Text("Unit Type") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitTypeExpanded) },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = unitTypeExpanded,
                                    onDismissRequest = { unitTypeExpanded = false }
                                ) {
                                    listOf("PIECE", "KG", "LITER", "BOX", "PACK").forEach { type ->
                                        DropdownMenuItem(
                                            text = { Text(type) },
                                            onClick = {
                                                unitType = type
                                                unitTypeExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    val trimmed = name.trim()
                                    if (trimmed.isBlank()) {
                                        android.widget.Toast.makeText(context, "Please enter Product Name", android.widget.Toast.LENGTH_SHORT).show()
                                    } else if (!isSubmitting) {
                                        isSubmitting = true
                                        val purVal = ((purchasePrice.toDoubleOrNull() ?: 0.0) * 100).toLong()
                                        val saleVal = ((salePrice.toDoubleOrNull() ?: 0.0) * 100).toLong()
                                        val minStockVal = minStockLevel.toDoubleOrNull() ?: 0.0
                                        viewModel.addProduct(trimmed, selectedCategoryId, purVal, saleVal, unitType, barcode.ifBlank { null }, minStockVal, onSuccess = {
                                            isSubmitting = false
                                            name = ""
                                            purchasePrice = ""
                                            salePrice = ""
                                            barcode = ""
                                            minStockLevel = "5.0"
                                            message = "Product added successfully"
                                            android.widget.Toast.makeText(context, "Product added successfully", android.widget.Toast.LENGTH_SHORT).show()
                                        }, onError = {
                                            isSubmitting = false
                                            message = "Error: ${it.message}"
                                            android.widget.Toast.makeText(context, "Error: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
                                        })
                                    }
                                },
                                enabled = !isSubmitting && name.isNotBlank(),
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
                        label = { Text("Search", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
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
                    items(products, key = { it.id }) { product ->
                        val catName = categories.find { it.id == product.categoryId }?.name ?: "Unknown Category"
                        val curStock = stockBalances[product.id] ?: 0L
                        val purText = Money(product.purchasePriceMinorUnits).toString()
                        val saleText = Money(product.salePriceMinorUnits).toString()
                        val unitLabel = if (product.unitType == "KG") "Kg" else if (product.unitType == "LITER") "Ltr" else "Piece"
                        val isExpanded = expandedProductId == product.id
                        Card(
                            onClick = { expandedProductId = if (isExpanded) null else product.id },
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize()
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                        Text(catName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f))
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(saleText, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                                        Text("Stock: $curStock $unitLabel", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (curStock.toDouble() <= product.minStockLevel) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                if (isExpanded) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 2.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Purchase: $purText", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                        val margin = if (product.salePriceMinorUnits > 0L) {
                                            ((product.salePriceMinorUnits - product.purchasePriceMinorUnits).toDouble() / product.salePriceMinorUnits * 100).toInt()
                                        } else 0
                                        Text("Margin: $margin%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF059669))
                                    }

                                    if (!product.barcode.isNullOrBlank()) {
                                        Text("Barcode: ${product.barcode}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        FilledTonalIconButton(
                                            onClick = { adjustingStockProduct = product },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Tune, contentDescription = "Adjust Stock", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        FilledTonalIconButton(
                                            onClick = { editingProduct = product },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Product", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        FilledTonalIconButton(
                                            onClick = { deletingProduct = product },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Product", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1.2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LowStockAlertsBanner(lowStockProducts)
                    
                    // Bulk Import / Template Export Card (Tablet Layout)
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { csvPickerLauncher.launch(arrayOf("*/*")) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Import CSV / Excel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { templateExportLauncher.launch("sample_products_template.csv") },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(4.dp))
                                Text("Template", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Create Product", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Product Name", fontSize = 13.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
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
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.name) },
                                        onClick = {
                                            selectedCategoryId = cat.id
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
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )

                            OutlinedTextField(
                                value = salePrice,
                                onValueChange = { salePrice = it },
                                label = { Text("Sale Price (₹)") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )

                            OutlinedTextField(
                                value = barcode,
                                onValueChange = { barcode = it },
                                label = { Text("Barcode / EAN (Optional)") },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { showBarcodeScanner = true }) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode", tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = minStockLevel,
                                onValueChange = { minStockLevel = it },
                                label = { Text("Min Stock Level (Alerts)") },
                                singleLine = true,
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
                                value = unitType,
                                onValueChange = {},
                                label = { Text("Unit Type") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitTypeExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = unitTypeExpanded,
                                onDismissRequest = { unitTypeExpanded = false }
                            ) {
                                listOf("PIECE", "KG", "LITER", "BOX", "PACK").forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type) },
                                        onClick = {
                                            unitType = type
                                            unitTypeExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val trimmed = name.trim()
                                if (trimmed.isBlank()) {
                                    message = "Product name cannot be empty"
                                } else if (!isSubmitting) {
                                    isSubmitting = true
                                    val purVal = ((purchasePrice.toDoubleOrNull() ?: 0.0) * 100).toLong()
                                    val saleVal = ((salePrice.toDoubleOrNull() ?: 0.0) * 100).toLong()
                                    val minStockVal = minStockLevel.toDoubleOrNull() ?: 0.0
                                    viewModel.addProduct(trimmed, selectedCategoryId, purVal, saleVal, unitType, barcode.ifBlank { null }, minStockVal, onSuccess = {
                                        isSubmitting = false
                                        name = ""
                                        purchasePrice = ""
                                        salePrice = ""
                                        unitType = "PIECE"
                                        barcode = ""
                                        minStockLevel = ""
                                        message = "Product added successfully"
                                        android.widget.Toast.makeText(context, "Product added successfully", android.widget.Toast.LENGTH_SHORT).show()
                                    }, onError = {
                                        isSubmitting = false
                                        message = "Error: ${it.message}"
                                        android.widget.Toast.makeText(context, "Error: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
                                    })
                                }
                            },
                            enabled = !isSubmitting && name.isNotBlank(),
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
                } // End of Column for LowStockAlertsBanner + Card

                Column(modifier = Modifier.weight(1.8f).fillMaxHeight()) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = {
                            search = it
                            viewModel.updateSearch(it)
                        },
                        label = { Text("Search", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
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
                                val curStock = stockBalances[product.id] ?: 0L
                                val purText = Money(product.purchasePriceMinorUnits).toString()
                                val saleText = Money(product.salePriceMinorUnits).toString()
                                val unitLabel = if (product.unitType == "KG") "Kg" else if (product.unitType == "LITER") "Ltr" else "Piece"
                                Card(
                                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                            Text(catName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f))
                                            Text("Stock: $curStock $unitLabel • Sale: $saleText • Pur: $purText", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { adjustingStockProduct = product }) {
                                                Icon(Icons.Default.Tune, contentDescription = "Adjust Stock", tint = MaterialTheme.colorScheme.secondary)
                                            }
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
    var initialDebtText by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var selectedCustomerForCredit by remember { mutableStateOf<CustomerEntity?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

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
        val isMobile = when (LocalLayoutMode.current) {
            "Mobile" -> true
            "Tablet" -> false
            else -> this.maxWidth < 600.dp
        }
        
        if (isMobile) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Create Customer", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Customer Name") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("Phone Number") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = address,
                                onValueChange = { address = it },
                                label = { Text("Address (Optional)") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = initialDebtText,
                                onValueChange = { initialDebtText = it },
                                label = { Text("Opening Debt / Balance (₹)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    val trimmed = name.trim()
                                    if (trimmed.isNotBlank() && !isSubmitting) {
                                        isSubmitting = true
                                        val initialDebtVal = ((initialDebtText.toDoubleOrNull() ?: 0.0) * 100).toLong()
                                        viewModel.addCustomer(
                                            name = trimmed,
                                            phone = phone.trim().takeIf { it.isNotBlank() },
                                            address = address.trim().takeIf { it.isNotBlank() },
                                            initialDebtMinorUnits = initialDebtVal,
                                            onSuccess = {
                                                isSubmitting = false
                                                name = ""
                                                phone = ""
                                                address = ""
                                                initialDebtText = ""
                                                message = "Customer added successfully"
                                            },
                                            onError = {
                                                isSubmitting = false
                                                message = "Error: ${it.message}"
                                            }
                                        )
                                    }
                                },
                                enabled = !isSubmitting && name.isNotBlank(),
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
                        label = { Text("Search", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
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
                    items(customers, key = { it.id }) { customer ->
                        val balanceFlow = remember(customer.id) { viewModel.getCustomerBalance(customer.id) }
                        val balance by balanceFlow.collectAsState(initial = 0L)
                        val bal = balance
                        val isOverLimit = (customer.creditLimitMinorUnits > 0L) && (bal > customer.creditLimitMinorUnits)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Row(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f).clickable { selectedCustomerForCredit = customer }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(customer.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                        if (isOverLimit) {
                                            Spacer(modifier = Modifier.width(6.dp))
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
                                            color = if (isOverLimit) MaterialTheme.colorScheme.error else Color(0xFFFF9800)
                                        )
                                    }
                                    if (!customer.phone.isNullOrBlank() || !customer.address.isNullOrBlank()) {
                                        Text(
                                            text = listOfNotNull(customer.phone, customer.address).joinToString(" | "),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
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
                    shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Create Customer", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Customer Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone Number") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Address (Optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = initialDebtText,
                            onValueChange = { initialDebtText = it },
                            label = { Text("Opening Debt / Balance (₹)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                val trimmed = name.trim()
                                if (trimmed.isNotBlank() && !isSubmitting) {
                                    isSubmitting = true
                                    val initialDebtVal = ((initialDebtText.toDoubleOrNull() ?: 0.0) * 100).toLong()
                                    viewModel.addCustomer(
                                        name = trimmed,
                                        phone = phone.trim().takeIf { it.isNotBlank() },
                                        address = address.trim().takeIf { it.isNotBlank() },
                                        initialDebtMinorUnits = initialDebtVal,
                                        onSuccess = {
                                            isSubmitting = false
                                            name = ""
                                            phone = ""
                                            address = ""
                                            initialDebtText = ""
                                            message = "Customer added successfully"
                                        },
                                        onError = {
                                            isSubmitting = false
                                            message = "Error: ${it.message}"
                                        }
                                    )
                                }
                            },
                            enabled = !isSubmitting && name.isNotBlank(),
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
                        label = { Text("Search", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
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
                                val bal = balance
                                val isOverLimit = (customer.creditLimitMinorUnits > 0L) && (bal > customer.creditLimitMinorUnits)

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f).clickable { selectedCustomerForCredit = customer }) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(customer.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                                if (isOverLimit) {
                                                    Spacer(modifier = Modifier.width(6.dp))
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
                                                    color = if (isOverLimit) MaterialTheme.colorScheme.error else Color(0xFFFF9800)
                                                )
                                            }
                                            if (!customer.phone.isNullOrBlank() || !customer.address.isNullOrBlank()) {
                                                Text(
                                                    text = listOfNotNull(customer.phone, customer.address).joinToString(" | "),
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
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
    var isSubmitting by remember { mutableStateOf(false) }

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
        val isMobile = when (LocalLayoutMode.current) {
            "Mobile" -> true
            "Tablet" -> false
            else -> this.maxWidth < 600.dp
        }
        
        if (isMobile) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Create Supplier", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Supplier Name") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("Phone Number") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = address,
                                onValueChange = { address = it },
                                label = { Text("Address") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    val trimmed = name.trim()
                                    if (trimmed.isNotBlank() && !isSubmitting) {
                                        isSubmitting = true
                                        viewModel.addSupplier(
                                            name = trimmed,
                                            phone = phone.trim().takeIf { it.isNotBlank() },
                                            address = address.trim().takeIf { it.isNotBlank() },
                                            onSuccess = {
                                                isSubmitting = false
                                                name = ""
                                                phone = ""
                                                address = ""
                                                message = "Supplier added successfully"
                                            },
                                            onError = {
                                                isSubmitting = false
                                                message = "Error: ${it.message}"
                                            }
                                        )
                                    }
                                },
                                enabled = !isSubmitting && name.isNotBlank(),
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
                        label = { Text("Search", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
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
                    items(suppliers, key = { it.id }) { supplier ->
                        val balanceFlow = remember(supplier.id) { viewModel.getSupplierBalance(supplier.id) }
                        val balance by balanceFlow.collectAsState(initial = 0L)
                        val bal = balance
                        
                        val creditsFlow = remember(supplier.id) { viewModel.getSupplierCredits(supplier.id) }
                        val credits by creditsFlow.collectAsState(initial = emptyList())
                        
                        val isOverdue = (bal > 0L) && credits.any { (it.amountMinorUnits > 0L) && (it.dueDateEpochMs > 0L) && (it.dueDateEpochMs < System.currentTimeMillis()) }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f).clickable { selectedSupplierForCredit = supplier }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(supplier.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                        if (isOverdue) {
                                            Spacer(modifier = Modifier.width(6.dp))
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
                                            color = if (isOverdue) MaterialTheme.colorScheme.error else Color(0xFFFF9800)
                                        )
                                    }
                                    if (!supplier.phone.isNullOrBlank() || !supplier.address.isNullOrBlank()) {
                                        Text(
                                            text = listOfNotNull(supplier.phone, supplier.address).joinToString(" | "),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
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
                    shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Create Supplier", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Supplier Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone Number") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Address") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                val trimmed = name.trim()
                                if (trimmed.isNotBlank() && !isSubmitting) {
                                    isSubmitting = true
                                    viewModel.addSupplier(
                                        name = trimmed,
                                        phone = phone.trim().takeIf { it.isNotBlank() },
                                        address = address.trim().takeIf { it.isNotBlank() },
                                        onSuccess = {
                                            isSubmitting = false
                                            name = ""
                                            phone = ""
                                            address = ""
                                            message = "Supplier added successfully"
                                        },
                                        onError = {
                                            isSubmitting = false
                                            message = "Error: ${it.message}"
                                        }
                                    )
                                }
                            },
                            enabled = !isSubmitting && name.isNotBlank(),
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
                        label = { Text("Search", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
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
                        val credits by creditsFlow.collectAsState(initial = emptyList())
                                
                                val isOverdue = bal > 0L && credits.any { it.amountMinorUnits > 0L && it.dueDateEpochMs > 0L && it.dueDateEpochMs < System.currentTimeMillis() }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f).clickable { selectedSupplierForCredit = supplier }) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(supplier.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                                if (isOverdue) {
                                                    Spacer(modifier = Modifier.width(6.dp))
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
                                                    color = if (isOverdue) MaterialTheme.colorScheme.error else Color(0xFFFF9800)
                                                )
                                            }
                                            if (!supplier.phone.isNullOrBlank() || !supplier.address.isNullOrBlank()) {
                                                Text(
                                                    text = listOfNotNull(supplier.phone, supplier.address).joinToString(" | "),
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
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
    var isSubmitting by remember { mutableStateOf(false) }

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
        val isMobile = when (LocalLayoutMode.current) {
            "Mobile" -> true
            "Tablet" -> false
            else -> this.maxWidth < 600.dp
        }
        
        if (isMobile) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Create Expense", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                                    val trimmed = description.trim()
                                    val amountDouble = amountText.toDoubleOrNull()
                                    if (trimmed.isNotBlank() && amountDouble != null && !isSubmitting) {
                                        isSubmitting = true
                                        val minorUnits = (amountDouble * 100).toLong()
                                        viewModel.addExpense(minorUnits, trimmed, onSuccess = {
                                            isSubmitting = false
                                            description = ""
                                            amountText = ""
                                            message = "Expense recorded successfully"
                                        }, onError = {
                                            isSubmitting = false
                                            message = "Error: ${it.message}"
                                        })
                                    } else if (!isSubmitting) {
                                        message = "Invalid amount or description"
                                    }
                                },
                                enabled = !isSubmitting && description.isNotBlank(),
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
                    items(expenses, key = { it.id }) { expense ->
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
                                    Text(dateStr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f))
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
                    shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Create Expense", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                                val trimmed = description.trim()
                                val amountDouble = amountText.toDoubleOrNull()
                                if (trimmed.isNotBlank() && amountDouble != null && !isSubmitting) {
                                    isSubmitting = true
                                    val minorUnits = (amountDouble * 100).toLong()
                                    viewModel.addExpense(minorUnits, trimmed, onSuccess = {
                                        isSubmitting = false
                                        description = ""
                                        amountText = ""
                                        message = "Expense recorded successfully"
                                    }, onError = {
                                        isSubmitting = false
                                        message = "Error: ${it.message}"
                                    })
                                } else if (!isSubmitting) {
                                    message = "Invalid amount or description"
                                }
                            },
                            enabled = !isSubmitting && description.isNotBlank(),
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

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
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
                                            Text(dateStr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f))
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
    val isOverLimit = (customer.creditLimitMinorUnits > 0L) && (bal > customer.creditLimitMinorUnits)

    var amountText by remember { mutableStateOf("") }
    var reasonText by remember { mutableStateOf("") }
    var limitText by remember { mutableStateOf("") }
    
    var showAddCredit by remember { mutableStateOf(value = false) }
    var showReceivePayment by remember { mutableStateOf(value = false) }
    var showSetLimit by remember { mutableStateOf(false) }
    
    var auditReportText by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf("") }
    var isActionSubmitting by remember { mutableStateOf(false) }

    if (auditReportText != null) {
        AuditReportDialog(
            title = "Audit Report - ${customer.name}",
            reportText = auditReportText!!,
            onDismiss = { auditReportText = null }
        )
    }

    AlertDialog(
        onDismissRequest = {
            if (!isActionSubmitting) onDismiss()
        },
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
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isActionSubmitting
                            )
                            OutlinedTextField(
                                value = reasonText,
                                onValueChange = { reasonText = it },
                                label = { Text("Reason / Description") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isActionSubmitting
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    enabled = !isActionSubmitting,
                                    onClick = { showAddCredit = false; amountText = ""; reasonText = "" }
                                ) { Text("Cancel") }
                                Spacer(modifier = Modifier.weight(1f))
                                Button(
                                    enabled = !isActionSubmitting,
                                    onClick = {
                                        if (isActionSubmitting) return@Button
                                        val amtMinor = amountText.toDoubleOrNull()?.let { (it * 100).toLong() }
                                        if (amtMinor != null && amtMinor > 0 && reasonText.isNotBlank()) {
                                            isActionSubmitting = true
                                            viewModel.addCustomerCredit(customer.id, amtMinor, reasonText, onSuccess = {
                                                isActionSubmitting = false
                                                showAddCredit = false
                                                amountText = ""
                                                reasonText = ""
                                                errorMessage = ""
                                            }, onError = {
                                                isActionSubmitting = false
                                                errorMessage = "Error: ${it.message}"
                                            })
                                        } else {
                                            errorMessage = "Please enter valid amount and description"
                                        }
                                    }
                                ) { Text(if (isActionSubmitting) "Recording..." else "Record") }
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
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isActionSubmitting
                            )
                            OutlinedTextField(
                                value = reasonText,
                                onValueChange = { reasonText = it },
                                label = { Text("Notes (e.g. Receipt No, Cash/UPI)") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isActionSubmitting
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    enabled = !isActionSubmitting,
                                    onClick = { showReceivePayment = false; amountText = ""; reasonText = "" }
                                ) { Text("Cancel") }
                                Spacer(modifier = Modifier.weight(1f))
                                Button(
                                    enabled = !isActionSubmitting,
                                    onClick = {
                                        if (isActionSubmitting) return@Button
                                        val amtMinor = amountText.toDoubleOrNull()?.let { (it * 100).toLong() }
                                        if (amtMinor != null && amtMinor > 0) {
                                            isActionSubmitting = true
                                            val reasonString = "Payment Received" + if (reasonText.isNotBlank()) " - $reasonText" else ""
                                            viewModel.addCustomerCredit(customer.id, -amtMinor, reasonString, onSuccess = {
                                                isActionSubmitting = false
                                                showReceivePayment = false
                                                amountText = ""
                                                reasonText = ""
                                                errorMessage = ""
                                            }, onError = {
                                                isActionSubmitting = false
                                                errorMessage = "Error: ${it.message}"
                                            })
                                        } else {
                                            errorMessage = "Please enter valid payment amount"
                                        }
                                    }
                                ) { Text(if (isActionSubmitting) "Recording..." else "Record Settle") }
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
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isActionSubmitting
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    enabled = !isActionSubmitting,
                                    onClick = { showSetLimit = false; limitText = "" }
                                ) { Text("Cancel") }
                                Spacer(modifier = Modifier.weight(1f))
                                Button(
                                    enabled = !isActionSubmitting,
                                    onClick = {
                                        if (isActionSubmitting) return@Button
                                        val limitMinor = limitText.toDoubleOrNull()?.let { (it * 100).toLong() }
                                        if (limitMinor != null && limitMinor >= 0) {
                                            isActionSubmitting = true
                                            viewModel.updateCustomerCreditLimit(customer.id, limitMinor, onSuccess = {
                                                isActionSubmitting = false
                                                showSetLimit = false
                                                limitText = ""
                                                errorMessage = ""
                                            }, onError = {
                                                isActionSubmitting = false
                                                errorMessage = "Error: ${it.message}"
                                            })
                                        } else {
                                            errorMessage = "Please enter a valid credit limit"
                                        }
                                    }
                                ) { Text(if (isActionSubmitting) "Saving..." else "Save Limit") }
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
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(entry.description, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = if (isDebit) "+${Money(amt)}" else "-${Money(amt)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isDebit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                        if (!entry.description.startsWith("Bill #")) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.deleteCustomerCredit(entry.id, onSuccess = {}, onError = { errorMessage = it.message ?: "Failed to delete" })
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Reverse Entry", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                                            }
                                        }
                                    }
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
    val credits by viewModel.getSupplierCredits(supplier.id).collectAsState(initial = emptyList())
    val isOverdue = bal > 0L && credits.any { it.amountMinorUnits > 0L && it.dueDateEpochMs > 0L && it.dueDateEpochMs < System.currentTimeMillis() }

    var amountText by remember { mutableStateOf("") }
    var termsText by remember { mutableStateOf("Net 30") }
    var repaymentDaysText by remember { mutableStateOf("30") }
    
    var showAddCredit by remember { mutableStateOf(value = false) }
    var showMakePayment by remember { mutableStateOf(value = false) }
    
    var auditReportText by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf("") }
    var isActionSubmitting by remember { mutableStateOf(false) }

    if (auditReportText != null) {
        AuditReportDialog(
            title = "Supplier Audit Report - ${supplier.name}",
            reportText = auditReportText!!,
            onDismiss = { auditReportText = null }
        )
    }

    AlertDialog(
        onDismissRequest = {
            if (!isActionSubmitting) onDismiss()
        },
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
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isActionSubmitting
                            )
                            OutlinedTextField(
                                value = termsText,
                                onValueChange = { termsText = it },
                                label = { Text("Terms (e.g. Net 30, Cash on Del)") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isActionSubmitting
                            )
                            OutlinedTextField(
                                value = repaymentDaysText,
                                onValueChange = { repaymentDaysText = it },
                                label = { Text("Repayment Due Days") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isActionSubmitting
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    enabled = !isActionSubmitting,
                                    onClick = { showAddCredit = false; amountText = ""; termsText = "Net 30"; repaymentDaysText = "30" }
                                ) { Text("Cancel") }
                                Spacer(modifier = Modifier.weight(1f))
                                Button(
                                    enabled = !isActionSubmitting,
                                    onClick = {
                                        if (isActionSubmitting) return@Button
                                        val amtMinor = amountText.toDoubleOrNull()?.let { (it * 100).toLong() }
                                        val days = repaymentDaysText.toLongOrNull() ?: 30L
                                        if (amtMinor != null && amtMinor > 0 && termsText.isNotBlank()) {
                                            isActionSubmitting = true
                                            val dueEpoch = System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000L)
                                            viewModel.addSupplierCredit(supplier.id, amtMinor, termsText, dueEpoch, onSuccess = {
                                                isActionSubmitting = false
                                                showAddCredit = false
                                                amountText = ""
                                                termsText = "Net 30"
                                                repaymentDaysText = "30"
                                                errorMessage = ""
                                            }, onError = {
                                                isActionSubmitting = false
                                                errorMessage = "Error: ${it.message}"
                                            })
                                        } else {
                                            errorMessage = "Please enter valid amount and terms"
                                        }
                                    }
                                ) { Text(if (isActionSubmitting) "Recording..." else "Record") }
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
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isActionSubmitting
                            )
                            OutlinedTextField(
                                value = termsText,
                                onValueChange = { termsText = it },
                                label = { Text("Payment Reference / Notes") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isActionSubmitting
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    enabled = !isActionSubmitting,
                                    onClick = { showMakePayment = false; amountText = ""; termsText = "" }
                                ) { Text("Cancel") }
                                Spacer(modifier = Modifier.weight(1f))
                                Button(
                                    enabled = !isActionSubmitting,
                                    onClick = {
                                        if (isActionSubmitting) return@Button
                                        val amtMinor = amountText.toDoubleOrNull()?.let { (it * 100).toLong() }
                                        if (amtMinor != null && amtMinor > 0) {
                                            isActionSubmitting = true
                                            val termString = "Repayment Paid" + if (termsText.isNotBlank()) " - $termsText" else ""
                                            viewModel.addSupplierCredit(supplier.id, -amtMinor, termString, 0L, onSuccess = {
                                                isActionSubmitting = false
                                                showMakePayment = false
                                                amountText = ""
                                                termsText = ""
                                                errorMessage = ""
                                            }, onError = {
                                                isActionSubmitting = false
                                                errorMessage = "Error: ${it.message}"
                                            })
                                        } else {
                                            errorMessage = "Please enter valid payment amount"
                                        }
                                    }
                                ) { Text(if (isActionSubmitting) "Recording..." else "Record Payment") }
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
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(entry.description, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = if (isCredit) "+${Money(amt)}" else "-${Money(amt)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isCredit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                        if (!entry.description.startsWith("Purchase") && !entry.description.startsWith("Order #")) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.deleteSupplierCredit(entry.id, onSuccess = {}, onError = { errorMessage = it.message ?: "Failed to delete" })
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Reverse Entry", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                                            }
                                        }
                                    }
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
    
    var viewMode by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableIntStateOf(0) }
    
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
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Content Above Search Bar (normal scrollable item)
        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max).padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Total Customer Credits Receivable", fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(Money(totalRec ?: 0L).toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
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
                    .padding(top = 4.dp, bottom = 8.dp),
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
                        label = { Text("Search", fontSize = 13.sp) },
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
                val balanceFlow = remember(customer.id) { customerVm.getCustomerBalance(customer.id) }
                val balance by balanceFlow.collectAsState(initial = 0L)
                val bal = balance
                val isOverLimit = (customer.creditLimitMinorUnits > 0L) && (bal > customer.creditLimitMinorUnits)
                
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
                                Text("Credit Limit: ${Money(customer.creditLimitMinorUnits)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (bal > 0L) "Due: ${Money(bal)}" else Money(bal).toString(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = when {
                                        isOverLimit -> MaterialTheme.colorScheme.error
                                        bal > 0L -> Color(0xFFFF9800)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    }
                                )
                                if (isOverLimit) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Over Limit", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                                    }
                                } else if (bal == 0L) {
                                    Text("Settled", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
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
                val balanceFlow = remember(supplier.id) { supplierVm.getSupplierBalance(supplier.id) }
                val balance by balanceFlow.collectAsState(initial = 0L)
                val bal = balance
                
                val creditsFlow = remember(supplier.id) { supplierVm.getSupplierCredits(supplier.id) }
                val credits by creditsFlow.collectAsState(initial = emptyList())
                val isOverdue = (bal > 0L) && credits.any { (it.amountMinorUnits > 0L) && (it.dueDateEpochMs > 0L) && (it.dueDateEpochMs < System.currentTimeMillis()) }
                
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
                                val nextDue = credits.asSequence().filter { it.amountMinorUnits > 0L && it.dueDateEpochMs > 0L }.minByOrNull { it.dueDateEpochMs }
                                if (nextDue != null && bal > 0L) {
                                    val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                    Text("Next Repayment Due: ${df.format(Date(nextDue.dueDateEpochMs))}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (bal > 0L) "Payable: ${Money(bal)}" else Money(bal).toString(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = when {
                                        isOverdue -> MaterialTheme.colorScheme.error
                                        bal > 0L -> Color(0xFFFF9800)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    }
                                )
                                if (isOverdue) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Repayment Overdue", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                                    }
                                } else if (bal == 0L) {
                                    Text("Settled", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
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
    var purchasePrice by remember { mutableStateOf(String.format(java.util.Locale.US, "%.2f", product.purchasePriceMinorUnits / 100.0)) }
    var salePrice by remember { mutableStateOf(String.format(java.util.Locale.US, "%.2f", product.salePriceMinorUnits / 100.0)) }
    var unitType by remember { mutableStateOf(product.unitType) }
    var barcode by remember { mutableStateOf(product.barcode ?: "") }
    var minStockLevel by remember { mutableStateOf(product.minStockLevel.toString()) }
    
    var catExpanded by remember { mutableStateOf(false) }
    var unitExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    if (showBarcodeScanner) {
        CameraBarcodeScannerDialog(
            title = "Scan Product Barcode",
            continuousScan = false,
            onBarcodeScanned = {
                barcode = it
                showBarcodeScanner = false
            },
            onDismiss = { showBarcodeScanner = false }
        )
    }
    
    AlertDialog(
        onDismissRequest = {
            if (!isSubmitting) onDismiss()
        },
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
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSubmitting
                )
                
                ExposedDropdownMenuBox(
                    expanded = catExpanded,
                    onExpandedChange = { if (!isSubmitting) catExpanded = it }
                ) {
                    val currentCatName = categories.find { it.id == selectedCategoryId }?.name ?: "Select Category"
                    OutlinedTextField(
                        value = currentCatName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = !isSubmitting),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isSubmitting
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
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSubmitting
                )
                
                OutlinedTextField(
                    value = salePrice,
                    onValueChange = { salePrice = it },
                    label = { Text("Sale Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSubmitting
                )

                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text("Barcode / EAN (Optional)") },
                    trailingIcon = {
                        IconButton(
                            enabled = !isSubmitting,
                            onClick = { showBarcodeScanner = true }
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSubmitting
                )

                OutlinedTextField(
                    value = minStockLevel,
                    onValueChange = { minStockLevel = it },
                    label = { Text("Min Stock Level (Alerts)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSubmitting
                )
                
                ExposedDropdownMenuBox(
                    expanded = unitExpanded,
                    onExpandedChange = { if (!isSubmitting) unitExpanded = it }
                ) {
                    OutlinedTextField(
                        value = unitType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Unit Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = !isSubmitting),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isSubmitting
                    )
                    ExposedDropdownMenu(
                        expanded = unitExpanded,
                        onDismissRequest = { unitExpanded = false }
                    ) {
                        listOf("PIECE", "KG", "LITER", "BOX", "PACK").forEach { type ->
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
                enabled = !isSubmitting,
                onClick = {
                    if (isSubmitting) return@Button
                    val pPrice = purchasePrice.toDoubleOrNull()?.let { (it * 100).toLong() }
                    val sPrice = salePrice.toDoubleOrNull()?.let { (it * 100).toLong() }
                    val minStockVal = minStockLevel.toDoubleOrNull() ?: 0.0
                    if (name.isBlank() || selectedCategoryId.isBlank() || pPrice == null || sPrice == null) {
                        error = "Please fill in all fields correctly"
                    } else {
                        isSubmitting = true
                        viewModel.updateProduct(
                            product = product,
                            newName = name,
                            newCategoryId = selectedCategoryId,
                            newPurchasePriceMinorUnits = pPrice,
                            newSalePriceMinorUnits = sPrice,
                            newUnitType = unitType,
                            newBarcode = barcode.ifBlank { null },
                            newMinStockLevel = minStockVal,
                            onSuccess = {
                                isSubmitting = false
                                android.widget.Toast.makeText(context, "Product updated successfully", android.widget.Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            onError = {
                                isSubmitting = false
                                error = "Error: ${it.message}"
                            }
                        )
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isSubmitting) "Updating..." else "Update")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = onDismiss
            ) {
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
    var isSubmitting by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    AlertDialog(
        onDismissRequest = {
            if (!isSubmitting) onDismiss()
        },
        title = { Text("Edit Customer", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Customer Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSubmitting
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSubmitting
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSubmitting
                )
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting,
                onClick = {
                    if (isSubmitting) return@Button
                    if (name.isBlank()) {
                        error = "Name cannot be empty"
                    } else {
                        isSubmitting = true
                        viewModel.updateCustomer(
                            customer = customer,
                            newName = name,
                            newPhone = phone.takeIf { it.isNotBlank() },
                            newAddress = address.takeIf { it.isNotBlank() },
                            onSuccess = {
                                isSubmitting = false
                                android.widget.Toast.makeText(context, "Customer updated successfully", android.widget.Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            onError = {
                                isSubmitting = false
                                error = "Error: ${it.message}"
                            }
                        )
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isSubmitting) "Updating..." else "Update")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = onDismiss
            ) {
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
    var isSubmitting by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    AlertDialog(
        onDismissRequest = {
            if (!isSubmitting) onDismiss()
        },
        title = { Text("Edit Supplier", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Supplier Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSubmitting
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSubmitting
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSubmitting
                )
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting,
                onClick = {
                    if (isSubmitting) return@Button
                    if (name.isBlank()) {
                        error = "Name cannot be empty"
                    } else {
                        isSubmitting = true
                        viewModel.updateSupplier(
                            supplier = supplier,
                            newName = name,
                            newPhone = phone.takeIf { it.isNotBlank() },
                            newAddress = address.takeIf { it.isNotBlank() },
                            onSuccess = {
                                isSubmitting = false
                                android.widget.Toast.makeText(context, "Supplier updated successfully", android.widget.Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            onError = {
                                isSubmitting = false
                                error = "Error: ${it.message}"
                            }
                        )
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isSubmitting) "Updating..." else "Update")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = onDismiss
            ) {
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
    var amount by remember { mutableStateOf(String.format(java.util.Locale.US, "%.2f", expense.amountMinorUnits / 100.0)) }
    var description by remember { mutableStateOf(expense.description) }
    var error by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    AlertDialog(
        onDismissRequest = {
            if (!isSubmitting) onDismiss()
        },
        title = { Text("Edit Expense", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSubmitting
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSubmitting
                )
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting,
                onClick = {
                    if (isSubmitting) return@Button
                    val amt = amount.toDoubleOrNull()?.let { (it * 100).toLong() }
                    if (amt == null || description.isBlank()) {
                        error = "Please fill in all fields correctly"
                    } else {
                        isSubmitting = true
                        viewModel.updateExpense(
                            expense = expense,
                            newAmountMinorUnits = amt,
                            newDescription = description,
                            onSuccess = {
                                isSubmitting = false
                                android.widget.Toast.makeText(context, "Expense updated successfully", android.widget.Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            onError = {
                                isSubmitting = false
                                error = "Error: ${it.message}"
                            }
                        )
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isSubmitting) "Updating..." else "Update")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun LowStockAlertsBanner(lowStockProducts: List<com.kadaikutty.pos.core.database.LowStockRow>) {
    if (lowStockProducts.isEmpty()) return
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Low Stock Alerts",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            lowStockProducts.forEach { item ->
                Text(
                    text = "• ${item.productName}: ${item.currentStock} remaining (Min: ${item.minStockLevel})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockAdjustmentDialog(
    product: ProductEntity,
    currentStock: Long,
    viewModel: ProductViewModel,
    onDismiss: () -> Unit
) {
    var newStockText by remember { mutableStateOf(currentStock.toString()) }
    var selectedReason by remember { mutableStateOf("Damaged / Expired") }
    var customReason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val reasons = listOf("Damaged / Expired", "Count Correction", "Lost / Missing", "Found Extra", "Opening Balance Fix")

    AlertDialog(
        onDismissRequest = {
            if (!isSubmitting) onDismiss()
        },
        title = {
            Column {
                Text("Adjust Stock - ${product.name}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Current Inventory: $currentStock ${product.unitType}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newStockText,
                    onValueChange = { newStockText = it },
                    label = { Text("New Physical Stock Count") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSubmitting
                )

                Text("Adjustment Reason:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    reasons.forEach { r ->
                        FilterChip(
                            selected = selectedReason == r,
                            onClick = { if (!isSubmitting) selectedReason = r },
                            label = { Text(r, fontSize = 11.sp) },
                            enabled = !isSubmitting
                        )
                    }
                }

                OutlinedTextField(
                    value = customReason,
                    onValueChange = { customReason = it },
                    label = { Text("Note / Comment (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSubmitting
                )

                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting,
                onClick = {
                    if (isSubmitting) return@Button
                    val newCount = newStockText.toLongOrNull()
                    if (newCount == null || newCount < 0) {
                        error = "Please enter a valid non-negative count"
                    } else {
                        isSubmitting = true
                        val finalReason = if (customReason.isNotBlank()) "$selectedReason - $customReason" else selectedReason
                        viewModel.adjustStock(
                            product = product,
                            newQuantity = newCount,
                            reason = finalReason,
                            onSuccess = {
                                isSubmitting = false
                                android.widget.Toast.makeText(context, "Stock adjusted to $newCount successfully", android.widget.Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            onError = {
                                isSubmitting = false
                                error = "Error: ${it.message}"
                            }
                        )
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isSubmitting) "Saving..." else "Save Adjustment")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

