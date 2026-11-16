package com.qirke.airnotify.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.qirke.airnotify.data.PreferencesManager
import com.qirke.airnotify.service.AirBridgeService
import com.qirke.airnotify.ui.i18n.EnglishStrings
import com.qirke.airnotify.ui.i18n.LocalStrings
import com.qirke.airnotify.ui.i18n.PolishStrings
import com.qirke.airnotify.ui.screens.AppsScreen
import com.qirke.airnotify.ui.screens.CallsChimeScreen
import com.qirke.airnotify.ui.screens.DashboardScreen
import com.qirke.airnotify.ui.screens.SettingsScreen
import com.qirke.airnotify.ui.theme.AirNotifyTheme
import com.qirke.airnotify.ui.theme.StatusGreen
import com.qirke.airnotify.ui.theme.StatusRed

enum class Screen(val title: String, val navLabel: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", "Dashboard", Icons.Default.Dashboard),
    APPS("Apps & Rules", "Apps", Icons.Default.Apps),
    CALLS_CHIME("Calls & Chimes", "Calls", Icons.Default.RingVolume),
    SETTINGS("Settings", "Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable true full-screen Edge-to-Edge with transparent system bars
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Request runtime permissions if missing
        requestMissingPermissions()

        val prefs = PreferencesManager.getInstance(this)

        setContent {
            var currentLang by remember { mutableStateOf(prefs.getAppLanguage()) }
            var currentAccent by remember { mutableStateOf(prefs.getThemeAccent()) }
            var currentThemeMode by remember { mutableStateOf(prefs.getThemeMode()) }
            val strings = if (currentLang == "pl") PolishStrings else EnglishStrings

            CompositionLocalProvider(LocalStrings provides strings) {
                AirNotifyTheme(
                    accentId = currentAccent,
                    themeMode = currentThemeMode
                ) {
                    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
                    val isVibrating by AirBridgeService.isVibratingFlow.collectAsState()
                    val liveStatus by AirBridgeService.currentStatusFlow.collectAsState()
                    var deviceConfig by remember { mutableStateOf(prefs.getDeviceConfig()) }

                    LaunchedEffect(liveStatus) {
                        deviceConfig = prefs.getDeviceConfig()
                    }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            TopAppBar(
                                title = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "AirNotify",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = if (deviceConfig.address != null) {
                                                if (isVibrating) MaterialTheme.colorScheme.primaryContainer else StatusGreen.copy(alpha = 0.2f)
                                            } else StatusRed.copy(alpha = 0.2f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(if (deviceConfig.address != null) StatusGreen else StatusRed)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (deviceConfig.address != null) {
                                                        if (isVibrating) "Pulsing..." else strings.connected
                                                    } else strings.disconnected,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                        },
                        bottomBar = {
                            NavigationBar {
                                Screen.values().forEach { screen ->
                                    val label = when (screen) {
                                        Screen.DASHBOARD -> strings.navDashboard
                                        Screen.APPS -> strings.navApps
                                        Screen.CALLS_CHIME -> strings.navCalls
                                        Screen.SETTINGS -> strings.navSettings
                                    }
                                    NavigationBarItem(
                                        selected = currentScreen == screen,
                                        onClick = { currentScreen = screen },
                                        icon = {
                                            Icon(
                                                imageVector = screen.icon,
                                                contentDescription = label
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = label,
                                                maxLines = 1,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        alwaysShowLabel = true
                                    )
                                }
                            }
                        }
                    ) { padding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                        ) {
                            when (currentScreen) {
                                Screen.DASHBOARD -> DashboardScreen(onNavigateToSettings = { currentScreen = Screen.SETTINGS })
                                Screen.APPS -> AppsScreen()
                                Screen.CALLS_CHIME -> CallsChimeScreen()
                                Screen.SETTINGS -> SettingsScreen(
                                    onLanguageChanged = { currentLang = it },
                                    onThemeChanged = {
                                        currentAccent = prefs.getThemeAccent()
                                        currentThemeMode = prefs.getThemeMode()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestMissingPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        permissions.add(Manifest.permission.READ_PHONE_STATE)
        permissions.add(Manifest.permission.READ_CALL_LOG)
        permissions.add(Manifest.permission.READ_CONTACTS)

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        }
    }
}
