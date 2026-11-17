package com.fitbit.bluetooth.fbgatt

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import timber.log.Timber

object BitGattHelper {

    fun stopAllScanners(fitbitGatt: FitbitGatt, context: Context) {
        runCatching {
            fitbitGatt.alwaysConnectedScanner?.stop(context)
        }
        runCatching {
            fitbitGatt.cancelScan(context)
        }
        runCatching {
            fitbitGatt.cancelPeriodicalScan(context)
        }
        runCatching {
            fitbitGatt.cancelHighPriorityScan(context)
        }
    }

    fun clearConnections(fitbitGatt: FitbitGatt, context: Context? = null) {
        runCatching {
            if (context != null) {
                stopAllScanners(fitbitGatt, context)
            }
            val connections = fitbitGatt.connectionMap.values.toList()
            connections.forEach { connection ->
                runCatching { connection.gatt?.disconnect() }
                runCatching { connection.disconnect() }
            }
            connections.forEach { connection ->
                runCatching { connection.close() }
            }
            fitbitGatt.connectionMap.clear()
        }
    }

    fun removeConnectionForAddress(fitbitGatt: FitbitGatt, address: String) {
        runCatching {
            val map = fitbitGatt.connectionMap
            val targets = map.filter { it.key.address.equals(address, ignoreCase = true) }
            targets.forEach { (device, connection) ->
                runCatching { connection.gatt?.disconnect() }
                runCatching { connection.disconnect() }
                runCatching { connection.close() }
                map.remove(device)
            }
        }
    }

    fun forceDisconnectBle(context: Context, address: String) {
        try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
            val adapter = bm.adapter ?: return
            if (!adapter.isEnabled) return

            val targetDevice = adapter.getRemoteDevice(address) ?: return
            val connectedLe = bm.getConnectedDevices(BluetoothProfile.GATT)
            val isConnected = connectedLe.any { it.address.equals(address, ignoreCase = true) }

            Timber.i("forceDisconnectBle: address=%s isConnected=%b", address, isConnected)

            targetDevice.connectGatt(context, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    Timber.d("forceDisconnectBle onConnectionStateChange: status=%d, newState=%d", status, newState)
                    runCatching { gatt.disconnect() }
                    runCatching { gatt.close() }
                }
            })
        } catch (e: Throwable) {
            Timber.w(e, "Error in forceDisconnectBle")
        }
    }
}
