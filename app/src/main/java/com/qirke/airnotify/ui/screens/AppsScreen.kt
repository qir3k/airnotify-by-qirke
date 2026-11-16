package com.qirke.airnotify.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.window.Dialog
import com.qirke.airnotify.ble.AirHapticPattern
import com.qirke.airnotify.data.ContactHelper
import com.qirke.airnotify.data.PreferencesManager
import com.qirke.airnotify.model.AppRule
import com.qirke.airnotify.model.FilterMode
import com.qirke.airnotify.service.AirBridgeService
import com.qirke.airnotify.service.AirNotificationTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppsScreen() {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context) }

    var appRules by remember { mutableStateOf(prefs.getAppRules()) }
    var selectedAppForEdit by remember { mutableStateOf<AppRule?>(null) }
    var showAddAppDialog by remember { mutableStateOf(false) }

    fun refreshRules() {
        appRules = prefs.getAppRules()
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddAppDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add App") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp)
        ) {
            item {
                Text(
                    text = "Monitored Apps & Filters",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Choose which apps vibrate your band, with custom patterns and favorite person filtering.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
            }

            if (appRules.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No apps configured yet. Tap '+ Add App' to select an app.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(appRules) { rule ->
                    AppRuleCard(
                        rule = rule,
                        onToggle = { enabled ->
                            val updated = rule.copy(isEnabled = enabled)
                            prefs.saveOrUpdateAppRule(updated)
                            refreshRules()
                        },
                        onClick = { selectedAppForEdit = rule },
                        onDelete = {
                            prefs.removeAppRule(rule.packageName)
                            refreshRules()
                        }
                    )
                }
            }
        }
    }

    // Edit App Dialog / Sheet
    selectedAppForEdit?.let { rule ->
        EditAppRuleDialog(
            rule = rule,
            onDismiss = { selectedAppForEdit = null },
            onSave = { updatedRule ->
                prefs.saveOrUpdateAppRule(updatedRule)
                refreshRules()
                selectedAppForEdit = null
            }
        )
    }

    // Add App Dialog (Installed Apps list)
    if (showAddAppDialog) {
        AddAppDialog(
            existingPackages = appRules.map { it.packageName }.toSet(),
            onDismiss = { showAddAppDialog = false },
            onAppSelected = { appName, pkgName ->
                val newRule = AppRule(
                    packageName = pkgName,
                    appName = appName,
                    isEnabled = true,
                    pattern = AirHapticPattern.DOUBLE,
                    filterMode = FilterMode.ALL_NOTIFICATIONS,
                    favoritePersons = emptyList()
                )
                prefs.saveOrUpdateAppRule(newRule)
                refreshRules()
                showAddAppDialog = false
                selectedAppForEdit = newRule
            }
        )
    }
}

@Composable
fun AppRuleCard(
    rule: AppRule,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.isEnabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = rule.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = onToggle
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SuggestionChip(
                    onClick = { onClick() },
                    label = { Text(rule.pattern.displayName, style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Default.Vibration, contentDescription = null, modifier = Modifier.size(14.dp)) }
                )

                SuggestionChip(
                    onClick = { onClick() },
                    label = {
                        val filterText = when (rule.filterMode) {
                            FilterMode.ALL_NOTIFICATIONS -> "All Senders"
                            FilterMode.FAVORITES_ONLY -> {
                                if (rule.favoritePersons.isEmpty()) "Favorites (None added!)"
                                else "Favorites: ${rule.favoritePersons.joinToString(", ")}"
                            }
                            FilterMode.EXCLUDE_SPECIFIED -> "Exclude: ${rule.favoritePersons.joinToString(", ")}"
                        }
                        Text(filterText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    },
                    icon = { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = if (rule.filterMode == FilterMode.FAVORITES_ONLY) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                )

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete Rule",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditAppRuleDialog(
    rule: AppRule,
    onDismiss: () -> Unit,
    onSave: (AppRule) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context) }
    var isEnabled by remember { mutableStateOf(rule.isEnabled) }
    var pattern by remember { mutableStateOf(rule.pattern) }
    var filterMode by remember { mutableStateOf(rule.filterMode) }
    val favoritePersons = remember { mutableStateListOf(*rule.favoritePersons.toTypedArray()) }
    var newPersonText by remember { mutableStateOf("") }
    var showContactPicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "Edit ${rule.appName}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = rule.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Vibration Pattern Picker
                item {
                    Text(
                        text = "Vibration Pattern",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            AirHapticPattern.SINGLE,
                            AirHapticPattern.DOUBLE,
                            AirHapticPattern.TRIPLE,
                            AirHapticPattern.URGENT
                        ).forEach { pat ->
                            FilterChip(
                                selected = pattern == pat,
                                onClick = { pattern = pat },
                                label = { Text(pat.displayName) },
                                leadingIcon = if (pattern == pat) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }
                    }
                }

                // Filter Mode Picker
                item {
                    Text(
                        text = "Notification Filter Mode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterMode.values().forEach { mode ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (filterMode == mode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { filterMode = mode }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = filterMode == mode,
                                        onClick = { filterMode = mode }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = mode.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = mode.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Favorite Persons Section (when not ALL)
                if (filterMode != FilterMode.ALL_NOTIFICATIONS) {
                    item {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (filterMode == FilterMode.FAVORITES_ONLY) "Favorite Persons" else "Excluded Persons",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )

                                TextButton(onClick = { showContactPicker = true }) {
                                    Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pick Contact", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            Text(
                                text = "e.g. 'Mis' matches 'Misica', 'Miś', or '❤️ Misica ❤️' (matches words, emotes & substrings)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            // Tag chips
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                favoritePersons.forEach { person ->
                                    InputChip(
                                        selected = true,
                                        onClick = { favoritePersons.remove(person) },
                                        label = { Text(person) },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove",
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    )
                                }
                            }

                            // Recent Senders suggestion chips (captured from notifications)
                            val recentSenders = remember { prefs.getRecentSenders(rule.packageName) }
                            val unaddedRecent = recentSenders.filterNot { favoritePersons.contains(it) }
                            if (unaddedRecent.isNotEmpty()) {
                                Text(
                                    text = "Recent Senders (tap to add):",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                                ) {
                                    unaddedRecent.take(8).forEach { sender ->
                                        SuggestionChip(
                                            onClick = { favoritePersons.add(sender) },
                                            label = { Text(sender) },
                                            icon = {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        )
                                    }
                                }
                            }

                            // Add tag input
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newPersonText,
                                    onValueChange = { newPersonText = it },
                                    label = { Text("Add person / keyword") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick = {
                                        val trimmed = newPersonText.trim()
                                        if (trimmed.isNotEmpty() && !favoritePersons.contains(trimmed)) {
                                            favoritePersons.add(trimmed)
                                            newPersonText = ""
                                        }
                                    }
                                ) {
                                    Text("Add")
                                }
                            }
                        }
                    }
                }

                // Actions: Test, Cancel, Save
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                AirBridgeService.enqueue(
                                    context,
                                    AirNotificationTrigger(
                                        source = "Test ${rule.appName}",
                                        pattern = pattern,
                                        isManual = true
                                    )
                                )
                            }
                        ) {
                            Text("Test")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                val updated = rule.copy(
                                    isEnabled = isEnabled,
                                    pattern = pattern,
                                    filterMode = filterMode,
                                    favoritePersons = favoritePersons.toList()
                                )
                                onSave(updated)
                            }
                        ) {
                            Text("Save")
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
                if (!favoritePersons.contains(contactName)) {
                    favoritePersons.add(contactName)
                }
                showContactPicker = false
            }
        )
    }
}

@Composable
fun AddAppDialog(
    existingPackages: Set<String>,
    onDismiss: () -> Unit,
    onAppSelected: (String, String) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val list = packages
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || it.packageName.contains("messaging") || it.packageName.contains("dialer") }
                .map { appInfo ->
                    val label = pm.getApplicationLabel(appInfo).toString()
                    label to appInfo.packageName
                }
                .sortedBy { it.first.lowercase() }
            installedApps = list
            isLoading = false
        }
    }

    val filtered = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter {
            it.first.contains(searchQuery, ignoreCase = true) || it.second.contains(searchQuery, ignoreCase = true)
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
                Text(
                    text = "Select App to Monitor",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search installed apps...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered) { (name, pkg) ->
                            val alreadyAdded = existingPackages.contains(pkg)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (alreadyAdded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !alreadyAdded) {
                                        onAppSelected(name, pkg)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        Text(text = pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (alreadyAdded) {
                                        Text("Added", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun ContactPickerDialog(
    onDismiss: () -> Unit,
    onContactSelected: (String) -> Unit
) {
    val context = LocalContext.current
    var contacts by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            contacts = ContactHelper.getAllContacts(context)
            isLoading = false
        }
    }

    val filtered = remember(contacts, search) {
        if (search.isBlank()) contacts
        else contacts.filter { it.first.contains(search, ignoreCase = true) || it.second.contains(search) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .padding(vertical = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Text(
                    text = "Pick Contact",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search contacts...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (ContactHelper.hasContactPermission(context)) "No contacts found" else "Contacts permission required",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filtered) { (name, number) ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onContactSelected(name) }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(text = name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    if (number.isNotBlank()) {
                                        Text(text = number, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
