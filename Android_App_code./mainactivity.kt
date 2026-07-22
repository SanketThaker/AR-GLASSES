package com.example.arglasses

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { MainActivityRoot() }
    }
}

@Composable
private fun MainActivityRoot() {

    val context = LocalContext.current
    val appState = remember { AppState() }
    val authManager = remember { AuthManager(context) }
    val ble = remember { BleManagerAndroid(context, appState) }
    val camera = remember { CameraXControllerAndroid(context) }
    val callManager = remember { CallManager(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Neckband MAC — used only for Classic BT battery matching
    val NECKBAND_MAC = "41:42:C1:69:CF:E6"

    appState.isBluetoothEnabled = { ble.isBluetoothEnabled }
    appState.bleSendCommand     = { message -> ble.sendTextCommand(message) }
    appState.blePushNavigation  = { dir, dist, street, next ->
        ble.pushNavigationToGlasses(dir, dist, street, next)
    }

    // Double-click call action from glasses
    ble.onCallAction = {
        Log.d("CALL_ACTION", "═══════════════════════════════════════")
        Log.d("CALL_ACTION", "🔔 BLE CALL ACTION TRIGGERED")
        Log.d("CALL_ACTION", "Call status: ${CallReceiver.callStateFlow.value.status}")
        Log.d("CALL_ACTION", "Caller name: ${CallReceiver.callStateFlow.value.callerName}")
        Log.d("CALL_ACTION", "Phone number: ${CallReceiver.callStateFlow.value.phoneNumber}")
        Log.d("CALL_ACTION", "Attempting acceptCall()...")
        callManager.acceptCall()
        Log.d("CALL_ACTION", "═══════════════════════════════════════")
    }

    // Triple-click SOS
    ble.onSosAction = {
        Log.d("SOS_ACTION", "🆘 SOS triggered - calling 112")
        callManager.makeCall("112")
    }

    // =========================
    // CLASSIC BT NECKBAND BATTERY
    // =========================
    LaunchedEffect(Unit) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE)
                as BluetoothManager
        val adapter = bluetoothManager.adapter

        while (true) {
            try {
                val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                } else true

                if (hasPerm) {
                    val bondedDevices = adapter.bondedDevices
                    var found = false

                    bondedDevices?.forEach { device ->
                        try {
                            val batteryLevel = device.javaClass
                                .getMethod("getBatteryLevel")
                                .invoke(device) as? Int

                            Log.d("CLASSIC_BT", "Device: ${device.name} (${device.address}) Battery: $batteryLevel%")

                            if (device.address == NECKBAND_MAC && batteryLevel != null && batteryLevel >= 0) {
                                appState.neckbandBatteryLevel.value = batteryLevel
                                appState.neckbandConnected.value    = true
                                found = true
                                Log.d("CLASSIC_BT", "✅ Neckband battery updated: $batteryLevel%")
                            }
                        } catch (e: Exception) {
                            Log.e("CLASSIC_BT", "getBatteryLevel failed for ${device.name}: ${e.message}")
                        }
                    }

                    if (!found) {
                        appState.neckbandConnected.value    = false
                        appState.neckbandBatteryLevel.value = null
                        Log.d("CLASSIC_BT", "⚠️ Neckband not found in bonded devices")
                    }
                }
            } catch (e: Exception) {
                Log.e("CLASSIC_BT", "Battery poll error: ${e.message}")
            }

            kotlinx.coroutines.delay(10_000)
        }
    }

    // =========================
    // NOTIFICATION MIRRORING
    // =========================
    LaunchedEffect(Unit) {
        GlassNotificationListener.onNotificationReceived = onNotificationReceived@{ message ->

            if (!message.startsWith("CALL:") && (message.contains(
                    "Incoming call", ignoreCase = true
                ) || message.contains("Phone:", ignoreCase = true))
            ) {
                return@onNotificationReceived
            }

            val isPriority = message.startsWith("NAV:") || message.startsWith("CALL:")

            if (appState.connected.value && (isPriority || appState.notificationMirroring.value)) {

                if (message == "NAV:END" || message == "NAV:STOP") {
                    appState.navActive.value = false
                    appState.lastNavMessage  = ""
                    ble.sendTextCommand("NAV:END")
                    return@onNotificationReceived
                }

                if (message.startsWith("NAV:")) {
                    Log.d("NAV_SEND", "Forwarding to glasses: $message")
                    appState.navActive.value  = true
                    appState.lastNavMessage   = message
                    appState.lastNavTime      = System.currentTimeMillis()
                    ble.sendTextCommand(message)
                    return@onNotificationReceived
                }

                if (!appState.navActive.value) {
                    ble.sendTextCommand(message)
                }

                if (appState.navActive.value) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        val now = System.currentTimeMillis()
                        if (now - appState.lastNavTime < 40_000) {
                            ble.sendTextCommand(appState.lastNavMessage)
                        } else {
                            appState.navActive.value = false
                        }
                    }, 3000)
                }
            }
        }
    }

    // =========================
    // NAV KEEP-ALIVE
    // =========================
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000)
            if (appState.navActive.value) {
                val now = System.currentTimeMillis()
                if (now - appState.lastNavTime < 40_000) {
                    ble.sendTextCommand(appState.lastNavMessage)
                } else {
                    appState.navActive.value = false
                }
            }
        }
    }

    // =========================
    // CALL STATE MIRRORING
    // =========================
    val callState by CallReceiver.callStateFlow.collectAsState()

    LaunchedEffect(callState) {
        Log.d("CALL_MIRROR", "═══════════════════════════════════════")
        Log.d("CALL_MIRROR", "📞 Call state changed:")
        Log.d("CALL_MIRROR", "  Status: ${callState.status}")
        Log.d("CALL_MIRROR", "  Name: ${callState.callerName}")
        Log.d("CALL_MIRROR", "  Number: ${callState.phoneNumber}")

        val name   = callState.callerName
        val number = callState.phoneNumber

        val display = when {
            !name.isNullOrBlank() && !number.isNullOrBlank() && name != number -> "$name ($number)"
            !name.isNullOrBlank()   -> name
            !number.isNullOrBlank() -> number
            else                    -> "Unknown"
        }

        Log.d("CALL_MIRROR", "  Display string: $display")

        val message = when (callState.status) {
            "RINGING" -> "CALL:$display".also { Log.d("CALL_MIRROR", "  Sending to glasses: $it") }
            "ON_CALL" -> "CALL:ACTIVE".also  { Log.d("CALL_MIRROR", "  Sending to glasses: $it") }
            "IDLE"    -> "CALL:END".also     { Log.d("CALL_MIRROR", "  Sending to glasses: $it") }
            else      -> null.also           { Log.d("CALL_MIRROR", "  Unknown status, not sending") }
        }

        message?.let {
            Log.d("CALL_MIRROR", "📤 Sending: $it")
            ble.sendTextCommand(it)
        }

        Log.d("CALL_MIRROR", "═══════════════════════════════════════")
    }

    // =========================
    // AUTO LOGIN RESTORE
    // =========================
    LaunchedEffect(Unit) {
        if (authManager.isLoggedIn()) {
            appState.email.value      = authManager.getEmail() ?: ""
            appState.username.value   = context
                .getSharedPreferences("auth_data", Context.MODE_PRIVATE)
                .getString("username", "") ?: ""
            appState.isLoggedIn.value = true
        }
    }

    // =========================
    // PERMISSIONS
    // =========================
    fun hasPermission(p: String): Boolean =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    fun requiredBlePermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasBlePermissionSet(): Boolean = requiredBlePermissions().all { hasPermission(it) }

    val launcherBleOnly = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (requiredBlePermissions().all { result[it] == true }) {
            appState.clearScanResults()
            appState.bleScanning.value = ble.startScan()
        } else {
            appState.errors.add("BLE permissions denied.")
        }
    }

    fun requestBlePermsThenScanIfPossible() {
        if (hasBlePermissionSet()) {
            appState.clearScanResults()
            appState.bleScanning.value = ble.startScan()
            return
        }
        launcherBleOnly.launch(requiredBlePermissions())
    }

    DisposableEffect(Unit) {
        ble.onDevicesUpdated = { list ->
            appState.setScanResults(list.map { ScanDevice(it.name, it.address, it.rssi) })
        }
        onDispose {
            ble.stopScan()
            ble.onDevicesUpdated = null
        }
    }

    appState.bleStartScan  = { requestBlePermsThenScanIfPossible() }
    appState.bleStopScan   = {
        ble.stopScan()
        appState.clearScanResults()
        appState.bleScanning.value = false
    }
    appState.bleConnect    = { address ->
        ble.stopScan()
        appState.bleScanning.value = false
        if (hasBlePermissionSet()) ble.connectByAddress(address)
    }
    appState.bleDisconnect = { ble.disconnect() }

    App(
        state    = appState,

        // ── Register ──────────────────────────────────────────────────────
        onRegister = { email, password, username ->
            val success = authManager.register(email, password)
            if (success) {
                // AuthManager doesn't store username — save it ourselves
                context.getSharedPreferences("auth_data", Context.MODE_PRIVATE)
                    .edit().putString("username", username).apply()
                appState.email.value      = email
                appState.username.value   = username
                appState.isLoggedIn.value = true
            } else {
                appState.errors.add("Registration failed. Use a valid Gmail address.")
            }
        },

        // ── Login ─────────────────────────────────────────────────────────
        onLogin = { email, password ->
            val success = authManager.login(email, password)
            if (success) {
                appState.email.value      = email
                appState.username.value   = context
                    .getSharedPreferences("auth_data", Context.MODE_PRIVATE)
                    .getString("username", "") ?: ""
                appState.isLoggedIn.value = true
            } else {
                appState.errors.add("Login failed. Check your email and password.")
            }
        },

        // ── Logout ────────────────────────────────────────────────────────
        onLogout = {
            ble.disconnect()
            authManager.logout()
            appState.isLoggedIn.value             = false
            appState.email.value                  = ""
            appState.username.value               = ""
            appState.connected.value              = false
            appState.connectedDeviceName.value    = ""
            appState.connectedDeviceAddress.value = ""
            appState.temperatureC.value           = 0f
            appState.neckbandBatteryLevel.value   = null
            appState.errors.clear()
        },

        onOpenNotificationSettings = {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            context.startActivity(intent)
        }
    )
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(
        state = AppState(),
        onRegister = { _, _, _ -> },
        onLogin    = { _, _ -> },
        onLogout   = {},
        onOpenNotificationSettings = {}
    )
}
