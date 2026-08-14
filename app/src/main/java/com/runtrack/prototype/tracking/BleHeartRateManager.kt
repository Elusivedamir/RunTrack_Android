package com.runtrack.prototype.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.runtrack.prototype.settings.SettingsRepository
import com.runtrack.prototype.domain.HeartRateProtocol
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

sealed interface BleHeartRateState {
    data object Unsupported : BleHeartRateState
    data object PermissionRequired : BleHeartRateState
    data object BluetoothOff : BleHeartRateState
    data object Idle : BleHeartRateState
    data object Scanning : BleHeartRateState
    data class Connecting(val name: String) : BleHeartRateState
    data class Subscribing(val name: String) : BleHeartRateState
    data class Connected(val name: String, val bpm: Int? = null) : BleHeartRateState
    data class Error(val message: String) : BleHeartRateState
}

data class BleHeartRateDevice(
    val address: String,
    val name: String,
    val rssi: Int,
)

/**
 * Standard Bluetooth SIG Heart Rate Service client (0x180D / Measurement 0x2A37).
 * It never marks a device connected until GATT service discovery and CCCD notification subscription succeed.
 */
@SuppressLint("MissingPermission")
class BleHeartRateManager(
    context: Context,
    private val settingsRepository: SettingsRepository,
    private val onMeasurement: suspend (bpm: Int, wallClockMillis: Long, elapsedRealtimeMillis: Long) -> Unit,
) {
    private val app = context.applicationContext
    private val bluetoothManager = app.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<BleHeartRateState>(initialState())
    val state: StateFlow<BleHeartRateState> = _state.asStateFlow()
    private val _devices = MutableStateFlow<List<BleHeartRateDevice>>(emptyList())
    val devices: StateFlow<List<BleHeartRateDevice>> = _devices.asStateFlow()

    private val found = LinkedHashMap<String, BluetoothDevice>()
    private val rssiByAddress = HashMap<String, Int>()
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var scanTimeout: Job? = null
    private var connectTimeout: Job? = null
    private var reconnectJob: Job? = null
    private var targetDevice: BluetoothDevice? = null
    private var userDisconnected = false
    private var reconnectAttempt = 0

    fun refreshSystemState() {
        val system = initialState()
        if (system != BleHeartRateState.Idle) {
            if (system == BleHeartRateState.BluetoothOff || system == BleHeartRateState.PermissionRequired) {
                stopScanInternal()
                closeGatt()
            }
            _state.value = system
        } else if (_state.value is BleHeartRateState.Unsupported || _state.value is BleHeartRateState.PermissionRequired || _state.value is BleHeartRateState.BluetoothOff || _state.value is BleHeartRateState.Error) {
            _state.value = BleHeartRateState.Idle
        }
    }

    fun startScan() {
        refreshSystemState()
        if (_state.value != BleHeartRateState.Idle) return
        val localAdapter = adapter ?: run { _state.value = BleHeartRateState.Unsupported; return }
        val localScanner = runCatching { localAdapter.bluetoothLeScanner }.getOrNull() ?: run {
            _state.value = BleHeartRateState.BluetoothOff
            return
        }
        stopScanInternal()
        found.clear(); rssiByAddress.clear(); _devices.value = emptyList()
        scanner = localScanner
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(HEART_RATE_SERVICE)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            localScanner.startScan(listOf(filter), settings, scanCallback)
            _state.value = BleHeartRateState.Scanning
            scanTimeout = scope.launch {
                delay(SCAN_TIMEOUT_MS)
                if (_state.value == BleHeartRateState.Scanning) {
                    stopScanInternal()
                    _state.value = if (_devices.value.isEmpty()) BleHeartRateState.Error("Пульсометр не найден") else BleHeartRateState.Idle
                }
            }
        } catch (_: SecurityException) {
            _state.value = BleHeartRateState.PermissionRequired
        } catch (t: Throwable) {
            _state.value = BleHeartRateState.Error(t.message ?: "Не удалось запустить BLE-сканирование")
        }
    }

    fun cancelScan() {
        stopScanInternal()
        if (_state.value == BleHeartRateState.Scanning) _state.value = BleHeartRateState.Idle
    }

    fun connect(address: String) {
        refreshSystemState()
        if (_state.value == BleHeartRateState.PermissionRequired || _state.value == BleHeartRateState.BluetoothOff || _state.value == BleHeartRateState.Unsupported) return
        val device = found[address] ?: runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
        if (device == null) {
            _state.value = BleHeartRateState.Error("Bluetooth-устройство недоступно")
            return
        }
        stopScanInternal()
        userDisconnected = false
        reconnectAttempt = 0
        targetDevice = device
        connectDevice(device)
    }

    fun connectSaved(address: String) = connect(address)

    fun disconnect() {
        userDisconnected = true
        reconnectJob?.cancel(); reconnectJob = null
        stopScanInternal()
        closeGatt()
        _state.value = initialState().let { if (it == BleHeartRateState.Idle) BleHeartRateState.Idle else it }
    }

    fun close() {
        userDisconnected = true
        scanTimeout?.cancel(); connectTimeout?.cancel(); reconnectJob?.cancel()
        stopScanInternal(); closeGatt(); scope.cancel()
    }

    private fun connectDevice(device: BluetoothDevice) {
        if (!hasConnectPermission()) {
            _state.value = BleHeartRateState.PermissionRequired
            return
        }
        closeGatt()
        val name = safeName(device)
        _state.value = BleHeartRateState.Connecting(name)
        try {
            gatt = device.connectGatt(app, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            connectTimeout?.cancel()
            connectTimeout = scope.launch {
                delay(CONNECT_TIMEOUT_MS)
                val current = _state.value
                if (current is BleHeartRateState.Connecting || current is BleHeartRateState.Subscribing) {
                    closeGatt()
                    _state.value = BleHeartRateState.Error("Таймаут подключения пульсометра")
                }
            }
        } catch (_: SecurityException) {
            _state.value = BleHeartRateState.PermissionRequired
        } catch (t: Throwable) {
            _state.value = BleHeartRateState.Error(t.message ?: "Ошибка подключения BLE")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            scope.launch { publishScanResult(result) }
        }
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            scope.launch { results.forEach(::publishScanResult) }
        }
        override fun onScanFailed(errorCode: Int) {
            scope.launch {
                stopScanInternal()
                _state.value = BleHeartRateState.Error("BLE scan error: $errorCode")
            }
        }
    }

    private fun publishScanResult(result: ScanResult) {
        val device = result.device ?: return
        val address = runCatching { device.address }.getOrNull() ?: return
        found[address] = device
        rssiByAddress[address] = result.rssi
        _devices.value = found.map { (addr, dev) -> BleHeartRateDevice(addr, safeName(dev), rssiByAddress[addr] ?: Int.MIN_VALUE) }
            .sortedByDescending { it.rssi }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            scope.launch { handleConnectionStateChange(gatt, status, newState) }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            scope.launch { handleServicesDiscovered(gatt, status) }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            scope.launch { handleDescriptorWrite(gatt, descriptor, status) }
        }

        @Deprecated("API 33 callback retained for older Android")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33 && characteristic.uuid == HEART_RATE_MEASUREMENT) {
                @Suppress("DEPRECATION")
                val value = characteristic.value?.copyOf() ?: return
                scope.launch { handleMeasurement(value, safeName(gatt.device)) }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == HEART_RATE_MEASUREMENT) {
                val snapshot = value.copyOf()
                scope.launch { handleMeasurement(snapshot, safeName(gatt.device)) }
            }
        }
    }

    private fun handleConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
            reconnectAttempt = 0
            val name = safeName(gatt.device)
            _state.value = BleHeartRateState.Subscribing(name)
            val started = runCatching { gatt.discoverServices() }.getOrDefault(false)
            if (!started) failGatt("Не удалось начать обнаружение BLE-сервисов")
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            closeGatt(gatt)
            if (!userDisconnected && targetDevice != null && reconnectAttempt < MAX_RECONNECT_ATTEMPTS && initialState() == BleHeartRateState.Idle) {
                val attempt = ++reconnectAttempt
                _state.value = BleHeartRateState.Error("Пульсометр отключён · переподключение $attempt/$MAX_RECONNECT_ATTEMPTS")
                reconnectJob?.cancel()
                reconnectJob = scope.launch {
                    delay(RECONNECT_BASE_DELAY_MS * attempt)
                    targetDevice?.let(::connectDevice)
                }
            } else if (!userDisconnected) {
                _state.value = initialState().let { if (it == BleHeartRateState.Idle) BleHeartRateState.Error("Пульсометр отключён") else it }
            }
        } else if (status != BluetoothGatt.GATT_SUCCESS) {
            failGatt("GATT error: $status")
        }
    }

    private fun handleServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            failGatt("Не удалось обнаружить BLE-сервисы: $status")
            return
        }
        val measurement = gatt.getService(HEART_RATE_SERVICE)?.getCharacteristic(HEART_RATE_MEASUREMENT)
        if (measurement == null) {
            failGatt("Устройство не предоставляет Heart Rate Measurement")
            return
        }
        if (measurement.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0) {
            failGatt("Heart Rate Measurement не поддерживает notifications")
            return
        }
        try {
            if (!gatt.setCharacteristicNotification(measurement, true)) {
                failGatt("Не удалось включить уведомления пульса")
                return
            }
            val cccd = measurement.getDescriptor(CCCD)
            if (cccd == null) {
                failGatt("У пульсометра отсутствует CCCD")
                return
            }
            val initiated = if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run { cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; gatt.writeDescriptor(cccd) }
            }
            if (!initiated) failGatt("Не удалось подписаться на пульс")
        } catch (_: SecurityException) {
            _state.value = BleHeartRateState.PermissionRequired
            closeGatt()
        }
    }

    private fun handleDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        if (descriptor.uuid != CCCD) return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            failGatt("Ошибка подписки на пульс: $status")
            return
        }
        connectTimeout?.cancel(); connectTimeout = null
        val name = safeName(gatt.device)
        _state.value = BleHeartRateState.Connected(name)
        val address = runCatching { gatt.device.address }.getOrNull()
        if (address != null) scope.launch(Dispatchers.IO) { settingsRepository.setHeartRateDevice(address, name) }
    }

    private fun handleMeasurement(value: ByteArray, name: String) {
        val bpm = HeartRateProtocol.parseMeasurement(value) ?: return
        _state.value = BleHeartRateState.Connected(name, bpm)
        scope.launch(Dispatchers.IO) {
            onMeasurement(bpm, System.currentTimeMillis(), SystemClock.elapsedRealtime())
        }
    }

    private fun stopScanInternal() {
        scanTimeout?.cancel(); scanTimeout = null
        val localScanner = scanner
        scanner = null
        if (localScanner != null && hasScanPermission()) runCatching { localScanner.stopScan(scanCallback) }
    }

    private fun closeGatt(target: BluetoothGatt? = gatt) {
        connectTimeout?.cancel(); connectTimeout = null
        if (target != null) {
            if (target === gatt) gatt = null
            if (hasConnectPermission()) runCatching { target.disconnect() }
            runCatching { target.close() }
        }
    }

    private fun failGatt(message: String) {
        closeGatt()
        _state.value = BleHeartRateState.Error(message)
    }

    private fun initialState(): BleHeartRateState {
        val localAdapter = adapter ?: return BleHeartRateState.Unsupported
        if (!hasScanPermission() || !hasConnectPermission()) return BleHeartRateState.PermissionRequired
        val enabled = try { localAdapter.isEnabled } catch (_: SecurityException) { return BleHeartRateState.PermissionRequired }
        if (!enabled) return BleHeartRateState.BluetoothOff
        return BleHeartRateState.Idle
    }

    private fun hasScanPermission(): Boolean = if (Build.VERSION.SDK_INT >= 31) {
        ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    } else ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasConnectPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun safeName(device: BluetoothDevice): String = if (hasConnectPermission()) {
        runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "BLE пульсометр"
    } else "BLE пульсометр"

    companion object {
        val HEART_RATE_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val SCAN_TIMEOUT_MS = 12_000L
        private const val CONNECT_TIMEOUT_MS = 12_000L
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val RECONNECT_BASE_DELAY_MS = 2_000L

    }
}
