package com.qirke.airnotify.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.qirke.airnotify.BuildConfig
import com.qirke.airnotify.ble.BleScanner
import com.qirke.airnotify.ble.DiscoveredDevice
import com.qirke.airnotify.data.ContactHelper
import com.qirke.airnotify.data.PreferencesManager
import com.qirke.airnotify.service.AirNotificationListenerService
import com.qirke.airnotify.ui.theme.StatusGreen
import com.qirke.airnotify.ui.theme.StatusRed
import com.qirke.airnotify.updater.InAppUpdater
import com.qirke.airnotify.updater.UpdateInfo
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferencesManager.getInstance(context) }

    var deviceConfig by remember { mutableStateOf(prefs.getDeviceConfig()) }
    var showScanDialog by remember { mutableStateOf(false) }

    // Updater State
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    var autoCheckUpdates by remember { mutableStateOf(prefs.isAutoCheckUpdatesEnabled()) }
    var coexistenceGuard by remember { mutableStateOf(prefs.isCoexistenceGuardEnabled()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        item {
            Text(
                text = "Settings & Updates",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Device pairing, in-app updates, and system permissions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // --- IN-APP UPDATER CARD ---
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "In-App Updater",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Installed Version: v${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!isCheckingUpdate && !isDownloadingUpdate) {
                            FilledTonalButton(
                                onClick = {
                                    scope.launch {
                                        isCheckingUpdate = true
                                        updateMessage = null
                                        try {
                                            val info = InAppUpdater.checkForUpdate()
                                            updateInfo = info
                                            if (!info.hasUpdate) {
                                                updateMessage = "You have the latest version installed (v${BuildConfig.VERSION_NAME})!"
                                            }
                                        } catch (e: Exception) {
                                            updateMessage = "Update check: ${e.localizedMessage ?: "Network error"}"
                                        } finally {
                                            isCheckingUpdate = false
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Check Now")
                            }
                        }
                    }

                    if (isCheckingUpdate) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Checking GitHub for updates...", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    updateMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Update Available Section
                    updateInfo?.let { info ->
                        if (info.hasUpdate) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.NewReleases, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "New Update Available: v${info.versionName}!",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = info.releaseNotes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    if (isDownloadingUpdate) {
                                        Column {
                                            LinearProgressIndicator(
                                                progress = { downloadProgress / 100f },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Downloading APK: $downloadProgress%",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    } else if (downloadedApk != null) {
                                        Button(
                                            onClick = { InAppUpdater.launchInstaller(context, downloadedApk!!) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Install Downloaded Update")
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    isDownloadingUpdate = true
                                                    downloadProgress = 0
                                                    try {
                                                        val apk = InAppUpdater.downloadAndInstall(
                                                            context = context,
                                                            downloadUrl = info.downloadUrl,
                                                            versionName = info.versionName,
                                                            onProgress = { downloadProgress = it }
                                                        )
                                                        downloadedApk = apk
                                                        InAppUpdater.launchInstaller(context, apk)
                                                    } catch (e: Exception) {
                                                        updateMessage = "Download failed: ${e.localizedMessage}"
                                                    } finally {
                                                        isDownloadingUpdate = false
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Download & Install v${info.versionName}")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Check for updates on launch",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Switch(
                            checked = autoCheckUpdates,
                            onCheckedChange = {
                                autoCheckUpdates = it
                                prefs.setAutoCheckUpdatesEnabled(it)
                            }
                        )
                    }
                }
            }
        }

        // --- FITBIT AIR DEVICE PAIRING CARD ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Watch, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Fitbit Air Device",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (deviceConfig.address != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = deviceConfig.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "MAC: ${deviceConfig.address}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showScanDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Change Band")
                            }

                            TextButton(
                                onClick = {
                                    prefs.saveDeviceConfig(deviceConfig.copy(address = null))
                                    deviceConfig = prefs.getDeviceConfig()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Forget Band", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    } else {
                        Text(
                            text = "No band paired. Scan to detect your Fitbit Air band automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { showScanDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan for Fitbit Air")
                        }
                    }
                }
            }
        }

        // --- COEXISTENCE GUARD CARD ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.SyncLock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Smart Coexistence Guard",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Prevents conflicts with Google Health & Pulse",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = coexistenceGuard,
                            onCheckedChange = {
                                coexistenceGuard = it
                                prefs.setCoexistenceGuardEnabled(it)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "When enabled, AirNotify cleanly releases Bluetooth immediately after every vibration, detects when Google Health is actively transferring sync bursts, and yields the radio with an automated settle delay to prevent connection drops (GATT Error 133).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // --- SYSTEM PERMISSIONS HEALTH CHECK ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "System Permissions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ensure all permissions are active for reliable background alerts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )

                    // 1. Notification Listener
                    val isListenerConnected = AirNotificationListenerService.isConnected
                    PermissionRow(
                        title = "Notification Listener",
                        subtitle = if (isListenerConnected) "Active & intercepting alerts" else "Tap to enable in Android settings",
                        isGranted = isListenerConnected,
                        onAction = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 2. Battery Optimization
                    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    val isBatteryIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        pm?.isIgnoringBatteryOptimizations(context.packageName) == true
                    } else true

                    PermissionRow(
                        title = "Battery Optimization",
                        subtitle = if (isBatteryIgnored) "Unrestricted in background" else "Request unrestricted battery",
                        isGranted = isBatteryIgnored,
                        onAction = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                runCatching { context.startActivity(intent) }
                            }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 3. Contacts Permission
                    val hasContacts = ContactHelper.hasContactPermission(context)
                    PermissionRow(
                        title = "Contacts Access",
                        subtitle = if (hasContacts) "Granted (for favorite person filtering)" else "Required for contact matching",
                        isGranted = hasContacts,
                        onAction = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }

        // --- ABOUT CARD ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "AirNotify by qirke",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Fitbit Air Bluetooth Vibration Bridge",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }

    if (showScanDialog) {
        BleScanDialog(
            onDismiss = { showScanDialog = false },
            onDeviceSelected = { dev ->
                prefs.saveDeviceAddress(dev.address, dev.name)
                deviceConfig = prefs.getDeviceConfig()
                showScanDialog = false
            }
        )
    }
}

@Composable
fun PermissionRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAction() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isGranted) StatusGreen else StatusRed,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp)
            )
        }

        if (!isGranted) {
            FilledTonalButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Enable", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun BleScanDialog(
    onDismiss: () -> Unit,
    onDeviceSelected: (DiscoveredDevice) -> Unit
) {
    val context = LocalContext.current
    val scanner = remember { BleScanner(context) }
    val discoveredList = remember { mutableStateListOf<DiscoveredDevice>() }
    var scanError by remember { mutableStateOf<String?>(null) }
    var isScanning by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        scanner.startScan(
            onDeviceFound = { dev ->
                val idx = discoveredList.indexOfFirst { it.address.equals(dev.address, ignoreCase = true) }
                if (idx >= 0) {
                    discoveredList[idx] = dev
                } else {
                    if (dev.isFitbit) {
                        discoveredList.add(0, dev) // Put Fitbit bands at the top
                    } else {
                        discoveredList.add(dev)
                    }
                }
            },
            onError = { error ->
                scanError = error
                isScanning = false
            }
        )

        onDispose {
            scanner.stopScan()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Scan Nearby Bands",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Make sure your Fitbit Air is awake and nearby.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                scanError?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (discoveredList.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Searching for Bluetooth devices...", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(discoveredList) { dev ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (dev.isFitbit) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onDeviceSelected(dev) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = dev.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (dev.isFitbit) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                                    Text("Fitbit", color = MaterialTheme.colorScheme.onPrimary)
                                                }
                                            }
                                        }
                                        Text(
                                            text = dev.address,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Button(
                                        onClick = { onDeviceSelected(dev) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Pair")
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}
