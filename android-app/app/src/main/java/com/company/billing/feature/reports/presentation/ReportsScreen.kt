package com.company.billing.feature.reports.presentation

import com.company.billing.core.ui.LocalLayoutMode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.company.billing.feature.reports.domain.ReportType
import com.company.billing.core.common.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: ReportsViewModel) {
    val context = LocalContext.current
    var documentBytes by remember { mutableStateOf<ByteArray?>(null) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null && documentBytes != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(documentBytes)
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    val excelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null && documentBytes != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(documentBytes)
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    val reportData by viewModel.reportData.collectAsState()
    val selectedType by viewModel.selectedType.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var activeReportTab by remember { mutableStateOf(0) }
    val reportTypes = ReportType.values()
    val reportNames = listOf(
        "Sales Daily", "Bills Detail", "Item-wise Sales", "Inventory Stock",
        "Profit/Loss", "Purchases", "Customer Spending", "Supplier Summary", "Expenses"
    )

    var showDatePicker by remember { mutableStateOf(false) }
    val dateRangeState = rememberDateRangePickerState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports Dashboard", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            val layoutMode = LocalLayoutMode.current
            val isMobile = when (layoutMode) {
                "Mobile" -> true
                "Tablet" -> false
                else -> maxWidth < 600.dp
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                val totalSalesSum by viewModel.totalSalesSum.collectAsState()
                val purchaseCostSum by viewModel.purchaseCostSum.collectAsState()
                val netProfitSum by viewModel.netProfitSum.collectAsState()

                // Top KPI Statistics Summary Cards
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KpiCard(
                        title = "Total Sales",
                        value = if (totalSalesSum == 0L) "—" else Money(totalSalesSum).toString(),
                        trend = if (totalSalesSum == 0L) "No sales" else "Live calculated",
                        isPositive = totalSalesSum > 0L,
                        showArrow = totalSalesSum > 0L,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Purchase Cost",
                        value = if (purchaseCostSum == 0L) "—" else Money(purchaseCostSum).toString(),
                        trend = if (purchaseCostSum == 0L) "No purchases" else "Live calculated",
                        isPositive = purchaseCostSum > 0L,
                        showArrow = purchaseCostSum > 0L,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Net Profit",
                        value = if (totalSalesSum == 0L && purchaseCostSum == 0L) "—" else Money(netProfitSum).toString(),
                        trend = if (totalSalesSum == 0L && purchaseCostSum == 0L) "No activity" else if (netProfitSum >= 0) "Profit" else "Loss",
                        isPositive = netProfitSum >= 0,
                        showArrow = (totalSalesSum > 0L || purchaseCostSum > 0L),
                        modifier = Modifier.weight(1f)
                    )
                }

                ScrollableTabRow(
                    selectedTabIndex = activeReportTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    reportNames.forEachIndexed { index, name ->
                        Tab(
                            selected = activeReportTab == index,
                            onClick = {
                                activeReportTab = index
                                viewModel.setReportType(reportTypes[index])
                            },
                            text = { Text(name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                        )
                    }
                }

                // Filters and Exports buttons
                if (isMobile) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { showDatePicker = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            ) {
                                Icon(Icons.Default.DateRange, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Filter Dates")
                            }
                            
                            val fromMs = viewModel.fromEpochMs.collectAsState().value
                            val toMs = viewModel.toEpochMs.collectAsState().value
                            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            if (fromMs != null && toMs != null) {
                                Text("Range: ${fmt.format(Date(fromMs))} to ${fmt.format(Date(toMs))}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            } else {
                                Text("Range: All Time", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                             OutlinedButton(
                                 onClick = {
                                     try {
                                         documentBytes = viewModel.exportPdf()
                                         val filename = "${selectedType.name.lowercase()}_report.pdf"
                                         pdfLauncher.launch(filename)
                                     } catch (e: Exception) {
                                         // handle error
                                     }
                                 },
                                 shape = RoundedCornerShape(12.dp),
                                 modifier = Modifier.weight(1f)
                             ) {
                                 Text("Export PDF")
                             }
                             OutlinedButton(
                                 onClick = {
                                     try {
                                         documentBytes = viewModel.exportExcel()
                                         val filename = "${selectedType.name.lowercase()}_report.csv"
                                         excelLauncher.launch(filename)
                                     } catch (e: Exception) {
                                         // handle error
                                     }
                                 },
                                 shape = RoundedCornerShape(12.dp),
                                 modifier = Modifier.weight(1f)
                             ) {
                                 Text("Export Excel")
                             }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                             Button(
                                 onClick = { showDatePicker = true },
                                 shape = RoundedCornerShape(12.dp),
                                 colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                             ) {
                                 Icon(Icons.Default.DateRange, contentDescription = null)
                                 Spacer(Modifier.width(8.dp))
                                 Text("Filter Dates")
                             }
                            Spacer(Modifier.width(12.dp))
                            
                            val fromMs = viewModel.fromEpochMs.collectAsState().value
                            val toMs = viewModel.toEpochMs.collectAsState().value
                            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            if (fromMs != null && toMs != null) {
                                Text("Range: ${fmt.format(Date(fromMs))} to ${fmt.format(Date(toMs))}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            } else {
                                Text("Range: All Time", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        documentBytes = viewModel.exportPdf()
                                        val filename = "${selectedType.name.lowercase()}_report.pdf"
                                        pdfLauncher.launch(filename)
                                    } catch (e: Exception) {
                                        // handle error
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Export PDF")
                            }
                            OutlinedButton(
                                onClick = {
                                    try {
                                        documentBytes = viewModel.exportExcel()
                                        val filename = "${selectedType.name.lowercase()}_report.csv"
                                        excelLauncher.launch(filename)
                                    } catch (e: Exception) {
                                        // handle error
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Export Excel")
                            }
                        }
                    }
                }

                // Disclaimer for Profit costing method
                if (selectedType == ReportType.PROFIT) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = "Disclaimer: Cost calculations are tentative. Method pending client confirmation: REQUIRES_CLIENT_CONFIRMATION: PROFIT_COSTING_METHOD",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Report Content Table Grid
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else if (!error.isNullOrBlank()) {
                        Text(error ?: "Error loading report", color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
                    } else if (reportData == null || reportData?.rows?.isEmpty() == true) {
                        Text("No transactions found matching criteria.", color = MaterialTheme.colorScheme.outline, modifier = Modifier.align(Alignment.Center))
                    } else {
                        val report = reportData!!
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(report.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))

                            // Table header & rows horizontal scroll for responsiveness
                            val scrollState = rememberScrollState()
                            Column(modifier = Modifier.fillMaxSize().horizontalScroll(scrollState)) {
                                // Headers
                                Row(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                ) {
                                    report.columns.forEach { col ->
                                        Text(
                                            text = col,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(150.dp).padding(12.dp),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            textAlign = TextAlign.Start
                                        )
                                    }
                                }
                                // Rows
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(report.rows) { row ->
                                        val isTotalRow = row.firstOrNull() == "TOTAL"
                                        val rowBg = if (isTotalRow) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                                        val fontW = if (isTotalRow) FontWeight.Bold else FontWeight.Normal
                                        Row(modifier = Modifier.background(rowBg).border(0.5.dp, MaterialTheme.colorScheme.surfaceVariant)) {
                                            row.forEach { cell ->
                                                Text(
                                                    text = cell,
                                                    fontWeight = fontW,
                                                    modifier = Modifier.width(150.dp).padding(12.dp),
                                                    textAlign = TextAlign.Start
                                                )
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

@Composable
fun KpiCard(
    title: String,
    value: String,
    trend: String,
    isPositive: Boolean,
    modifier: Modifier = Modifier,
    showArrow: Boolean = true
) {
    Card(
        modifier = modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            shape = RoundedCornerShape(16.dp)
        ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showArrow) {
                    Icon(
                        imageVector = if (isPositive) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = if (isPositive) Color(0xFF0F766E) else Color(0xFFDC2626)
                    )
                }
                Text(
                    text = trend,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPositive) Color(0xFF0F766E) else Color(0xFFDC2626)
                )
            }
        }
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
