package com.company.billing.feature.settings.presentation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(480.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Column: Configure Settings Card
                Card(
                    modifier = Modifier.weight(1.5f).fillMaxHeight(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxSize(),
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

                        Spacer(modifier = Modifier.weight(1f))

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

                // Right Column: Diagnostic Printing Console
                Card(
                    modifier = Modifier.weight(1.5f).fillMaxHeight(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxSize(),
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

            // 3. Database Maintenance Card
            Card(
                modifier = Modifier.fillMaxWidth(),
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
    }
}
