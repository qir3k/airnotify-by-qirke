package com.qirke.airnotify.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.fitbit.bluetooth.fbgatt.*
import com.fitbit.bluetooth.fbgatt.rx.PeripheralConnectionChangeListener
import com.fitbit.goldengate.bindings.coap.CoapEndpoint
import com.fitbit.goldengate.bindings.coap.CoapEndpointBuilder
import com.fitbit.goldengate.bindings.dtls.DtlsProtocolStatus
import com.fitbit.goldengate.bindings.node.BluetoothAddressNodeKey
import com.fitbit.goldengate.bindings.stack.DtlsSocketNetifGattlink
import com.fitbit.goldengate.bt.DEFAULT_CLIENT_ADDRESS
import com.fitbit.goldengate.bt.DEFAULT_SERVER_ADDRESS
import com.fitbit.goldengate.bt.PeerRole
import com.fitbit.goldengate.node.stack.StackPeer
import com.qirke.airnotify.R
import com.qirke.airnotify.ble.*
import com.qirke.airnotify.data.PreferencesManager
import com.qirke.airnotify.ui.MainActivity
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.net.Inet4Address
import java.util.ArrayDeque
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

class AirBridgeService : Service() {

    private val queue = ArrayDeque<AirNotificationTrigger>()
    private val disposables = CompositeDisposable()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var processing = false
    private var foregroundReady = false
    @Volatile private var stopRequested = false
    private var activePeer: StackPeer<CoapEndpoint>? = null
    private var activeEndpoint: CoapEndpoint? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentStatus: String = "Ready (Idle)"
    @Volatile private var isCallRinging = false
    @Volatile private var callGeneration = 0L
    private var callRingStartTime = 0L

    companion object {
        const val ACTION_TRIGGER = "com.qirke.airnotify.ACTION_TRIGGER"
        const val ACTION_CANCEL_CALL = "com.qirke.airnotify.ACTION_CANCEL_CALL"
        const val ACTION_CHECK_CONNECTION = "com.qirke.airnotify.ACTION_CHECK_CONNECTION"
        const val ACTION_STOP_ALL = "com.qirke.airnotify.ACTION_STOP_ALL"

        const val EXTRA_SOURCE = "extra_source"
        const val EXTRA_PATTERN = "extra_pattern"
        const val EXTRA_IS_CALL = "extra_is_call"
        const val EXTRA_IS_CONTINUOUS = "extra_is_continuous"
        const val EXTRA_IS_MANUAL = "extra_is_manual"

        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "airnotify_bridge_channel"
        private const val MAX_QUEUE_SIZE = 5
        private const val MAX_ALERT_AGE_MILLIS = 30_000L
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val SCAN_TIMEOUT_MILLIS = 8_000L

        private val _isVibratingFlow = MutableStateFlow(false)
        val isVibratingFlow = _isVibratingFlow.asStateFlow()

        private val _currentStatusFlow = MutableStateFlow("Ready (Idle)")
        val currentStatusFlow = _currentStatusFlow.asStateFlow()

        fun enqueue(context: Context, trigger: AirNotificationTrigger): Boolean {
            val prefs = PreferencesManager.getInstance(context)
            if (!trigger.isManual) {
                if (prefs.isSyncPaused()) {
                    Timber.d("Sync pause active for companion app sync; ignoring alert from %s", trigger.source)
                    prefs.addLog("Sync", "Alert paused for companion sync (${trigger.source})")
                    return false
                }
                val devConfig = prefs.getDeviceConfig()
                if (!devConfig.masterEnabled) {
                    Timber.d("Master alert toggle disabled; ignoring trigger from %s", trigger.source)
                    return false
                }
                if (prefs.getQuietHoursConfig().isQuietNow()) {
                    Timber.d("Quiet hours active; suppressing alert from %s", trigger.source)
                    prefs.addLog("QuietHours", "Suppressed alert for ${trigger.source}")
                    return false
                }
            }

            val intent = Intent(context, AirBridgeService::class.java).apply {
                action = ACTION_TRIGGER
                putExtra(EXTRA_SOURCE, trigger.source)
                putExtra(EXTRA_PATTERN, trigger.pattern.wireName)
                putExtra(EXTRA_IS_CALL, trigger.isCall)
                putExtra(EXTRA_IS_CONTINUOUS, trigger.isContinuousCall)
                putExtra(EXTRA_IS_MANUAL, trigger.isManual)
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                return true
            } catch (e: Exception) {
                Timber.e(e, "Failed to start AirBridgeService")
                return false
            }
        }

        fun cancelCall(context: Context) {
            val intent = Intent(context, AirBridgeService::class.java).apply {
                action = ACTION_CANCEL_CALL
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun checkConnection(context: Context) {
            val intent = Intent(context, AirBridgeService::class.java).apply {
                action = ACTION_CHECK_CONNECTION
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }

        fun stopAlerts(context: Context) {
            val intent = Intent(context, AirBridgeService::class.java).apply {
                action = ACTION_STOP_ALL
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Timber.w(e, "Unable to stop alerts via intent")
            }
            // Synchronously ensure BLE force disconnect and scanner stoppage
            val prefs = PreferencesManager.getInstance(context)
            val address = prefs.getDeviceConfig().address
            if (AirRuntime.isInitialized()) {
                runCatching {
                    val fg = FitbitGatt.getInstance()
                    BitGattHelper.clearConnections(fg, context)
                }
            }
            if (!address.isNullOrBlank()) {
                BitGattHelper.forceDisconnectBle(context, address)
            }
        }
    }

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            androidx.core.app.ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification("Connecting to Fitbit Air..."),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
            foregroundReady = true
        } catch (error: Exception) {
            Timber.w(error, "Unable to enter connected-device foreground mode")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foregroundReady) {
            stopSelf()
            return START_NOT_STICKY
        }

        val action = intent?.action
        if (action == ACTION_CANCEL_CALL) {
            isCallRinging = false
            callGeneration++
            queue.removeAll { it.isCall }
            updateStatus(if (processing) "Call ended; completing pulse" else "Call ended")
            if (!processing) processNext()
            return START_NOT_STICKY
        }

        if (action == ACTION_STOP_ALL) {
            stopRequested = true
            callGeneration++
            isCallRinging = false
            queue.clear()
            releasePeer()
            if (AirRuntime.isInitialized()) {
                runCatching {
                    val fg = FitbitGatt.getInstance()
                    BitGattHelper.clearConnections(fg, this)
                }
            }
            val pairedAddress = PreferencesManager.getInstance(this).getDeviceConfig().address
            if (!pairedAddress.isNullOrBlank()) {
                BitGattHelper.forceDisconnectBle(this, pairedAddress)
            }
            releaseWakeLock()
            updateStatus("Alerts stopped (Radio released)")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_CHECK_CONNECTION) {
            queue.addLast(
                AirNotificationTrigger(
                    source = "Connection Test",
                    pattern = AirHapticPattern.SINGLE,
                    isConnectionCheck = true,
                    isManual = true
                )
            )
            processNext()
            return START_NOT_STICKY
        }

        val source = intent?.getStringExtra(EXTRA_SOURCE)
        val patternStr = intent?.getStringExtra(EXTRA_PATTERN)
        val isCall = intent?.getBooleanExtra(EXTRA_IS_CALL, false) ?: false
        val isContinuous = intent?.getBooleanExtra(EXTRA_IS_CONTINUOUS, false) ?: false
        val isManual = intent?.getBooleanExtra(EXTRA_IS_MANUAL, false) ?: false

        if (source != null && patternStr != null) {
            val pattern = AirHapticPattern.fromWireName(patternStr)
            val trigger = AirNotificationTrigger(
                source = source,
                pattern = pattern,
                isCall = isCall,
                isContinuousCall = isContinuous,
                isManual = isManual,
                callSessionId = if (isContinuous) ++callGeneration else 0L
            )

            if (isCall && isContinuous) {
                isCallRinging = true
                callRingStartTime = SystemClock.elapsedRealtime()
                while (queue.size >= MAX_QUEUE_SIZE) queue.removeLast()
                queue.addFirst(trigger)
            } else {
                queue.removeAll { !it.isCall && it.source == source }
                if (queue.size < MAX_QUEUE_SIZE) {
                    queue.addLast(trigger)
                } else {
                    updateStatus("Queue full; discarded $source")
                }
            }
        }

        processNext()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        _isVibratingFlow.value = false
        mainHandler.removeCallbacksAndMessages(null)
        disposables.dispose()
        releasePeer()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun processNext() {
        if (processing) return

        queue.removeAll {
            !it.isCall && (SystemClock.elapsedRealtime() - it.createdAtElapsed > MAX_ALERT_AGE_MILLIS)
        }

        val trigger = queue.pollFirst()
        if (trigger == null) {
            stopSelfIfQueueEmpty()
            return
        }

        val prefs = PreferencesManager.getInstance(this)
        if (prefs.isSyncPaused()) {
            val remainMin = (prefs.getSyncPauseRemainingMillis() / 60_000L) + 1
            updateStatus("Alerts paused for companion app sync (~${remainMin}m left)")
            queue.clear()
            stopSelfIfQueueEmpty()
            return
        }

        val address = prefs.getDeviceConfig().address
        if (address.isNullOrBlank()) {
            val msg = "No Fitbit Air paired! Go to Settings to pair."
            updateStatus(msg)
            prefs.addLog("BLE", msg, isError = true)
            stopSelfIfQueueEmpty()
            return
        }

        acquireWakeLock()
        processing = true
        _isVibratingFlow.value = true
        updateStatus("Alerting Fitbit: ${trigger.source} [${trigger.pattern.displayName}]")

        disposables.add(
            executeWithRetry(trigger, address)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { _ ->
                        val status = if (trigger.isConnectionCheck) {
                            "Fitbit Air link verified successfully!"
                        } else {
                            "Alert delivered: ${trigger.source} [${trigger.pattern.displayName}]"
                        }
                        updateStatus(status)
                        prefs.addLog("Alert", status)
                        processing = false
                        _isVibratingFlow.value = false
                        if (trigger.isContinuousCall && trigger.callSessionId == callGeneration) {
                            isCallRinging = false
                        }
                        processNext()
                    },
                    { throwable ->
                        val friendly = formatFriendlyError(trigger, throwable)
                        Timber.w(throwable, friendly)
                        updateStatus(friendly)
                        prefs.addLog("BLE", friendly, isError = true)
                        processing = false
                        _isVibratingFlow.value = false
                        activeEndpoint = null
                        releasePeer()
                        if (AirRuntime.isInitialized()) {
                            runCatching { BitGattHelper.clearConnections(FitbitGatt.getInstance()) }
                        }
                        mainHandler.postDelayed({ processNext() }, BleCoexistenceManager.RADIO_SETTLE_MILLIS)
                    }
                )
        )
    }

    private fun isTransientBleError(throwable: Throwable): Boolean {
        val msg = throwable.message.orEmpty()
        return msg.contains("INVALID_STATE", ignoreCase = true) ||
               msg.contains("Encrypted session", ignoreCase = true) ||
               msg.contains("TLS", ignoreCase = true) ||
               msg.contains("133") ||
               msg.contains("busy", ignoreCase = true) ||
               msg.contains("timed out", ignoreCase = true) ||
               msg.contains("timeout", ignoreCase = true) ||
               msg.contains("closed", ignoreCase = true) ||
               msg.contains("disconnect", ignoreCase = true) ||
               msg.contains("connection", ignoreCase = true) ||
               msg.contains("GattConnectTransaction", ignoreCase = true) ||
               throwable is java.util.concurrent.TimeoutException ||
               throwable is IllegalStateException
    }

    private fun executeWithRetry(
        trigger: AirNotificationTrigger,
        address: String,
        maxRetries: Int = 2
    ): Single<AirHapticExecutionResult> {
        return Single.defer { executeOnce(trigger, address) }
            .retryWhen { errors: Flowable<Throwable> ->
                errors.zipWith(
                    Flowable.range(1, maxRetries + 1),
                    BiFunction<Throwable, Int, Pair<Throwable, Int>> { error, attempt -> error to attempt }
                ).flatMap { pair ->
                    val error = pair.first
                    val attempt = pair.second
                    val isRecoverable = isTransientBleError(error)
                    val isUserDisabled = error.message?.contains("Bluetooth is disabled", true) == true
                    val isPermMissing = error is SecurityException

                    if (attempt <= maxRetries && isRecoverable && !isUserDisabled && !isPermMissing) {
                        BleCoexistenceManager.markCollision()
                        val delay: Long = if (error.message?.contains("Encrypted session", true) == true ||
                                        error.message?.contains("TLS", true) == true) {
                            1200L * attempt.toLong()
                        } else {
                            450L * attempt.toLong()
                        }
                        Timber.w("BLE transient glitch on ${trigger.source} (attempt $attempt/$maxRetries): ${error.message}. Retrying in ${delay}ms...")
                        updateStatus("Retrying link to Fitbit Air ($attempt/$maxRetries)...")
                        releasePeer()
                        if (AirRuntime.isInitialized()) {
                            runCatching { BitGattHelper.clearConnections(FitbitGatt.getInstance()) }
                        }
                        Flowable.timer(delay, TimeUnit.MILLISECONDS)
                    } else {
                        Flowable.error(error)
                    }
                }
            }
    }

    private fun executeOnce(trigger: AirNotificationTrigger, address: String): Single<AirHapticExecutionResult> {
        val backoff: Single<Long> = if (PreferencesManager.getInstance(this).isCoexistenceGuardEnabled() && BleCoexistenceManager.isRecentCollision()) {
            updateStatus("Waiting for companion health sync to yield radio...")
            Single.timer(BleCoexistenceManager.SYNC_COLLISION_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
        } else {
            Single.just(0L)
        }

        val getEndpoint: Single<CoapEndpoint> = backoff.flatMap {
            activeEndpoint?.let { Single.just(it) }
                ?: Single.fromCallable {
                    AirRuntime.ensureInitialized(this)
                    address
                }.flatMap { resolveDeviceConnection(it) }
                    .flatMap { connection -> connectStack(connection.device.address) }
        }

        return getEndpoint.flatMap { endpoint ->
            if (trigger.isConnectionCheck) {
                updateStatus("Connected; verifying band link without vibrating")
                return@flatMap AirHapticPatternExecutor.checkConnection(endpoint).timeout(12, TimeUnit.SECONDS)
            }

            updateStatus("Connected! Vibrating ${trigger.pattern.displayName}...")

            val execution = if (trigger.isCall && trigger.isContinuousCall) {
                AirHapticPatternExecutor.executeRinging(
                    endpoint,
                    trigger.pattern,
                    shouldContinue = { isCallRinging && (SystemClock.elapsedRealtime() - callRingStartTime < 60_000L) },
                    maxDurationMillis = 60_000L
                )
            } else {
                AirHapticPatternExecutor.execute(endpoint, trigger.pattern)
            }

            execution.timeout(if (trigger.isCall && trigger.isContinuousCall) 70L else 25L, TimeUnit.SECONDS)
        }.flatMap { result ->
            releasePeerAndWait().andThen(Single.timer(BleCoexistenceManager.RADIO_SETTLE_MILLIS, TimeUnit.MILLISECONDS).map { result })
        }
    }

    private fun resolveDeviceConnection(address: String): Single<GattConnection> = Single.create { emitter ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            emitter.onError(SecurityException("Nearby devices permission is required"))
            return@create
        }
        val fitbitGatt = FitbitGatt.getInstance()
        val normalizedAddress = address.uppercase()

        runCatching { fitbitGatt.addConnectedDevices() }

        val existing = fitbitGatt.getMatchingConnectionsForDeviceNames(null)
            .firstOrNull { it.device.address.equals(normalizedAddress, ignoreCase = true) }
            ?: fitbitGatt.getConnectionForAddress(normalizedAddress)

        existing?.let {
            Timber.i("Closing prior connection for %s", normalizedAddress)
            runCatching { it.gatt?.disconnect() }
            runCatching { it.disconnect() }
            runCatching { it.close() }
            BitGattHelper.removeConnectionForAddress(fitbitGatt, normalizedAddress)
        }

        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            if (!emitter.isDisposed) emitter.onError(IllegalStateException("Bluetooth is disabled"))
            return@create
        }

        val connectedLeDevices = runCatching { manager.getConnectedDevices(7) }.getOrDefault(emptyList())
        val bondedDevices = adapter.bondedDevices ?: emptySet()
        val bondedOrConnected = (connectedLeDevices + bondedDevices)
            .firstOrNull { it.address.equals(normalizedAddress, ignoreCase = true) }

        if (bondedOrConnected != null) {
            val wrappedDevice = FitbitBluetoothDevice(bondedOrConnected)
            val connection = GattConnection(wrappedDevice, Looper.getMainLooper())
            fitbitGatt.putConnectionIntoDevices(wrappedDevice, connection)
            if (!emitter.isDisposed) emitter.onSuccess(connection)
            return@create
        }

        Timber.i("Device not in cache/bonded; scanning for %s", normalizedAddress)
        emitter.setDisposable(scanForDevice(normalizedAddress).subscribe(
            { if (!emitter.isDisposed) emitter.onSuccess(it) },
            { if (!emitter.isDisposed) emitter.onError(it) }
        ))
    }

    private fun scanForDevice(address: String): Single<GattConnection> = Single.create { emitter ->
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter? = manager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            emitter.onError(IllegalStateException("Bluetooth is disabled"))
            return@create
        }
        val scanner = adapter.bluetoothLeScanner ?: run {
            emitter.onError(IllegalStateException("BLE scanner unavailable"))
            return@create
        }

        var callback: ScanCallback? = null
        var timeout: Runnable? = null
        val stopScan = {
            timeout?.let { mainHandler.removeCallbacks(it) }
            callback?.let {
                try { scanner.stopScan(it) } catch (_: Exception) {}
            }
        }

        val activeCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val dev = result.device ?: return
                if (!dev.address.equals(address, ignoreCase = true)) return
                stopScan()
                if (!emitter.isDisposed) {
                    val fitbitGatt = FitbitGatt.getInstance()
                    BitGattHelper.removeConnectionForAddress(fitbitGatt, dev.address)
                    val wrappedDevice = FitbitBluetoothDevice(dev)
                    val conn = GattConnection(wrappedDevice, Looper.getMainLooper())
                    fitbitGatt.putConnectionIntoDevices(wrappedDevice, conn)
                    emitter.onSuccess(conn)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                stopScan()
                if (!emitter.isDisposed) emitter.onError(IllegalStateException("BLE scan failed code $errorCode"))
            }
        }
        callback = activeCallback

        timeout = Runnable {
            stopScan()
            val directDev = runCatching { adapter.getRemoteDevice(address) }.getOrNull()
            if (directDev != null) {
                val fitbitGatt = FitbitGatt.getInstance()
                val wrapped = FitbitBluetoothDevice(directDev)
                val conn = GattConnection(wrapped, Looper.getMainLooper())
                fitbitGatt.putConnectionIntoDevices(wrapped, conn)
                if (!emitter.isDisposed) emitter.onSuccess(conn)
            } else {
                if (!emitter.isDisposed) emitter.onError(IllegalStateException("Fitbit Air not in range (scan timeout)"))
            }
        }

        try {
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            scanner.startScan(null, settings, activeCallback)
            mainHandler.postDelayed(timeout, SCAN_TIMEOUT_MILLIS)
        } catch (e: Exception) {
            stopScan()
            if (!emitter.isDisposed) emitter.onError(e)
        }
    }

    private fun connectStack(address: String): Single<CoapEndpoint> {
        updateStatus("Fitbit Air discovered. Establishing secure session...")
        val stackConfig = DtlsSocketNetifGattlink(
            Inet4Address.getByName(DEFAULT_SERVER_ADDRESS) as Inet4Address,
            0,
            Inet4Address.getByName(DEFAULT_CLIENT_ADDRESS) as Inet4Address,
            0
        )
        val peer = StackPeer(
            BluetoothAddressNodeKey(address),
            PeerRole.Peripheral,
            stackConfig,
            CoapEndpointBuilder(),
            { connection ->
                PeripheralConnectionChangeListener().register(connection)
            },
            { stack ->
                stack.dtlsEventObservable.observeOn(Schedulers.io()).doOnNext { status ->
                    check(status.state != DtlsProtocolStatus.TlsProtocolState.TLS_STATE_ERROR) {
                        "Encrypted session error (${status.lastError}). Check Fitbit companion app."
                    }
                }
            }
        )
        activePeer = peer
        return peer.connection()
            .observeOn(Schedulers.io())
            .firstOrError()
            .timeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .map { peer.stackService }
    }

    private fun releasePeerAndWait(): Completable {
        _isVibratingFlow.value = false
        val peer = synchronized(this) {
            activeEndpoint = null
            val p = activePeer
            activePeer = null
            p
        }
        return NativePeerCleanup.close {
            peer?.disconnect(true)
            if (AirRuntime.isInitialized()) {
                runCatching {
                    val fg = FitbitGatt.getInstance()
                    BitGattHelper.clearConnections(fg, this)
                }
            }
        }
    }

    private fun releasePeer() {
        activeEndpoint = null
        val peer = activePeer
        activePeer = null
        peer?.disconnect(true)
        if (AirRuntime.isInitialized()) {
            runCatching {
                val fg = FitbitGatt.getInstance()
                BitGattHelper.clearConnections(fg, this)
            }
        }
    }

    private fun formatFriendlyError(trigger: AirNotificationTrigger, throwable: Throwable): String {
        val msg = throwable.message.orEmpty()
        if (msg.contains("133") || msg.contains("busy", true) || msg.contains("timed out", true) ||
            msg.contains("empty", true) || msg.contains("INVALID_STATE", true) ||
            msg.contains("Encrypted session", true) || msg.contains("TLS", true)) {
            BleCoexistenceManager.markCollision()
        }
        val hint = when {
            msg.contains("Bluetooth is disabled", ignoreCase = true) ->
                "Bluetooth is turned off on your phone."
            msg.contains("scan timeout", ignoreCase = true) || msg.contains("not in range", ignoreCase = true) ->
                "Fitbit Air not found nearby. Make sure it is awake, charged, and in range."
            msg.contains("Encrypted session", ignoreCase = true) ->
                "Secure session failed. Ensure band is paired in Google Health / Fitbit app."
            msg.contains("INVALID_STATE", ignoreCase = true) ->
                "Bluetooth radio was busy transitioning state. Retrying will resolve this."
            msg.contains("timed out", ignoreCase = true) ->
                "Connection timed out. Band may be busy or syncing."
            throwable is SecurityException ->
                "Bluetooth Nearby Devices permission missing."
            else ->
                throwable.localizedMessage ?: "Unknown error"
        }
        return "Failed [${trigger.source}]: $hint"
    }

    private fun updateStatus(status: String) {
        currentStatus = status
        _currentStatusFlow.value = status
        PreferencesManager.getInstance(this).saveLastStatus(status)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun stopSelfIfQueueEmpty() {
        releasePeer()
        releaseWakeLock()
        _isVibratingFlow.value = false
        updateStatus("Ready (Idle)")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AirNotify::BridgeLock")
        }
        if (wakeLock?.isHeld != true) {
            wakeLock?.acquire(90_000L)
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            try { wakeLock?.release() } catch (_: Exception) {}
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(chan)
        }
    }

    private fun buildNotification(text: String): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AirNotify")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
