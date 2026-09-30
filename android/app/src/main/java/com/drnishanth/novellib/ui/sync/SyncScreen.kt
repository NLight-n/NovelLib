package com.drnishanth.novellib.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drnishanth.novellib.core.database.entities.SyncDeviceEntity
import com.drnishanth.novellib.core.sync.models.DiscoveredDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    viewModel: SyncViewModel,
    onBack: () -> Unit
) {
    val activeProfile by viewModel.activeProfile.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val trustedDevices by viewModel.trustedDevices.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val trustedDeviceIds = remember(trustedDevices) {
        trustedDevices.map { it.deviceId }.toSet()
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Local Device Sync")
                        Text(
                            text = "Wi-Fi P2P Active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. My Device Status Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "This Device: ${viewModel.myDeviceName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Device ID: ${viewModel.myDeviceId}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            activeProfile?.let {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Syncing Profile: @${it.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // 2. Discovered Nearby Devices Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Discovered Nearby Devices",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scanning...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (discoveredDevices.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Devices,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No other NovelLib devices found yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Open this screen on another device connected to the same Wi-Fi network",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                } else {
                    items(discoveredDevices) { device ->
                        val isPaired = device.deviceId in trustedDeviceIds
                        DiscoveredDeviceCard(
                            device = device,
                            isPaired = isPaired,
                            onPair = { viewModel.initiatePairing(device) },
                            onSync = { viewModel.startSyncWithDevice(device.hostAddress, device.port) }
                        )
                    }
                }

                // 3. Trusted Devices Section
                if (trustedDevices.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Trusted Paired Devices (${trustedDevices.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(trustedDevices) { device ->
                        TrustedDeviceCard(
                            device = device,
                            onForget = { viewModel.forgetDevice(device.deviceId) }
                        )
                    }
                }
            }

            // Syncing loading overlay
            if (uiState.isSyncing) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Synchronizing Library Data...", fontWeight = FontWeight.Bold)
                        Text(
                            "Merging metadata, progress, and preferences",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Outgoing Pairing Confirmation Dialog
    if (uiState.showPairingDialog && uiState.pendingPairingResponse != null) {
        val resp = uiState.pendingPairingResponse!!
        AlertDialog(
            onDismissRequest = { viewModel.cancelOutgoingPairing() },
            title = { Text("Confirm Pairing Code") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Verify that this 6-digit confirmation code matches the code on ${resp.receiverDeviceName}:")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = resp.sasCode,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Both devices must display the identical code to confirm mutual trust.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmOutgoingPairing() }) {
                    Text("Confirm Match")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelOutgoingPairing() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Incoming Pairing Request Dialog
    if (uiState.showIncomingPairingDialog && uiState.incomingSasCode != null) {
        AlertDialog(
            onDismissRequest = { viewModel.rejectIncomingPairing() },
            title = { Text("Pairing Request") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${uiState.incomingPairingDeviceName ?: "Nearby device"} wants to pair with your device. Verify code:")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = uiState.incomingSasCode!!,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.acceptIncomingPairing() }) {
                    Text("Accept Pairing")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.rejectIncomingPairing() }) {
                    Text("Reject")
                }
            }
        )
    }
}

@Composable
fun DiscoveredDeviceCard(
    device: DiscoveredDevice,
    isPaired: Boolean,
    onPair: () -> Unit,
    onSync: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = when (device.deviceType.lowercase()) {
                    "tablet" -> Icons.Default.Tablet
                    "eink" -> Icons.AutoMirrored.Filled.MenuBook
                    else -> Icons.Default.PhoneAndroid
                }
                Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = device.deviceName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${device.hostAddress}:${device.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "ID: ${device.deviceId}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            if (isPaired) {
                Button(onClick = onSync) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sync")
                }
            } else {
                OutlinedButton(onClick = onPair) {
                    Text("Pair")
                }
            }
        }
    }
}

@Composable
fun TrustedDeviceCard(
    device: SyncDeviceEntity,
    onForget: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = device.deviceName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ID: ${device.deviceId} • Trusted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onForget) {
                Icon(Icons.Default.Delete, contentDescription = "Forget Device", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
