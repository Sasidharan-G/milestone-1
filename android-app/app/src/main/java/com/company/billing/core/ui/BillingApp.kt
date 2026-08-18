package com.company.billing.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.company.billing.feature.billing.presentation.BillingScreen
import com.company.billing.feature.billing.presentation.BillingViewModel
import com.company.billing.feature.masters.presentation.*
import com.company.billing.feature.purchase.presentation.PurchaseScreen
import com.company.billing.feature.purchase.presentation.PurchaseViewModel
import com.company.billing.feature.reports.presentation.ReportsScreen
import com.company.billing.feature.reports.presentation.ReportsViewModel

val LocalLayoutMode = staticCompositionLocalOf { "Auto" }

@Composable
fun BillingApp() {
    val navController = rememberNavController()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val layoutMode by settingsViewModel.layoutMode.collectAsState()

    CompositionLocalProvider(LocalLayoutMode provides layoutMode) {
        MaterialTheme {
            NavHost(navController = navController, startDestination = AppRoute.Login.path) {
                composable(AppRoute.Login.path) {
                    val vm: LoginViewModel = hiltViewModel()
                    LoginScreen(viewModel = vm, onLoginSuccess = {
                        navController.navigate(AppRoute.Home.path) {
                            popUpTo(AppRoute.Login.path) { inclusive = true }
                        }
                    })
                }

                composable(AppRoute.Home.path) {
                    HomeScreen(
                        onNavigateTo = { route -> navController.navigate(route.path) },
                        onLogout = {
                            navController.navigate(AppRoute.Login.path) {
                                popUpTo(AppRoute.Home.path) { inclusive = true }
                            }
                        }
                    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateTo: (AppRoute) -> Unit,
    onLogout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Client Billing System", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                actions = {
                    IconButton(onClick = { onNavigateTo(AppRoute.Settings) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onPrimary)
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
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    DashboardCard(
                        title = "Master Data",
                        subtitle = "Manage Categories, Products, Customers, Suppliers, Expenses",
                        icon = Icons.Default.Menu,
                        modifier = Modifier.fillMaxWidth().height(130.dp),
                        onClick = { onNavigateTo(AppRoute.Masters) }
                    )

                    DashboardCard(
                        title = "Sales Invoicing",
                        subtitle = "Draft bills, invoice products and log sales ledger",
                        icon = Icons.Default.ShoppingCart,
                        modifier = Modifier.fillMaxWidth().height(130.dp),
                        onClick = { onNavigateTo(AppRoute.Billing) }
                    )

                    DashboardCard(
                        title = "Purchases & Stock",
                        subtitle = "Record stock inward, manage supplier invoices & ledger",
                        icon = Icons.Default.AddCircle,
                        modifier = Modifier.fillMaxWidth().height(130.dp),
                        onClick = { onNavigateTo(AppRoute.Purchases) }
                    )

                    DashboardCard(
                        title = "Reports Engine",
                        subtitle = "Analyze Sales, Stock, Profits, Purchases & Expenses",
                        icon = Icons.Default.List,
                        modifier = Modifier.fillMaxWidth().height(130.dp),
                        onClick = { onNavigateTo(AppRoute.Reports) }
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Text(
                        text = "Welcome to your Business Dashboard",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        DashboardCard(
                            title = "Master Data",
                            subtitle = "Manage Categories, Products, Customers, Suppliers, Expenses",
                            icon = Icons.Default.Menu,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = { onNavigateTo(AppRoute.Masters) }
                        )

                        DashboardCard(
                            title = "Sales Invoicing",
                            subtitle = "Draft bills, invoice products and log sales ledger",
                            icon = Icons.Default.ShoppingCart,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = { onNavigateTo(AppRoute.Billing) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        DashboardCard(
                            title = "Purchases & Stock",
                            subtitle = "Record stock inward, manage supplier invoices & ledger",
                            icon = Icons.Default.AddCircle,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = { onNavigateTo(AppRoute.Purchases) }
                        )

                        DashboardCard(
                            title = "Reports Engine",
                            subtitle = "Analyze Sales, Stock, Profits, Purchases & Expenses",
                            icon = Icons.Default.List,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = { onNavigateTo(AppRoute.Reports) }
                        )
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
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
