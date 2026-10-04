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
import com.kadaikutty.pos.core.common.CheckoutMath
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.layout.layout

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
    val isLicenseLoaded by settingsViewModel.isLicenseLoaded.collectAsState()
    val isClockTampered by settingsViewModel.isClockTampered.collectAsState()
    var showRenewalDailyDialog by remember { mutableStateOf(value = false) }

    LaunchedEffect(currentLicense) {
        if ((currentLicense?.isExpiringSoon == true) && settingsViewModel.shouldShowRenewalAlert()) {
            showRenewalDailyDialog = true
        }
    }



    val isSessionTerminated by settingsViewModel.isSessionTerminated.collectAsState()
    val terminationReason by settingsViewModel.terminationReason.collectAsState()

    val isLicenseLoading = (isLoggedIn == true) && (activeSession?.role != "SUPER_ADMIN") && (!isLicenseLoaded || currentLicense == null)
    val isLicenseLocked = (isLoggedIn == true) && (activeSession?.role != "SUPER_ADMIN") && ((currentLicense?.isExpired == true) || isClockTampered)

    // Checked only once the company-wide license lock above has already passed, so a shop that's
    // simply mid-renewal never sees two different lock screens fighting for the same problem.

    if (isLoggedIn == null || isLicenseLoading) {
        // Same dark brand colour as the launch splash, so the white status bar icons stay readable while loading.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E3A8A)),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    CompositionLocalProvider(LocalLayoutMode provides layoutMode) {
        BillingTheme(themeMode = themeMode, darkTheme = useDarkTheme) {
            if (isSessionTerminated) {
                AlertDialog(
                    onDismissRequest = { },
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Session Expired",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    text = {
                        Text(
                            text = terminationReason ?: "Your account has been logged in on another device. This session has expired.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Back to Login", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            val isOnline by settingsViewModel.isOnline.collectAsState()

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
                return@BillingTheme
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
                        isOnline = isOnline,
                        onNavigateTo = { route -> navController.navigate(route.path) },
                        onOpenMasters = { tab -> navController.navigate("${AppRoute.Masters.path}?tab=$tab") },
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
                                            onValueChange = { shiftCashInput = com.kadaikutty.pos.core.common.InputRules.money(it) },
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
                                            val minorUnits = CheckoutMath.rupeesToMinorUnits(declared)
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

                // Optional ?tab= picks the Masters tab to open on (0 Categories, 1 Products, 5 Ledger);
                // a plain "masters" still matches and opens Categories.
                composable(
                    "${AppRoute.Masters.path}?tab={tab}",
                    arguments = listOf(androidx.navigation.navArgument("tab") {
                        type = androidx.navigation.NavType.IntType
                        defaultValue = 0
                    })
                ) { backStackEntry ->
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
                        expenseVm = expVm,
                        initialTab = backStackEntry.arguments?.getInt("tab") ?: 0,
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
                        },
                        onAccountDeleted = {
                            navController.navigate(AppRoute.Login.path) { popUpTo(0) { inclusive = true } }
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

            // 7-Day Expiry Renewal Reminder Dialog (Max 2 times per day)
            if (showRenewalDailyDialog && (currentLicense != null)) {
                val context = LocalContext.current
                val masterContactPhone = "+919840000000"
                AlertDialog(
                    onDismissRequest = {
                        settingsViewModel.markRenewalAlertShown()
                        showRenewalDailyDialog = false
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 8.dp,
                    icon = { Icon(Icons.Default.Timer, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(36.dp)) },
                    title = { Text("License Expiry Reminder", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Your KadaiKutty POS License expires in ${currentLicense!!.remainingDays} days!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Text("To avoid billing disruptions and maintain your uninterrupted POS operations, please contact the Master Admin to renew your 1-Year license.", color = MaterialTheme.colorScheme.onPrimaryContainer)
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
                            Text("Remind Later", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                )
            }

            // Phones freeze background apps; without this offline bills would wait for the next time the app is opened.
            if (isLoggedIn == true && !showRenewalDailyDialog) BackgroundAccessPrompt()

            val syncNotificationState by settingsViewModel.syncNotificationState.collectAsState()
            SyncNotificationOverlay(
                state = syncNotificationState,
                onRetry = { settingsViewModel.retrySync() },
                onDismiss = { settingsViewModel.dismissSyncNotification() },
                modifier = if (syncNotificationState is com.kadaikutty.pos.core.sync.SyncNotificationState.Failed) {
                    Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp)
                } else {
                    // Small "Synced" pill stays top-start so it never covers the sync / lock / settings icons
                    // at top-end. 56dp of top padding clears the 26sp bold shop-name title below it (that
                    // title's own Column starts at topInset + 20.dp and is roughly 32dp tall) - 4dp landed
                    // the pill directly on top of the title text.
                    Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 56.dp)
                }
            )

            // Offline is a normal state, not an error: billing keeps working and syncs later, so
            // this is a small badge, never a blocking screen. Anchored top-start so it never
            // covers the cloud-sync / settings buttons which live top-end. Hidden on the login
            // screen, which explains a failed sign-in itself.
            if (isLoggedIn == true) {
                ConnectionStatusBanner(
                    isOnline = isOnline,
                    needsSignIn = isOnline && activeSession != null && activeSession?.accessToken.isNullOrBlank(),
                    onSignIn = {
                        settingsViewModel.logout {
                            navController.navigate(AppRoute.Login.path) { popUpTo(0) { inclusive = true } }
                        }
                    },
                    modifier = Modifier.align(Alignment.TopStart)
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
    isOnline: Boolean,
    onNavigateTo: (AppRoute) -> Unit,
    onOpenMasters: (tab: Int) -> Unit,
    onLogout: () -> Unit,
    onCloseShiftClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val dashboardState by viewModel.dashboardState.collectAsState()
    val hasStaleUnsyncedData by viewModel.hasStaleUnsyncedData.collectAsState()
    val negativeStockProducts by viewModel.negativeStockProducts.collectAsState()

    val permissions = session?.permissions ?: emptySet()
    val isAdmin = session?.role == "ADMIN" || session?.role == "SUPER_ADMIN"
    
    val showMasters = isAdmin || permissions.any { (it == Permission.CATEGORY_VIEW) || (it == Permission.PRODUCT_VIEW) || (it == Permission.USER_MANAGE) }
    val showSales = isAdmin || permissions.any { it == Permission.SALE_CREATE || it == Permission.SALE_VIEW }
    val showPurchases = isAdmin || permissions.any { it == Permission.PURCHASE_CREATE || it == Permission.PURCHASE_VIEW }
    val showReports = isAdmin || permissions.any { it == Permission.REPORT_SALES || it == Permission.REPORT_STOCK || it == Permission.REPORT_PROFIT }
    val showSettings = isAdmin || permissions.any { it == Permission.SETTINGS_VIEW || it == Permission.USER_MANAGE }

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
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 8.dp, // Light shadow
            title = { Text("Confirm Logout", color = MaterialTheme.colorScheme.onPrimaryContainer) },
            text = { Text("Are you sure you want to logout? Unsynced data will be preserved in cloud queue.", color = MaterialTheme.colorScheme.onPrimaryContainer) },
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
                    Text("Cancel", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        )
    }

    // Guard: show loading spinner if session not yet loaded from DataStore.
    // All remember/composable hooks above run unconditionally. This return is safe.
    if (session == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                // Applied after the scroll, so it is room at the END of the content: the last card and the
                // start-bill button can be scrolled clear of the floating bottom bar instead of sitting under it.
                .padding(bottom = paddingValues.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            val topInset = paddingValues.calculateTopPadding()
            // A plain rounded-bottom banner - simpler and predictable at any content height,
            // unlike the old custom S-curve Shape whose control points were tuned for a much
            // taller (KPI-carousel) header and would have looked wrong once that content left.
            val brandHeaderShape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)

            Box(modifier = Modifier.fillMaxWidth()) {
                // Behind the status bar the header would sit under the phone's white
                // signal/wifi/battery icons and hide them; a dark-blue strip keeps them readable.
                Box(modifier = Modifier.fillMaxWidth().height(topInset).background(Color(0xFF1E3A8A)))
                // 1. Dark-blue banner with bottom-rounded corners
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(brandHeaderShape)
                        .background(Color(0xFF1E3A8A))
                        .padding(top = topInset + 20.dp)
                        .padding(bottom = 20.dp)
                ) {
                    Text(
                        text = shopName.ifBlank { "Kadaikutty POS" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = Color.White,
                        // A long shop name wrapping to a 2nd line would grow this header past what
                        // the KPI row's fixed 28dp "tuck up" below assumes, letting the KPI cards'
                        // touch target creep into the sync/close-shift/settings icons above them.
                        // Capping to one line keeps the header height - and that assumption - fixed.
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } // close brand-header Column

                // 2. Icons overlay at top-right
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = topInset, end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        // Without this, a second tap while a sync is already running calls
                        // triggerCloudSync() again; HomeViewModel enqueues it with REPLACE, so the
                        // impatient tap cancels the in-flight sync and restarts it instead of letting
                        // it finish.
                        enabled = !dashboardState.isSyncing,
                        onClick = {
                            if (isOnline) {
                                viewModel.triggerCloudSync()
                                Toast.makeText(context, "Cloud sync triggered...", Toast.LENGTH_SHORT).show()
                            } else {
                                // The sync job only runs once connectivity returns (it's enqueued
                                // network-constrained) - saying "triggered" here would be misleading.
                                Toast.makeText(context, "You're offline. Sync will resume once you're back online.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        val syncIconModifier = if (dashboardState.isSyncing) Modifier.rotate(rotationAngle) else Modifier
                        // These three icons sit on the fixed dark-blue header, not a themed surface -
                        // a primary-tinted icon would all but vanish into it. White reads on the header in
                        // both themes, the same way the shop-name text above it is fixed white too.
                        Icon(
                            imageVector = if (dashboardState.pendingSyncCount == 0 && !dashboardState.isSyncing) Icons.Default.CloudDone else Icons.Default.Sync,
                            contentDescription = "Cloud Sync",
                            tint = if (dashboardState.isSyncing) Color(0xFF38BDF8) else Color.White,
                            modifier = Modifier.size(24.dp).then(syncIconModifier)
                        )
                    }
                    IconButton(onClick = onCloseShiftClick) {
                        Icon(Icons.Default.Lock, contentDescription = "Close Shift", tint = Color.White)
                    }
                    Box {
                        IconButton(onClick = { showSettingsMenu = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

            // Four KPI cards instead of a plain table - still no chart/graph to read, just a
            // bigger number and an icon per period, easier to scan at a glance than a stacked list.
            // The sales cards open Reports (bill-wise sales) for anyone allowed to see it.
            val openReports: (() -> Unit)? = if (showReports) ({ onNavigateTo(AppRoute.Reports) }) else null
            fun invoiceSubtitle(count: Int) = "$count ${if (count == 1) "Invoice" else "Invoices"}"
            if (showSales) {
                Column(
                    // Tucks the cards 28dp up into the header. Unlike offset(), this also gives the
                    // 28dp back to the layout, so no empty band is left under the cards.
                    modifier = Modifier.fillMaxWidth().layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val overlap = 28.dp.roundToPx()
                        layout(placeable.width, (placeable.height - overlap).coerceAtLeast(0)) {
                            placeable.place(0, -overlap)
                        }
                    },
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardKpiCard(
                            title = "Today Sale",
                            value = Money(dashboardState.todaySalesMinorUnits).toString(),
                            subtitle = invoiceSubtitle(dashboardState.todayInvoicesCount),
                            icon = Icons.Default.Today,
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                            onClick = openReports
                        )
                        DashboardKpiCard(
                            title = "Yesterday Sale",
                            value = Money(dashboardState.yesterdaySalesMinorUnits).toString(),
                            subtitle = invoiceSubtitle(dashboardState.yesterdayInvoicesCount),
                            icon = Icons.Default.History,
                            accentColor = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f),
                            onClick = openReports
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardKpiCard(
                            title = "Weekly Sale",
                            value = Money(dashboardState.weeklySalesMinorUnits).toString(),
                            subtitle = invoiceSubtitle(dashboardState.weeklyInvoicesCount),
                            icon = Icons.Default.CalendarViewWeek,
                            accentColor = Color(0xFFF59E0B),
                            modifier = Modifier.weight(1f),
                            onClick = openReports
                        )
                        DashboardKpiCard(
                            title = "Monthly Sale",
                            value = Money(dashboardState.monthlySalesMinorUnits).toString(),
                            subtitle = invoiceSubtitle(dashboardState.monthlyInvoicesCount),
                            icon = Icons.Default.CalendarMonth,
                            accentColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f),
                            onClick = openReports
                        )
                    }
                }
            }

            if (hasStaleUnsyncedData) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            "You have unsynced changes and haven't connected in over a day — connect to Wi-Fi or mobile data soon to keep your backups current.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (negativeStockProducts.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        val names = negativeStockProducts.take(3).joinToString { it.name }
                        val more = if (negativeStockProducts.size > 3) " and ${negativeStockProducts.size - 3} more" else ""
                        Text(
                            "Stock below zero: $names$more. These were sold on more than one device while offline — count the stock and adjust it.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // 2. Billing hero card, then the Business Modules: three master tiles and two wide cards.
            // Every entry calls the same onNavigateTo(...)/permission check the old flat grid used.
            if (showSales) {
                BillingHeroCard(onClick = { onNavigateTo(AppRoute.Billing) })
            }

            if (showMasters || showPurchases || showReports) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Business Modules",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    if (showMasters) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ModuleTile("Category", "Groups", com.kadaikutty.pos.R.drawable.ic3d_category, Modifier.weight(1f)) { onOpenMasters(0) }
                            ModuleTile("Product", "Items & stock", com.kadaikutty.pos.R.drawable.ic3d_product, Modifier.weight(1f)) { onOpenMasters(1) }
                            ModuleTile("Ledger", "Customers", com.kadaikutty.pos.R.drawable.ic3d_ledger, Modifier.weight(1f)) { onOpenMasters(5) }
                        }
                    }
                    if (showPurchases || showReports) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (showPurchases) {
                                val low = dashboardState.lowStockCount
                                ModuleWideCard(
                                    title = "Purchase",
                                    subtitle = "Inward stock & suppliers",
                                    iconRes = com.kadaikutty.pos.R.drawable.ic3d_purchase,
                                    badge = if (low > 0) "$low Low" else null,
                                    badgeBg = Color(0xFFFEE2E2),
                                    badgeFg = Color(0xFFDC2626),
                                    modifier = Modifier.weight(1f)
                                ) { onNavigateTo(AppRoute.Purchases) }
                            }
                            if (showReports) {
                                ModuleWideCard(
                                    title = "Reports",
                                    subtitle = "Sales, bills & profit",
                                    iconRes = com.kadaikutty.pos.R.drawable.ic3d_reports,
                                    badge = "Live",
                                    badgeBg = Color(0xFFD1FAE5),
                                    badgeFg = Color(0xFF047857),
                                    modifier = Modifier.weight(1f)
                                ) { onNavigateTo(AppRoute.Reports) }
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dashboardState.recentSales.forEach { sale ->
                    Card(
                        onClick = { selectedBillNumForDetail = sale.billNumber },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(10.dp)
                            ),
                        shape = RoundedCornerShape(10.dp),
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
                                    color = com.kadaikutty.pos.core.ui.theme.paymentChipColors(sale.paymentMode).first,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = sale.paymentMode,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = com.kadaikutty.pos.core.ui.theme.paymentChipColors(sale.paymentMode).second,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }

            BrandFooter(modifier = Modifier.padding(top = 8.dp))

            Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding() + 24.dp))
        } // close the inner Column
        } // close the outer scrolling Column
    }
}

/** Big blue "Point of Sale" card with the 3D invoice art and a START NEW BILL button. */
@Composable
private fun BillingHeroCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF0A3FC4), Color(0xFF0B6BFF), Color(0xFF4FA3FF))))
            .clickable(onClickLabel = "Start new bill", onClick = onClick)
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(com.kadaikutty.pos.R.drawable.ic3d_billing),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 6.dp)
                .size(128.dp)
                .rotate(-6f)
        )
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Surface(color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(10.dp)) {
                Text(
                    "POINT OF SALE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("Billing", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text(
                "Create invoices, barcode scan & instant checkout",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(top = 3.dp).widthIn(max = 190.dp)
            )
            Spacer(Modifier.height(16.dp))
            // Looks like a button, but the whole card is the one tap target (set on the Box above),
            // so TalkBack reads a single "Start new bill" action instead of two.
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("START NEW BILL", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0A3FC4), modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF0A3FC4))
                }
            }
        }
    }
}

/** Small square module tile: 3D icon, bold title, grey caption. */
@Composable
private fun ModuleTile(
    title: String,
    subtitle: String,
    @androidx.annotation.DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(54.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Wide module card: 3D icon top-left, optional badge top-right, title + caption below. */
@Composable
private fun ModuleWideCard(
    title: String,
    subtitle: String,
    @androidx.annotation.DrawableRes iconRes: Int,
    badge: String?,
    badgeBg: Color,
    badgeFg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(60.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            if (badge != null) {
                Surface(color = badgeBg, shape = RoundedCornerShape(9.dp), modifier = Modifier.align(Alignment.TopEnd)) {
                    Text(badge, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = badgeFg, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
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
    onClick: (() -> Unit)? = null
) {
    Card(
        // Only tappable when it leads somewhere; a ripple that goes nowhere read as a broken button.
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = accentColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
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
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )

            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
            )
        }
    }
}

