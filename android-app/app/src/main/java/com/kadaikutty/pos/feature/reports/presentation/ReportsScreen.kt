package com.kadaikutty.pos.feature.reports.presentation

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInParent
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
fun ReportsScreen(viewModel: ReportsViewModel, onBack: () -> Unit = {}) {
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
    val reportTabs = listOf(
        Pair("Sales & Bills", Icons.Default.Receipt),
        Pair("Stock Value", Icons.Default.Inventory2),
        Pair("Profit & Loss", Icons.Default.TrendingUp),
        Pair("Purchases", Icons.Default.LocalShipping),
        Pair("Audit Log", Icons.Default.History)
    )
    val auditLogs by viewModel.auditLogs.collectAsState()
    var deleteReason by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    val dateRangeState = rememberDateRangePickerState()
    var isGridView by remember { mutableStateOf(false) }
    var deletingBillNum by remember { mutableStateOf<String?>(null) }

    var selectedBillNumForDetail by remember { mutableStateOf<String?>(null) }
    var billDetailData by remember { mutableStateOf<BillDetailData?>(null) }
    var isBillDetailLoading by remember { mutableStateOf(false) }
    var salesSearchQuery by remember { mutableStateOf("") }
    var expandedStockRows by remember { mutableStateOf(setOf<String>()) }

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
            onDismissRequest = { 
                deletingBillNum = null 
                deleteReason = ""
            },
            title = { Text("Delete Bill #$bNum", fontWeight = FontWeight.Bold) },
            text = { 
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Are you sure you want to delete this bill? All sold items will be automatically returned back into inventory stock.")
                    OutlinedTextField(
                        value = deleteReason,
                        onValueChange = { deleteReason = it },
                        label = { Text("Reason for cancellation (optional)") },
                        placeholder = { Text("e.g. Customer return, billing error") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        val target = deletingBillNum!!
                        val reason = deleteReason
                        deletingBillNum = null
                        deleteReason = ""
                        viewModel.deleteSale(target, target, reason = reason, onSuccess = {
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
                TextButton(onClick = { 
                    deletingBillNum = null 
                    deleteReason = ""
                }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Business Reports", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
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
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
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
                val totalStockValue by viewModel.totalStockValue.collectAsState()
                val totalStockInward by viewModel.totalStockInward.collectAsState()
                val totalStockOutward by viewModel.totalStockOutward.collectAsState()
                val totalStockUnits by viewModel.totalStockUnits.collectAsState()

                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(reportTabs.size) { index ->
                        val tab = reportTabs[index]
                        val isSelected = activeReportTab == index
                        val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                        val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {
                                    activeReportTab = index
                                    if (index < reportTypes.size) {
                                        viewModel.setReportType(reportTypes[index])
                                    }
                                }
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
                                    imageVector = tab.second,
                                    contentDescription = tab.first,
                                    tint = contentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = tab.first,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    var activePreset by remember { mutableStateOf("All Time") }
                    val presetOptions = listOf("Today", "Yesterday", "This Month", "All Time", "Custom")
                    val presetIndex = presetOptions.indexOf(activePreset)

                    var tabWidths by remember { mutableStateOf(mapOf<Int, androidx.compose.ui.geometry.Rect>()) }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        // Sliding indicator background
                        val currentRect = tabWidths[presetIndex]
                        if (currentRect != null) {
                            val density = androidx.compose.ui.platform.LocalDensity.current
                            val offset by androidx.compose.animation.core.animateDpAsState(
                                targetValue = with(density) { currentRect.left.toDp() },
                                animationSpec = androidx.compose.animation.core.spring(stiffness = 520f, dampingRatio = 0.75f)
                            )
                            val width by androidx.compose.animation.core.animateDpAsState(
                                targetValue = with(density) { currentRect.width.toDp() },
                                animationSpec = androidx.compose.animation.core.spring(stiffness = 260f, dampingRatio = 0.75f)
                            )
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .offset(x = offset)
                                    .width(width)
                                    .height(32.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            )
                        }

                        // The items
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            presetOptions.forEachIndexed { index, preset ->
                                val isSelected = presetIndex == index
                                
                                Box(
                                    modifier = Modifier
                                        .onGloballyPositioned { coords ->
                                            tabWidths = tabWidths + (index to coords.boundsInParent())
                                        }
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = if (isSelected) 0.dp else 1.dp,
                                            color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            activePreset = preset
                                            val cal = java.util.Calendar.getInstance()
                                            when (preset) {
                                                "Today" -> {
                                                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
                                                    val start = cal.timeInMillis
                                                    cal.set(java.util.Calendar.HOUR_OF_DAY, 23); cal.set(java.util.Calendar.MINUTE, 59); cal.set(java.util.Calendar.SECOND, 59); cal.set(java.util.Calendar.MILLISECOND, 999)
                                                    viewModel.setDateFilter(start, cal.timeInMillis)
                                                }
                                                "Yesterday" -> {
                                                    cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                                                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
                                                    val start = cal.timeInMillis
                                                    cal.set(java.util.Calendar.HOUR_OF_DAY, 23); cal.set(java.util.Calendar.MINUTE, 59); cal.set(java.util.Calendar.SECOND, 59); cal.set(java.util.Calendar.MILLISECOND, 999)
                                                    viewModel.setDateFilter(start, cal.timeInMillis)
                                                }
                                                "This Month" -> {
                                                    cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                                                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
                                                    val start = cal.timeInMillis
                                                    val end = System.currentTimeMillis()
                                                    viewModel.setDateFilter(start, end)
                                                }
                                                "All Time" -> {
                                                    viewModel.setDateFilter(null, null)
                                                }
                                                "Custom" -> {
                                                    showDatePicker = true
                                                }
                                            }
                                        }
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (preset == "Custom") {
                                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp), tint = if(isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        
                                        Box(contentAlignment = Alignment.Center) {
                                            androidx.compose.animation.AnimatedVisibility(
                                                visible = !isSelected,
                                                enter = androidx.compose.animation.fadeIn(),
                                                exit = androidx.compose.animation.fadeOut()
                                            ) {
                                                Text(preset, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            
                                            androidx.compose.animation.AnimatedVisibility(
                                                visible = isSelected,
                                                enter = androidx.compose.animation.fadeIn(),
                                                exit = androidx.compose.animation.fadeOut()
                                            ) {
                                                Text(preset, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (activeReportTab == 1) {
                            // Stock Value Tab Specific KPI Cards
                            // Card 1: Stock Value
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
                                    Text("Stock Value", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${Money(totalStockValue)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1
                                    )
                                }
                            }

                            // Card 2: Inward (+)
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
                                    Text("Inward (+)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val inText = if (totalStockInward % 1.0 == 0.0) totalStockInward.toLong().toString() else String.format(java.util.Locale.US, "%.2f", totalStockInward)
                                    Text(
                                        text = "+$inText",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF059669),
                                        maxLines = 1
                                    )
                                }
                            }

                            // Card 3: Outward (-)
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
                                    Text("Sold (-)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val outText = if (totalStockOutward % 1.0 == 0.0) totalStockOutward.toLong().toString() else String.format(java.util.Locale.US, "%.2f", totalStockOutward)
                                    Text(
                                        text = "-$outText",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFDC2626),
                                        maxLines = 1
                                    )
                                }
                            }

                            // Card 4: Closing Units
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
                                    Text("Closing Units", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val unitText = if (totalStockUnits % 1.0 == 0.0) totalStockUnits.toLong().toString() else String.format(java.util.Locale.US, "%.2f", totalStockUnits)
                                    Text(
                                        text = unitText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2563EB),
                                        maxLines = 1
                                    )
                                }
                            }
                        } else if (activeReportTab == 4) {
                            val totalCancelledCount = auditLogs.size
                            val totalCancelledAmount = auditLogs.sumOf { it.amountMinorUnits }

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
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text("Cancelled Bills", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$totalCancelledCount Bills",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 1
                                    )
                                }
                            }

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
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text("Cancelled Value", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = Money(totalCancelledAmount).toString(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 1
                                    )
                                }
                            }
                        } else {
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
                }

                HorizontalDivider()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    if (activeReportTab == 4) {
                        AuditLogsView(auditLogs = auditLogs)
                    } else if (isLoading) {
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
                    } else {
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

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Professional Report Header Banner
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                val timeFormat = remember { java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()) }
                                val periodStr = if (report.fromEpochMs != null || report.toEpochMs != null) {
                                    val fromStr = report.fromEpochMs?.let { java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: "Beginning"
                                    val toStr = report.toEpochMs?.let { java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: "Now"
                                    if (fromStr == toStr) fromStr else "$fromStr - $toStr"
                                } else {
                                    "All Time"
                                }
                                val generatedTime = remember(report.generatedAtEpochMs) {
                                    timeFormat.format(java.util.Date(report.generatedAtEpochMs))
                                }
                                val recordCount = report.rows.count { !it.any { c -> c.startsWith("TOTAL") || c == "---" } }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = report.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "📅 $periodStr",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text("•", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                            Text(
                                                text = "🕒 $generatedTime",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "$recordCount items",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            if (!isGridView) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                if (selectedType == ReportType.SALES) {
                                    OutlinedTextField(
                                        value = salesSearchQuery,
                                        onValueChange = { salesSearchQuery = it },
                                        placeholder = { Text("Search", fontSize = 13.sp) },
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
                                                                Text(customer, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                                                Text(dateTime, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f))
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
                                                    val inward = row.getOrNull(7) ?: ""
                                                    val outward = row.getOrNull(8) ?: ""
                                                    val opening = row.getOrNull(9) ?: ""
                                                    val lastActivity = row.getOrNull(10) ?: ""
                                                    val isOutOfStock = stock == "0" || stock == "0.000"
                                                    val rowKey = "$sNo-$prod"
                                                    val isExpanded = expandedStockRows.contains(rowKey)

                                                    Card(
                                                        onClick = {
                                                            expandedStockRows = if (isExpanded) expandedStockRows - rowKey else expandedStockRows + rowKey
                                                        },
                                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                        shape = RoundedCornerShape(12.dp),
                                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .animateContentSize()
                                                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
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
                                                                        text = "Closing: $stock $unit",
                                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                                        fontSize = 11.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = if (isOutOfStock) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                                                    )
                                                                }
                                                            }

                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(cat, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                    Text(totalVal, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                                                    Icon(
                                                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                                        contentDescription = null,
                                                                        tint = MaterialTheme.colorScheme.outline,
                                                                        modifier = Modifier.size(18.dp)
                                                                    )
                                                                }
                                                            }

                                                            if (isExpanded) {
                                                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 2.dp))
                                                                if (opening.isNotBlank() || inward.isNotBlank() || outward.isNotBlank()) {
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        if (opening.isNotBlank()) {
                                                                            Surface(
                                                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                                                                shape = RoundedCornerShape(4.dp)
                                                                            ) {
                                                                                Text("Open: $opening", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                            }
                                                                        }
                                                                        if (inward.isNotBlank()) {
                                                                            Surface(
                                                                                color = Color(0xFFDCFCE7),
                                                                                shape = RoundedCornerShape(4.dp)
                                                                            ) {
                                                                                Text("+$inward In", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                                                                            }
                                                                        }
                                                                        if (outward.isNotBlank()) {
                                                                            Surface(
                                                                                color = Color(0xFFFEE2E2),
                                                                                shape = RoundedCornerShape(4.dp)
                                                                            ) {
                                                                                Text("-$outward Sold", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text("Cost Rate: $cost", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                                                    if (lastActivity.isNotBlank() && lastActivity != "-") {
                                                                        Text("🕒 $lastActivity", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                                                    }
                                                                }
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
                                                            Text(supplier, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                            Text("Mode: $mode", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                                        }
                                                        Text(date, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
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
    }

    if (showDatePicker) {
        DateRangePickerDialog(
            onDismiss = { showDatePicker = false },
            onConfirm = {
                // The Material picker reports UTC midnight, but sales are stored in local
                // wall-clock millis and every preset above builds local day boundaries. Without
                // reinterpreting the picked date locally, a custom range in IST is shifted 5.5
                // hours: sales before 05:30 land on the previous day.
                val start = dateRangeState.selectedStartDateMillis?.let { localDayStart(it) }
                val end = dateRangeState.selectedEndDateMillis?.let { localDayEnd(it) }
                viewModel.setDateFilter(start, end)
                showDatePicker = false
            },
            dateRangePickerState = dateRangeState
        )
    }
}

/** Reinterprets the picker's UTC-midnight value as local 00:00:00.000 on the same calendar date. */
private fun localDayStart(utcMidnightMillis: Long): Long = localDayBoundary(utcMidnightMillis, endOfDay = false)

/** Reinterprets the picker's UTC-midnight value as local 23:59:59.999 on the same calendar date. */
private fun localDayEnd(utcMidnightMillis: Long): Long = localDayBoundary(utcMidnightMillis, endOfDay = true)

private fun localDayBoundary(utcMidnightMillis: Long, endOfDay: Boolean): Long {
    val utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnightMillis }
    return java.util.Calendar.getInstance().apply {
        set(utc.get(java.util.Calendar.YEAR), utc.get(java.util.Calendar.MONTH), utc.get(java.util.Calendar.DAY_OF_MONTH))
        set(java.util.Calendar.HOUR_OF_DAY, if (endOfDay) 23 else 0)
        set(java.util.Calendar.MINUTE, if (endOfDay) 59 else 0)
        set(java.util.Calendar.SECOND, if (endOfDay) 59 else 0)
        set(java.util.Calendar.MILLISECOND, if (endOfDay) 999 else 0)
    }.timeInMillis
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

@Composable
private fun AuditLogsView(auditLogs: List<com.kadaikutty.pos.feature.billing.data.AuditLogEntity>) {
    val timeFormatter = remember { SimpleDateFormat("hh:mm a, dd MMM yyyy", Locale.getDefault()) }

    if (auditLogs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
                Text("No deleted or cancelled bills recorded.", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(auditLogs) { log ->
                val dateStr = if (log.timestampEpochMs > 0) timeFormatter.format(Date(log.timestampEpochMs)) else "Recent"
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "CANCELLED BILL #${log.billNumber}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Text(
                                text = Money(log.amountMinorUnits).toString(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Cancelled by: ${log.performedByUserName.ifBlank { "Staff" }}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = dateStr,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        if (log.reason.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Reason: ${log.reason}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

