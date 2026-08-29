package com.kadaikutty.pos.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import java.io.File
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import com.kadaikutty.pos.core.common.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.kadaikutty.pos.core.security.Permission
import com.kadaikutty.pos.core.auth.Session
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kadaikutty.pos.core.navigation.AppRoute
import com.kadaikutty.pos.feature.settings.presentation.SettingsScreen
import com.kadaikutty.pos.feature.settings.presentation.SettingsViewModel
import com.kadaikutty.pos.feature.auth.LoginScreen
import com.kadaikutty.pos.feature.auth.LoginViewModel
import com.kadaikutty.pos.feature.auth.RegisterScreen
import com.kadaikutty.pos.feature.auth.RegisterViewModel
import com.kadaikutty.pos.feature.auth.SetNewPasswordScreen
import com.kadaikutty.pos.feature.auth.SetNewPasswordViewModel
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.kadaikutty.pos.feature.home.HomeViewModel
import com.kadaikutty.pos.feature.home.HomeDashboardUiState
import com.kadaikutty.pos.feature.reports.presentation.components.BillDetailsDialog
import com.kadaikutty.pos.feature.billing.presentation.BillingScreen
import com.kadaikutty.pos.feature.billing.presentation.BillingViewModel
import com.kadaikutty.pos.feature.masters.presentation.*
import com.kadaikutty.pos.feature.purchase.presentation.PurchaseScreen
import com.kadaikutty.pos.feature.purchase.presentation.PurchaseViewModel
import com.kadaikutty.pos.feature.reports.presentation.ReportsScreen
import com.kadaikutty.pos.feature.reports.presentation.ReportsViewModel
import com.kadaikutty.pos.core.ui.theme.BillingTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.background

val LocalLayoutMode = staticCompositionLocalOf { "Auto" }

@Composable
fun BillingApp(isRecoveryFlow: Boolean = false) {
    val navController = rememberNavController()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val layoutMode by settingsViewModel.layoutMode.collectAsState()
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val shopName by settingsViewModel.shopName.collectAsState()
    val shopLogoPath by settingsViewModel.shopLogoPath.collectAsState()
    val useDarkTheme = when (themeMode) {
        "Dark" -> true
        "Light" -> false
        else -> isSystemInDarkTheme()
    }

    val isLoggedIn by settingsViewModel.isLoggedIn.collectAsState()
    val subscriptionStatus by settingsViewModel.subscriptionStatus.collectAsState()

    if (isLoggedIn == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF1E88E5))
        }
        return
    }

    CompositionLocalProvider(LocalLayoutMode provides layoutMode) {
        BillingTheme(darkTheme = useDarkTheme) {
            val startDest = if (isLoggedIn == true) AppRoute.Home.path else AppRoute.Login.path
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(navController = navController, startDestination = startDest) {
                composable(AppRoute.Login.path) {
                    val vm: LoginViewModel = hiltViewModel()
                    LoginScreen(
                        viewModel = vm,
                        onLoginSuccess = {
                            navController.navigate(AppRoute.Home.path) {
                                popUpTo(AppRoute.Login.path) { inclusive = true }
                            }
                        },
                        onNavigateToRegister = {
                            navController.navigate(AppRoute.Register.path)
                        }
                    )
                }

                composable(AppRoute.Register.path) {
                    val vm: RegisterViewModel = hiltViewModel()
                    RegisterScreen(
                        viewModel = vm,
                        onNavigateBackToLogin = {
                            navController.popBackStack()
                        },
                        onRegisterSuccess = {
                            navController.navigate(AppRoute.Home.path) {
                                popUpTo(AppRoute.Login.path) { inclusive = true }
                            }
                        }
                    )
                }

                composable(AppRoute.SetNewPassword.path) {
                    val vm: SetNewPasswordViewModel = hiltViewModel()
                    SetNewPasswordScreen(
                        viewModel = vm,
                        onPasswordSetSuccess = {
                            navController.navigate(AppRoute.Login.path) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }

                composable(AppRoute.Home.path) {
                    val vm: com.kadaikutty.pos.feature.home.HomeViewModel = hiltViewModel()
                    val session by vm.activeSession.collectAsState()
                    var showCloseShiftDialog by remember { mutableStateOf(false) }
                    var shiftCashInput by remember { mutableStateOf("") }
                    var shiftMessage by remember { mutableStateOf("") }

                    HomeScreen(
                        viewModel = vm,
                        session = session,
                        shopName = shopName,
                        shopLogoPath = shopLogoPath,
                        onNavigateTo = { route -> navController.navigate(route.path) },
                        onLogout = {
                            vm.logout()
                            navController.navigate(AppRoute.Login.path) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onCloseShiftClick = { showCloseShiftDialog = true }
                    )

                    val shiftHistory by vm.shiftHistory.collectAsState()
                    var shiftDialogTab by remember { mutableStateOf(0) }

                    if (showCloseShiftDialog) {
                        AlertDialog(
                            onDismissRequest = { showCloseShiftDialog = false; shiftMessage = "" },
                            title = {
                                Column {
                                    Text("Cash Register & Shifts", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TabRow(
                                        selectedTabIndex = shiftDialogTab,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ) {
                                        Tab(
                                            selected = shiftDialogTab == 0,
                                            onClick = { shiftDialogTab = 0 },
                                            text = { Text("Close Register", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                                        )
                                        Tab(
                                            selected = shiftDialogTab == 1,
                                            onClick = { shiftDialogTab = 1 },
                                            text = { Text("Shift History (${shiftHistory.size})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                                        )
                                    }
                                }
                            },
                            text = {
                                if (shiftDialogTab == 0) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                        Text("Please count and enter the physical cash currently in the drawer to close register and tally daily cash.", fontSize = 13.sp)
                                        OutlinedTextField(
                                            value = shiftCashInput,
                                            onValueChange = { shiftCashInput = it },
                                            label = { Text("Physical Cash Amount (₹)") },
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        if (shiftMessage.isNotEmpty()) {
                                            Text(shiftMessage, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 350.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (shiftHistory.isEmpty()) {
                                            Text("No past register closures logged yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                                        } else {
                                            val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                            shiftHistory.forEach { shift ->
                                                val isMatch = shift.discrepancyMinorUnits == 0L
                                                val isShortage = shift.discrepancyMinorUnits < 0L
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                                ) {
                                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                            Text(df.format(Date(shift.closedAtEpochMs)), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                            val statusText = when {
                                                                isMatch -> "✅ Tally Matched"
                                                                isShortage -> "⚠️ Shortage: -${Money(kotlin.math.abs(shift.discrepancyMinorUnits))}"
                                                                else -> "ℹ️ Extra: +${Money(shift.discrepancyMinorUnits)}"
                                                            }
                                                            val statusColor = when {
                                                                isMatch -> Color(0xFF2E7D32)
                                                                isShortage -> MaterialTheme.colorScheme.error
                                                                else -> MaterialTheme.colorScheme.primary
                                                            }
                                                            Text(statusText, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = statusColor)
                                                        }
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                            Text("Expected: ${Money(shift.expectedCashMinorUnits)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Text("Counted: ${Money(shift.declaredCashMinorUnits)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                if (shiftDialogTab == 0) {
                                    Button(
                                        enabled = shiftCashInput.isNotBlank(),
                                        onClick = {
                                            val declared = shiftCashInput.toDoubleOrNull() ?: 0.0
                                            val minorUnits = (declared * 100).toLong()
                                            vm.closeShift(minorUnits, onSuccess = {
                                                shiftMessage = "Shift closed and tallied successfully!"
                                                shiftCashInput = ""
                                                shiftDialogTab = 1
                                            }, onError = { err ->
                                                shiftMessage = "Error: $err"
                                            })
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Submit & Tally")
                                    }
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showCloseShiftDialog = false; shiftMessage = "" }) { Text("Close") }
                            }
                        )
                    }
                }

                composable(AppRoute.Masters.path) {
                    val catVm: CategoryViewModel = hiltViewModel()
                    val prodVm: ProductViewModel = hiltViewModel()
                    val custVm: CustomerViewModel = hiltViewModel()
                    val suppVm: SupplierViewModel = hiltViewModel()
                    val expVm: ExpenseViewModel = hiltViewModel()

                    MasterScreens(
                        categoryVm = catVm,
                        productVm = prodVm,
                        customerVm = custVm,
                        supplierVm = suppVm,
                        expenseVm = expVm
                    )
                }

                composable(AppRoute.Billing.path) {
                    val vm: BillingViewModel = hiltViewModel()
                    BillingScreen(viewModel = vm)
                }

                composable(AppRoute.Purchases.path) {
                    val vm: PurchaseViewModel = hiltViewModel()
                    PurchaseScreen(viewModel = vm)
                }

                composable(AppRoute.Reports.path) {
                    val vm: ReportsViewModel = hiltViewModel()
                    ReportsScreen(viewModel = vm)
                }

                composable(AppRoute.Settings.path) {
                    val vm: SettingsViewModel = hiltViewModel()
                    SettingsScreen(viewModel = vm)
                }

                composable(AppRoute.Subscription.path) {
                    com.kadaikutty.pos.feature.subscription.PaywallScreen(
                        onSimulatePayment = { planId ->
                            settingsViewModel.simulatePayment(planId)
                        }
                    )
                }

                composable(
                    route = AppRoute.Payment.path,
                    arguments = listOf(androidx.navigation.navArgument("price") { type = androidx.navigation.NavType.IntType })
                ) { backStackEntry ->
                    val price = backStackEntry.arguments?.getInt("price") ?: 0
                    com.kadaikutty.pos.feature.subscription.PaymentScreen(price = price)
                }
            }
            
            if (isLoggedIn == true && subscriptionStatus?.isExpired == true) {
                com.kadaikutty.pos.feature.subscription.PaywallScreen(
                    onSimulatePayment = { planId ->
                        settingsViewModel.simulatePayment(planId)
                    }
                )
            }
        } // Close Box
        } // Close BillingTheme
    } // Close CompositionLocalProvider
} // Close BillingApp function

data class DashboardItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: AppRoute,
    val badge: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    session: Session?,
    shopName: String,
    shopLogoPath: String,
    onNavigateTo: (AppRoute) -> Unit,
    onLogout: () -> Unit,
    onCloseShiftClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val dashboardState by viewModel.dashboardState.collectAsState()
    val permissions = session?.permissions ?: emptySet()
    
    val showMasters = permissions.any { it == Permission.CATEGORY_VIEW || it == Permission.PRODUCT_VIEW || it == Permission.USER_MANAGE }
    val showSales = permissions.any { it == Permission.SALE_CREATE || it == Permission.SALE_VIEW }
    val showPurchases = permissions.any { it == Permission.PURCHASE_CREATE || it == Permission.PURCHASE_VIEW }
    val showReports = permissions.any { it == Permission.REPORT_SALES || it == Permission.REPORT_STOCK || it == Permission.REPORT_PROFIT }
    val showSettings = permissions.any { it == Permission.SETTINGS_VIEW || it == Permission.USER_MANAGE }

    // Live Infinite Rotation Animation for Cloud Sync Button
    val infiniteTransition = rememberInfiniteTransition(label = "CloudSyncRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CloudSyncAngle"
    )

    var selectedBillNumForDetail by remember { mutableStateOf<String?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Bill Details Dialog for Recent Invoices
    if (selectedBillNumForDetail != null) {
        var billDetailData by remember(selectedBillNumForDetail) { mutableStateOf<com.kadaikutty.pos.feature.reports.presentation.BillDetailData?>(null) }
        var isBillLoading by remember(selectedBillNumForDetail) { mutableStateOf(true) }

        LaunchedEffect(selectedBillNumForDetail) {
            isBillLoading = true
            billDetailData = viewModel.getBillDetails(selectedBillNumForDetail!!)
            isBillLoading = false
        }

        BillDetailsDialog(
            billDetail = billDetailData,
            isLoading = isBillLoading,
            onDismiss = { selectedBillNumForDetail = null }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Confirm Logout") },
            text = { Text("Are you sure you want to logout? Unsynced data will be preserved in cloud queue.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) {
                    Text("Logout", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = shopName.ifBlank { "Kadaikutty POS" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = "Terminal Active • ${session?.userId ?: "Cashier"}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                actions = {
                    // 🔄 Live Rotating Cloud Backup Action Pill
                    Surface(
                        onClick = {
                            viewModel.triggerCloudSync()
                            Toast.makeText(context, "Cloud sync triggered...", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = if (dashboardState.isSyncing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val syncIconModifier = if (dashboardState.isSyncing) Modifier.rotate(rotationAngle) else Modifier
                            Icon(
                                imageVector = if (dashboardState.pendingSyncCount == 0 && !dashboardState.isSyncing) Icons.Default.CloudDone else Icons.Default.Sync,
                                contentDescription = "Cloud Sync",
                                tint = if (dashboardState.isSyncing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp).then(syncIconModifier)
                            )
                            Text(
                                text = when {
                                    dashboardState.isSyncing -> "Syncing..."
                                    dashboardState.pendingSyncCount > 0 -> "${dashboardState.pendingSyncCount} Pending"
                                    else -> "Live Cloud"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (dashboardState.isSyncing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    if (showSettings) {
                        IconButton(onClick = { onNavigateTo(AppRoute.Settings) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    IconButton(onClick = onCloseShiftClick) {
                        Icon(Icons.Default.Lock, contentDescription = "Close Shift", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 📈 Live Business Performance Summary (Top 2x2 KPI Matrix)
            Text(
                text = "Today's Live Performance",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // KPI 1: Today's Revenue
                DashboardKpiCard(
                    title = "Today's Sales",
                    value = "₹${Money(dashboardState.todaySalesMinorUnits)}",
                    subtitle = "${dashboardState.todayInvoicesCount} Invoices",
                    icon = Icons.Default.ShoppingCart,
                    accentColor = Color(0xFF059669),
                    modifier = Modifier.weight(1f),
                    onClick = { if (showReports) onNavigateTo(AppRoute.Reports) }
                )

                // KPI 2: Low Stock Warning
                DashboardKpiCard(
                    title = "Low Stock Alert",
                    value = "${dashboardState.lowStockCount} Items",
                    subtitle = if (dashboardState.lowStockCount > 0) "Needs Restock" else "Stock Healthy",
                    icon = Icons.Default.Warning,
                    accentColor = if (dashboardState.lowStockCount > 0) MaterialTheme.colorScheme.error else Color(0xFF059669),
                    modifier = Modifier.weight(1f),
                    onClick = { if (showPurchases) onNavigateTo(AppRoute.Purchases) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // KPI 3: Customer Due
                DashboardKpiCard(
                    title = "Customer Due",
                    value = "₹${Money(dashboardState.customerCreditDueMinorUnits)}",
                    subtitle = "Ledger Balance",
                    icon = Icons.Default.AccountBox,
                    accentColor = Color(0xFF2563EB),
                    modifier = Modifier.weight(1f),
                    onClick = { if (showMasters) onNavigateTo(AppRoute.Masters) }
                )

                // KPI 4: Inward Purchases
                DashboardKpiCard(
                    title = "Inward Stock",
                    value = "₹${Money(dashboardState.todayPurchasesMinorUnits)}",
                    subtitle = "Purchased Today",
                    icon = Icons.Default.Add,
                    accentColor = Color(0xFF7C3AED),
                    modifier = Modifier.weight(1f),
                    onClick = { if (showPurchases) onNavigateTo(AppRoute.Purchases) }
                )
            }

            // 2. ⚡ Hero Point of Sale Card
            if (showSales) {
                val heroGradient = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1E40AF),
                        Color(0xFF2563EB),
                        Color(0xFF3B82F6)
                    )
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onNavigateTo(AppRoute.Billing) }
                        .border(
                            width = 1.dp,
                            color = Color(0xFF60A5FA).copy(alpha = 0.4f),
                            shape = RoundedCornerShape(20.dp)
                        ),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(heroGradient)
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                            ) {
                                Icon(
                                    Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.padding(12.dp).size(30.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Point of Sale (POS)",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Surface(
                                        color = Color(0xFF10B981),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "⚡ START BILL",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Create Invoices, Barcode Scan & Instant Checkout",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // 3. 🗂️ Operations Matrix Grid (2x2 Grid Tiles)
            Text(
                text = "Business Modules",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (showPurchases) {
                    DashboardTileCard(
                        title = "Inward Stock",
                        subtitle = "Purchases & Inward Ledger",
                        icon = Icons.Default.AddCircle,
                        iconContainerColor = Color(0xFFEFF6FF),
                        iconTint = Color(0xFF2563EB),
                        badge = if (dashboardState.lowStockCount > 0) "${dashboardState.lowStockCount} Low" else null,
                        badgeColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateTo(AppRoute.Purchases) }
                    )
                }
                if (showMasters) {
                    DashboardTileCard(
                        title = "Master Catalog",
                        subtitle = "Products, Customers & Suppliers",
                        icon = Icons.Default.Menu,
                        iconContainerColor = Color(0xFFF5F3FF),
                        iconTint = Color(0xFF7C3AED),
                        badge = "6 Modules",
                        badgeColor = Color(0xFF7C3AED),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateTo(AppRoute.Masters) }
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (showReports) {
                    DashboardTileCard(
                        title = "Analytics & Reports",
                        subtitle = "Sales, Bills & Profit Margin",
                        icon = Icons.Default.List,
                        iconContainerColor = Color(0xFFECFDF5),
                        iconTint = Color(0xFF059669),
                        badge = "Live Reports",
                        badgeColor = Color(0xFF059669),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateTo(AppRoute.Reports) }
                    )
                }
                if (showSettings) {
                    DashboardTileCard(
                        title = "Subscription & Cloud",
                        subtitle = "Business Plan & Licenses",
                        icon = Icons.Default.Star,
                        iconContainerColor = Color(0xFFFFFBEB),
                        iconTint = Color(0xFFD97706),
                        badge = "PRO",
                        badgeColor = Color(0xFFD97706),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateTo(AppRoute.Subscription) }
                    )
                }
            }

            // 4. 🧾 Recent Invoices Live Activity Feed
            if (dashboardState.recentSales.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Invoices (Live)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (showReports) {
                        TextButton(onClick = { onNavigateTo(AppRoute.Reports) }) {
                            Text("View All ➔", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                val df = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                dashboardState.recentSales.forEach { sale ->
                    Card(
                        onClick = { selectedBillNumForDetail = sale.billNumber },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(14.dp)
                            ),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Bill #${sale.billNumber}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = df.format(Date(sale.createdAtEpochMs)),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "₹${Money(sale.totalMinorUnits)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Surface(
                                    color = when (sale.paymentMode.uppercase()) {
                                        "CASH" -> Color(0xFFD1FAE5)
                                        "UPI" -> Color(0xFFDBEAFE)
                                        else -> Color(0xFFFEF3C7)
                                    },
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = sale.paymentMode,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (sale.paymentMode.uppercase()) {
                                            "CASH" -> Color(0xFF065F46)
                                            "UPI" -> Color(0xFF1E40AF)
                                            else -> Color(0xFF92400E)
                                        },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun DashboardKpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .clickable { onClick() }
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = accentColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.padding(4.dp).size(16.dp)
                    )
                }
            }

            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )

            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun DashboardTileCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    badge: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable { onClick() }
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = iconContainerColor,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.padding(8.dp).size(22.dp)
                    )
                }

                if (badge != null) {
                    Surface(
                        color = badgeColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}
