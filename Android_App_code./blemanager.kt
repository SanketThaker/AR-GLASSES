package com.example.arglasses

import android.util.Log
import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

data class BleScanDevice(val name: String, val address: String, val rssi: Int)

class BleManagerAndroid(
    private val context: Context,
    private val state: AppState
) {

    companion object {

        // ── Glasses (your custom board) ──────────────────────────────────
        private val SERVICE_UUID =
            UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")

        private val CHAR_UUID_COMMAND =
            UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26ae")

        private val CHAR_UUID_STATUS =
            UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26af")

        private val CCCD_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // ── Neckband battery (standard BLE Battery Service) ──────────────
        private val BATTERY_SERVICE_UUID =
            UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")

        private val BATTERY_LEVEL_UUID =
            UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    }

    // ── Main-thread helper ────────────────────────────────────────────────────
    // BLE callbacks arrive on a background thread. Every write to Compose
    // mutableStateOf MUST happen on the main thread, otherwise the UI either
    // doesn't update or throws an exception. Use this helper everywhere.
    private val mainHandler = Handler(Looper.getMainLooper())
    private fun mainThread(block: () -> Unit) = mainHandler.post(block)

    // ─────────────────────────────────────────────────────────────────────────

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val adapter: BluetoothAdapter?
        get() = bluetoothManager.adapter

    private val scanner: BluetoothLeScanner?
        get() = adapter?.bluetoothLeScanner

    val isBluetoothEnabled: Boolean
        get() = adapter?.isEnabled == true

    private var scanning = false

    // ── Glasses GATT ─────────────────────────────────────────────────────────
    private var gatt: BluetoothGatt? = null
    private var commandChar: BluetoothGattCharacteristic? = null
    private var statusChar: BluetoothGattCharacteristic? = null
    private var negotiatedMtu = 23

    // ── Neckband GATT ────────────────────────────────────────────────────────
    private var neckbandGatt: BluetoothGatt? = null

    private val _neckbandBattery = MutableStateFlow<Int?>(null)
    val neckbandBattery: StateFlow<Int?> = _neckbandBattery

    var onCallAction: (() -> Unit)? = null
    var onSosAction: (() -> Unit)? = null
    var onDevicesUpdated: ((List<BleScanDevice>) -> Unit)? = null

    private val devices = linkedMapOf<String, BleScanDevice>()


    // ------------------------------------------------
    // PERMISSIONS
    // ------------------------------------------------

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun canScan(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            hasPermission(Manifest.permission.BLUETOOTH_SCAN)
        else
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun canConnect(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        else
            true

    private fun pushError(msg: String) {
        mainThread {
            if (state.errors.size > 50) state.errors.removeAt(0)
            state.errors.add(msg)
        }
    }


    // ------------------------------------------------
    // SCAN
    // ------------------------------------------------

    private val scanCallback = object : ScanCallback() {

        override fun onScanResult(type: Int, result: ScanResult) {
            val device  = result.device ?: return
            val address = device.address ?: return
            val name = try {
                if (canConnect()) device.name else null
            } catch (_: SecurityException) { null }
                ?: (result.scanRecord?.deviceName ?: "Unknown")

            Log.d("BLE_SCAN", "Device: $name | Address: $address | RSSI: ${result.rssi}")

            val item = BleScanDevice(name, address, result.rssi)
            devices[address] = item
            // onDevicesUpdated updates AppState.scannedDevices — must be on main thread
            mainThread { onDevicesUpdated?.invoke(devices.values.toList()) }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            pushError("Scan failed: $errorCode")
        }
    }

    fun startScan(): Boolean {
        if (scanning) return true
        if (!canScan()) { pushError("Missing scan permission"); return false }
        val s = scanner ?: return false
        devices.clear()
        mainThread { onDevicesUpdated?.invoke(emptyList()) }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanning = true
        return try {
            s.startScan(null, settings, scanCallback)
            true
        } catch (_: SecurityException) {
            scanning = false
            pushError("Scan security error")
            false
        }
    }

    fun stopScan() {
        if (!scanning) return
        scanning = false
        try { scanner?.stopScan(scanCallback) } catch (_: SecurityException) {}
    }


    // ------------------------------------------------
    // GLASSES GATT CALLBACK
    // ------------------------------------------------

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.d("BLE_CONN", "Connection state changed - status: $status, newState: $newState")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("BLE_CONN", "Connection failed with status: $status")
                mainThread { state.setConnected(false) }
                close()
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    val name = try {
                        if (canConnect()) g.device.name ?: "Unknown" else "Unknown"
                    } catch (_: SecurityException) { "Unknown" }
                    val address = g.device.address ?: ""
                    Log.d("BLE_CONN", "✅ Connected to $name ($address)")
                    mainThread { state.setConnected(true, name, address) }
                    try {
                        Log.d("BLE_CONN", "Requesting MTU 247...")
                        g.requestMtu(247)
                    } catch (_: SecurityException) {
                        Log.e("BLE_CONN", "SecurityException requesting MTU")
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d("BLE_CONN", "❌ Disconnected")
                    mainThread { state.setConnected(false) }
                    close()
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d("BLE_CONN", "MTU changed - mtu: $mtu, status: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                negotiatedMtu = mtu
                Log.d("BLE_CONN", "✅ MTU negotiated: $mtu")
                try {
                    Log.d("BLE_CONN", "Starting service discovery...")
                    gatt.discoverServices()
                } catch (_: SecurityException) {
                    Log.e("BLE_CONN", "SecurityException discovering services")
                }
            } else {
                Log.e("BLE_CONN", "❌ MTU negotiation failed with status: $status")
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            Log.d("BLE_CONN", "Services discovered - status: $status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("BLE_CONN", "❌ Service discovery failed"); return
            }
            val service = g.getService(SERVICE_UUID)
            if (service == null) {
                Log.e("BLE_CONN", "❌ Service UUID not found: $SERVICE_UUID"); return
            }
            Log.d("BLE_CONN", "✅ Service found: $SERVICE_UUID")
            commandChar = service.getCharacteristic(CHAR_UUID_COMMAND)
            statusChar  = service.getCharacteristic(CHAR_UUID_STATUS)
            Log.d("BLE_CONN", "Command char: ${commandChar?.uuid}")
            Log.d("BLE_CONN", "Status char: ${statusChar?.uuid}")

            // Send the current brightness as soon as we're connected
            // Read brightness on main thread to avoid race, then send
            mainThread {
                val b = state.brightness.value
                val initialBrightness = (b * 255).toInt().coerceIn(0, 255)
                sendTextCommand("BRIGHT:$initialBrightness")
            }

            statusChar?.let {
                Log.d("BLE_CONN", "Enabling notifications on status characteristic...")
                enableNotifications(g, it)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (descriptor.uuid == CCCD_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS)
                    Log.d("BLE_NOTIF", "✅✅✅ Notifications ENABLED successfully!")
                else
                    Log.e("BLE_NOTIF", "❌❌❌ Failed to enable notifications. Status: $status")
            }
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = characteristic.value ?: return
            onCharacteristicChanged(gatt, characteristic, value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val hex = value.joinToString(" ") { "%02X".format(it) }
            Log.d("BLE_RAW", "📦 Raw bytes received: $hex")
            Log.d("BLE_RX",  "📍 Characteristic UUID: ${characteristic.uuid}")
            Log.d("BLE_RX",  "📍 Expected UUID:       $CHAR_UUID_STATUS")

            if (characteristic.uuid != CHAR_UUID_STATUS) {
                Log.w("BLE_RX", "⚠️ UUID mismatch - ignoring notification"); return
            }

            val msg = value.toString(Charsets.UTF_8).trim()
            Log.d("BLE_RX", "📨 Decoded message: '$msg' (length: ${msg.length})")

            // ── All state writes go to main thread ────────────────────────
            when {
                msg == "CAMERA_ON" -> mainThread {
                    Log.d("BLE_RX", "📷 Camera ON triggered")
                    state.cameraActive.value = true
                }
                msg == "CAMERA_OFF" -> mainThread {
                    Log.d("BLE_RX", "📷 Camera OFF triggered")
                    state.cameraActive.value = false
                }
                msg == "CALL:ACCEPT" -> {
                    Log.d("BLE_RX", "📞 CALL:ACCEPT matched!")
                    if (onCallAction != null) mainThread { onCallAction?.invoke() }
                    else Log.e("BLE_RX", "❌ onCallAction is NULL")
                }
                msg == "CALL:REJECT" -> {
                    Log.d("BLE_RX", "📞 CALL:REJECT matched!")
                    mainThread { onCallAction?.invoke() }
                }
                msg == "SOS" -> {
                    Log.d("BLE_RX", "🆘 SOS triggered")
                    mainThread { onSosAction?.invoke() }
                }
                msg.startsWith("TEMP:") -> {
                    val tempStr = msg.removePrefix("TEMP:")
                    Log.d("BLE_RX", "🌡️ Temperature string: '$tempStr'")
                    val temp = tempStr.toFloatOrNull()
                    if (temp != null) {
                        Log.d("BLE_RX", "🌡️ Temperature parsed: $temp°C")
                        // ✅ Main thread — this is why it wasn't showing before
                        mainThread {
                            state.updateTemperature(temp)
                            Log.d("BLE_RX", "🌡️ Temperature updated in state: ${state.temperatureC.value}°C")
                        }
                    } else {
                        Log.e("BLE_RX", "❌ Failed to parse temperature: '$tempStr'")
                    }
                }
                else -> Log.d("BLE_RX", "❓ Unknown message: '$msg'")
            }
        }
    }


    // ------------------------------------------------
    // NECKBAND GATT CALLBACK
    // ------------------------------------------------

    private val neckbandGattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    val name = try { g.device.name ?: "Neckband" } catch (_: SecurityException) { "Neckband" }
                    Log.d("NECKBAND", "✅ Connected to neckband: $name")
                    mainThread { state.neckbandConnected.value = true }
                    try { g.discoverServices() } catch (_: SecurityException) {}
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d("NECKBAND", "❌ Neckband disconnected — clearing battery")
                    _neckbandBattery.value = null
                    mainThread {
                        state.neckbandBatteryLevel.value = null
                        state.neckbandConnected.value = false
                    }
                    closeNeckband()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("NECKBAND", "❌ Service discovery failed"); return
            }

            val batteryChar = g.getService(BATTERY_SERVICE_UUID)
                ?.getCharacteristic(BATTERY_LEVEL_UUID)

            if (batteryChar != null) {
                Log.d("NECKBAND", "✅ Standard battery characteristic found — reading...")
                try { g.readCharacteristic(batteryChar) } catch (_: SecurityException) {}

                // Subscribe for automatic battery updates
                try {
                    g.setCharacteristicNotification(batteryChar, true)
                    val cccd = batteryChar.getDescriptor(CCCD_UUID)
                    if (cccd != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            g.writeDescriptor(cccd)
                        }
                    }
                } catch (_: SecurityException) {}
            } else {
                Log.w("NECKBAND", "⚠️ Standard battery service not found. Logging all services:")
                g.services.forEach { service ->
                    Log.d("NECKBAND_SERVICES", "  SERVICE: ${service.uuid}")
                    service.characteristics.forEach { char ->
                        Log.d("NECKBAND_SERVICES", "    CHAR: ${char.uuid} | properties: ${char.properties}")
                    }
                }
                Log.w("NECKBAND", "👆 Share these logs — we'll find the right UUID from above")
            }
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val value = characteristic.value ?: return
                onCharacteristicRead(gatt, characteristic, value, status)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (characteristic.uuid == BATTERY_LEVEL_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                val percent = value[0].toInt() and 0xFF
                Log.d("NECKBAND", "🔋 Battery level: $percent%")
                _neckbandBattery.value = percent
                mainThread { state.neckbandBatteryLevel.value = percent }
            }
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = characteristic.value ?: return
            onCharacteristicChanged(gatt, characteristic, value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == BATTERY_LEVEL_UUID) {
                val percent = value[0].toInt() and 0xFF
                Log.d("NECKBAND", "🔋 Battery notification: $percent%")
                _neckbandBattery.value = percent
                mainThread { state.neckbandBatteryLevel.value = percent }
            }
        }
    }


    // ------------------------------------------------
    // CONNECT — GLASSES
    // ------------------------------------------------

    fun connectByAddress(address: String) {
        val a = adapter ?: return
        if (!canConnect()) return
        val device = try {
            a.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) { null } ?: return
        stopScan()
        close()
        Log.d("BLE_CONN", "🔌 Connecting to $address...")
        try {
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (_: SecurityException) {
            Log.e("BLE_CONN", "SecurityException during connect")
        }
    }

    fun disconnect() {
        try { gatt?.disconnect() } catch (_: SecurityException) {}
    }

    fun close() {
        commandChar = null
        statusChar  = null
        try { gatt?.close() } catch (_: SecurityException) {}
        gatt = null
    }


    // ------------------------------------------------
    // CONNECT — NECKBAND
    // ------------------------------------------------

    fun connectNeckband(address: String) {
        val a = adapter ?: return
        if (!canConnect()) return
        val device = try {
            a.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) { null } ?: return
        closeNeckband()
        Log.d("NECKBAND", "🔌 Connecting to neckband at $address...")
        try {
            neckbandGatt = device.connectGatt(
                context, false, neckbandGattCallback, BluetoothDevice.TRANSPORT_LE
            )
        } catch (_: SecurityException) {
            Log.e("NECKBAND", "SecurityException connecting to neckband")
        }
    }

    fun disconnectNeckband() {
        try { neckbandGatt?.disconnect() } catch (_: SecurityException) {}
    }

    fun closeNeckband() {
        try { neckbandGatt?.close() } catch (_: SecurityException) {}
        neckbandGatt = null
    }

    fun refreshNeckbandBattery() {
        val g = neckbandGatt ?: run {
            Log.w("NECKBAND", "Cannot refresh — neckband not connected"); return
        }
        val char = g.getService(BATTERY_SERVICE_UUID)
            ?.getCharacteristic(BATTERY_LEVEL_UUID) ?: run {
            Log.w("NECKBAND", "Cannot refresh — battery characteristic not found"); return
        }
        try { g.readCharacteristic(char) } catch (_: SecurityException) {}
    }

    val isNeckbandConnected: Boolean
        get() = neckbandGatt != null


    // ------------------------------------------------
    // SEND TEXT
    // ------------------------------------------------

    fun sendTextCommand(text: String) {
        val g  = gatt ?: run { Log.w("BLE_SEND", "sendTextCommand: gatt is null"); return }
        val ch = commandChar ?: run { Log.w("BLE_SEND", "sendTextCommand: commandChar is null"); return }
        val data = text.toByteArray(Charsets.UTF_8)
        Log.d("BLE_SEND", "📤 Sending: $text")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(ch, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                ch.value = data
                @Suppress("DEPRECATION")
                g.writeCharacteristic(ch)
            }
        } catch (_: SecurityException) {
            pushError("Write security error")
        }
    }

    fun pushNavigationToGlasses(
        direction: String,
        distance: String,
        streetName: String,
        nextTurn: String = ""
    ) {
        fun clean(input: String) = input.replace(Regex("[^a-zA-Z0-9 ]"), "").trim()
        val navCommand = "NAV|${clean(direction)}|${clean(distance)}|${clean(streetName)}|${clean(nextTurn)}"
        Log.d("BLE_SEND", navCommand)
        sendTextCommand(navCommand)
    }

    fun stopNavigation() { sendTextCommand("NAV:STOP") }


    // ------------------------------------------------
    // ENABLE NOTIFICATIONS
    // ------------------------------------------------

    private fun enableNotifications(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
        Log.d("BLE_NOTIF", "🔔 Enabling notifications for ${ch.uuid}")
        try {
            val notifEnabled = g.setCharacteristicNotification(ch, true)
            Log.d("BLE_NOTIF", "setCharacteristicNotification result: $notifEnabled")
            val descriptor = ch.getDescriptor(CCCD_UUID) ?: run {
                Log.e("BLE_NOTIF", "❌ CCCD descriptor not found!"); return
            }
            Log.d("BLE_NOTIF", "✅ CCCD descriptor found: ${descriptor.uuid}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Log.d("BLE_NOTIF", "Using Android 13+ writeDescriptor API")
                val result = g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                Log.d("BLE_NOTIF", "writeDescriptor (new API) result: $result")
            } else {
                Log.d("BLE_NOTIF", "Using legacy writeDescriptor API")
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                val result = g.writeDescriptor(descriptor)
                Log.d("BLE_NOTIF", "writeDescriptor (old API) result: $result")
            }
        } catch (e: SecurityException) {
            Log.e("BLE_NOTIF", "❌ SecurityException: ${e.message}")
        } catch (e: Exception) {
            Log.e("BLE_NOTIF", "❌ Exception: ${e.message}")
        }
    }
}
