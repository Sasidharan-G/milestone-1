package com.kadaikutty.pos.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import com.kadaikutty.pos.R
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kadaikutty.pos.core.navigation.AppRoute
import com.kadaikutty.pos.feature.settings.presentation.SettingsScreen
import com.kadaikutty.pos.feature.settings.presentation.SettingsViewModel
import com.kadaikutty.pos.feature.settings.presentation.SyncDiagnosticsScreen
import com.kadaikutty.pos.feature.settings.presentation.SyncDiagnosticsViewModel
import com.kadaikutty.pos.feature.auth.LoginViewModel
import com.kadaikutty.pos.feature.auth.RegisterViewModel
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.kadaikutty.pos.feature.home.HomeViewModel
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
import androidx.compose.ui.draw.shadow

val LocalLayoutMode = staticCompositionLocalOf { "Auto" }

@Composable
fun BillingApp() {
    val navController = rememberNavController()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val layoutMode by settingsViewModel.layoutMode.collectAsState()
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val shopName by settingsViewModel.shopName.collectAsState()
    val useDarkTheme = when (themeMode) {
        "Dark" -> true
        "Light" -> false
        else -> isSystemInDarkTheme()
    }

    val isLoggedIn by settingsViewModel.isLoggedIn.collectAsState()
    val activeSession by settingsViewModel.activeSession.collectAsState()

    val currentLicense by settingsViewModel.currentLicense.collectAsState()
    val isClockTampered by settingsViewModel.isClockTampered.collectAsState()
    var showRenewalDailyDialog by remember { mutableStateOf(value = false) }

    LaunchedEffect(currentLicense) {
        if ((currentLicense?.isExpiringSoon == true) && settingsViewModel.shouldShowRenewalAlert()) {
            showRenewalDailyDialog = true
        }
    }



    val isSessionTerminated by settingsViewModel.isSessionTerminated.collectAsState()
    val terminationReason by settingsViewModel.terminationReason.collectAsState()

    if (isLoggedIn == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Color(0xFF1E88E5))
        }
        return
    }

    CompositionLocalProvider(LocalLayoutMode provides layoutMode) {
        BillingTheme(themeMode = themeMode, darkTheme = useDarkTheme) {
            if (isSessionTerminated) {
                AlertDialog(
                    onDismissRequest = { },
                    containerColor = Color(0xFF1E293B),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Session Expired",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    },
                    text = {
                        Text(
                            text = terminationReason ?: "Your account has been logged in on another device. This session has expired.",
                            color = Color(0xFFE2E8F0),
                            fontSize = 14.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                settingsViewModel.acknowledgeSessionTermination()
                                navController.navigate(AppRoute.Login.path) {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                        ) {
                            Text("Back to Login", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            val startDest = if (isLoggedIn == true) AppRoute.Home.path else AppRoute.Login.path
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = startDest,
                    enterTransition = { androidx.compose.animation.EnterTransition.None },
                    exitTransition = { androidx.compose.animation.ExitTransition.None },
                    popEnterTransition = { androidx.compose.animation.EnterTransition.None },
                    popExitTransition = { androidx.compose.animation.ExitTransition.None }
                ) {
                composable(AppRoute.Login.path) {
                    val loginVm: LoginViewModel = hiltViewModel()
                    val registerVm: RegisterViewModel = hiltViewModel()
                    
                    com.kadaikutty.pos.feature.auth.AuthScreen(
                        loginViewModel = loginVm,
                        registerViewModel = registerVm,
                        onLoginSuccess = {
                            navController.navigate(AppRoute.Home.path) {
                                popUpTo(AppRoute.Login.path) { inclusive = true }
                            }
                        },
                        onOpenMasterControl = {
                            navController.navigate(AppRoute.MasterControl.path)
                        }
                    )
                }

                // Removed AppRoute.Register.path as it is now handled via flip inside AuthScreen

                composable(AppRoute.Home.path) {
                    val vm: HomeViewModel = hiltViewModel()
                    val session by vm.activeSession.collectAsState()
                    var showCloseShiftDialog by remember { mutableStateOf(value = false) }
                    var shiftCashInput by remember { mutableStateOf("") }
                    var shiftMessage by remember { mutableStateOf("") }

                    HomeScreen(
                        viewModel = vm,
                        session = session,
                        shopName = shopName,
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
                    var shiftDialogTab by remember { mutableIntStateOf(0) }

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
                                                                isMatch -> "Tally Matched"
                                                                isShortage -> "Shortage: -${Money(kotlin.math.abs(shift.discrepancyMinorUnits))}"
                                                                else -> "Extra: +${Money(shift.discrepancyMinorUnits)}"
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
                                            vm.closeShift(
                                                declaredCashMinorUnits = minorUnits,
                                                onSuccess = {
                                                    shiftMessage = "Shift closed and tallied successfully!"
                                                    shiftCashInput = ""
                                                    shiftDialogTab = 1
                                                },
                                                onError = { err ->
                                                    shiftMessage = "Error: $err"
                                                }
                                            )
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
                    val settingsVm: SettingsViewModel = hiltViewModel()

                    MasterScreens(
                        categoryVm = catVm,
                        productVm = prodVm,
                        customerVm = custVm,
                        supplierVm = suppVm,
                        expenseVm = expVm,
                        settingsVm = settingsVm,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(AppRoute.Billing.path) {
                    val vm: BillingViewModel = hiltViewModel()
                    BillingScreen(viewModel = vm, onBack = { navController.popBackStack() })
                }

                composable(AppRoute.Purchases.path) {
                    val vm: PurchaseViewModel = hiltViewModel()
                    PurchaseScreen(viewModel = vm, onBack = { navController.popBackStack() })
                }

                composable(AppRoute.Reports.path) {
                    val vm: ReportsViewModel = hiltViewModel()
                    ReportsScreen(viewModel = vm, onBack = { navController.popBackStack() })
                }

                composable(AppRoute.Settings.path) {
                    val vm: SettingsViewModel = hiltViewModel()
                    SettingsScreen(
                        viewModel = vm,
                        onBack = { navController.popBackStack() },
                        onOpenMasterControl = {
                            navController.navigate(AppRoute.MasterControl.path)
                        },
                        onOpenSyncDiagnostics = {
                            navController.navigate(AppRoute.SyncDiagnostics.path)
                        }
                    )
                }

                composable(AppRoute.SyncDiagnostics.path) {
                    val vm: SyncDiagnosticsViewModel = hiltViewModel()
                    SyncDiagnosticsScreen(
                        viewModel = vm,
                        onBack = { navController.popBackStack() }
                    )
                }



                composable(AppRoute.MasterControl.path) {
                    val masterVm: com.kadaikutty.pos.feature.mastercontrol.presentation.MasterControlViewModel = hiltViewModel()
                    com.kadaikutty.pos.feature.mastercontrol.presentation.MasterControlScreen(
                        viewModel = masterVm,
                        onBack = { navController.popBackStack() }
                    )
                }


            }

            // Strict Offline/Online Expiry Lock Screen
            val isLicenseLocked = (isLoggedIn == true) && (activeSession?.role != "SUPER_ADMIN") && ((currentLicense?.isExpired == true) || isClockTampered)
            if (isLicenseLocked) {
                com.kadaikutty.pos.feature.subscription.LicenseExpiredLockScreen(
                    license = currentLicense,
                    shopName = shopName,
                    onRefreshStatus = { settingsViewModel.refreshLicenseStatus() },
                    onLogout = {
                        settingsViewModel.logout {
                            navController.navigate(AppRoute.Login.path) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                )
            }

            // 7-Day Expiry Renewal Reminder Dialog (Max 2 times per day)
            if (showRenewalDailyDialog && (currentLicense != null)) {
                val context = LocalContext.current
                val masterContactPhone = "+919840000000"
                AlertDialog(
                    onDismissRequest = {
                        settingsViewModel.markRenewalAlertShown()
                        showRenewalDailyDialog = false
                    },
                    containerColor = Color(0xFFFDF7F7), // Light maroon shade
                    tonalElevation = 8.dp,
                    icon = { Icon(Icons.Default.Timer, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(36.dp)) },
                    title = { Text("License Expiry Reminder", fontWeight = FontWeight.Bold, color = Color(0xFF5C151A)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Your KadaiKutty POS License expires in ${currentLicense!!.remainingDays} days!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Text("To avoid billing disruptions and maintain your uninterrupted POS operations, please contact the Master Admin to renew your 1-Year license.", color = Color.Black.copy(alpha = 0.8f))
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                try {
                                    val url = "https://api.whatsapp.com/send?phone=$masterContactPhone&text=Hello%20Master%20Admin,%20I%20want%20to%20renew%20my%20license%20for%20${android.net.Uri.encode(shopName)}"
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri())
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                                settingsViewModel.markRenewalAlertShown()
                                showRenewalDailyDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Text("WhatsApp to Renew", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                settingsViewModel.markRenewalAlertShown()
                                showRenewalDailyDialog = false
                            }
                        ) {
                            Text("Remind Later", color = Color(0xFF5C151A))
                        }
                    }
                )
            }
        } // Close Box
        } // Close BillingTheme
    } // Close CompositionLocalProvider
} // Close BillingApp function

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    session: Session?,
    shopName: String,
    onNavigateTo: (AppRoute) -> Unit,
    onLogout: () -> Unit,
    onCloseShiftClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val dashboardState by viewModel.dashboardState.collectAsState()
    val permissions = session?.permissions ?: emptySet()
    
    val showMasters = permissions.any { (it == Permission.CATEGORY_VIEW) || (it == Permission.PRODUCT_VIEW) || (it == Permission.USER_MANAGE) }
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
    var showLogoutDialog by remember { mutableStateOf(value = false) }
    var showSettingsMenu by remember { mutableStateOf(value = false) }

    // Bill Details Dialog for Recent Invoices
    if (selectedBillNumForDetail != null) {
        var billDetailData by remember(selectedBillNumForDetail) { mutableStateOf<com.kadaikutty.pos.feature.reports.presentation.BillDetailData?>(null) }
        var isBillLoading by remember(selectedBillNumForDetail) { mutableStateOf(value = true) }

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
            containerColor = Color(0xFFFDF7F7), // Light maroon shade
            tonalElevation = 8.dp, // Light shadow
            title = { Text("Confirm Logout", color = Color(0xFF5C151A)) },
            text = { Text("Are you sure you want to logout? Unsynced data will be preserved in cloud queue.", color = Color.Black.copy(alpha = 0.7f)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("Logout", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = Color(0xFF5C151A))
                }
            }
        )
    }

    Scaffold(
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp), // Floating margin
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.width(260.dp).height(65.dp),
                    shape = RoundedCornerShape(35.dp),
                    color = Color.Transparent,
                    shadowElevation = 8.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(Color(0xFF4A1115), Color(0xFF5C151A), Color(0xFF4A1115))
                            )
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        // Left: Master Catalog
                        if (showMasters) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { onNavigateTo(AppRoute.Masters) }.padding(8.dp)
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "Masters", tint = Color.White.copy(alpha = 0.8f))
                                Text("Masters", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(48.dp))
                        }

                        // Center Spacer for FAB
                        Spacer(modifier = Modifier.width(64.dp))

                        // Right: Reports
                        if (showReports) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { onNavigateTo(AppRoute.Reports) }.padding(8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Reports", tint = Color.White.copy(alpha = 0.8f))
                                Text("Reports", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(48.dp))
                        }
                        }
                    }
                }

                // Center Raised FAB (Inward Stock)
                if (showPurchases) {
                    FloatingActionButton(
                        onClick = { onNavigateTo(AppRoute.Purchases) },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-20).dp)
                            .size(64.dp)
                            .border(4.dp, MaterialTheme.colorScheme.background, CircleShape),
                        shape = CircleShape,
                        containerColor = Color(0xFF5C151A),
                        contentColor = Color.White,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Inward Stock", modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            val topInset = paddingValues.calculateTopPadding()
            val scoopHeight = topInset + 72.dp
            val density = LocalDensity.current
            val maroonScoopShape = remember(scoopHeight, density) {
                object : androidx.compose.ui.graphics.Shape {
                    override fun createOutline(
                        size: androidx.compose.ui.geometry.Size,
                        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                        density: androidx.compose.ui.unit.Density
                    ): androidx.compose.ui.graphics.Outline {
                        val scoopDepth = with(density) { scoopHeight.toPx() }
                        val corner = with(density) { 48.dp.toPx() }
                        val path = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width * 0.35f, 0f)
                            // S-curve: wider white area at top for icons
                            cubicTo(
                                size.width * 0.58f, 0f,
                                size.width * 0.50f, scoopDepth * 0.85f,
                                size.width, scoopDepth
                            )
                            lineTo(size.width, size.height - corner)
                            quadraticTo(size.width, size.height, size.width - corner, size.height)
                            lineTo(corner, size.height)
                            quadraticTo(0f, size.height, 0f, size.height - corner)
                            close()
                        }
                        return androidx.compose.ui.graphics.Outline.Generic(path)
                    }
                }
            }

            val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 4 })
            LaunchedEffect(pagerState) {
                while(true) {
                    kotlinx.coroutines.delay(3000)
                    val nextPage = (pagerState.currentPage + 1) % 4
                    pagerState.animateScrollToPage(
                        page = nextPage,
                        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                // 1. Maroon background with S-curve + bottom rounded corners
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(maroonScoopShape)
                        .background(Color(0xFF5C151A))
                        .padding(top = scoopHeight - 12.dp)
                        .padding(bottom = 20.dp)
                ) {
                    Text(
                        text = shopName.ifBlank { "Kadaikutty POS" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "In the moment",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    androidx.compose.foundation.pager.HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(start = 16.dp, end = 48.dp),
                        pageSpacing = 12.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        when (page) {
                            0 -> DashboardKpiCard(
                                title = "Today's Sales",
                                value = Money(dashboardState.todaySalesMinorUnits).toString(),
                                subtitle = "${dashboardState.todayInvoicesCount} Invoices",
                                icon = Icons.Default.ShoppingCart,
                                accentColor = Color(0xFF8B252C),
                                modifier = Modifier.fillMaxWidth().height(150.dp),
                                onClick = { if (showReports) onNavigateTo(AppRoute.Reports) }
                            )
                            1 -> DashboardKpiCard(
                                title = "Low Stock Alert",
                                value = "${dashboardState.lowStockCount} Items",
                                subtitle = if (dashboardState.lowStockCount > 0) "Needs Restock" else "Stock Healthy",
                                icon = Icons.Default.Warning,
                                accentColor = if (dashboardState.lowStockCount > 0) MaterialTheme.colorScheme.error else Color(0xFF8B252C),
                                modifier = Modifier.fillMaxWidth().height(150.dp),
                                onClick = { if (showPurchases) onNavigateTo(AppRoute.Purchases) }
                            )
                            2 -> DashboardKpiCard(
                                title = "Customer Due",
                                value = Money(dashboardState.customerCreditDueMinorUnits).toString(),
                                subtitle = "Ledger Balance",
                                icon = Icons.Default.AccountBox,
                                accentColor = Color(0xFF8B252C),
                                modifier = Modifier.fillMaxWidth().height(150.dp),
                                onClick = { if (showMasters) onNavigateTo(AppRoute.Masters) }
                            )
                            3 -> DashboardKpiCard(
                                title = "Inward Stock",
                                value = Money(dashboardState.todayPurchasesMinorUnits).toString(),
                                subtitle = "Purchased Today",
                                icon = Icons.Default.Add,
                                accentColor = Color(0xFF8B252C),
                                modifier = Modifier.fillMaxWidth().height(150.dp),
                                onClick = { if (showPurchases) onNavigateTo(AppRoute.Purchases) }
                            )
                        }
                    }

                    // Dots
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                                .align(Alignment.Center),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(4) { iteration ->
                                val color = if (pagerState.currentPage == iteration) Color.White else Color.White.copy(alpha = 0.3f)
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .size(6.dp)
                                )
                            }
                        }
                    }
                } // close maroon Column

                // 2. Icons overlay at top-right
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = topInset, end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        viewModel.triggerCloudSync()
                        Toast.makeText(context, "Cloud sync triggered...", Toast.LENGTH_SHORT).show()
                    }) {
                        val syncIconModifier = if (dashboardState.isSyncing) Modifier.rotate(rotationAngle) else Modifier
                        Icon(
                            imageVector = if (dashboardState.pendingSyncCount == 0 && !dashboardState.isSyncing) Icons.Default.CloudDone else Icons.Default.Sync,
                            contentDescription = "Cloud Sync",
                            tint = if (dashboardState.isSyncing) Color(0xFF38BDF8) else Color(0xFF5C151A),
                            modifier = Modifier.size(24.dp).then(syncIconModifier)
                        )
                    }
                    IconButton(onClick = onCloseShiftClick) {
                        Icon(Icons.Default.Lock, contentDescription = "Close Shift", tint = Color(0xFF5C151A))
                    }
                    Box {
                        IconButton(onClick = { showSettingsMenu = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color(0xFF5C151A))
                        }
                        DropdownMenu(
                            expanded = showSettingsMenu,
                            onDismissRequest = { showSettingsMenu = false }
                        ) {
                            if (showSettings) {
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    onClick = {
                                        showSettingsMenu = false
                                        onNavigateTo(AppRoute.Settings)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Logout", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showSettingsMenu = false
                                    showLogoutDialog = true
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                }
            } // close header Box
            // Spacer to wrap bottom contents
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {

            // 2. Hero Point of Sale Card
            if (showSales) {
                val heroGradient = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF5C151A),
                        Color(0xFF8B252C),
                        Color(0xFF4A1115)
                    )
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onNavigateTo(AppRoute.Billing) }
                        .border(
                            width = 1.dp,
                            color = Color(0xFF8B252C).copy(alpha = 0.4f),
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
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                        modifier = Modifier.padding(10.dp).size(26.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Point of Sale (POS)",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Create Invoices, Barcode Scan & Instant Checkout",
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                }
                            }

                            // Dedicated START NEW BILL CTA Button
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "START NEW BILL",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF5C151A)
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                        contentDescription = null,
                                        tint = Color(0xFF5C151A),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }



            // 4. Recent Invoices Live Activity Feed
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
                            Text("View All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                    text = Money(sale.totalMinorUnits).toString(),
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
        } // close the inner Column
        } // close the outer scrolling Column
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
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = accentColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.padding(6.dp).size(20.dp)
                    )
                }
            }

            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )

            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
fun DashboardTileCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    badge: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
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
            }
        }
    }
}
