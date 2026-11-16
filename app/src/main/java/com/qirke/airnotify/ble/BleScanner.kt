package com.qirke.airnotify.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import timber.log.Timber

data class DiscoveredDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val isFitbit: Boolean = false,
    val isBonded: Boolean = false
)

class BleScanner(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val mainHandler = Handler(Looper.getMainLooper())
    private var scanCallback: ScanCallback? = null
    private var isScanning = false

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun startScan(
        onDeviceFound: (DiscoveredDevice) -> Unit,
        onError: (String) -> Unit,
        durationMillis: Long = 15000L
    ) {
        if (!isBluetoothEnabled()) {
            onError("Bluetooth is turned off")
            return
        }
        if (!hasScanPermission()) {
            onError("Bluetooth permissions not granted")
            return
        }
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            onError("Bluetooth LE scanner unavailable")
            return
        }

        stopScan()

        // First add already paired / bonded devices
        adapter?.bondedDevices?.forEach { dev ->
            val devName = dev.name ?: "Unknown Device"
            val isFitbit = isFitbitName(devName)
            onDeviceFound(
                DiscoveredDevice(
                    name = devName,
                    address = dev.address,
                    rssi = 0,
                    isFitbit = isFitbit,
                    isBonded = true
                )
            )
        }

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val dev = result.device ?: return
                val addr = dev.address ?: return
                val devName = result.scanRecord?.deviceName ?: dev.name ?: "Unknown Device"
                val isFitbit = isFitbitName(devName)
                val isBonded = dev.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED

                onDeviceFound(
                    DiscoveredDevice(
                        name = devName,
                        address = addr,
                        rssi = result.rssi,
                        isFitbit = isFitbit,
                        isBonded = isBonded
                    )
                )
            }

            override fun onScanFailed(errorCode: Int) {
                isScanning = false
                onError("BLE Scan failed with error code $errorCode")
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)
            isScanning = true
            Timber.i("Started BLE Scan")

            mainHandler.postDelayed({
                stopScan()
            }, durationMillis)
        } catch (e: SecurityException) {
            isScanning = false
            onError("Security exception starting scan: ${e.message}")
        }
    }

    fun stopScan() {
        if (!isScanning) return
        isScanning = false
        val scanner = adapter?.bluetoothLeScanner
        scanCallback?.let {
            try {
                scanner?.stopScan(it)
                Timber.i("Stopped BLE Scan")
            } catch (_: Exception) {}
        }
        scanCallback = null
    }

    private fun isFitbitName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.lowercase()
        return lower.contains("fitbit") || lower.contains("air")
    }
}
