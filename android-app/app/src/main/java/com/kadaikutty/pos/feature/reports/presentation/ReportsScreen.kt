package com.kadaikutty.pos.feature.reports.presentation

import com.kadaikutty.pos.core.ui.LocalLayoutMode
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.kadaikutty.pos.feature.reports.domain.ReportType
import com.kadaikutty.pos.core.common.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material.icons.automirrored.filled.List
import com.kadaikutty.pos.feature.reports.presentation.components.BillDetailsDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: ReportsViewModel) {
    val context = LocalContext.current
    var documentBytes by remember { mutableStateOf<ByteArray?>(null) }



    val excelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null && documentBytes != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(documentBytes)
                }
                android.widget.Toast.makeText(context, "Excel CSV exported successfully!", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val reportData by viewModel.reportData.collectAsState()
    val selectedType by viewModel.selectedType.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var activeReportTab by remember { mutableStateOf(0) }
    val reportTypes = listOf(
        ReportType.SALES,
        ReportType.STOCK,
        ReportType.PROFIT,
        ReportType.PURCHASES
    )
    val reportNames = listOf(
        "📊 Sales & Bills", 
        "📦 Stock Value", 
        "💰 Profit & Loss", 
        "🚚 Purchases"
    )

    var showDatePicker by remember { mutableStateOf(false) }
    val dateRangeState = rememberDateRangePickerState()
    var isGridView by remember { mutableStateOf(false) }
    var deletingBillNum by remember { mutableStateOf<String?>(null) }

    var selectedBillNumForDetail by remember { mutableStateOf<String?>(null) }
    var billDetailData by remember { mutableStateOf<BillDetailData?>(null) }
    var isBillDetailLoading by remember { mutableStateOf(false) }
    var salesSearchQuery by remember { mutableStateOf("") }

    LaunchedEffect(selectedBillNumForDetail) {
        val target = selectedBillNumForDetail
        if (target != null) {
            isBillDetailLoading = true
            billDetailData = viewModel.getBillDetails(target)
            isBillDetailLoading = false
        } else {
            billDetailData = null
        }
    }

    if (selectedBillNumForDetail != null) {
        BillDetailsDialog(
            billDetail = billDetailData,
            isLoading = isBillDetailLoading,
            onDismiss = { selectedBillNumForDetail = null }
        )
    }

    if (deletingBillNum != null) {
        val bNum = deletingBillNum!!
        AlertDialog(
            onDismissRequest = { deletingBillNum = null },
            title = { Text("Delete Bill #$bNum", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this bill? All sold items will be automatically returned back into inventory stock.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        val target = deletingBillNum!!
                        deletingBillNum = null
                        viewModel.deleteSale(target, target, onSuccess = {
                            android.widget.Toast.makeText(context, "Bill #$target deleted & stock restored!", android.widget.Toast.LENGTH_SHORT).show()
                        }, onError = {
                            android.widget.Toast.makeText(context, "Failed to delete: ${it.message}", android.widget.Toast.LENGTH_SHORT).show()
                        })
                    }
                ) {
                    Text("Delete & Restore Stock")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingBillNum = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Business Reports", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                actions = {
                    IconButton(onClick = { isGridView = !isGridView }) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Filled.List else Icons.Default.Menu,
                            contentDescription = "Toggle View",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = {
                        viewModel.shareReportPdf()
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share PDF", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = {
                        try {
                            documentBytes = viewModel.exportExcel()
                            val filename = "${selectedType.name.lowercase()}_report.csv"
                            excelLauncher.launch(filename)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Export error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Export Excel", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                val totalSalesSum by viewModel.totalSalesSum.collectAsState()
                val purchaseCostSum by viewModel.purchaseCostSum.collectAsState()
                val totalPurchasesSum by viewModel.totalPurchasesSum.collectAsState()
                val expensesSum by viewModel.expensesSum.collectAsState()
                val netProfitSum by viewModel.netProfitSum.collectAsState()

                ScrollableTabRow(
                    selectedTabIndex = activeReportTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 12.dp
                ) {
                    reportNames.forEachIndexed { index, name ->
                        Tab(
                            selected = activeReportTab == index,
                            onClick = {
                                activeReportTab = index
                                viewModel.setReportType(reportTypes[index])
                            },
                            text = { Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    var activePreset by remember { mutableStateOf("All Time") }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = activePreset == "Today",
                            onClick = {
                                activePreset = "Today"
                                val cal = java.util.Calendar.getInstance()
                                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                cal.set(java.util.Calendar.MINUTE, 0)
                                cal.set(java.util.Calendar.SECOND, 0)
                                cal.set(java.util.Calendar.MILLISECOND, 0)
                                val start = cal.timeInMillis
                                cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
                                cal.set(java.util.Calendar.MINUTE, 59)
                                cal.set(java.util.Calendar.SECOND, 59)
                                cal.set(java.util.Calendar.MILLISECOND, 999)
                                val end = cal.timeInMillis
                                viewModel.setDateFilter(start, end)
                            },
                            label = { Text("Today", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(16.dp)
                        )
                        FilterChip(
                            selected = activePreset == "Yesterday",
                            onClick = {
                                activePreset = "Yesterday"
                                val cal = java.util.Calendar.getInstance()
                                cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                cal.set(java.util.Calendar.MINUTE, 0)
                                cal.set(java.util.Calendar.SECOND, 0)
                                cal.set(java.util.Calendar.MILLISECOND, 0)
                                val start = cal.timeInMillis
                                cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
                                cal.set(java.util.Calendar.MINUTE, 59)
                                cal.set(java.util.Calendar.SECOND, 59)
                                cal.set(java.util.Calendar.MILLISECOND, 999)
                                val end = cal.timeInMillis
                                viewModel.setDateFilter(start, end)
                            },
                            label = { Text("Yesterday", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(16.dp)
                        )
                        FilterChip(
                            selected = activePreset == "This Month",
                            onClick = {
                                activePreset = "This Month"
                                val cal = java.util.Calendar.getInstance()
                                cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                cal.set(java.util.Calendar.MINUTE, 0)
                                cal.set(java.util.Calendar.SECOND, 0)
                                cal.set(java.util.Calendar.MILLISECOND, 0)
                                val start = cal.timeInMillis
                                val end = System.currentTimeMillis()
                                viewModel.setDateFilter(start, end)
                            },
                            label = { Text("This Month", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(16.dp)
                        )
                        FilterChip(
                            selected = activePreset == "All Time",
                            onClick = {
                                activePreset = "All Time"
                                viewModel.setDateFilter(null, null)
                            },
                            label = { Text("All Time", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(16.dp)
                        )
                        FilterChip(
                            selected = activePreset == "Custom",
                            onClick = {
                                activePreset = "Custom"
                                showDatePicker = true
                            },
                            label = { Text("Custom Date", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            shape = RoundedCornerShape(16.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Card 1: Sales
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                                Text("Sales", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${Money(totalSalesSum)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF059669),
                                    maxLines = 1
                                )
                            }
                        }

                        // Card 2: COGS / Purchases
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                                Text(if (activeReportTab == 3) "Purchases" else "COGS", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (activeReportTab == 3) "${Money(totalPurchasesSum)}" else "${Money(purchaseCostSum)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2563EB),
                                    maxLines = 1
                                )
                            }
                        }

                        // Card 3: Expenses
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                                Text("Expenses", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${Money(expensesSum)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD97706),
                                    maxLines = 1
                                )
                            }
                        }

                        // Card 4: Net Profit
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                                Text("Net Profit", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${Money(netProfitSum)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (netProfitSum >= 0) Color(0xFF059669) else Color(0xFFDC2626),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else if (!error.isNullOrBlank()) {
                        Text(error ?: "Error loading report", color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
                    } else if (reportData == null || reportData?.rows?.isEmpty() == true) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
                            Text("No records found for this period.", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
                        }
                                          val report = reportData!!
                        val isBillsDetail = selectedType == ReportType.SALES

                        val filteredRows = if (selectedType == ReportType.SALES && salesSearchQuery.isNotBlank()) {
                            val q = salesSearchQuery.trim().lowercase()
                            report.rows.filter { row ->
                                val isTotalRow = row.any { it.startsWith("TOTAL") } || row.any { it == "---" }
                                if (isTotalRow) true
                                else {
                                    val billNum = row.getOrNull(1)?.lowercase() ?: ""
                                    val customer = row.getOrNull(3)?.lowercase() ?: ""
                                    val amount = row.getOrNull(4)?.lowercase() ?: ""
                                    billNum.contains(q) || customer.contains(q) || amount.contains(q)
                                }
                            }
                        } else {
                            report.rows
                        }

                        if (!isGridView) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                if (selectedType == ReportType.SALES) {
                                    OutlinedTextField(
                                        value = salesSearchQuery,
                                        onValueChange = { salesSearchQuery = it },
                                        placeholder = { Text("Search by Bill #, Customer name, Amount...", fontSize = 13.sp) },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                        trailingIcon = {
                                            if (salesSearchQuery.isNotBlank()) {
                                                IconButton(onClick = { salesSearchQuery = "" }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(filteredRows) { row ->
                                        val isTotalRow = row.any { it.startsWith("TOTAL") } || row.any { it == "---" }
                                        
                                        if (isTotalRow) {
                                            if (row.any { it.startsWith("TOTAL") }) {
                                                val totalTitle = row.find { it.startsWith("TOTAL") } ?: "TOTAL"
                                                val totalDesc = row.find { it.endsWith("Bills") || it.endsWith("Orders") } ?: ""
                                                val totalAmount = row.lastOrNull() ?: ""
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column {
                                                            Text(totalTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                                            if (totalDesc.isNotBlank()) {
                                                                Text(totalDesc, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                                                            }
                                                        }
                                                        Text(
                                                            text = totalAmount,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontSize = 18.sp,
                                                            color = Color.White
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            when (selectedType) {
                                                ReportType.SALES -> {
                                                    val sNo = row.getOrNull(0) ?: ""
                                                    val billNum = row.getOrNull(1) ?: ""
                                                    val dateTime = row.getOrNull(2) ?: ""
                                                    val customer = row.getOrNull(3) ?: "Walk-in Customer"
                                                    val amount = row.getOrNull(4) ?: "₹0"

                                                    Card(
                                                        onClick = { selectedBillNumForDetail = billNum },
                                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                        shape = RoundedCornerShape(12.dp),
                                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                                        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                                    ) {
                                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                    if (sNo.isNotBlank()) {
                                                                        Surface(
                                                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                                                            shape = RoundedCornerShape(4.dp)
                                                                        ) {
                                                                            Text("#$sNo", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                                        }
                                                                    }
                                                                    Surface(
                                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                                        shape = RoundedCornerShape(6.dp)
                                                                    ) {
                                                                        Text("Bill #$billNum", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                                                    }
                                                                }
                                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                    Text(amount, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2E7D32))
                                                                    IconButton(
                                                                        onClick = { deletingBillNum = billNum },
                                                                        modifier = Modifier.size(28.dp)
                                                                    ) {
                                                                        Icon(Icons.Default.Delete, contentDescription = "Delete Bill", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                                                    }
                                                                }
                                                            }
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text("👤 $customer", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                                                Text("🕒 $dateTime", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f))
                                                            }
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                                                horizontalArrangement = Arrangement.End
                                                            ) {
                                                                Text("Tap to view items 📋", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), fontWeight = FontWeight.Medium)
                                                            }
                                                        }
                                                    }
                                                }
                                                ReportType.STOCK -> {
                                                    val sNo = row.getOrNull(0) ?: ""
                                                    val prod = row.getOrNull(1) ?: ""
                                                    val cat = row.getOrNull(2) ?: "General"
                                                    val unit = row.getOrNull(3) ?: "PIECE"
                                                    val stock = row.getOrNull(4) ?: "0"
                                                    val cost = row.getOrNull(5) ?: "₹0"
                                                    val totalVal = row.getOrNull(6) ?: "₹0"
                                                    val isOutOfStock = stock == "0" || stock == "0.000"

                                                    Card(
                                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                        shape = RoundedCornerShape(12.dp),
                                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                                        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                                    ) {
                                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                if (sNo.isNotBlank()) {
                                                                    Surface(
                                                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                                                        shape = RoundedCornerShape(4.dp)
                                                                    ) {
                                                                        Text("#$sNo", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                                    }
                                                                }
                                                                Text(prod, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                            }
                                                            Surface(
                                                                color = if (isOutOfStock) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                                                                shape = RoundedCornerShape(6.dp)
                                                            ) {
                                                                Text(
                                                                    text = "Stock: $stock $unit",
                                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (isOutOfStock) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                                                )
                                                            }
                                                        }
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text("🏷️ $cat", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                                            Text("Cost Rate: $cost", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                                        }
                                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text("Total Stock Value:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                            Text(totalVal, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                                        }
                                                    }
                                                }
                                            }
                                            ReportType.PROFIT -> {
                                                val sNo = row.getOrNull(0) ?: ""
                                                val prod = row.getOrNull(1) ?: ""
                                                val qty = row.getOrNull(2) ?: "0"
                                                val rev = row.getOrNull(3) ?: "₹0"
                                                val cost = row.getOrNull(4) ?: "₹0"
                                                val profit = row.getOrNull(5) ?: "₹0"
                                                val isNegative = profit.startsWith("-")

                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                    shape = RoundedCornerShape(12.dp),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                if (sNo.isNotBlank()) {
                                                                    Surface(
                                                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                                                        shape = RoundedCornerShape(4.dp)
                                                                    ) {
                                                                        Text("#$sNo", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                                    }
                                                                }
                                                                Text(prod, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                            }
                                                            Text(
                                                                text = "Profit: $profit",
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 14.sp,
                                                                color = if (isNegative) Color(0xFFC62828) else Color(0xFF2E7D32)
                                                            )
                                                        }
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text("Qty Sold: $qty", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                                            Text("Revenue: $rev | Cost: $cost", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                                        }
                                                    }
                                                }
                                            }
                                            ReportType.PURCHASES -> {
                                                val sNo = row.getOrNull(0) ?: ""
                                                val invId = row.getOrNull(1) ?: ""
                                                val date = row.getOrNull(2) ?: ""
                                                val supplier = row.getOrNull(3) ?: "General Supplier"
                                                val mode = row.getOrNull(4) ?: "CASH"
                                                val total = row.getOrNull(5) ?: "₹0"

                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                    shape = RoundedCornerShape(12.dp),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                if (sNo.isNotBlank()) {
                                                                    Surface(
                                                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                                                        shape = RoundedCornerShape(4.dp)
                                                                    ) {
                                                                        Text("#$sNo", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                                    }
                                                                }
                                                                Surface(
                                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                                    shape = RoundedCornerShape(6.dp)
                                                                ) {
                                                                    Text(invId, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                            }
                                                            Text(total, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                                        }
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text("🏢 $supplier", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                            Text("Mode: $mode", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                                        }
                                                        Text("🕒 $date", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        } else {
                            val scrollState = rememberScrollState()
                            Column(modifier = Modifier.fillMaxSize().horizontalScroll(scrollState)) {
                                Row(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                ) {
                                    report.columns.forEach { col ->
                                        Text(
                                            text = col,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(130.dp).padding(10.dp),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Start
                                        )
                                    }
                                    if (isBillsDetail) {
                                        Text(
                                            text = "Actions",
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(70.dp).padding(10.dp),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(report.rows) { row ->
                                        val isTotalRow = row.firstOrNull()?.startsWith("TOTAL") == true || row.firstOrNull() == "---"
                                        val rowBg = if (isTotalRow) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                                        val fontW = if (isTotalRow) FontWeight.Bold else FontWeight.Normal
                                        val billNum = row.firstOrNull() ?: ""
                                        Row(
                                            modifier = Modifier.background(rowBg).border(0.5.dp, MaterialTheme.colorScheme.surfaceVariant),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            row.forEach { cell ->
                                                Text(
                                                    text = cell,
                                                    fontWeight = fontW,
                                                    modifier = Modifier.width(130.dp).padding(10.dp),
                                                    fontSize = 12.sp,
                                                    textAlign = TextAlign.Start
                                                )
                                            }
                                            if (isBillsDetail) {
                                                Box(modifier = Modifier.width(70.dp), contentAlignment = Alignment.Center) {
                                                    if (!isTotalRow && billNum.isNotBlank()) {
                                                        IconButton(onClick = { deletingBillNum = billNum }, modifier = Modifier.size(28.dp)) {
                                                            Icon(Icons.Default.Delete, contentDescription = "Delete Bill", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
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
        }
    }

    if (showDatePicker) {
        DateRangePickerDialog(
            onDismiss = { showDatePicker = false },
            onConfirm = {
                val start = dateRangeState.selectedStartDateMillis
                val end = dateRangeState.selectedEndDateMillis
                viewModel.setDateFilter(start, end)
                showDatePicker = false
            },
            dateRangePickerState = dateRangeState
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    dateRangePickerState: DateRangePickerState
) {
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DateRangePicker(
            state = dateRangePickerState,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
}
