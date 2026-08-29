package com.kadaikutty.pos.feature.mastercontrol.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kadaikutty.pos.core.license.LicenseEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterControlScreen(
    viewModel: MasterControlViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var selectedShopForLicense by remember { mutableStateOf<LicenseEntity?>(null) }
    var selectedShopForRevoke by remember { mutableStateOf<LicenseEntity?>(null) }
    var showTrialConfirmDialog by remember { mutableStateOf<LicenseEntity?>(null) }

    LaunchedEffect(state.successMessage, state.errorMessage) {
        if (state.successMessage != null) {
            android.widget.Toast.makeText(context, state.successMessage, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
        if (state.errorMessage != null) {
            android.widget.Toast.makeText(context, state.errorMessage, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
    }

    val filteredList = remember(state.licenses, state.searchQuery, state.selectedFilter) {
        state.licenses.filter { license ->
            val matchesSearch = license.businessName.contains(state.searchQuery, ignoreCase = true) ||
                    license.ownerName.contains(state.searchQuery, ignoreCase = true) ||
                    license.ownerMobile.contains(state.searchQuery, ignoreCase = true) ||
                    license.companyId.contains(state.searchQuery, ignoreCase = true)

            val matchesFilter = when (state.selectedFilter) {
                "PENDING" -> license.licenseStatus == "PENDING_APPROVAL"
                "TRIAL" -> license.licenseStatus == "TRIAL" && !license.isExpired
                "ACTIVE" -> license.licenseStatus == "ACTIVE_PAID" && !license.isExpired
                "EXPIRING" -> license.isExpiringSoon
                "REVOKED" -> license.isExpired || license.licenseStatus == "REVOKED"
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("🛡️ Super Master Control", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFE11D48)
                            ) {
                                Text("SUPER ADMIN", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        }
                        Text("SaaS Direct Offline Licensing & Remote Control", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0F17))
                .padding(paddingValues)
        ) {
            // 1. Top 4-KPI Overview Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pending
                MasterKpiPill(
                    title = "Pending",
                    count = state.pendingCount,
                    accentColor = Color(0xFFEF4444),
                    modifier = Modifier.weight(1f),
                    isSelected = state.selectedFilter == "PENDING",
                    onClick = { viewModel.updateFilter(if (state.selectedFilter == "PENDING") "ALL" else "PENDING") }
                )
                // Trial (2 Days)
                MasterKpiPill(
                    title = "2d Trials",
                    count = state.activeTrialCount,
                    accentColor = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f),
                    isSelected = state.selectedFilter == "TRIAL",
                    onClick = { viewModel.updateFilter(if (state.selectedFilter == "TRIAL") "ALL" else "TRIAL") }
                )
                // Active Paid (365d+)
                MasterKpiPill(
                    title = "1yr Paid",
                    count = state.activePaidCount,
                    accentColor = Color(0xFF10B981),
                    modifier = Modifier.weight(1f),
                    isSelected = state.selectedFilter == "ACTIVE",
                    onClick = { viewModel.updateFilter(if (state.selectedFilter == "ACTIVE") "ALL" else "ACTIVE") }
                )
                // Expired / Cut
                MasterKpiPill(
                    title = "Expired",
                    count = state.expiredCount,
                    accentColor = Color(0xFF64748B),
                    modifier = Modifier.weight(1f),
                    isSelected = state.selectedFilter == "REVOKED",
                    onClick = { viewModel.updateFilter(if (state.selectedFilter == "REVOKED") "ALL" else "REVOKED") }
                )
            }

            // 2. Pending Approval Alert Banner (If Any)
            if (state.pendingCount > 0 && state.selectedFilter != "PENDING") {
                Surface(
                    color = Color(0xFF3B0711),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE11D48)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { viewModel.updateFilter("PENDING") }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFFFB7185))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🔔 ${state.pendingCount} New Shops Waiting For Approval!", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            Text("Click here to grant 2-Day Free Trial or 365-Day Full License.", fontSize = 11.sp, color = Color(0xFFFDA4AF))
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White)
                    }
                }
            }

            // 3. Search Bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search by Shop Name, Owner, Mobile (+91)...", color = Color(0xFF64748B), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
                trailingIcon = {
                    if (state.searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF162238),
                    unfocusedContainerColor = Color(0xFF162238),
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF334155)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // 4. Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    Pair("ALL", "All Shops (${state.licenses.size})"),
                    Pair("PENDING", "🔔 Pending (${state.pendingCount})"),
                    Pair("TRIAL", "🟡 2-Day Trial (${state.activeTrialCount})"),
                    Pair("ACTIVE", "🟢 1-Year Paid (${state.activePaidCount})"),
                    Pair("REVOKED", "🔴 Expired/Cut (${state.expiredCount})")
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = state.selectedFilter == key,
                        onClick = { viewModel.updateFilter(key) },
                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF2563EB),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF162238),
                            labelColor = Color(0xFF94A3B8)
                        )
                    )
                }
            }

            // 5. Shops List
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Storefront, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(48.dp))
                        Text("No businesses match this filter", color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredList, key = { it.companyId }) { license ->
                        ShopLicenseAdminCard(
                            license = license,
                            onGrantTrial = { showTrialConfirmDialog = license },
                            onGrantYearly = { selectedShopForLicense = license },
                            onRevoke = { selectedShopForRevoke = license },
                            onCallPhone = { phone ->
                                try {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {}
                            },
                            onWhatsApp = { phone ->
                                try {
                                    val clean = phone.filter { it.isDigit() }
                                    val url = "https://api.whatsapp.com/send?phone=+91$clean&text=Hello%20${Uri.encode(license.businessName)},%20regarding%20your%20KadaiKutty%20POS%20License:"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                } catch (e: Exception) {}
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal 1: 2-Day Trial Confirmation Dialog
    if (showTrialConfirmDialog != null) {
        val shop = showTrialConfirmDialog!!
        AlertDialog(
            onDismissRequest = { showTrialConfirmDialog = null },
            title = { Text("Approve 2-Day Free Trial", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Grant 48 Hours (2 Days) instant free trial access for:")
                    Text("🏪 ${shop.businessName}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("👤 Owner: ${shop.ownerName} (${shop.ownerMobile})")
                    Text("Duration will be calculated starting from now.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.approve2DayTrial(shop.companyId, shop.businessName)
                        showTrialConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                ) {
                    Text("Approve 2 Days", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTrialConfirmDialog = null }) { Text("Cancel") }
            }
        )
    }

    // Modal 2: Grant Paid License Dialog (How many years / custom days)
    if (selectedShopForLicense != null) {
        val shop = selectedShopForLicense!!
        var selectedYears by remember { mutableStateOf(1) }
        var isCustomDays by remember { mutableStateOf(false) }
        var customDaysInput by remember { mutableStateOf("30") }

        val format = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }
        val calculatedExpiry = remember(selectedYears, isCustomDays, customDaysInput) {
            val now = System.currentTimeMillis()
            val totalDays = if (isCustomDays) (customDaysInput.toIntOrNull() ?: 30) else (selectedYears * 365)
            val expiryMs = now + (totalDays.toLong() * 24 * 60 * 60 * 1000L)
            Pair(totalDays, format.format(Date(expiryMs)))
        }

        AlertDialog(
            onDismissRequest = { selectedShopForLicense = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF10B981))
                    Text("Grant Full License Access", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                ) {
                    Text("Business: ${shop.businessName}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Owner: ${shop.ownerName} (+91 ${shop.ownerMobile})", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    HorizontalDivider()

                    Text("Select License Duration (Starts Today):", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    // Year Selection Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1, 2, 3, 5).forEach { yr ->
                            FilterChip(
                                selected = !isCustomDays && selectedYears == yr,
                                onClick = {
                                    isCustomDays = false
                                    selectedYears = yr
                                },
                                label = { Text("$yr Year", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    FilterChip(
                        selected = isCustomDays,
                        onClick = { isCustomDays = true },
                        label = { Text("⚡ Custom Days", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (isCustomDays) {
                        OutlinedTextField(
                            value = customDaysInput,
                            onValueChange = { customDaysInput = it.filter { ch -> ch.isDigit() }.take(4) },
                            label = { Text("Enter Number of Days") },
                            placeholder = { Text("e.g. 30, 90, 180") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("🗓️ Access Duration: ${calculatedExpiry.first} Days", fontWeight = FontWeight.Bold, color = Color(0xFF047857), fontSize = 13.sp)
                            Text("⏰ Valid Until: ${calculatedExpiry.second}", fontWeight = FontWeight.Bold, color = Color(0xFF047857), fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isCustomDays) {
                            val days = customDaysInput.toIntOrNull() ?: 30
                            viewModel.grantCustomDaysLicense(shop.companyId, shop.businessName, days)
                        } else {
                            viewModel.grantYearlyLicense(shop.companyId, shop.businessName, selectedYears)
                        }
                        selectedShopForLicense = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Grant License Now", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedShopForLicense = null }) { Text("Cancel") }
            }
        )
    }

    // Modal 3: Revoke Access Confirmation Dialog
    if (selectedShopForRevoke != null) {
        val shop = selectedShopForRevoke!!
        AlertDialog(
            onDismissRequest = { selectedShopForRevoke = null },
            title = { Text("Revoke Business Access?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Are you sure you want to CUT access for:")
                    Text("🏪 ${shop.businessName}", fontWeight = FontWeight.Bold)
                    Text("Their terminal will be locked immediately and will display the contact renewal screen.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.revokeAccess(shop.companyId, shop.businessName)
                        selectedShopForRevoke = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Yes, Cut Access", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedShopForRevoke = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun MasterKpiPill(
    title: String,
    count: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) accentColor.copy(alpha = 0.2f) else Color(0xFF162238),
        border = androidx.compose.foundation.BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) accentColor else Color(0xFF334155)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "$count", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = accentColor)
            Text(text = title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
        }
    }
}

@Composable
fun ShopLicenseAdminCard(
    license: LicenseEntity,
    onGrantTrial: () -> Unit,
    onGrantYearly: () -> Unit,
    onRevoke: () -> Unit,
    onCallPhone: (String) -> Unit,
    onWhatsApp: (String) -> Unit
) {
    val format = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }
    val startDateStr = remember(license.activatedAtEpochMs) {
        if (license.activatedAtEpochMs > 0) format.format(Date(license.activatedAtEpochMs)) else "Not Activated"
    }
    val expiryDateStr = remember(license.validUntilEpochMs) {
        if (license.validUntilEpochMs > 0) format.format(Date(license.validUntilEpochMs)) else "Not Set"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111C2E)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
                license.licenseStatus == "PENDING_APPROVAL" -> Color(0xFFEF4444)
                license.isExpired || license.licenseStatus == "REVOKED" -> Color(0xFF475569)
                license.licenseStatus == "TRIAL" -> Color(0xFFF59E0B)
                else -> Color(0xFF10B981).copy(alpha = 0.5f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Shop Name & Status Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = license.businessName.ifBlank { "Store #${license.companyId.take(6)}" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF1E293B)
                        ) {
                            Text("ADMIN", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }
                    Text(
                        text = "👤 ${license.ownerName.ifBlank { "Owner" }} • 📱 +91 ${license.ownerMobile}",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                // Phone / WhatsApp Contact Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (license.ownerMobile.isNotBlank()) {
                        IconButton(onClick = { onCallPhone(license.ownerMobile) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Phone, contentDescription = "Call", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onWhatsApp(license.ownerMobile) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Chat, contentDescription = "WhatsApp", tint = Color(0xFF22C55E), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Status Badge & Dates
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = when {
                    license.licenseStatus == "PENDING_APPROVAL" -> Color(0xFF450A0A)
                    license.licenseStatus == "REVOKED" -> Color(0xFF1E293B)
                    license.isExpired -> Color(0xFF334155)
                    license.licenseStatus == "TRIAL" -> Color(0xFF451A03)
                    else -> Color(0xFF064E3B)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val icon = when {
                            license.licenseStatus == "PENDING_APPROVAL" -> Icons.Default.HourglassTop
                            license.isExpired || license.licenseStatus == "REVOKED" -> Icons.Default.Block
                            license.licenseStatus == "TRIAL" -> Icons.Default.Timer
                            else -> Icons.Default.CheckCircle
                        }
                        val statusText = when {
                            license.licenseStatus == "PENDING_APPROVAL" -> "PENDING MASTER APPROVAL"
                            license.licenseStatus == "REVOKED" -> "ACCESS REVOKED BY MASTER"
                            license.isExpired -> "EXPIRED"
                            license.licenseStatus == "TRIAL" -> "2-DAY TRIAL (${license.remainingHours}h Left)"
                            else -> "1-YEAR ACTIVE (${license.remainingDays}d Left)"
                        }
                        val statusColor = when {
                            license.licenseStatus == "PENDING_APPROVAL" -> Color(0xFFF87171)
                            license.licenseStatus == "REVOKED" || license.isExpired -> Color(0xFF94A3B8)
                            license.licenseStatus == "TRIAL" -> Color(0xFFFBBF24)
                            else -> Color(0xFF34D399)
                        }

                        Icon(icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(16.dp))
                        Text(statusText, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = statusColor)
                    }

                    if (license.validUntilEpochMs > 0) {
                        Text("Expires: $expiryDateStr", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }

            // Quick Actions Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 2-Day Trial Button
                Button(
                    onClick = onGrantTrial,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("⚡ 2d Trial", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                // 365 Days / Multi-Year License Button
                Button(
                    onClick = onGrantYearly,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Text("💎 Grant 1-Yr", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                // Cut / Revoke Button
                Button(
                    onClick = onRevoke,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(0.9f)
                ) {
                    Text("🚫 Cut", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
