package com.example.arglasses

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

data class ScanDevice(
    val name: String,
    val address: String,
    val rssi: Int
)

class AppState {

    // =========================
    // Auth / onboarding
    // =========================

    val email = mutableStateOf("")
    val username = mutableStateOf("")
    val isLoggedIn = mutableStateOf(false)
    val permissionsDone = mutableStateOf(false)

    // =========================
    // UI
    // =========================

    val darkMode = mutableStateOf(true)

    // =========================
    // Device Status
    // =========================

    val leftBattery = mutableStateOf(85)
    val rightBattery = mutableStateOf(82)

    // ── Neckband ──────────────────────────────────────────────────────────
    val neckbandBatteryLevel = mutableStateOf<Int?>(null)
    val neckbandConnected = mutableStateOf(false)

    var bleConnectNeckband: ((String) -> Unit)? = null
    var bleDisconnectNeckband: (() -> Unit)? = null
    var bleRefreshNeckband: (() -> Unit)? = null

    val connected = mutableStateOf(false)

    val dataSpeedKbps = mutableStateOf(0)
    val latencyMs = mutableStateOf(0)

    val notificationMirroring = mutableStateOf(false)
    val mapsAssistEnabled = mutableStateOf(false)

    // Temperature (updated from ESP32 via BLE TEMP: notification)
    val temperatureC = mutableStateOf(0f)

    val brightness = mutableStateOf(1.0f)
    val volume = mutableStateOf(0.5f)

    val cameraActive = mutableStateOf(false)
    val cameraStatusText = mutableStateOf("")

    // NAV STATE CONTROL
    val navActive = mutableStateOf(false)
    var lastNavMessage: String = ""
    var lastNavTime = 0L

    // =========================
    // Camera
    // =========================

    val cameraMode = mutableStateOf(CameraMode.STREAM)

    val micEnabled = mutableStateOf(false)

    val recording = mutableStateOf(false)
    val streaming = mutableStateOf(false)

    var cameraTakePhoto:
            (((String) -> Unit, (String) -> Unit) -> Unit)? = null

    var cameraStartVideo:
            ((withAudio: Boolean, onEvent: (String) -> Unit) -> Unit)? = null

    var cameraStopVideo:
            (() -> Unit)? = null

    // =========================
    // Camera hardware flag
    // Set this to true from MainActivity once a physical camera is wired up
    // =========================
    var cameraHardwareConnected = false

    // =========================
    // Permissions
    // =========================

    val permLocation = mutableStateOf(false)
    val permNearby = mutableStateOf(false)
    val permSms = mutableStateOf(false)
    val permCall = mutableStateOf(false)
    val permMediaAudio = mutableStateOf(false)

    // =========================
    // BLE
    // =========================

    val bleScanning = mutableStateOf(false)

    val connectedDeviceName = mutableStateOf("")
    val connectedDeviceAddress = mutableStateOf("")

    var isBluetoothEnabled: (() -> Boolean)? = null

    val scannedDevices = mutableStateListOf<ScanDevice>()

    var bleStartScan: (() -> Unit)? = null
    var bleStopScan: (() -> Unit)? = null
    var bleConnect: ((String) -> Unit)? = null
    var bleDisconnect: (() -> Unit)? = null

    var bleCameraOn: (() -> Unit)? = null
    var bleCameraOff: (() -> Unit)? = null

    // Generic BLE sender
    var bleSendCommand: ((String) -> Unit)? = null

    var blePushNavigation: ((String, String, String, String) -> Unit)? = null

    // =========================
    // BLE Helpers
    // =========================

    fun sendBleText(text: String) {
        if (text.isNotBlank()) {
            bleSendCommand?.invoke(text)
        }
    }

    fun clearScanResults() {
        scannedDevices.clear()
    }

    fun setScanResults(list: List<ScanDevice>) {
        scannedDevices.clear()
        scannedDevices.addAll(list)
    }

    fun upsertScanResult(device: ScanDevice) {
        val idx = scannedDevices.indexOfFirst { it.address == device.address }
        if (idx >= 0) scannedDevices[idx] = device
        else scannedDevices.add(device)
    }

    // =========================
    // Connection State
    // =========================

    fun setConnected(
        isConnected: Boolean,
        name: String = "",
        address: String = ""
    ) {
        connected.value = isConnected

        if (isConnected) {
            connectedDeviceName.value = name
            connectedDeviceAddress.value = address
        } else {
            connectedDeviceName.value = ""
            connectedDeviceAddress.value = ""
            cameraActive.value = false
            recording.value = false
            streaming.value = false
            temperatureC.value = 0f
        }
    }

    // =========================
    // Temperature Update
    // =========================

    fun updateTemperature(temp: Float) {
        if (temp.isFinite()) {
            temperatureC.value = temp
        }
    }

    // =========================
    // Diagnostics
    // =========================

    val errors = mutableStateListOf<String>()

    val lastChecked = mutableStateOf("Never")

    val bleOk      = mutableStateOf(false)
    val cameraOk   = mutableStateOf(false)
    val micOk      = mutableStateOf(false)
    val displayOk  = mutableStateOf(true)

    private var checkCount = 0

    fun clearErrors() {
        errors.clear()
    }

    fun runDiagnosticsCheck() {
        errors.clear()
        checkCount += 1
        lastChecked.value = "Run #$checkCount"

        // BLE: pass if connected
        bleOk.value = connected.value

        // Camera: pass only when physical camera hardware is wired up.
        // Set state.cameraHardwareConnected = true in MainActivity once you
        // have a camera module attached and initialised.
        cameraOk.value = cameraHardwareConnected

        // Mic: always pass — Bluetooth mic is always active when neckband is connected.
        // When you add a toggle for this in future, swap back to micEnabled.value.
        micOk.value = true

        // Display: always pass (no way to detect failure in software)
        displayOk.value = true

        // Collect errors for anything that failed
        if (!bleOk.value)    errors.add("BLE disconnected")
        if (!cameraOk.value) errors.add("Camera not connected")
        // Mic error removed — mic always passes

        if (temperatureC.value >= 60f)
            errors.add("High temperature: ${temperatureC.value}°C")
        if (leftBattery.value <= 5)
            errors.add("Left battery critically low: ${leftBattery.value}%")
        if (rightBattery.value <= 5)
            errors.add("Right battery critically low: ${rightBattery.value}%")

        val neckBat = neckbandBatteryLevel.value
        if (neckBat != null && neckBat <= 10)
            errors.add("Neckband battery critically low: $neckBat%")
        if (!neckbandConnected.value)
            errors.add("Neckband not connected")
    }
}

enum class CameraMode {
    PHOTO,
    VIDEO,
    STREAM
}
