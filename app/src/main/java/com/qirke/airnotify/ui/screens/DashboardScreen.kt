package com.qirke.airnotify.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qirke.airnotify.ble.AirHapticPattern
import com.qirke.airnotify.data.PreferencesManager
import com.qirke.airnotify.model.DeviceConfig
import com.qirke.airnotify.model.LogEntry
import com.qirke.airnotify.service.AirBridgeService
import com.qirke.airnotify.service.AirNotificationTrigger
import com.qirke.airnotify.ui.components.BleScanDialog
import com.qirke.airnotify.ui.i18n.LocalStrings
import com.qirke.airnotify.ui.i18n.localizedName
import com.qirke.airnotify.ui.theme.StatusGreen
import com.qirke.airnotify.ui.theme.StatusRed
import kotlinx.coroutines.delay
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val strings = LocalStrings.current
    val prefs = remember { PreferencesManager.getInstance(context) }

    var deviceConfig by remember { mutableStateOf(prefs.getDeviceConfig()) }
    var logs by remember { mutableStateOf(prefs.getLogs()) }
    var showScanDialog by remember { mutableStateOf(false) }
    var syncPaused by remember { mutableStateOf(prefs.isSyncPaused()) }
    var syncRemainingMillis by remember { mutableStateOf(prefs.getSyncPauseRemainingMillis()) }

    val isVibrating by AirBridgeService.isVibratingFlow.collectAsState()
    val liveStatus by AirBridgeService.currentStatusFlow.collectAsState()

    // Countdown timer effect for sync pause
    LaunchedEffect(syncPaused) {
        while (syncPaused) {
            val rem = prefs.getSyncPauseRemainingMillis()
            syncRemainingMillis = rem
            if (rem <= 0L) {
                syncPaused = false
                break
            }
            delay(1000L)
        }
    }

    // Refresh state when liveStatus changes
    LaunchedEffect(liveStatus) {
        logs = prefs.getLogs()
        deviceConfig = prefs.getDeviceConfig()
        syncPaused = prefs.isSyncPaused()
        syncRemainingMillis = prefs.getSyncPauseRemainingMillis()
    }

    if (showScanDialog) {
        BleScanDialog(
            onDismiss = { showScanDialog = false },
            onDeviceSelected = { dev ->
                prefs.saveDeviceConfig(
                    DeviceConfig(
                        name = dev.name,
                        address = dev.address,
                        masterEnabled = true
                    )
                )
                deviceConfig = prefs.getDeviceConfig()
                AirBridgeService.checkConnection(context)
                showScanDialog = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        // --- 1. FITBIT AIR CONNECTION & PAIRING CARD ---
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (deviceConfig.address != null) {
                                            if (isVibrating) Color(0xFF60A5FA) else StatusGreen
                                        } else StatusRed
                                    )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (deviceConfig.address != null) deviceConfig.name else strings.noBandPaired,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (deviceConfig.address == null) {
                            Button(
                                onClick = { showScanDialog = true },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.BluetoothSearching, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.pairNow)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (deviceConfig.address != null) {
                            "${strings.address}: ${deviceConfig.address}"
                        } else {
                            strings.pairPrompt
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isVibrating) Icons.Default.Vibration else Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = liveStatus,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2
                            )
                        }
                    }

                    if (deviceConfig.address != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { AirBridgeService.checkConnection(context) },
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.checkLink, style = MaterialTheme.typography.labelSmall)
                            }

                            FilledTonalButton(
                                onClick = { showScanDialog = true },
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.BluetoothSearching, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.changeBand, style = MaterialTheme.typography.labelSmall)
                            }

                            TextButton(
                                onClick = {
                                    prefs.saveDeviceConfig(DeviceConfig(name = "No Band Paired", address = null))
                                    deviceConfig = prefs.getDeviceConfig()
                                },
                                modifier = Modifier.height(38.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(strings.forgetBand, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // --- 2. MASTER ENABLE SWITCH (AIRNOTIFY SERVICE ON/OFF) ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.bandAlertsTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (deviceConfig.masterEnabled) strings.bandAlertsEnabled else strings.bandAlertsDisabled,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Switch(
                        checked = deviceConfig.masterEnabled,
                        onCheckedChange = { enabled ->
                            prefs.setMasterEnabled(enabled)
                            deviceConfig = prefs.getDeviceConfig()
                            if (!enabled) {
                                AirBridgeService.stopAlerts(context)
                            }
                        }
                    )
                }
            }
        }

        // --- 2.5 GOOGLE HEALTH COEXISTENCE & SYNC CARD ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (syncPaused) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = if (syncPaused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.coexistenceTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.syncCooldownNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (syncPaused) {
                        val minutes = (syncRemainingMillis / 1000) / 60
                        val seconds = (syncRemainingMillis / 1000) % 60
                        val timeStr = String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = String.format(strings.pauseSyncActive, timeStr),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }

                            Button(
                                onClick = {
                                    prefs.clearSyncPause()
                                    syncPaused = false
                                    syncRemainingMillis = 0L
                                }
                            ) {
                                Text(strings.resumeAlertsNow, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else {
                        FilledTonalButton(
                            onClick = {
                                prefs.pauseForSync(5)
                                AirBridgeService.stopAlerts(context)
                                syncPaused = true
                                syncRemainingMillis = prefs.getSyncPauseRemainingMillis()
                                launchHealthApp(context)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.pauseSyncButton, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // --- 3. QUICK VIBRATION TEST CARD ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Vibration,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.testVibration,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            AirHapticPattern.SINGLE,
                            AirHapticPattern.DOUBLE,
                            AirHapticPattern.TRIPLE,
                            AirHapticPattern.GENTLE,
                            AirHapticPattern.HEARTBEAT,
                            AirHapticPattern.SYNCOPATED,
                            AirHapticPattern.URGENT,
                            AirHapticPattern.SPACED
                        ).forEach { pat ->
                            FilledTonalButton(
                                onClick = {
                                    AirBridgeService.enqueue(
                                        context,
                                        AirNotificationTrigger(
                                            source = "Test ${pat.displayName}",
                                            pattern = pat,
                                            isManual = true
                                        )
                                    )
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(pat.localizedName(strings), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        // --- 4. LIVE ACTIVITY LOG ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.recentEvents,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (logs.isNotEmpty()) {
                    TextButton(onClick = {
                        prefs.clearLogs()
                        logs = emptyList()
                    }) {
                        Text(strings.clearLogs)
                    }
                }
            }
        }

        if (logs.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strings.noEvents,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(logs.take(15)) { log ->
                LogItemRow(log)
            }
        }
    }
}

@Composable
fun LogItemRow(log: LogEntry) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val formattedTime = remember(log.timestamp) { timeFormat.format(Date(log.timestamp)) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (log.isError) StatusRed else StatusGreen)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = log.tag,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = log.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun launchHealthApp(context: Context) {
    val pm = context.packageManager
    val packages = listOf(
        "com.fitbit.FitbitMobile",
        "com.google.android.apps.fitness",
        "com.google.android.apps.healthdata"
    )
    for (pkg in packages) {
        val launchIntent = pm.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(launchIntent)
                return
            } catch (e: Exception) {
                Timber.w(e, "Failed to launch health package: %s", pkg)
            }
        }
    }
}
