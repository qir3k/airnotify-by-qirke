package com.qirke.airnotify.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import com.qirke.airnotify.ble.BleCoexistenceManager
import com.qirke.airnotify.data.PreferencesManager
import com.qirke.airnotify.model.DeviceConfig
import com.qirke.airnotify.model.LogEntry
import com.qirke.airnotify.service.AirBridgeService
import com.qirke.airnotify.service.AirNotificationTrigger
import com.qirke.airnotify.ui.theme.StatusGreen
import com.qirke.airnotify.ui.theme.StatusRed
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context) }

    var deviceConfig by remember { mutableStateOf(prefs.getDeviceConfig()) }
    var logs by remember { mutableStateOf(prefs.getLogs()) }
    var isSyncPaused by remember { mutableStateOf(prefs.isSyncPaused()) }
    var quietConfig by remember { mutableStateOf(prefs.getQuietHoursConfig()) }
    val installedHealthApps = remember { BleCoexistenceManager.findInstalledHealthApps(context) }

    val isVibrating by AirBridgeService.isVibratingFlow.collectAsState()
    val liveStatus by AirBridgeService.currentStatusFlow.collectAsState()

    // Refresh logs periodically or when status changes
    LaunchedEffect(liveStatus) {
        logs = prefs.getLogs()
        deviceConfig = prefs.getDeviceConfig()
        isSyncPaused = prefs.isSyncPaused()
        quietConfig = prefs.getQuietHoursConfig()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        // Band Connection Card
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
                                text = if (deviceConfig.address != null) deviceConfig.name else "No Band Paired",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (deviceConfig.address == null) {
                            FilledTonalButton(
                                onClick = onNavigateToSettings,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Pair Now")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (deviceConfig.address != null) {
                            "Address: ${deviceConfig.address}"
                        } else {
                            "Pair your Fitbit Air to start vibrating on notifications"
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
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = { AirBridgeService.checkConnection(context) },
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Check Link", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        // Master Enable Switch
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
                            text = "Band Alerts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (deviceConfig.masterEnabled) "Wrist vibrations enabled" else "All vibrations paused",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = deviceConfig.masterEnabled,
                        onCheckedChange = {
                            prefs.setMasterEnabled(it)
                            deviceConfig = prefs.getDeviceConfig()
                        }
                    )
                }
            }
        }

        // Active Sleep Window Indicator
        if (quietConfig.isEnabled && quietConfig.isQuietNow()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Bedtime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Sleep / Inactive Window Active",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Alerts and chimes are currently silenced (until ${String.format("%02d:00", quietConfig.endHour)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }

        // Google Health / Pulse Coexistence Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSyncPaused) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isSyncPaused) Icons.Default.PauseCircle else Icons.Default.Sync,
                                contentDescription = null,
                                tint = if (isSyncPaused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isSyncPaused) "Health Sync Pause Active" else "Health Apps Coexistence",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (isSyncPaused) {
                            FilledTonalButton(
                                onClick = {
                                    prefs.clearSyncPause()
                                    isSyncPaused = false
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Resume Now", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isSyncPaused) {
                            val remainMin = (prefs.getSyncPauseRemainingMillis() / 60_000L) + 1
                            "Alerts are paused for ~${remainMin} min so Google Health or Pulse can sync without Bluetooth conflicts."
                        } else {
                            "AirNotify connects on-demand and immediately frees Bluetooth for Google Health & Pulse. If sync ever gets stuck, pause alerts for 5 min."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isSyncPaused) {
                            OutlinedButton(
                                onClick = {
                                    prefs.pauseForSync(5)
                                    isSyncPaused = true
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pause for Sync (5m)", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        if (installedHealthApps.isNotEmpty()) {
                            val (pkg, name) = installedHealthApps.first()
                            FilledTonalButton(
                                onClick = {
                                    prefs.pauseForSync(5)
                                    isSyncPaused = true
                                    BleCoexistenceManager.launchHealthApp(context, pkg)
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Open $name", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // Quick Vibration Test Card
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
                            text = "Test Wrist Vibrations",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "Tap any pattern to test physical vibration on your Fitbit Air:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                AirBridgeService.enqueue(
                                    context,
                                    AirNotificationTrigger(
                                        source = "Test Single",
                                        pattern = AirHapticPattern.SINGLE,
                                        isManual = true
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text("Single", style = MaterialTheme.typography.labelSmall)
                        }

                        Button(
                            onClick = {
                                AirBridgeService.enqueue(
                                    context,
                                    AirNotificationTrigger(
                                        source = "Test Double",
                                        pattern = AirHapticPattern.DOUBLE,
                                        isManual = true
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text("Double", style = MaterialTheme.typography.labelSmall)
                        }

                        Button(
                            onClick = {
                                AirBridgeService.enqueue(
                                    context,
                                    AirNotificationTrigger(
                                        source = "Test Triple",
                                        pattern = AirHapticPattern.TRIPLE,
                                        isManual = true
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text("Triple", style = MaterialTheme.typography.labelSmall)
                        }

                        FilledTonalButton(
                            onClick = {
                                AirBridgeService.enqueue(
                                    context,
                                    AirNotificationTrigger(
                                        source = "Test Urgent",
                                        pattern = AirHapticPattern.URGENT,
                                        isManual = true
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text("Burst", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Live Activity Log
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Activity Log",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (logs.isNotEmpty()) {
                    TextButton(onClick = {
                        prefs.clearLogs()
                        logs = emptyList()
                    }) {
                        Text("Clear")
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
                            text = "No alerts yet. Notifications and vibrations will appear here.",
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
