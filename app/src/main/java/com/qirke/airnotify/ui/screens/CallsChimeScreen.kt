package com.qirke.airnotify.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qirke.airnotify.ble.AirHapticPattern
import com.qirke.airnotify.chime.HourlyChimeScheduler
import com.qirke.airnotify.data.ContactHelper
import com.qirke.airnotify.data.PreferencesManager
import com.qirke.airnotify.model.FilterMode
import com.qirke.airnotify.service.AirBridgeService
import com.qirke.airnotify.service.AirNotificationTrigger

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CallsChimeScreen() {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context) }

    var quietConfig by remember { mutableStateOf(prefs.getQuietHoursConfig()) }
    var chimeConfig by remember { mutableStateOf(prefs.getHourlyChimeConfig()) }
    var callConfig by remember { mutableStateOf(prefs.getCallConfig()) }

    var newCallerText by remember { mutableStateOf("") }
    var showContactPicker by remember { mutableStateOf(false) }

    fun saveCall() {
        prefs.saveCallConfig(callConfig)
    }

    fun saveChime() {
        prefs.saveHourlyChimeConfig(chimeConfig)
        HourlyChimeScheduler.scheduleNextChime(context)
    }

    fun saveQuiet() {
        prefs.saveQuietHoursConfig(quietConfig)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        item {
            Text(
                text = "Calls & Hourly Chimes",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Manage incoming phone call alerts and top-of-the-hour wrist chimes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // =====================================================================
        // 1. HOURLY CHIME CARD
        // =====================================================================
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Hourly Chime",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Gentle wrist tap on every hour :00",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = chimeConfig.isEnabled,
                            onCheckedChange = {
                                chimeConfig = chimeConfig.copy(isEnabled = it)
                                saveChime()
                            }
                        )
                    }

                    if (chimeConfig.isEnabled) {
                        Spacer(modifier = Modifier.height(14.dp))

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
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (quietConfig.isEnabled && quietConfig.muteChimes) {
                                        "Chimes every hour at :00, automatically silenced during your sleep window (${String.format("%02d:00", quietConfig.startHour)} - ${String.format("%02d:00", quietConfig.endHour)})."
                                    } else {
                                        "Chimes 24/7 on every hour at :00."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Chime pattern
                        Text(
                            text = "Chime Pattern",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                AirHapticPattern.GENTLE,
                                AirHapticPattern.SINGLE,
                                AirHapticPattern.DOUBLE
                            ).forEach { pat ->
                                FilterChip(
                                    selected = chimeConfig.pattern == pat,
                                    onClick = {
                                        chimeConfig = chimeConfig.copy(pattern = pat)
                                        saveChime()
                                    },
                                    label = { Text(pat.displayName) },
                                    leadingIcon = if (chimeConfig.pattern == pat) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = {
                                AirBridgeService.enqueue(
                                    context,
                                    AirNotificationTrigger(
                                        source = "Test Hourly Chime",
                                        pattern = chimeConfig.pattern,
                                        isManual = true
                                    )
                                )
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Chime Now")
                        }
                    }
                }
            }
        }

        // =====================================================================
        // 3. PHONE CALLS CARD
        // =====================================================================
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhoneInTalk, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Phone Calls",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Switch(
                            checked = callConfig.isEnabled,
                            onCheckedChange = {
                                callConfig = callConfig.copy(isEnabled = it)
                                saveCall()
                            }
                        )
                    }

                    if (callConfig.isEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))

                        if (quietConfig.isEnabled && quietConfig.muteCalls) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Bedtime,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Call vibrations are silenced during Sleep Window (${String.format("%02d:00", quietConfig.startHour)} - ${String.format("%02d:00", quietConfig.endHour)}).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Continuous ringing toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    callConfig = callConfig.copy(isContinuous = !callConfig.isContinuous)
                                    saveCall()
                                }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Continuous Ringing",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Keeps pulsing until answered, declined, or silenced",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = callConfig.isContinuous,
                                onCheckedChange = {
                                    callConfig = callConfig.copy(isContinuous = it)
                                    saveCall()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Filter mode tabs
                        Text(
                            text = "Filter Callers",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = callConfig.filterMode == FilterMode.ALL_NOTIFICATIONS,
                                onClick = {
                                    callConfig = callConfig.copy(filterMode = FilterMode.ALL_NOTIFICATIONS)
                                    saveCall()
                                },
                                label = { Text("All Incoming Calls") },
                                leadingIcon = if (callConfig.filterMode == FilterMode.ALL_NOTIFICATIONS) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )

                            FilterChip(
                                selected = callConfig.filterMode == FilterMode.FAVORITES_ONLY,
                                onClick = {
                                    callConfig = callConfig.copy(filterMode = FilterMode.FAVORITES_ONLY)
                                    saveCall()
                                },
                                label = { Text("Favorite Callers Only") },
                                leadingIcon = if (callConfig.filterMode == FilterMode.FAVORITES_ONLY) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }

                        // Favorite callers list if FAVORITES_ONLY
                        if (callConfig.filterMode == FilterMode.FAVORITES_ONLY) {
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Allowed Callers (${callConfig.favoritePersons.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // 1-Tap Starred Contacts import
                                    TextButton(
                                        onClick = {
                                            val starred = ContactHelper.getStarredContacts(context)
                                            val combined = (callConfig.favoritePersons + starred).distinct()
                                            callConfig = callConfig.copy(favoritePersons = combined)
                                            saveCall()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Starred", style = MaterialTheme.typography.labelSmall)
                                    }

                                    // Contact picker
                                    TextButton(
                                        onClick = { showContactPicker = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Pick", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            // Chips
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                callConfig.favoritePersons.forEach { caller ->
                                    InputChip(
                                        selected = true,
                                        onClick = {
                                            val updated = callConfig.favoritePersons.filterNot { it == caller }
                                            callConfig = callConfig.copy(favoritePersons = updated)
                                            saveCall()
                                        },
                                        label = { Text(caller) },
                                        trailingIcon = {
                                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newCallerText,
                                    onValueChange = { newCallerText = it },
                                    label = { Text("Add caller name or phone number") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick = {
                                        val trimmed = newCallerText.trim()
                                        if (trimmed.isNotEmpty() && !callConfig.favoritePersons.contains(trimmed)) {
                                            callConfig = callConfig.copy(favoritePersons = callConfig.favoritePersons + trimmed)
                                            saveCall()
                                            newCallerText = ""
                                        }
                                    }
                                ) {
                                    Text("Add")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = {
                                AirBridgeService.enqueue(
                                    context,
                                    AirNotificationTrigger(
                                        source = "Test Call",
                                        pattern = callConfig.pattern,
                                        isCall = true,
                                        isManual = true
                                    )
                                )
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.PhoneInTalk, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Call Pulse")
                        }
                    }
                }
            }
        }
    }

    if (showContactPicker) {
        ContactPickerDialog(
            onDismiss = { showContactPicker = false },
            onContactSelected = { contactName ->
                if (!callConfig.favoritePersons.contains(contactName)) {
                    callConfig = callConfig.copy(favoritePersons = callConfig.favoritePersons + contactName)
                    saveCall()
                }
                showContactPicker = false
            }
        )
    }
}
