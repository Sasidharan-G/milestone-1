package com.company.billing.feature.settings.presentation

import com.company.billing.core.ui.LocalLayoutMode
import com.company.billing.core.auth.UserEntity
import com.company.billing.core.security.Permission
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import com.google.api.services.drive.DriveScopes
import androidx.compose.ui.platform.LocalContext

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val printerType by viewModel.printerType.collectAsState()
    val layoutModePref by viewModel.layoutMode.collectAsState()
    val printerDeviceId by viewModel.printerDeviceId.collectAsState()
    val printerPaperWidth by viewModel.printerPaperWidth.collectAsState()
    val bluetoothDevices by viewModel.bluetoothDevices.collectAsState()
    val printStatus by viewModel.printStatus.collectAsState()

    val backupStatus by viewModel.backupStatus.collectAsState()
    val restoreStatus by viewModel.restoreStatus.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedType by remember { mutableStateOf("Bluetooth") }
    var selectedDeviceId by remember { mutableStateOf("") }
    var selectedPaperWidth by remember { mutableStateOf(32) }
    var message by remember { mutableStateOf("") }

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
            viewModel.runBackup { bytes ->
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(bytes)
                    }
                } catch (ignored: Exception) {}
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bytes = inputStream.readBytes()
                    viewModel.runRestore(bytes) {
                        // Restored successfully
                    }
                }
            } catch (ignored: Exception) {}
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Maintenance", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val session by viewModel.activeSession.collectAsState()
            val hasUserManagePermission = session?.permissions?.contains(com.company.billing.core.security.Permission.USER_MANAGE) == true

            val layoutMode = LocalLayoutMode.current
            val isMobile = when (layoutMode) {
                "Mobile" -> true
                "Tablet" -> false
                else -> maxWidth < 600.dp
            }

            val googleDriveCard: @Composable (Modifier) -> Unit = { modifier ->
                val googleAccount by viewModel.googleAccount.collectAsState()
                val driveBackupStatus by viewModel.driveBackupStatus.collectAsState()
                val context = LocalContext.current

                val gso = remember {
                    GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
                        .build()
                }
                val signInClient = remember { GoogleSignIn.getClient(context, gso) }
                var errorMessage by remember { mutableStateOf("") }

                val signInLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    try {
                        val account = task.getResult(ApiException::class.java)
                        viewModel.linkGoogleAccount(account?.email)
                        errorMessage = ""
                    } catch (e: ApiException) {
                        e.printStackTrace()
                        errorMessage = "Sign-In Failed (Code: ${e.statusCode}). Make sure the app's SHA-1 is registered in Google Cloud Console."
                    } catch (e: Exception) {
                        e.printStackTrace()
                        errorMessage = "Sign-In Failed: ${e.message}"
                    }
                }

                Card(
                    modifier = modifier,
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Google Drive Auto-Backup", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Automatically backup your transaction database to your personal Google Drive account in a secure app-specific folder.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.outline
                        )

                        if (!googleAccount.isNullOrBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Linked Account", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    Text(googleAccount!!, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            signInClient.signOut().addOnCompleteListener {
                                                signInLauncher.launch(signInClient.signInIntent)
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Switch Account")
                                    }
                                    TextButton(
                                        onClick = {
                                            signInClient.signOut().addOnCompleteListener {
                                                viewModel.linkGoogleAccount(null)
                                            }
                                        }
                                    ) {
                                        Text("Disconnect", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.backupToGoogleDrive() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Backup Now", fontWeight = FontWeight.Bold)
                                }

                                var showBackupsDialog by remember { mutableStateOf(false) }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.fetchDriveBackups()
                                        showBackupsDialog = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Restore Cloud", fontWeight = FontWeight.Bold)
                                }

                                if (showBackupsDialog) {
                                    val backupsList by viewModel.driveBackupsList.collectAsState()
                                    AlertDialog(
                                        onDismissRequest = { showBackupsDialog = false },
                                        title = { Text("Restore from Google Drive") },
                                        text = {
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())
                                            ) {
                                                if (backupsList.isEmpty()) {
                                                    Text("No backup files found. Click fetch to reload.")
                                                } else {
                                                    backupsList.forEach { file ->
                                                        val formattedTime = remember(file.createdTime) {
                                                            if (file.createdTime != null) {
                                                                java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
                                                                    .format(java.util.Date(file.createdTime.value))
                                                            } else {
                                                                "Unknown Date"
                                                            }
                                                        }
                                                        val sizeKb = (file.getSize() ?: 0L) / 1024
                                                        Card(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                                            onClick = {
                                                                viewModel.restoreFromGoogleDrive(file.id) { success ->
                                                                    if (success) {
                                                                        showBackupsDialog = false
                                                                    }
                                                                }
                                                            }
                                                        ) {
                                                            Column(modifier = Modifier.padding(12.dp)) {
                                                                Text(file.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                                ) {
                                                                    Text(formattedTime, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                                                    Text("${sizeKb} KB", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        confirmButton = {
                                            TextButton(onClick = { viewModel.fetchDriveBackups() }) {
                                                Text("Refresh")
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showBackupsDialog = false }) {
                                                Text("Close")
                                            }
                                        }
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    signInLauncher.launch(signInClient.signInIntent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Link Google Account", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (!driveBackupStatus.isNullOrBlank()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = driveBackupStatus!!,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(12.dp),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (errorMessage.isNotBlank()) {
                            Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "Setup Guide:\n1. Register package com.company.billing in Google Cloud Console.\n2. Add your SHA-1 certificate fingerprint.\n3. Enable the Google Drive API in Google Cloud APIs library.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            val layoutModeCard: @Composable (Modifier) -> Unit = { modifier ->
                Card(
                    modifier = modifier,
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
                            color = MaterialTheme.colorScheme.outline
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

            val printerPreferencesCard: @Composable (Modifier) -> Unit = { modifier ->
                Card(
                    modifier = modifier,
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
                                    label = { Text("Paired Bluetooth Printer") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
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
                                label = { Text("USB Device Name (e.g. /dev/bus/usb/001/002)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.saveSettings(selectedType, selectedDeviceId, selectedPaperWidth)
                                message = "Settings saved successfully!"
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
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
                    modifier = modifier,
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
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Button(
                            onClick = {
                                viewModel.printTestReceipt { result ->
                                    message = result
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
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
                var selectedUser by remember { mutableStateOf<com.company.billing.core.auth.UserEntity?>(null) }

                Card(
                    modifier = modifier,
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Staff User Management", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Button(
                                onClick = { showAddDialog = true },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Add User")
                            }
                        }

                        Text(
                            "Create staff logins, assign specific screen permissions, and reset user passwords here.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.outline
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (users.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No custom users created yet. Click 'Add User' above.", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            } else {
                                users.forEach { user ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            selectedUser = user
                                            showEditDialog = true
                                        },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(user.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text("ID/Username: ${user.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                            }
                                            Icon(Icons.Default.Edit, contentDescription = "Edit User", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Render dialogs for adding/editing users
                if (showAddDialog) {
                    AddUserDialog(onDismiss = { showAddDialog = false }, onCreate = { u, d, p, perms ->
                        viewModel.createUser(u, d, p, perms)
                        showAddDialog = false
                    })
                }

                if (showEditDialog && selectedUser != null) {
                    EditUserDialog(user = selectedUser!!, onDismiss = { showEditDialog = false }, onSave = { pass, perms ->
                        viewModel.updateUserCredentials(selectedUser!!.id, pass, perms)
                        showEditDialog = false
                    }, onDelete = {
                        viewModel.deleteUser(selectedUser!!.id)
                        showEditDialog = false
                    })
                }
            }

            val aiSettingsCard: @Composable (Modifier) -> Unit = { modifier ->
                var apiKey by remember { mutableStateOf("") }
                val savedKey by viewModel.geminiApiKey.collectAsState()

                LaunchedEffect(savedKey) {
                    apiKey = savedKey.orEmpty()
                }

                Card(
                    modifier = modifier,
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Gemini AI Configuration", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Add your Gemini API Key here to enable smart automatic parsing when uploading purchase bills and invoices.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = {
                                apiKey = it
                                viewModel.saveGeminiApiKey(it)
                            },
                            label = { Text("Gemini API Key") },
                            placeholder = { Text("AIzaSy...") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            val dbMaintenanceCard: @Composable (Modifier) -> Unit = { modifier ->
                Card(
                    modifier = modifier,
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Database Maintenance & Backup", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Create a local, transaction-safe compressed backup archive of your billing database. You can restore this backup on this or other devices to recover transactions.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.outline
                        )

                        if (isMobile) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = {
                                        createBackupLauncher.launch("billing_backup_${System.currentTimeMillis()}.zip")
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Create Backup Archive", fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        restoreLauncher.launch("application/zip")
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Text("Restore Backup Archive", fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Button(
                                    onClick = {
                                        createBackupLauncher.launch("billing_backup_${System.currentTimeMillis()}.zip")
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Create Backup Archive", fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        restoreLauncher.launch("application/zip")
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Text("Restore Backup Archive", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (!backupStatus.isNullOrBlank() || !restoreStatus.isNullOrBlank()) {
                            val statusMsg = backupStatus ?: restoreStatus
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isMobile) {
                    printerPreferencesCard(Modifier.fillMaxWidth())
                    printerDiagnosticsCard(Modifier.fillMaxWidth())
                    layoutModeCard(Modifier.fillMaxWidth())
                    googleDriveCard(Modifier.fillMaxWidth())
                    aiSettingsCard(Modifier.fillMaxWidth())
                    if (hasUserManagePermission) {
                        userManagementCard(Modifier.fillMaxWidth())
                    }
                    dbMaintenanceCard(Modifier.fillMaxWidth())
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(480.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        printerPreferencesCard(Modifier.weight(1.5f).fillMaxHeight())
                        printerDiagnosticsCard(Modifier.weight(1.5f).fillMaxHeight())
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        layoutModeCard(Modifier.weight(1f))
                        googleDriveCard(Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        aiSettingsCard(Modifier.weight(1f))
                        if (hasUserManagePermission) {
                            userManagementCard(Modifier.weight(1f))
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    dbMaintenanceCard(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
fun AddUserDialog(
    onDismiss: () -> Unit,
    onCreate: (username: String, displayName: String, password: CharArray, permissions: Set<Permission>) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var accessMasters by remember { mutableStateOf(true) }
    var accessSales by remember { mutableStateOf(true) }
    var accessPurchases by remember { mutableStateOf(false) }
    var accessReports by remember { mutableStateOf(false) }
    var accessSettings by remember { mutableStateOf(false) }

    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Staff Account") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(value = username, onValueChange = { username = it.trim().lowercase() }, label = { Text("Username / Login ID") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Full Display Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Initial Password") }, modifier = Modifier.fillMaxWidth())

                Spacer(Modifier.height(8.dp))
                Text("Role Permissions Access:", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = accessMasters, onCheckedChange = { accessMasters = it })
                    Text("Master Lists (Products/Customers/Suppliers)", fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = accessSales, onCheckedChange = { accessSales = it })
                    Text("Sales Billing & Invoicing Screen", fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = accessPurchases, onCheckedChange = { accessPurchases = it })
                    Text("Purchases & Inward Stock Screen", fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = accessReports, onCheckedChange = { accessReports = it })
                    Text("Reports & Stock Analytics Screen", fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = accessSettings, onCheckedChange = { accessSettings = it })
                    Text("App Printer & Data Settings Screen", fontSize = 13.sp)
                }

                if (errorMsg.isNotBlank()) {
                    Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (username.isBlank() || displayName.isBlank() || password.isBlank()) {
                    errorMsg = "Please fill in all fields"
                } else {
                    val pSet = buildSet {
                        if (accessMasters) {
                            addAll(listOf(Permission.CATEGORY_VIEW, Permission.CATEGORY_CREATE, Permission.CATEGORY_EDIT, Permission.PRODUCT_VIEW, Permission.PRODUCT_CREATE, Permission.PRODUCT_EDIT))
                        }
                        if (accessSales) {
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
                    onCreate(username, displayName, password.toCharArray(), pSet)
                }
            }) {
                Text("Create")
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
    onSave: (newPassword: CharArray?, permissions: Set<Permission>) -> Unit,
    onDelete: () -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    val initialPerms = remember(user.permissions) { user.toPermissionsSet() }

    var accessMasters by remember { mutableStateOf(initialPerms.contains(Permission.PRODUCT_VIEW)) }
    var accessSales by remember { mutableStateOf(initialPerms.contains(Permission.SALE_CREATE)) }
    var accessPurchases by remember { mutableStateOf(initialPerms.contains(Permission.PURCHASE_CREATE)) }
    var accessReports by remember { mutableStateOf(initialPerms.contains(Permission.REPORT_SALES)) }
    var accessSettings by remember { mutableStateOf(initialPerms.contains(Permission.SETTINGS_VIEW)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit User: ${user.displayName}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                Text("Username: ${user.username}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Reset Password (Leave blank to keep current)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                Text("Role Permissions Access:", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = accessMasters, onCheckedChange = { accessMasters = it })
                    Text("Master Lists (Products/Customers/Suppliers)", fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = accessSales, onCheckedChange = { accessSales = it })
                    Text("Sales Billing & Invoicing Screen", fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = accessPurchases, onCheckedChange = { accessPurchases = it })
                    Text("Purchases & Inward Stock Screen", fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = accessReports, onCheckedChange = { accessReports = it })
                    Text("Reports & Stock Analytics Screen", fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = accessSettings, onCheckedChange = { accessSettings = it })
                    Text("App Printer & Data Settings Screen", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete User")
                }
                Button(onClick = {
                    val pSet = buildSet {
                        if (accessMasters) {
                            addAll(listOf(Permission.CATEGORY_VIEW, Permission.CATEGORY_CREATE, Permission.CATEGORY_EDIT, Permission.PRODUCT_VIEW, Permission.PRODUCT_CREATE, Permission.PRODUCT_EDIT))
                        }
                        if (accessSales) {
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
                    onSave(passArray, pSet)
                }) {
                    Text("Save")
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
