package com.company.billing.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import java.io.File
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import com.company.billing.core.common.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.company.billing.core.security.Permission
import com.company.billing.core.auth.Session
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
import com.company.billing.core.navigation.AppRoute
import com.company.billing.feature.settings.presentation.SettingsScreen
import com.company.billing.feature.settings.presentation.SettingsViewModel
import com.company.billing.feature.auth.LoginScreen
import com.company.billing.feature.auth.LoginViewModel
import com.company.billing.feature.auth.RegisterScreen
import com.company.billing.feature.auth.RegisterViewModel
import com.company.billing.feature.auth.SetNewPasswordScreen
import com.company.billing.feature.auth.SetNewPasswordViewModel
import com.company.billing.feature.billing.presentation.BillingScreen
import com.company.billing.feature.billing.presentation.BillingViewModel
import com.company.billing.feature.masters.presentation.*
import com.company.billing.feature.purchase.presentation.PurchaseScreen
import com.company.billing.feature.purchase.presentation.PurchaseViewModel
import com.company.billing.feature.reports.presentation.ReportsScreen
import com.company.billing.feature.reports.presentation.ReportsViewModel
import com.company.billing.core.ui.theme.BillingTheme
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

    CompositionLocalProvider(LocalLayoutMode provides layoutMode) {
        BillingTheme(darkTheme = useDarkTheme) {
            val startDest = AppRoute.Home.path
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
                            navController.navigate(AppRoute.Login.path) {
                                popUpTo(AppRoute.Register.path) { inclusive = true }
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
                    val vm: com.company.billing.feature.home.HomeViewModel = hiltViewModel()
                    val session by vm.activeSession.collectAsState()
                    var showCloseShiftDialog by remember { mutableStateOf(false) }
                    var shiftCashInput by remember { mutableStateOf("") }
                    var shiftMessage by remember { mutableStateOf("") }

                    HomeScreen(
                        session = session,
                        shopName = shopName,
                        shopLogoPath = shopLogoPath,
                        onNavigateTo = { route -> navController.navigate(route.path) },
                        onLogout = {
                            vm.logout()
                            navController.navigate(AppRoute.Login.path) {
                                popUpTo(AppRoute.Home.path) { inclusive = true }
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
            }
        }
    }
}

data class DashboardItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: AppRoute
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    session: Session?,
    shopName: String,
    shopLogoPath: String,
    onNavigateTo: (AppRoute) -> Unit,
    onLogout: () -> Unit,
    onCloseShiftClick: () -> Unit = {}
) {
    val permissions = session?.permissions ?: emptySet()
    
    val showMasters = permissions.any { it == Permission.CATEGORY_VIEW || it == Permission.PRODUCT_VIEW || it == Permission.USER_MANAGE }
    val showSales = permissions.any { it == Permission.SALE_CREATE || it == Permission.SALE_VIEW }
    val showPurchases = permissions.any { it == Permission.PURCHASE_CREATE || it == Permission.PURCHASE_VIEW }
    val showReports = permissions.any { it == Permission.REPORT_SALES || it == Permission.REPORT_STOCK || it == Permission.REPORT_PROFIT }
    val showSettings = permissions.any { it == Permission.SETTINGS_VIEW || it == Permission.USER_MANAGE }

    val dashboardItems = remember(showMasters, showSales, showPurchases, showReports) {
        buildList {
            if (showMasters) {
                add(DashboardItem("Master Data", "Manage Categories, Products, Customers, Suppliers, Expenses", Icons.Default.Menu, AppRoute.Masters))
            }
            if (showSales) {
                add(DashboardItem("Sales Invoicing", "Draft bills, invoice products and log sales ledger", Icons.Default.ShoppingCart, AppRoute.Billing))
            }
            if (showPurchases) {
                add(DashboardItem("Purchases & Stock", "Record stock inward, manage supplier invoices & ledger", Icons.Default.AddCircle, AppRoute.Purchases))
            }
            if (showReports) {
                add(DashboardItem("Reports Engine", "Analyze Sales, Stock, Profits, Purchases & Expenses", Icons.Default.List, AppRoute.Reports))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        Text(
                            text = shopName.ifBlank { "KadaKutty" },
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                actions = {
                    if (showSettings) {
                        IconButton(onClick = { onNavigateTo(AppRoute.Settings) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    IconButton(onClick = onCloseShiftClick) {
                        Icon(Icons.Default.Lock, contentDescription = "Close Shift", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            val layoutMode = LocalLayoutMode.current
            val isMobile = when (layoutMode) {
                "Mobile" -> true
                "Tablet" -> false
                else -> maxWidth < 600.dp
            }
            
            if (isMobile) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Welcome to your Business Dashboard",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Manage sales, stocks and master lists in one tap",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    dashboardItems.forEach { item ->
                        DashboardCard(
                            title = item.title,
                            subtitle = item.subtitle,
                            icon = item.icon,
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            onClick = { onNavigateTo(item.route) }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column {
                        Text(
                            text = "Welcome to your Business Dashboard",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Manage sales, stocks and master lists in one tap",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    dashboardItems.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            rowItems.forEach { item ->
                                DashboardCard(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    icon = item.icon,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    onClick = { onNavigateTo(item.route) }
                                )
                            }
                            if (rowItems.size < 2) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable { onClick() }
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
