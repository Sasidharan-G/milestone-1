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
        // Same charcoal as the launch splash, so the white status bar icons stay readable while loading.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0C1018)),
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
                    // Small "Synced" pill stays top-start so it never covers the sync / lock / settings icons at top-end.
                    Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 4.dp)
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
    onNavigateTo: (AppRoute) -> Unit,
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
                // signal/wifi/battery icons and hide them; a charcoal strip keeps them readable.
                Box(modifier = Modifier.fillMaxWidth().height(topInset).background(Color(0xFF0C1018)))
                // 1. Charcoal banner with bottom-rounded corners
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(brandHeaderShape)
                        .background(Color(0xFF0C1018))
                        .padding(top = topInset + 20.dp)
                        .padding(bottom = 20.dp)
                ) {
                    Text(
                        text = shopName.ifBlank { "Kadaikutty POS" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = Color.White,
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
                    IconButton(onClick = {
                        viewModel.triggerCloudSync()
                        Toast.makeText(context, "Cloud sync triggered...", Toast.LENGTH_SHORT).show()
                    }) {
                        val syncIconModifier = if (dashboardState.isSyncing) Modifier.rotate(rotationAngle) else Modifier
                        // These three icons sit on the fixed charcoal header, not a themed surface -
                        // MaterialTheme.colorScheme.primary is that exact same charcoal in light mode,
                        // which made the icons nearly invisible there. White reads on the header in
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
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {

            // Plain sales-figures table - easy to read at a glance, no reading of chart/graph
            // needed, matches how the client's reference app shows this.
            if (showSales) {
                Card(
                    modifier = Modifier.fillMaxWidth().offset(y = (-28).dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        SalesSummaryRow("Today Sale", dashboardState.todaySalesMinorUnits, dashboardState.todayInvoicesCount)
                        SalesSummaryRow("Yesterday Sale", dashboardState.yesterdaySalesMinorUnits, dashboardState.yesterdayInvoicesCount)
                        SalesSummaryRow("Weekly Sale", dashboardState.weeklySalesMinorUnits, dashboardState.weeklyInvoicesCount)
                        SalesSummaryRow("Monthly Sale", dashboardState.monthlySalesMinorUnits, dashboardState.monthlyInvoicesCount, isLast = true)
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

            // 2. Big-icon menu grid - every destination one tap away, label under a coloured
            // icon so the screen reads even for a shopkeeper who isn't comfortable with English
            // text alone. Every tile below calls the exact same onNavigateTo(...)/permission
            // check the old bottom-bar/FAB/hero-card used - only the layout changed.
            val extendedColors = com.kadaikutty.pos.core.ui.theme.LocalExtendedColors.current
            data class MenuTile(val label: String, val icon: ImageVector, val container: Color, val onContainer: Color, val onClick: () -> Unit)
            val menuTiles = buildList {
                if (showSales) add(MenuTile("Billing", Icons.Default.PointOfSale, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) { onNavigateTo(AppRoute.Billing) })
                if (showMasters) add(MenuTile("Category", Icons.Default.Category, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) { onNavigateTo(AppRoute.Masters) })
                if (showMasters) add(MenuTile("Product", Icons.Default.Inventory2, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) { onNavigateTo(AppRoute.Masters) })
                if (showMasters) add(MenuTile("Ledger", Icons.Default.MenuBook, extendedColors.ledgerContainer, extendedColors.onLedgerContainer) { onNavigateTo(AppRoute.Masters) })
                if (showPurchases) add(MenuTile("Purchase", Icons.Default.LocalShipping, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) { onNavigateTo(AppRoute.Purchases) })
                if (showReports) add(MenuTile("Reports", Icons.Default.BarChart, extendedColors.reportsContainer, extendedColors.onReportsContainer) { onNavigateTo(AppRoute.Reports) })
                if (showSettings) add(MenuTile("Settings", Icons.Default.Settings, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant) { onNavigateTo(AppRoute.Settings) })
            }

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                menuTiles.chunked(3).forEach { rowTiles ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        rowTiles.forEach { tile ->
                            DashboardTileCard(
                                title = tile.label,
                                icon = tile.icon,
                                iconContainerColor = tile.container,
                                iconTint = tile.onContainer,
                                modifier = Modifier.width(100.dp),
                                onClick = tile.onClick
                            )
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

            Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding() + 24.dp))
        } // close the inner Column
        } // close the outer scrolling Column
    }
}

/** One row of the plain Today/Yesterday/Weekly/Monthly sales table - "amount - N bills". */
@Composable
private fun SalesSummaryRow(label: String, amountMinorUnits: Long, invoiceCount: Int, isLast: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = "${Money(amountMinorUnits)}  •  $invoiceCount",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (!isLast) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
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
