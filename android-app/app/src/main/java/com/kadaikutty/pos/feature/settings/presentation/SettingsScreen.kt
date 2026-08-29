package com.kadaikutty.pos.feature.settings.presentation

import com.kadaikutty.pos.core.ui.LocalLayoutMode
import com.kadaikutty.pos.core.auth.UserEntity
import kotlinx.serialization.json.jsonPrimitive
import com.kadaikutty.pos.core.security.Permission
import com.kadaikutty.pos.core.security.BiometricAuthenticator
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import androidx.compose.ui.platform.LocalContext

import androidx.activity.compose.BackHandler
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import com.kadaikutty.pos.core.presentation.components.LoadingOverlay
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class SettingsCategory(val title: String, val icon: ImageVector, val shortSummary: String) {
    SHOP_PROFILE("Store Profile", Icons.Default.AccountBox, "Business Name, Tax ID, Address"),
    PRINTER("Hardware & Printer", Icons.Default.Build, "Thermal Receipt, Bluetooth & USB"),
    CLOUD_BACKUP("Cloud Synchronization", Icons.Default.Refresh, "Online Backup & Data Recovery"),
    STAFF("User Management", Icons.Default.AccountCircle, "Staff Logins & Role Permissions"),
    DISPLAY("Display & Interface", Icons.Default.Settings, "Layout Preference & Theme Mode"),
    MAINTENANCE("Database & Security", Icons.Default.Lock, "Local Archives & System Maintenance")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    var activeCategory by remember { mutableStateOf<SettingsCategory?>(null) }
    BackHandler(enabled = activeCategory != null) {
        activeCategory = null
    }

    val printerType by viewModel.printerType.collectAsState()
    val layoutModePref by viewModel.layoutMode.collectAsState()
    val printerDeviceId by viewModel.printerDeviceId.collectAsState()
    val printerPaperWidth by viewModel.printerPaperWidth.collectAsState()
    val bluetoothDevices by viewModel.bluetoothDevices.collectAsState()
    val printStatus by viewModel.printStatus.collectAsState()

    val backupStatus by viewModel.backupStatus.collectAsState()
    val restoreStatus by viewModel.restoreStatus.collectAsState()
    val isBackupRunning by viewModel.isBackupRunning.collectAsState()
    val isRestoreRunning by viewModel.isRestoreRunning.collectAsState()
    val requireRestart by viewModel.requireRestart.collectAsState()
    val biometricAuthPending by viewModel.biometricAuthPending.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedType by remember { mutableStateOf("Bluetooth") }
    var selectedDeviceId by remember { mutableStateOf("") }
    var selectedPaperWidth by remember { mutableStateOf(32) }
    var message by remember { mutableStateOf("") }

    if (requireRestart) {
        AlertDialog(
            onDismissRequest = { /* Force user to click OK */ },
            title = { Text("Restart Required") },
            text = { Text("Restore completed successfully. The application will now restart to apply changes.") },
            confirmButton = {
                TextButton(onClick = {
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    if (intent != null) {
                        context.startActivity(intent)
                    }
                    kotlin.system.exitProcess(0)
                }) {
                    Text("OK, Restart")
                }
            }
        )
    }

    // Sync state once preferences load
    LaunchedEffect(printerType, printerDeviceId, printerPaperWidth) {
        printerType?.let { selectedType = it }
        printerDeviceId?.let { selectedDeviceId = it }
        selectedPaperWidth = printerPaperWidth
    }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.runBackup(uri)
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.runRestore(uri) {
                // Restored successfully
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            viewModel.loadPairedBluetoothDevices()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        } else {
            viewModel.loadPairedBluetoothDevices()
        }
    }

    // Handle biometric authentication callbacks
    LaunchedEffect(biometricAuthPending) {
        biometricAuthPending?.let { onAuthenticated ->
            BiometricAuthenticator.authenticate(
                activity = context as androidx.fragment.app.FragmentActivity,
                onSuccess = {
                    onAuthenticated()
                    viewModel.clearBiometricAuthPending()
                },
                onError = { error ->
                    viewModel.clearBiometricAuthPending()
                }
            )
        }
    }

    val brandingShopName by viewModel.shopName.collectAsState()
    val brandingLogoPath by viewModel.shopLogoPath.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        Text(
                            text = brandingShopName.ifBlank { stringResource(com.kadaikutty.pos.R.string.settings_title) },
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { paddingValues ->
        LoadingOverlay(isLoading = isBackupRunning || isRestoreRunning, text = stringResource(com.kadaikutty.pos.R.string.please_wait))
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val session by viewModel.activeSession.collectAsState()
            val hasUserManagePermission = session?.permissions?.contains(com.kadaikutty.pos.core.security.Permission.USER_MANAGE) == true

            val layoutMode = LocalLayoutMode.current
            val isMobile = when (layoutMode) {
                "Mobile" -> true
                "Tablet" -> false
                else -> maxWidth < 600.dp
            }

            val firebaseCloudCard: @Composable (Modifier) -> Unit = { modifier ->
                val cloudSyncStatus by viewModel.cloudSyncStatus.collectAsState()

                Card(
                    modifier = modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(20.dp)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Cloud Backup & Sync", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Securely backup and synchronize your store's transaction database to cloud storage.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val currentSession = session
                        if (currentSession != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Signed-In User", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    Text(currentSession.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                var showRestoreDialog by remember { mutableStateOf(false) }

                                Button(
                                    onClick = {
                                        viewModel.forceSyncNow()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Backup Now", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        showRestoreDialog = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Restore", fontWeight = FontWeight.Bold)
                                }

                                if (showRestoreDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showRestoreDialog = false },
                                        title = { Text("Restore from Cloud?") },
                                        text = {
                                            Text("Are you sure you want to restore your data from cloud backup? This will replace your local database with cloud records.")
                                        },
                                        confirmButton = {
                                            TextButton(onClick = { 
                                                showRestoreDialog = false
                                                viewModel.runRestoreFromCloud { } 
                                            }) {
                                                Text("Yes, Restore", color = MaterialTheme.colorScheme.error)
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showRestoreDialog = false }) {
                                                Text("Cancel")
                                            }
                                        }
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Offline: Please log in to enable Cloud Backup & Sync.", fontSize = 13.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                            }
                        }

                        if (!cloudSyncStatus.isNullOrBlank()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Text(
                                    text = cloudSyncStatus!!,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(12.dp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            val layoutModeCard: @Composable (Modifier) -> Unit = { modifier ->
                Card(
                    modifier = modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(20.dp)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Display Layout Preferences", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Choose whether the app layout forces a mobile stacked view, a tablet split view, or adapts automatically to screen size.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        var currentLayoutMode by remember { mutableStateOf("Auto") }
                        
                        LaunchedEffect(layoutModePref) {
                            currentLayoutMode = layoutModePref
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = currentLayoutMode == "Auto", onClick = {
                                    currentLayoutMode = "Auto"
                                    viewModel.saveLayoutMode("Auto")
                                })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Auto Detect (Responsive)")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = currentLayoutMode == "Mobile", onClick = {
                                    currentLayoutMode = "Mobile"
                                    viewModel.saveLayoutMode("Mobile")
                                })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Mobile Mode (Force Stacked)")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = currentLayoutMode == "Tablet", onClick = {
                                    currentLayoutMode = "Tablet"
                                    viewModel.saveLayoutMode("Tablet")
                                })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Tablet Mode (Force Side-by-Side)")
                            }
                        }
                    }
                }
            }

            val themePreferencesCard: @Composable (Modifier) -> Unit = { modifier ->
                val themeModePref by viewModel.themeMode.collectAsState()
                
                Card(
                    modifier = modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(20.dp)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("App Theme Preferences", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Choose whether the app uses a light theme, dark theme, or matches the system setting dynamically.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        var currentThemeMode by remember { mutableStateOf("System") }
                        
                        LaunchedEffect(themeModePref) {
                            currentThemeMode = themeModePref
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = currentThemeMode == "System", onClick = {
                                    currentThemeMode = "System"
                                    viewModel.saveThemeMode("System")
                                })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("System Default (Auto)")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = currentThemeMode == "Light", onClick = {
                                    currentThemeMode = "Light"
                                    viewModel.saveThemeMode("Light")
                                })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Light Mode")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = currentThemeMode == "Dark", onClick = {
                                    currentThemeMode = "Dark"
                                    viewModel.saveThemeMode("Dark")
                                })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Dark Mode")
                            }
                        }
                    }
                }
            }

            val printerPreferencesCard: @Composable (Modifier) -> Unit = { modifier ->
                Card(
                    modifier = modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(20.dp)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Printer Driver Preferences", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                        Text("Connection Type:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedType == "Bluetooth", onClick = { selectedType = "Bluetooth" })
                                Text("Bluetooth")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedType == "Usb", onClick = { selectedType = "Usb" })
                                Text("USB Printer")
                            }
                        }

                        HorizontalDivider()

                        Text("Paper Layout Size:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedPaperWidth == 32, onClick = { selectedPaperWidth = 32 })
                                Text("58 mm (32 chars)")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedPaperWidth == 48, onClick = { selectedPaperWidth = 48 })
                                Text("80 mm (48 chars)")
                            }
                        }

                        HorizontalDivider()

                        Text("Target Printer Address / ID:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        if (selectedType == "Bluetooth") {
                            var expanded by remember { mutableStateOf(false) }
                            val activeDeviceName = bluetoothDevices.find { it.address == selectedDeviceId }?.name ?: selectedDeviceId.ifBlank { "Select Paired Device" }
                            
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = !expanded }
                            ) {
                                OutlinedTextField(
                                    readOnly = true,
                                    value = activeDeviceName,
                                    onValueChange = {},
                                    label = { Text("Bluetooth Device") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    bluetoothDevices.forEach { device ->
                                        DropdownMenuItem(
                                            text = { Text("${device.name} (${device.address})") },
                                            onClick = {
                                                selectedDeviceId = device.address
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = selectedDeviceId,
                                onValueChange = { selectedDeviceId = it },
                                label = { Text("USB Target Name / Path") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.saveSettings(selectedType, selectedDeviceId, selectedPaperWidth)
                                message = "Settings saved successfully!"
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save Hardware Preferences", fontWeight = FontWeight.Bold)
                        }

                        if (message.isNotBlank()) {
                            Text(message, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            val printerDiagnosticsCard: @Composable (Modifier) -> Unit = { modifier ->
                Card(
                    modifier = modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(20.dp)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Hardware Printing Diagnostics", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth())

                        Icon(
                            Icons.Default.Build,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp).padding(top = 16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )

                        Text(
                            "Verify physical print output by dispatching a standard ESC/POS ticket payload to your connected hardware.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Button(
                            onClick = {
                                viewModel.printTestReceipt { result ->
                                    message = result
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                        ) {
                            Text("Print Test Receipt", fontWeight = FontWeight.Bold)
                        }

                        if (!printStatus.isNullOrBlank()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                    Text("Status: $printStatus", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }

            val userManagementCard: @Composable (Modifier) -> Unit = { modifier ->
                val users by viewModel.usersList.collectAsState()

                var showAddDialog by remember { mutableStateOf(false) }
                var showEditDialog by remember { mutableStateOf(false) }
                var selectedUser by remember { mutableStateOf<com.kadaikutty.pos.core.auth.UserEntity?>(null) }

                Card(
                    modifier = modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(20.dp)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Staff & Cashier Management",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Create cashier logins using mobile numbers, configure role-based permissions, and manage terminal accounts.",
                                    fontSize = 12.sp,
                                    lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (users.isEmpty()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(40.dp))
                                        Text("No staff users created yet", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Add your cashiers and store managers below with custom access.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                    }
                                }
                            } else {
                                users.forEach { user ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                            .clickable {
                                                selectedUser = user
                                                showEditDialog = true
                                            },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = when (user.role.uppercase()) {
                                                        "STORE_MANAGER" -> Color(0xFFF5F3FF)
                                                        "ADMIN" -> Color(0xFFFEF3C7)
                                                        else -> Color(0xFFEFF6FF)
                                                    },
                                                    modifier = Modifier.size(44.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.Person,
                                                            contentDescription = null,
                                                            tint = when (user.role.uppercase()) {
                                                                "STORE_MANAGER" -> Color(0xFF7C3AED)
                                                                "ADMIN" -> Color(0xFFD97706)
                                                                else -> Color(0xFF2563EB)
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(user.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = when (user.role.uppercase()) {
                                                                "STORE_MANAGER" -> Color(0xFFEDE9FE)
                                                                "ADMIN" -> Color(0xFFFEF3C7)
                                                                else -> Color(0xFFDBEAFE)
                                                            }
                                                        ) {
                                                            Text(
                                                                text = when (user.role.uppercase()) {
                                                                    "STORE_MANAGER" -> "MANAGER"
                                                                    else -> user.role.uppercase()
                                                                },
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                color = when (user.role.uppercase()) {
                                                                    "STORE_MANAGER" -> Color(0xFF6D28D9)
                                                                    "ADMIN" -> Color(0xFFB45309)
                                                                    else -> Color(0xFF1D4ED8)
                                                                },
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "📱 Login ID: +91 ${user.username}",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            IconButton(
                                                onClick = {
                                                    selectedUser = user
                                                    showEditDialog = true
                                                }
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit User", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add New Staff / Cashier", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Render dialogs for adding/editing users
                if (showAddDialog) {
                    AddUserDialog(
                        onDismiss = { showAddDialog = false },
                        onCreate = { phone, name, pass, role, perms ->
                            viewModel.createUser(phone, name, pass, role, perms) { success, msg ->
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                if (success) {
                                    showAddDialog = false
                                }
                            }
                        }
                    )
                }

                if (showEditDialog && selectedUser != null) {
                    EditUserDialog(
                        user = selectedUser!!,
                        onDismiss = { showEditDialog = false },
                        onSave = { name, role, pass, perms ->
                            viewModel.updateUserCredentials(selectedUser!!.id, name, role, pass, perms) { success, msg ->
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                if (success) {
                                    showEditDialog = false
                                }
                            }
                        },
                        onDelete = {
                            viewModel.deleteUser(selectedUser!!.id) { success, msg ->
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                if (success) {
                                    showEditDialog = false
                                }
                            }
                        }
                    )
                }
            }



            val dbMaintenanceCard: @Composable (Modifier) -> Unit = { modifier ->
                Card(
                    modifier = modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(20.dp)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Database Maintenance & Backup", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Create a local, transaction-safe compressed backup archive of your billing database. You can restore this backup on this or other devices to recover transactions.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val backupStatus by viewModel.backupStatus.collectAsState()
                        val restoreStatus by viewModel.restoreStatus.collectAsState()

                        var showLocalRestoreDialog by remember { mutableStateOf(false) }

                        var showClearDatabaseDialog by remember { mutableStateOf(false) }
                        var clearCloudOption by remember { mutableStateOf(false) }

                        if (isMobile) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = {
                                        createBackupLauncher.launch("billing_backup_${System.currentTimeMillis()}.zip")
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Create Backup Archive", fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { showLocalRestoreDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Text("Restore Backup Archive", fontWeight = FontWeight.Bold)
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                                OutlinedButton(
                                    onClick = {
                                        viewModel.loadDemoSampleData { resultMsg ->
                                            android.widget.Toast.makeText(context, resultMsg, android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Load 100 Demo Retail Records", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { showClearDatabaseDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Reset / Clear Database", fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            createBackupLauncher.launch("billing_backup_${System.currentTimeMillis()}.zip")
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Create Backup Archive", fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { showLocalRestoreDialog = true },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                    ) {
                                        Text("Restore Backup Archive", fontWeight = FontWeight.Bold)
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.loadDemoSampleData { resultMsg ->
                                                android.widget.Toast.makeText(context, resultMsg, android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Load 100 Demo Records", fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { showClearDatabaseDialog = true },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Reset Database", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        if (showClearDatabaseDialog) {
                            AlertDialog(
                                onDismissRequest = { showClearDatabaseDialog = false },
                                title = { Text("Reset Database Records?") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("This will safely delete all local Products, Customers, Sales, and Inward Stock records so you can start fresh.")
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth().clickable { clearCloudOption = !clearCloudOption }
                                        ) {
                                            Checkbox(
                                                checked = clearCloudOption,
                                                onCheckedChange = { clearCloudOption = it }
                                            )
                                            Text("Also clear Cloud Sync data on Firestore", fontSize = 13.sp)
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showClearDatabaseDialog = false
                                        viewModel.clearAllDatabase(clearCloudOption) { success ->
                                            val msg = if (success) "Database cleared successfully!" else "Failed to clear database."
                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Text("Yes, Clear Data", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showClearDatabaseDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        if (showLocalRestoreDialog) {
                            AlertDialog(
                                onDismissRequest = { showLocalRestoreDialog = false },
                                title = { Text("Restore Local Backup?") },
                                text = { Text("Are you sure you want to restore from a local backup file? This will completely overwrite your current database.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showLocalRestoreDialog = false
                                        restoreLauncher.launch("application/zip")
                                    }) {
                                        Text("Yes, Restore", color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showLocalRestoreDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        if (!backupStatus.isNullOrBlank() || !restoreStatus.isNullOrBlank()) {
                            val statusMsg = backupStatus ?: restoreStatus
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                    Text("Maintenance: $statusMsg", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }

            val shopDetailsCard: @Composable (Modifier) -> Unit = { modifier ->
                val context = LocalContext.current
                val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                val currentShopName by viewModel.shopName.collectAsState()
                val currentOwnerName by viewModel.ownerName.collectAsState()
                val currentGstNumber by viewModel.gstNumber.collectAsState()
                val currentShopAddress by viewModel.shopAddress.collectAsState()
                val currentShopPhone by viewModel.shopPhone.collectAsState()
                val currentShopEmail by viewModel.shopEmail.collectAsState()
                val currentShopLogoPath by viewModel.shopLogoPath.collectAsState()

                var inputShopName by remember { mutableStateOf("") }
                var inputOwnerName by remember { mutableStateOf("") }
                var inputGstNumber by remember { mutableStateOf("") }
                var inputShopAddress by remember { mutableStateOf("") }
                var inputShopPhone by remember { mutableStateOf("") }
                var inputShopEmail by remember { mutableStateOf("") }
                var inputShopLogoPath by remember { mutableStateOf("") }

                var isProcessingImage by remember { mutableStateOf(false) }

                LaunchedEffect(currentShopName, currentOwnerName, currentGstNumber, currentShopAddress, currentShopPhone, currentShopEmail, currentShopLogoPath) {
                    inputShopName = currentShopName
                    inputOwnerName = currentOwnerName
                    inputGstNumber = currentGstNumber
                    inputShopAddress = currentShopAddress
                    inputShopPhone = currentShopPhone
                    inputShopEmail = currentShopEmail
                    inputShopLogoPath = currentShopLogoPath
                }

                val logoPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: android.net.Uri? ->
                    if (uri != null) {
                        val type = context.contentResolver.getType(uri)
                        if (type != "image/png" && type != "image/jpeg" && type != "image/jpg") {
                            android.widget.Toast.makeText(context, "Invalid File Type: Only PNG, JPG, or JPEG images are accepted!", android.widget.Toast.LENGTH_LONG).show()
                            return@rememberLauncherForActivityResult
                        }

                        isProcessingImage = true
                        val res = viewModel.processAndSaveLogo(context, uri)
                        isProcessingImage = false

                        if (res == "SIZE_LIMIT_EXCEEDED") {
                            android.widget.Toast.makeText(context, "Oversized Image: Maximum limit is 5MB!", android.widget.Toast.LENGTH_LONG).show()
                        } else if (res != null) {
                            inputShopLogoPath = res
                            android.widget.Toast.makeText(context, "Logo processed successfully. Preview below!", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "Image Processing Failure: Failed to load image.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }

                val isEmailValid = remember(inputShopEmail) {
                    inputShopEmail.isEmpty() || android.util.Patterns.EMAIL_ADDRESS.matcher(inputShopEmail).matches()
                }

                val logoFile = File(inputShopLogoPath)
                val bitmap = remember(inputShopLogoPath) {
                    if (logoFile.exists()) {
                        try {
                            BitmapFactory.decodeFile(logoFile.absolutePath)
                        } catch (e: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                }

                Card(
                    modifier = modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(20.dp)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Shop Details Customization", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Configure your shop details, email contact, and brand logo. These details will be printed on all generated PDF invoices.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = inputShopName,
                            onValueChange = { inputShopName = it },
                            label = { Text("Shop Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = inputOwnerName,
                            onValueChange = { inputOwnerName = it },
                            label = { Text("Owner Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = inputGstNumber,
                            onValueChange = { inputGstNumber = it },
                            label = { Text("GST Number") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = inputShopAddress,
                            onValueChange = { inputShopAddress = it },
                            label = { Text("Shop Address") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            maxLines = 3,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = inputShopPhone,
                            onValueChange = { inputShopPhone = it },
                            label = { Text("Shop Phone Number") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = inputShopEmail,
                            onValueChange = { inputShopEmail = it },
                            label = { Text("Shop Email ID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = !isEmailValid,
                            supportingText = {
                                if (!isEmailValid) {
                                    Text("Invalid email format (e.g. shop@email.com)", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Logo upload section
                        Text("Shop Brand Logo", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { logoPickerLauncher.launch("image/*") },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Upload Logo")
                            }

                            if (isProcessingImage) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }

                        if (inputShopLogoPath.isNotEmpty() && bitmap != null) {
                            Text("Logo & App Icon Preview Mockup", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text("Original", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Original logo preview",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text("Compressed", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Compressed logo preview",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text("App Icon", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    Spacer(Modifier.height(4.dp))
                                    Card(
                                        modifier = Modifier.size(72.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = "App launcher icon mockup",
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }

                            TextButton(
                                onClick = {
                                    inputShopLogoPath = ""
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Remove Logo")
                            }
                        }

                        Button(
                            onClick = {
                                val gstTrimmed = inputGstNumber.trim()
                                if (gstTrimmed.isNotEmpty() && gstTrimmed.length != 15) {
                                    android.widget.Toast.makeText(context, "Validation Error: GST Number must be exactly 15 characters!", android.widget.Toast.LENGTH_LONG).show()
                                } else if (!isEmailValid) {
                                    android.widget.Toast.makeText(context, "Validation Error: Please enter a valid Email ID!", android.widget.Toast.LENGTH_LONG).show()
                                } else {
                                    viewModel.saveShopDetails(inputShopName, inputOwnerName, gstTrimmed, inputShopAddress, inputShopPhone, inputShopEmail, inputShopLogoPath)
                                    keyboardController?.hide()
                                    android.widget.Toast.makeText(context, "Shop details saved successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.align(Alignment.End),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save Shop Details", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (activeCategory == null) {
                    val categories = remember(hasUserManagePermission) {
                        listOfNotNull(
                            SettingsCategory.SHOP_PROFILE,
                            SettingsCategory.PRINTER,
                            SettingsCategory.CLOUD_BACKUP,
                            if (hasUserManagePermission) SettingsCategory.STAFF else null,
                            SettingsCategory.DISPLAY,
                            SettingsCategory.MAINTENANCE
                        )
                    }

                    val columns = if (isMobile) 2 else 3
                    val rows = categories.chunked(columns)

                    rows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            rowItems.forEach { cat ->
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(140.dp)
                                        .clickable(
                                            onClickLabel = "Open ${cat.title} category",
                                            onClick = { activeCategory = cat }
                                        )
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
                                            .fillMaxSize()
                                            .padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = cat.icon,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = cat.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = cat.shortSummary,
                                            fontSize = 10.sp,
                                            lineHeight = 13.sp,
                                            maxLines = 2,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                            if (rowItems.size < columns) {
                                repeat(columns - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = { activeCategory = null }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Go back to settings dashboard"
                            )
                        }
                        Text(
                            text = activeCategory!!.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    when (activeCategory) {
                        SettingsCategory.SHOP_PROFILE -> {
                            shopDetailsCard(Modifier.fillMaxWidth())
                        }
                        SettingsCategory.PRINTER -> {
                            printerPreferencesCard(Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(16.dp))
                            printerDiagnosticsCard(Modifier.fillMaxWidth())
                        }
                        SettingsCategory.CLOUD_BACKUP -> {
                            firebaseCloudCard(Modifier.fillMaxWidth())
                        }
                        SettingsCategory.STAFF -> {
                            userManagementCard(Modifier.fillMaxWidth())
                        }
                        SettingsCategory.DISPLAY -> {
                            layoutModeCard(Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(16.dp))
                            themePreferencesCard(Modifier.fillMaxWidth())
                        }
                        SettingsCategory.MAINTENANCE -> {
                            dbMaintenanceCard(Modifier.fillMaxWidth())
                        }
                        null -> {}
                    }
                }
            }
        }
    }
}

@Composable
fun AddUserDialog(
    onDismiss: () -> Unit,
    onCreate: (phone: String, displayName: String, password: CharArray, role: String, permissions: Set<Permission>) -> Unit
) {
    var phone by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    var selectedRole by remember { mutableStateOf("CASHIER") }

    var accessBilling by remember { mutableStateOf(true) }
    var accessMasters by remember { mutableStateOf(true) }
    var accessPurchases by remember { mutableStateOf(false) }
    var accessReports by remember { mutableStateOf(false) }
    var accessSettings by remember { mutableStateOf(false) }

    var errorMsg by remember { mutableStateOf("") }

    fun applyRoleDefaults(role: String) {
        selectedRole = role
        when (role) {
            "CASHIER" -> {
                accessBilling = true
                accessMasters = true
                accessPurchases = false
                accessReports = false
                accessSettings = false
            }
            "STORE_MANAGER" -> {
                accessBilling = true
                accessMasters = true
                accessPurchases = true
                accessReports = true
                accessSettings = false
            }
            "INWARD_CLERK" -> {
                accessBilling = false
                accessMasters = true
                accessPurchases = true
                accessReports = false
                accessSettings = false
            }
            "ADMIN" -> {
                accessBilling = true
                accessMasters = true
                accessPurchases = true
                accessReports = true
                accessSettings = true
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Add New Cashier / Staff", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Assign login credentials and screen access privileges for this terminal user.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Cashier Name
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Staff / Cashier Name *") },
                    placeholder = { Text("e.g. Ramesh Kumar") },
                    leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Mobile Number Login ID
                OutlinedTextField(
                    value = phone,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(10)
                        phone = digits
                    },
                    label = { Text("Mobile Number (Login User ID) *") },
                    placeholder = { Text("10-digit mobile number") },
                    leadingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 8.dp, end = 4.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("+91", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Password / PIN
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Security Password / 4-6 Digit PIN *") },
                    placeholder = { Text("Enter terminal login password or PIN") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) "HIDE" else "SHOW", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))

                // Role Presets
                Text("Select Staff Role Preset:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedRole == "CASHIER",
                        onClick = { applyRoleDefaults("CASHIER") },
                        label = { Text("🛒 Cashier", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(16.dp)
                    )
                    FilterChip(
                        selected = selectedRole == "STORE_MANAGER",
                        onClick = { applyRoleDefaults("STORE_MANAGER") },
                        label = { Text("📦 Manager", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(16.dp)
                    )
                    FilterChip(
                        selected = selectedRole == "INWARD_CLERK",
                        onClick = { applyRoleDefaults("INWARD_CLERK") },
                        label = { Text("🚚 Stock Inward", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(16.dp)
                    )
                    FilterChip(
                        selected = selectedRole == "CUSTOM",
                        onClick = { selectedRole = "CUSTOM" },
                        label = { Text("⚙️ Custom", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                Spacer(Modifier.height(4.dp))
                Text("Custom Screen Permissions:", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = accessBilling, onCheckedChange = { accessBilling = it; selectedRole = "CUSTOM" })
                            Text("🛒 Point of Sale Billing & Checkout", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = accessPurchases, onCheckedChange = { accessPurchases = it; selectedRole = "CUSTOM" })
                            Text("📦 Inventory & Inward Stock Purchases", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = accessMasters, onCheckedChange = { accessMasters = it; selectedRole = "CUSTOM" })
                            Text("🗂️ Master Catalog (Products & Pricing)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = accessReports, onCheckedChange = { accessReports = it; selectedRole = "CUSTOM" })
                            Text("📊 Business Reports & Profit Analytics", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = accessSettings, onCheckedChange = { accessSettings = it; selectedRole = "CUSTOM" })
                            Text("⚙️ Store Settings & Printer Setup", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                if (errorMsg.isNotBlank()) {
                    Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanDigits = phone.filter { it.isDigit() }
                    if (displayName.isBlank() || cleanDigits.isBlank() || password.isBlank()) {
                        errorMsg = "Please fill in all mandatory fields"
                    } else if (cleanDigits.length < 10) {
                        errorMsg = "Please enter a valid 10-digit mobile number"
                    } else if (password.length < 4) {
                        errorMsg = "Password / PIN must be at least 4 characters"
                    } else {
                        val pSet = buildSet {
                            if (accessMasters) {
                                addAll(listOf(Permission.CATEGORY_VIEW, Permission.CATEGORY_CREATE, Permission.CATEGORY_EDIT, Permission.PRODUCT_VIEW, Permission.PRODUCT_CREATE, Permission.PRODUCT_EDIT))
                            }
                            if (accessBilling) {
                                addAll(listOf(Permission.SALE_CREATE, Permission.SALE_VIEW))
                            }
                            if (accessPurchases) {
                                addAll(listOf(Permission.PURCHASE_CREATE, Permission.PURCHASE_VIEW))
                            }
                            if (accessReports) {
                                addAll(listOf(Permission.REPORT_SALES, Permission.REPORT_STOCK, Permission.REPORT_PROFIT))
                            }
                            if (accessSettings) {
                                addAll(listOf(Permission.SETTINGS_VIEW, Permission.SETTINGS_EDIT, Permission.BACKUP_CREATE))
                            }
                        }
                        onCreate(cleanDigits, displayName.trim(), password.toCharArray(), selectedRole, pSet)
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Create Staff Account", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditUserDialog(
    user: UserEntity,
    onDismiss: () -> Unit,
    onSave: (displayName: String?, role: String?, newPassword: CharArray?, permissions: Set<Permission>) -> Unit,
    onDelete: () -> Unit
) {
    var displayName by remember { mutableStateOf(user.displayName) }
    var selectedRole by remember { mutableStateOf(user.role.ifBlank { "CASHIER" }) }
    var newPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val initialPerms = remember(user.permissions) { user.toPermissionsSet() }

    var accessBilling by remember { mutableStateOf(initialPerms.contains(Permission.SALE_CREATE)) }
    var accessMasters by remember { mutableStateOf(initialPerms.contains(Permission.PRODUCT_VIEW)) }
    var accessPurchases by remember { mutableStateOf(initialPerms.contains(Permission.PURCHASE_CREATE)) }
    var accessReports by remember { mutableStateOf(initialPerms.contains(Permission.REPORT_SALES)) }
    var accessSettings by remember { mutableStateOf(initialPerms.contains(Permission.SETTINGS_VIEW)) }

    fun applyRoleDefaults(role: String) {
        selectedRole = role
        when (role) {
            "CASHIER" -> {
                accessBilling = true
                accessMasters = true
                accessPurchases = false
                accessReports = false
                accessSettings = false
            }
            "STORE_MANAGER" -> {
                accessBilling = true
                accessMasters = true
                accessPurchases = true
                accessReports = true
                accessSettings = false
            }
            "INWARD_CLERK" -> {
                accessBilling = false
                accessMasters = true
                accessPurchases = true
                accessReports = false
                accessSettings = false
            }
            "ADMIN" -> {
                accessBilling = true
                accessMasters = true
                accessPurchases = true
                accessReports = true
                accessSettings = true
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Edit Staff Account", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Column {
                            Text("Mobile Login User ID (Fixed)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("+91 ${user.username}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // Name
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Staff Full Name") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Password Reset
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Reset Password / PIN") },
                    placeholder = { Text("Leave blank to keep current") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) "HIDE" else "SHOW", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Role Presets
                Text("Role Preset:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedRole == "CASHIER",
                        onClick = { applyRoleDefaults("CASHIER") },
                        label = { Text("🛒 Cashier", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(16.dp)
                    )
                    FilterChip(
                        selected = selectedRole == "STORE_MANAGER",
                        onClick = { applyRoleDefaults("STORE_MANAGER") },
                        label = { Text("📦 Manager", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(16.dp)
                    )
                    FilterChip(
                        selected = selectedRole == "INWARD_CLERK",
                        onClick = { applyRoleDefaults("INWARD_CLERK") },
                        label = { Text("🚚 Stock Inward", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(16.dp)
                    )
                    FilterChip(
                        selected = selectedRole == "CUSTOM",
                        onClick = { selectedRole = "CUSTOM" },
                        label = { Text("⚙️ Custom", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                Spacer(Modifier.height(4.dp))
                Text("Custom Screen Permissions:", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = accessBilling, onCheckedChange = { accessBilling = it; selectedRole = "CUSTOM" })
                            Text("🛒 Point of Sale Billing & Checkout", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = accessPurchases, onCheckedChange = { accessPurchases = it; selectedRole = "CUSTOM" })
                            Text("📦 Inventory & Inward Stock Purchases", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = accessMasters, onCheckedChange = { accessMasters = it; selectedRole = "CUSTOM" })
                            Text("🗂️ Master Catalog (Products & Pricing)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = accessReports, onCheckedChange = { accessReports = it; selectedRole = "CUSTOM" })
                            Text("📊 Business Reports & Profit Analytics", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = accessSettings, onCheckedChange = { accessSettings = it; selectedRole = "CUSTOM" })
                            Text("⚙️ Store Settings & Printer Setup", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Delete")
                }
                Button(
                    onClick = {
                        val pSet = buildSet {
                            if (accessMasters) {
                                addAll(listOf(Permission.CATEGORY_VIEW, Permission.CATEGORY_CREATE, Permission.CATEGORY_EDIT, Permission.PRODUCT_VIEW, Permission.PRODUCT_CREATE, Permission.PRODUCT_EDIT))
                            }
                            if (accessBilling) {
                                addAll(listOf(Permission.SALE_CREATE, Permission.SALE_VIEW))
                            }
                            if (accessPurchases) {
                                addAll(listOf(Permission.PURCHASE_CREATE, Permission.PURCHASE_VIEW))
                            }
                            if (accessReports) {
                                addAll(listOf(Permission.REPORT_SALES, Permission.REPORT_STOCK, Permission.REPORT_PROFIT))
                            }
                            if (accessSettings) {
                                addAll(listOf(Permission.SETTINGS_VIEW, Permission.SETTINGS_EDIT, Permission.BACKUP_CREATE))
                            }
                        }
                        val passArray = if (newPassword.isBlank()) null else newPassword.toCharArray()
                        onSave(displayName.trim(), selectedRole, passArray, pSet)
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save Changes")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
