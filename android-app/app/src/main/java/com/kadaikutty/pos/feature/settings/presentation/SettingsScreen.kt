package com.kadaikutty.pos.feature.settings.presentation

import com.kadaikutty.pos.core.ui.LocalLayoutMode
import com.kadaikutty.pos.core.auth.UserEntity
import com.kadaikutty.pos.core.security.Permission
import com.kadaikutty.pos.core.security.BiometricAuthenticator
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.platform.LocalContext

import androidx.activity.compose.BackHandler
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import kotlinx.coroutines.launch

enum class SettingsCategory(val title: String, val icon: ImageVector) {
    SHOP_PROFILE("Store Profile", Icons.Default.AccountBox),
    PRINTER("Hardware & Printer", Icons.Default.Build),
    CLOUD_BACKUP("Cloud Synchronization", Icons.Default.Refresh),
    STAFF("User Management", Icons.Default.AccountCircle),
    DISPLAY("Display & Interface", Icons.Default.Settings),
    MAINTENANCE("Database & Security", Icons.Default.Lock)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit = {},
    onOpenMasterControl: () -> Unit = {},
    onOpenSyncDiagnostics: () -> Unit = {}
) {
    var activeCategory by remember { mutableStateOf<SettingsCategory?>(null) }
    BackHandler(enabled = activeCategory != null) {
        activeCategory = null
    }

    val printerType by viewModel.printerType.collectAsState()
    val savedPrinters by viewModel.savedPrinters.collectAsState()
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
    val activeSession by viewModel.activeSession.collectAsState()
    val liveBackupFolderUri by viewModel.liveBackupFolderUri.collectAsState()
    val liveBackupLastWriteAtEpochMs by viewModel.liveBackupLastWriteAtEpochMs.collectAsState()
    val canUndoLastRestore by viewModel.canUndoLastRestore.collectAsState()
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

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.runRestore(uri) {
                // Restored successfully
            }
        }
    }

    // Restores from an arbitrary folder (e.g. shared from another phone) without adopting it as
    // this device's Auto Backup folder — separate from liveBackupFolderPicker below, which does adopt it.
    val restoreFromFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreFromLiveBackup(uri) { }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            viewModel.loadPairedBluetoothDevices()
        }
    }

    val liveBackupFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.setupLiveBackup(uri) { }
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
            val activity = context as? androidx.fragment.app.FragmentActivity
            if (activity != null && BiometricAuthenticator.isBiometricAvailable(activity)) {
                BiometricAuthenticator.authenticate(
                    activity = activity,
                    onSuccess = {
                        onAuthenticated()
                        viewModel.clearBiometricAuthPending()
                    },
                    onError = { error ->
                        viewModel.clearBiometricAuthPending()
                        viewModel.onBiometricAuthFailed(error)
                    }
                )
            } else {
                onAuthenticated()
                viewModel.clearBiometricAuthPending()
            }
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
                            text = if (activeCategory != null) activeCategory!!.title else brandingShopName.ifBlank { stringResource(com.kadaikutty.pos.R.string.settings_title) },
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (activeCategory != null) {
                            activeCategory = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF5C151A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
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

            val cloudSyncCard: @Composable (Modifier) -> Unit = { modifier ->
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
                        Text("Cloud Sync Status", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Your store's transaction database synchronizes with the cloud automatically. For backup/restore, see \"Database Maintenance & Backup\" below.",
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
                        } else {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Not signed in: Please log in to enable Cloud Sync.", fontSize = 13.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                            }
                        }

                        if (currentSession != null) {
                            OutlinedButton(
                                onClick = onOpenSyncDiagnostics,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("View Sync Diagnostics & Errors", fontWeight = FontWeight.SemiBold)
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
                        if (savedPrinters.isNotEmpty()) {
                            Text("Saved printers (tap, then save to activate)")
                            savedPrinters.forEach { profile ->
                                TextButton(onClick = {
                                    selectedType = profile.type
                                    selectedDeviceId = profile.deviceId
                                    selectedPaperWidth = profile.paperWidth
                                }) {
                                    Text("${profile.type}: ${profile.deviceId} — ${if (profile.paperWidth == 48) 80 else 58} mm")
                                }
                            }
                        }

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

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedType == "Network", onClick = { selectedType = "Network" })
                            Text("Wi-Fi / LAN (ESC/POS)")
                        }
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
                        if (selectedType == "Usb") {
                            val usbManager = context.getSystemService(android.content.Context.USB_SERVICE) as? android.hardware.usb.UsbManager
                            usbManager?.deviceList?.values?.forEach { device ->
                                TextButton(onClick = { selectedDeviceId = device.deviceName }) {
                                    Text("${device.productName ?: "USB device"}: ${device.deviceName}")
                                }
                            }
                        }
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
                                    modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
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
                                label = { Text(if (selectedType == "Network") "Printer IP / host (optional :9100)" else "USB Target Name / Path") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.saveSettings(selectedType, selectedDeviceId, selectedPaperWidth)
                                message = "Hardware preferences saved."
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

            val thermalReceiptPreviewCard: @Composable (Modifier) -> Unit = { modifier ->
                val currentShopName by viewModel.shopName.collectAsState()
                val currentGstNumber by viewModel.gstNumber.collectAsState()
                val currentShopAddress by viewModel.shopAddress.collectAsState()
                val currentShopPhone by viewModel.shopPhone.collectAsState()
                val currentShopLogoPath by viewModel.shopLogoPath.collectAsState()
                val paperWidth by viewModel.printerPaperWidth.collectAsState()
                var previewPaperSize by remember { mutableStateOf(paperWidth) }

                val logoBitmap = remember(currentShopLogoPath) {
                    decodeSampledBitmapFromFile(currentShopLogoPath, 512, 512)
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
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column {
                                Text(
                                    "Live Thermal Receipt Preview",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Real-time mockup of printed customer receipt",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = previewPaperSize == 32,
                                    onClick = { previewPaperSize = 32 },
                                    label = { Text("58mm (Standard)", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                    leadingIcon = if (previewPaperSize == 32) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = previewPaperSize == 48,
                                    onClick = { previewPaperSize = 48 },
                                    label = { Text("80mm (Wide)", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                    leadingIcon = if (previewPaperSize == 48) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // The Realistic Receipt Mockup Container
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .padding(vertical = 14.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                modifier = Modifier
                                    .widthIn(max = if (previewPaperSize == 32) 300.dp else 420.dp)
                                    .fillMaxWidth(if (previewPaperSize == 32) 0.88f else 1f)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White,
                                shadowElevation = 4.dp
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Shop Logo
                                    if (logoBitmap != null) {
                                        Image(
                                            bitmap = logoBitmap.asImageBitmap(),
                                            contentDescription = "Receipt Logo",
                                            modifier = Modifier
                                                .size(48.dp)
                                                .padding(bottom = 4.dp)
                                        )
                                    }

                                    // Store Name
                                    Text(
                                        text = currentShopName.ifBlank { "KADAIKUTTY STORE" }.uppercase(),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = Color.Black,
                                        textAlign = TextAlign.Center,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )

                                    // Address & Phone
                                    if (currentShopAddress.isNotBlank()) {
                                        Text(
                                            text = currentShopAddress,
                                            fontSize = 11.sp,
                                            color = Color.DarkGray,
                                            textAlign = TextAlign.Center,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                        )
                                    }
                                    if (currentShopPhone.isNotBlank()) {
                                        Text(
                                            text = "Ph: $currentShopPhone",
                                            fontSize = 11.sp,
                                            color = Color.DarkGray,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                        )
                                    }
                                    if (currentGstNumber.isNotBlank()) {
                                        Text(
                                            text = "GSTIN: $currentGstNumber",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                        )
                                    }

                                    // Hoisted: a DrawScope is not a composable scope, so the theme
                                    // has to be read before the Canvas, not inside drawLine.
                                    val dashColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .height(1.dp)
                                    ) {
                                        drawLine(
                                            color = dashColor,
                                            start = Offset(0f, 0f),
                                            end = Offset(size.width, 0f),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f),
                                            strokeWidth = 2f
                                        )
                                    }

                                    // Meta details
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Bill: #INV-1024", fontSize = 10.sp, color = Color.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                        Text("02/09/26 02:30 PM", fontSize = 10.sp, color = Color.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Cashier: Staff", fontSize = 10.sp, color = Color.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                        Text("Customer: Cash", fontSize = 10.sp, color = Color.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                    }

                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .height(1.dp)
                                    ) {
                                        drawLine(
                                            color = dashColor,
                                            start = Offset(0f, 0f),
                                            end = Offset(size.width, 0f),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f),
                                            strokeWidth = 2f
                                        )
                                    }

                                    // Items Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("ITEM", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.weight(1.8f))
                                        Text("QTY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.weight(0.6f), textAlign = TextAlign.Center)
                                        Text("RATE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                                        Text("TOTAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                                    }

                                    // Sample Item 1
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Aashirvaad Atta 5kg", fontSize = 10.sp, color = Color.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.weight(1.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("1", fontSize = 10.sp, color = Color.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.weight(0.6f), textAlign = TextAlign.Center)
                                        Text("265.00", fontSize = 10.sp, color = Color.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                                        Text("265.00", fontSize = 10.sp, color = Color.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                                    }

                                    // Sample Item 2
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Sunflower Oil 1L", fontSize = 10.sp, color = Color.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.weight(1.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("2", fontSize = 10.sp, color = Color.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.weight(0.6f), textAlign = TextAlign.Center)
                                        Text("135.00", fontSize = 10.sp, color = Color.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                                        Text("270.00", fontSize = 10.sp, color = Color.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                                    }

                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .height(1.dp)
                                    ) {
                                        drawLine(
                                            color = dashColor,
                                            start = Offset(0f, 0f),
                                            end = Offset(size.width, 0f),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f),
                                            strokeWidth = 2f
                                        )
                                    }

                                    // Totals
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("TOTAL ITEMS: 2 (Qty: 3)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                        Text("₹535.00", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("PAYMENT MODE", fontSize = 10.sp, color = Color.DarkGray, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                        Text("UPI / GPAY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                    }

                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .height(1.dp)
                                    ) {
                                        drawLine(
                                            color = dashColor,
                                            start = Offset(0f, 0f),
                                            end = Offset(size.width, 0f),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f),
                                            strokeWidth = 2f
                                        )
                                    }

                                    // Tamil & English Footer
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "நன்றி மீண்டும் வருக!",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color.Black,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Thank you! Visit again",
                                        fontSize = 10.sp,
                                        color = Color.DarkGray,
                                        textAlign = TextAlign.Center,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                    Text(
                                        text = "*** KadaiKutty POS ***",
                                        fontSize = 9.sp,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
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
                            }
                        }

                        val staffMembers = remember(users, activeSession) {
                            users.filter { it.role.uppercase() != "ADMIN" && it.id != activeSession?.userId }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (staffMembers.isEmpty()) {
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
                                staffMembers.forEach { user ->
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
                                                        else -> com.kadaikutty.pos.core.ui.theme.roleChipColors(user.role).first
                                                    },
                                                    modifier = Modifier.size(44.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.Person,
                                                            contentDescription = null,
                                                            tint = when (user.role.uppercase()) {
                                                                else -> com.kadaikutty.pos.core.ui.theme.roleChipColors(user.role).second
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
                                                                else -> com.kadaikutty.pos.core.ui.theme.roleChipColors(user.role).first
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
                                                                    else -> com.kadaikutty.pos.core.ui.theme.roleChipColors(user.role).second
                                                                },
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }

                                                        if (user.role.uppercase() != "ADMIN") {
                                                            val isPending = user.toPermissionsSet().contains(com.kadaikutty.pos.core.security.Permission.PENDING_MASTER_APPROVAL)
                                                            val isInactive = user.toPermissionsSet().contains(com.kadaikutty.pos.core.security.Permission.ACCOUNT_INACTIVE)
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = when {
                                                                    else -> com.kadaikutty.pos.core.ui.theme.statusChipColors(isPending, isInactive).first
                                                                }
                                                            ) {
                                                                Text(
                                                                    text = when {
                                                                        isPending -> "Pending Master"
                                                                        isInactive -> "Deactivated"
                                                                        else -> "Active"
                                                                    },
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = when {
                                                                        else -> com.kadaikutty.pos.core.ui.theme.statusChipColors(isPending, isInactive).second
                                                                    },
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "Login ID: +91 ${user.username}",
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
                        onCreate = { phone, name, pass, role, perms, onComplete ->
                            viewModel.createUser(phone, name, pass, role, perms) { success, msg ->
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                onComplete(success)
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
                        onSave = { name, role, pass, perms, onComplete ->
                            viewModel.updateUserCredentials(selectedUser!!.id, name, role, pass, perms) { success, msg ->
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                onComplete(success)
                                if (success) {
                                    showEditDialog = false
                                }
                            }
                        },
                        onDelete = { onComplete ->
                            viewModel.deleteUser(selectedUser!!.id) { success, msg ->
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                onComplete(success)
                                if (success) {
                                    showEditDialog = false
                                }
                            }
                        }
                    )
                }
            }



            val backupCard: @Composable (Modifier) -> Unit = { modifier ->
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
                        Text("Backup & Restore", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Turn on Auto Backup once and every change is mirrored automatically. You can also export or restore a backup by hand anytime — no internet needed.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val backupStatus by viewModel.backupStatus.collectAsState()
                        val restoreStatus by viewModel.restoreStatus.collectAsState()
                        val lastBackupAtEpochMs by viewModel.lastBackupAtEpochMs.collectAsState()

                        var showRestoreChooserDialog by remember { mutableStateOf(false) }
                        var showFileRestoreDialog by remember { mutableStateOf(false) }
                        var showFolderRestoreDialog by remember { mutableStateOf(false) }
                        var showCloudRestoreDialog by remember { mutableStateOf(false) }
                        var showLiveRestoreDialog by remember { mutableStateOf(false) }

                        var showClearDatabaseDialog by remember { mutableStateOf(false) }
                        var clearCloudOption by remember { mutableStateOf(false) }

                        val lastBackupLabel = lastBackupAtEpochMs?.let { epochMs ->
                            "Last backup: ${android.text.format.DateUtils.getRelativeTimeSpanString(epochMs, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS)}"
                        } ?: "Last backup: never"

                        // Auto Backup: set once, mirrors every change automatically (and bootstraps cloud backup too).
                        if (liveBackupFolderUri.isNullOrBlank()) {
                            Button(
                                onClick = { liveBackupFolderPicker.launch(null) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Enable Auto Backup", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            val lastMirroredLabel = liveBackupLastWriteAtEpochMs?.let { epochMs ->
                                "Last mirrored: ${android.text.format.DateUtils.getRelativeTimeSpanString(epochMs, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS)}"
                            } ?: "Set up, waiting for the first change"
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text("Auto Backup active — $lastMirroredLabel", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(
                                    onClick = { liveBackupFolderPicker.launch(null) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Change Folder", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { showLiveRestoreDialog = true },
                                    enabled = !isRestoreRunning,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Restore This Folder", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        // Manual, portable path: works fully offline, one file, safe to move to a new phone.
                        if (isMobile) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { viewModel.exportBackupNow() },
                                    enabled = !isBackupRunning,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Export Backup Now", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { showRestoreChooserDialog = true },
                                    enabled = !isRestoreRunning,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Restore Backup", fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { viewModel.exportBackupNow() },
                                    enabled = !isBackupRunning,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Export Backup Now", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { showRestoreChooserDialog = true },
                                    enabled = !isRestoreRunning,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Restore Backup", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Text(lastBackupLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)

                        // Cloud: off-site protection against a lost, stolen, or damaged phone.
                        // Needs the server, so disabled (with a reason) while offline.
                        run {
                        val isOnline by viewModel.isOnline.collectAsState()
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        if (!isOnline) {
                            Text("Cloud backup and restore need internet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                        if (isMobile) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { viewModel.runCloudBackup() },
                                    enabled = session != null && !isBackupRunning && isOnline,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Back Up to Cloud", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { showCloudRestoreDialog = true },
                                    enabled = session != null && !isRestoreRunning && isOnline,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Restore Latest Cloud Backup", fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { viewModel.runCloudBackup() },
                                    enabled = session != null && !isBackupRunning && isOnline,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Back Up to Cloud", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { showCloudRestoreDialog = true },
                                    enabled = session != null && !isRestoreRunning && isOnline,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Restore Latest Cloud Backup", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

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

                        if (showRestoreChooserDialog) {
                            AlertDialog(
                                onDismissRequest = { showRestoreChooserDialog = false },
                                title = { Text("Restore From") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(
                                            onClick = {
                                                showRestoreChooserDialog = false
                                                showFileRestoreDialog = true
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("A Backup File (.zip)", modifier = Modifier.fillMaxWidth())
                                        }
                                        TextButton(
                                            onClick = {
                                                showRestoreChooserDialog = false
                                                showFolderRestoreDialog = true
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("An Auto-Backup Folder", modifier = Modifier.fillMaxWidth())
                                        }
                                    }
                                },
                                confirmButton = {},
                                dismissButton = {
                                    TextButton(onClick = { showRestoreChooserDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        if (showFileRestoreDialog) {
                            AlertDialog(
                                onDismissRequest = { showFileRestoreDialog = false },
                                title = { Text("Restore from a Backup File?") },
                                text = { Text("Are you sure you want to restore from a backup .zip file? This will completely overwrite your current database.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showFileRestoreDialog = false
                                        restoreLauncher.launch("application/zip")
                                    }) {
                                        Text("Yes, Restore", color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showFileRestoreDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        if (showFolderRestoreDialog) {
                            AlertDialog(
                                onDismissRequest = { showFolderRestoreDialog = false },
                                title = { Text("Restore from an Auto-Backup Folder?") },
                                text = { Text("Pick a folder that has an Auto Backup in it (from this or another phone). This replays its baseline and change history, completely overwriting your current local database.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showFolderRestoreDialog = false
                                        restoreFromFolderPicker.launch(null)
                                    }) {
                                        Text("Choose Folder", color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showFolderRestoreDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        if (showLiveRestoreDialog) {
                            AlertDialog(
                                onDismissRequest = { showLiveRestoreDialog = false },
                                title = { Text("Restore from This Folder?") },
                                text = { Text("This replays your Auto Backup folder's baseline and change history, completely overwriting your current local database.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showLiveRestoreDialog = false
                                        viewModel.restoreFromLiveBackup { }
                                    }) {
                                        Text("Yes, Restore", color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showLiveRestoreDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        if (showClearDatabaseDialog) {
                            var hasRecentBackupForClear by remember { mutableStateOf<Boolean?>(null) }
                            var isCheckingSafetyBackup by remember { mutableStateOf(false) }
                            val coroutineScope = rememberCoroutineScope()

                            fun recheckSafetyBackup() {
                                isCheckingSafetyBackup = true
                                coroutineScope.launch {
                                    hasRecentBackupForClear = viewModel.hasRecentSafetyBackup()
                                    isCheckingSafetyBackup = false
                                }
                            }

                            LaunchedEffect(clearCloudOption) {
                                if (clearCloudOption) {
                                    recheckSafetyBackup()
                                } else {
                                    hasRecentBackupForClear = null
                                }
                            }

                            val isBlockedByMissingBackup = clearCloudOption && hasRecentBackupForClear == false

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
                                            Text("Also clear backend cloud sync data", fontSize = 13.sp)
                                        }
                                        if (clearCloudOption && isCheckingSafetyBackup) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                                Text("Checking for a recent backup...", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                            }
                                        }
                                        if (isBlockedByMissingBackup) {
                                            Text(
                                                "You need a backup less than 3 days old before clearing cloud data.",
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                },
                                confirmButton = {
                                    if (isBlockedByMissingBackup) {
                                        TextButton(
                                            enabled = !isBackupRunning,
                                            onClick = {
                                                viewModel.runCloudBackup { success ->
                                                    if (success) recheckSafetyBackup()
                                                }
                                            }
                                        ) {
                                            Text("Back Up to Cloud Now", fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        TextButton(
                                            enabled = !isCheckingSafetyBackup,
                                            onClick = {
                                                showClearDatabaseDialog = false
                                                viewModel.clearAllDatabase(clearCloudOption) { success ->
                                                    val msg = if (success) "Database cleared successfully!" else "Failed to clear database."
                                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        ) {
                                            Text("Yes, Clear Data", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showClearDatabaseDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        if (showCloudRestoreDialog) {
                            AlertDialog(
                                onDismissRequest = { showCloudRestoreDialog = false },
                                title = { Text("Restore Latest Cloud Backup?") },
                                text = { Text("This downloads your most recent cloud backup and completely overwrites your current local database with it.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showCloudRestoreDialog = false
                                        viewModel.runCloudRestore { }
                                    }) {
                                        Text("Yes, Restore", color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showCloudRestoreDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        if (canUndoLastRestore) {
                            var showUndoDialog by remember { mutableStateOf(false) }
                            OutlinedButton(
                                onClick = { showUndoDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Undo Last Restore", fontWeight = FontWeight.Bold)
                            }
                            if (showUndoDialog) {
                                AlertDialog(
                                    onDismissRequest = { showUndoDialog = false },
                                    title = { Text("Undo Last Restore?") },
                                    text = { Text("This rolls back to the state your database was in immediately before your most recent restore, overwriting anything since.") },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            showUndoDialog = false
                                            viewModel.undoLastRestore { }
                                        }) {
                                            Text("Yes, Undo", color = MaterialTheme.colorScheme.error)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showUndoDialog = false }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }
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
                // A delayed DataStore/license refresh must never overwrite text the user is editing.
                // Once the saved values catch up with this draft, the form resumes normal syncing.
                var hasPendingShopDetailEdits by remember { mutableStateOf(false) }

                var isProcessingImage by remember { mutableStateOf(false) }

                LaunchedEffect(currentShopName, currentOwnerName, currentGstNumber, currentShopAddress, currentShopPhone, currentShopEmail, currentShopLogoPath) {
                    val storedValuesMatchDraft =
                        currentShopName == inputShopName &&
                            currentOwnerName == inputOwnerName &&
                            currentGstNumber == inputGstNumber &&
                            currentShopAddress == inputShopAddress &&
                            currentShopPhone == inputShopPhone &&
                            currentShopEmail == inputShopEmail &&
                            currentShopLogoPath == inputShopLogoPath
                    if (!hasPendingShopDetailEdits || storedValuesMatchDraft) {
                        inputShopName = currentShopName
                        inputOwnerName = currentOwnerName
                        inputGstNumber = currentGstNumber
                        inputShopAddress = currentShopAddress
                        inputShopPhone = currentShopPhone
                        inputShopEmail = currentShopEmail
                        inputShopLogoPath = currentShopLogoPath
                        hasPendingShopDetailEdits = false
                    }
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
                            hasPendingShopDetailEdits = true
                            android.widget.Toast.makeText(context, "Logo processed successfully. Preview below!", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "Image Processing Failure: Failed to load image.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }

                val isEmailValid = remember(inputShopEmail) {
                    inputShopEmail.isEmpty() || android.util.Patterns.EMAIL_ADDRESS.matcher(inputShopEmail).matches()
                }

                val bitmap = remember(inputShopLogoPath) {
                    decodeSampledBitmapFromFile(inputShopLogoPath, 512, 512)
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
                            onValueChange = { inputShopName = com.kadaikutty.pos.core.common.InputRules.name(it); hasPendingShopDetailEdits = true },
                            label = { Text("Shop Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = inputOwnerName,
                            onValueChange = { inputOwnerName = com.kadaikutty.pos.core.common.InputRules.name(it); hasPendingShopDetailEdits = true },
                            label = { Text("Owner Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = inputGstNumber,
                            onValueChange = { inputGstNumber = com.kadaikutty.pos.core.common.InputRules.gstin(it); hasPendingShopDetailEdits = true },
                            label = { Text("GST Number") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = inputShopAddress,
                            onValueChange = { inputShopAddress = com.kadaikutty.pos.core.common.InputRules.text(it); hasPendingShopDetailEdits = true },
                            label = { Text("Shop Address") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            maxLines = 3,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = inputShopPhone,
                            onValueChange = { inputShopPhone = com.kadaikutty.pos.core.common.InputRules.phone(it); hasPendingShopDetailEdits = true },
                            label = { Text("Shop Phone Number") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = inputShopEmail,
                            onValueChange = { inputShopEmail = com.kadaikutty.pos.core.common.InputRules.email(it); hasPendingShopDetailEdits = true },
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
                                    hasPendingShopDetailEdits = true
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Remove Logo")
                            }
                        }

                        Button(
                            onClick = {
                                val gstTrimmed = inputGstNumber.trim()
                                val problem = com.kadaikutty.pos.core.common.InputRules.firstError(
                                    com.kadaikutty.pos.core.common.InputRules.checkName(inputShopName, "Shop name"),
                                    inputOwnerName.takeIf { it.isNotBlank() }?.let { com.kadaikutty.pos.core.common.InputRules.checkName(it, "Owner name") },
                                    com.kadaikutty.pos.core.common.InputRules.checkGstin(gstTrimmed),
                                    com.kadaikutty.pos.core.common.InputRules.checkPhone(inputShopPhone),
                                    com.kadaikutty.pos.core.common.InputRules.checkEmail(inputShopEmail)
                                )
                                if (problem != null) {
                                    android.widget.Toast.makeText(context, problem, android.widget.Toast.LENGTH_LONG).show()
                                } else {
                                    viewModel.saveShopDetails(inputShopName, inputOwnerName, gstTrimmed, inputShopAddress, inputShopPhone, inputShopEmail, inputShopLogoPath) { saved, error ->
                                        if (saved) {
                                            hasPendingShopDetailEdits = false
                                            keyboardController?.hide()
                                            android.widget.Toast.makeText(context, "Shop details saved and synced to cloud!", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            android.widget.Toast.makeText(context, error ?: "Shop details could not be synced", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
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
                    when (activeCategory) {
                        SettingsCategory.SHOP_PROFILE -> {
                            shopDetailsCard(Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(16.dp))
                            thermalReceiptPreviewCard(Modifier.fillMaxWidth())
                        }
                        SettingsCategory.PRINTER -> {
                            printerPreferencesCard(Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(16.dp))
                            thermalReceiptPreviewCard(Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(16.dp))
                            printerDiagnosticsCard(Modifier.fillMaxWidth())
                        }
                        SettingsCategory.CLOUD_BACKUP -> {
                            cloudSyncCard(Modifier.fillMaxWidth())
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
                            backupCard(Modifier.fillMaxWidth())
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
    onCreate: (phone: String, displayName: String, password: CharArray, role: String, permissions: Set<Permission>, onComplete: (Boolean) -> Unit) -> Unit
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

    var requirePasswordChange by remember { mutableStateOf(false) }

    var errorMsg by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

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
        onDismissRequest = {
            if (!isSubmitting) onDismiss()
        },
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
                    onValueChange = { displayName = com.kadaikutty.pos.core.common.InputRules.name(it) },
                    label = { Text("Staff / Cashier Name *") },
                    placeholder = { Text("e.g. Ramesh Kumar") },
                    leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting
                )

                // Phone / Username
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = com.kadaikutty.pos.core.common.InputRules.phone(it) },
                    label = { Text("Mobile Number (Login ID) *") },
                    placeholder = { Text("10 digit mobile number") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting
                )

                // Role Selector Chips
                Text("Quick Preset Role:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("CASHIER" to "Cashier", "STORE_MANAGER" to "Manager", "INWARD_CLERK" to "Inward Clerk", "ADMIN" to "Admin").forEach { (roleKey, roleLabel) ->
                        FilterChip(
                            selected = selectedRole == roleKey,
                            onClick = { if (!isSubmitting) applyRoleDefaults(roleKey) },
                            label = { Text(roleLabel, fontSize = 12.sp) },
                            enabled = !isSubmitting
                        )
                    }
                }

                // Password / PIN
                OutlinedTextField(
                    value = password,
                    onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) password = it },
                    label = { Text("6-Digit Temporary Password (PIN) *") },
                    placeholder = { Text("Enter 6-digit numeric PIN") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(
                            enabled = !isSubmitting,
                            onClick = { showPassword = !showPassword }
                        ) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting
                )


                Text("Custom Screen Permissions:", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = accessBilling, onCheckedChange = { accessBilling = it; selectedRole = "CUSTOM" }, enabled = !isSubmitting)
                            Text("Point of Sale Billing & Checkout", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = accessPurchases, onCheckedChange = { accessPurchases = it; selectedRole = "CUSTOM" }, enabled = !isSubmitting)
                            Text("Inventory & Inward Stock Purchases", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = accessMasters, onCheckedChange = { accessMasters = it; selectedRole = "CUSTOM" }, enabled = !isSubmitting)
                            Text("Master Catalog (Products & Pricing)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = accessReports, onCheckedChange = { accessReports = it; selectedRole = "CUSTOM" }, enabled = !isSubmitting)
                            Text("Business Reports & Profit Analytics", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = accessSettings, onCheckedChange = { accessSettings = it; selectedRole = "CUSTOM" }, enabled = !isSubmitting)
                            Text("Store Settings & Printer Setup", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Checkbox(checked = requirePasswordChange, onCheckedChange = { requirePasswordChange = it }, enabled = !isSubmitting)
                        Column {
                            Text("Force Password Reset on Login", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("User must set a new PIN when they first log in", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                enabled = !isSubmitting,
                onClick = {
                    if (isSubmitting) return@Button
                    val cleanDigits = phone.filter { it.isDigit() }
                    if (displayName.isBlank() || cleanDigits.isBlank() || password.isBlank()) {
                        errorMsg = "Please fill in all mandatory fields"
                    } else if (com.kadaikutty.pos.core.common.InputRules.checkName(displayName, "Staff name") != null) {
                        errorMsg = com.kadaikutty.pos.core.common.InputRules.checkName(displayName, "Staff name")!!
                    } else if (com.kadaikutty.pos.core.common.InputRules.checkPhone(cleanDigits, required = true) != null) {
                        errorMsg = com.kadaikutty.pos.core.common.InputRules.checkPhone(cleanDigits, required = true)!!
                    } else if (password.length != 6 || !password.all { it.isDigit() }) {
                        errorMsg = "Password / PIN must be exactly 6 numeric digits"
                    } else {
                        isSubmitting = true
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
                            if (requirePasswordChange) {
                                add(Permission.REQUIRE_PASSWORD_CHANGE)
                            }
                        }
                        onCreate(cleanDigits, displayName.trim(), password.toCharArray(), selectedRole, pSet) {
                            isSubmitting = false
                        }
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (isSubmitting) "Creating..." else "Create Staff Account", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditUserDialog(
    user: UserEntity,
    onDismiss: () -> Unit,
    onSave: (displayName: String?, role: String?, newPassword: CharArray?, permissions: Set<Permission>, onComplete: (Boolean) -> Unit) -> Unit,
    onDelete: (onComplete: (Boolean) -> Unit) -> Unit
) {
    var displayName by remember { mutableStateOf(user.displayName) }
    var selectedRole by remember { mutableStateOf(user.role.ifBlank { "CASHIER" }) }
    var newPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    val initialPerms = remember(user.permissions) { user.toPermissionsSet() }

    var accessBilling by remember { mutableStateOf(initialPerms.contains(Permission.SALE_CREATE)) }
    var accessMasters by remember { mutableStateOf(initialPerms.contains(Permission.PRODUCT_VIEW)) }
    var accessPurchases by remember { mutableStateOf(initialPerms.contains(Permission.PURCHASE_CREATE)) }
    var accessReports by remember { mutableStateOf(initialPerms.contains(Permission.REPORT_SALES)) }
    var accessSettings by remember { mutableStateOf(initialPerms.contains(Permission.SETTINGS_VIEW)) }
    
    var isActive by remember { mutableStateOf(!initialPerms.contains(Permission.ACCOUNT_INACTIVE)) }
    var requirePasswordChange by remember { mutableStateOf(initialPerms.contains(Permission.REQUIRE_PASSWORD_CHANGE)) }

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
        onDismissRequest = {
            if (!isSubmitting) onDismiss()
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Edit Staff Credentials & Access", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                // Header Info
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Column {
                            Text(user.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Mobile Login: +91 ${user.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }

                // Preset Role
                Text("Role Preset:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("CASHIER" to "Cashier", "STORE_MANAGER" to "Manager", "INWARD_CLERK" to "Inward Clerk", "ADMIN" to "Admin").forEach { (roleKey, roleLabel) ->
                        FilterChip(
                            selected = selectedRole == roleKey,
                            onClick = { if (!isSubmitting) applyRoleDefaults(roleKey) },
                            label = { Text(roleLabel, fontSize = 12.sp) },
                            enabled = !isSubmitting
                        )
                    }
                }

                // Account Status (Active / Deactivated)
                val (statusFill, statusInk) = com.kadaikutty.pos.core.ui.theme.statusChipColors(
                    isPending = false, isInactive = !isActive
                )
                Surface(
                    color = statusFill,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusInk.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (isActive) "Account is Active" else "Account is Deactivated",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = statusInk
                            )
                            Text(
                                text = if (isActive) "User can login to their device" else "User is blocked from logging in",
                                fontSize = 11.sp,
                                color = statusInk.copy(alpha = 0.8f)
                            )
                        }
                        Switch(
                            checked = isActive,
                            onCheckedChange = { isActive = it },
                            enabled = !isSubmitting,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = MaterialTheme.colorScheme.error
                            )
                        )
                    }
                }

                // Name
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = com.kadaikutty.pos.core.common.InputRules.name(it) },
                    label = { Text("Staff Full Name") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting
                )

                // Password Reset
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) newPassword = it },
                    label = { Text("Reset 6-Digit Password (PIN)") },
                    placeholder = { Text("Enter 6-digit PIN or leave blank") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(
                            enabled = !isSubmitting,
                            onClick = { showPassword = !showPassword }
                        ) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting
                )


                Text("Custom Screen Permissions:", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = accessBilling, onCheckedChange = { accessBilling = it; selectedRole = "CUSTOM" }, enabled = !isSubmitting)
                            Text("Point of Sale Billing & Checkout", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = accessPurchases, onCheckedChange = { accessPurchases = it; selectedRole = "CUSTOM" }, enabled = !isSubmitting)
                            Text("Inventory & Inward Stock Purchases", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = accessMasters, onCheckedChange = { accessMasters = it; selectedRole = "CUSTOM" }, enabled = !isSubmitting)
                            Text("Master Catalog (Products & Pricing)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = accessReports, onCheckedChange = { accessReports = it; selectedRole = "CUSTOM" }, enabled = !isSubmitting)
                            Text("Business Reports & Profit Analytics", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = accessSettings, onCheckedChange = { accessSettings = it; selectedRole = "CUSTOM" }, enabled = !isSubmitting)
                            Text("Store Settings & Printer Setup", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Checkbox(checked = requirePasswordChange, onCheckedChange = { requirePasswordChange = it }, enabled = !isSubmitting)
                        Column {
                            Text("Force Password Reset on Login", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("User must set a new PIN when they next log in", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !isSubmitting,
                    onClick = {
                        if (isSubmitting) return@Button
                        isSubmitting = true
                        onDelete { isSubmitting = false }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isSubmitting) "..." else "Delete")
                }
                Button(
                    enabled = !isSubmitting,
                    onClick = {
                        if (isSubmitting) return@Button
                        isSubmitting = true
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
                            if (!isActive) {
                                add(Permission.ACCOUNT_INACTIVE)
                            }
                            if (requirePasswordChange) {
                                add(Permission.REQUIRE_PASSWORD_CHANGE)
                            }
                        }
                        val passArray = if (newPassword.isBlank()) null else newPassword.toCharArray()
                        onSave(displayName.trim(), selectedRole, passArray, pSet) {
                            isSubmitting = false
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isSubmitting) "Saving..." else "Save Changes")
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

fun decodeSampledBitmapFromFile(path: String, reqWidth: Int = 512, reqHeight: Int = 512): android.graphics.Bitmap? {
    if (path.isBlank()) return null
    val file = File(path)
    if (!file.exists()) return null
    return try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        var inSampleSize = 1
        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
    } catch (_: Exception) {
        null
    }
}
