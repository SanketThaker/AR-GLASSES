@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.arglasses

import androidx.compose.material3.TextButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arglasses.ui.components.GlassCard
import com.example.arglasses.ui.components.ShimmerLine
import com.example.arglasses.ui.theme.*

// ─── Nav destinations ────────────────────────────────────────────────────────
private enum class Screen { HOME, CONTROLS, DIAGNOSTICS, SETTINGS }
private enum class SettingsPage { MAIN, APPEARANCE, TROUBLESHOOT, DELETE_ACCOUNT, ABOUT }

// ─── Root ─────────────────────────────────────────────────────────────────────
@Composable
fun App(
    state: AppState,
    onRegister: (email: String, password: String, username: String) -> Unit,
    onLogin: (email: String, password: String) -> Unit,
    onLogout: () -> Unit,
    onOpenNotificationSettings: () -> Unit
) {
    AppTheme(darkMode = state.darkMode.value) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                !state.isLoggedIn.value -> LoginScreen(state, onRegister, onLogin)
                !state.connected.value  -> BleScreen(state)
                else -> MainShell(state, onLogout, onOpenNotificationSettings)
            }
        }
    }
}

// ─── Main shell with bottom nav ───────────────────────────────────────────────
@Composable
private fun MainShell(
    state: AppState,
    onLogout: () -> Unit,
    onOpenNotificationSettings: () -> Unit
) {
    var screen by remember { mutableStateOf(Screen.HOME) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "AR Glasses",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    // Refresh / sync icon on right
                    IconButton(onClick = { state.runDiagnosticsCheck() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier.border(
                    width = 1.dp,
                    color = AVBorder,
                    shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)
                )
            ) {
                AuraNavItem(
                    selected = screen == Screen.HOME,
                    onClick  = { screen = Screen.HOME },
                    icon     = Icons.Outlined.Home,
                    selectedIcon = Icons.Filled.Home,
                    label    = "Home"
                )
                AuraNavItem(
                    selected = screen == Screen.CONTROLS,
                    onClick  = { screen = Screen.CONTROLS },
                    icon     = Icons.Outlined.Tune,
                    selectedIcon = Icons.Filled.Tune,
                    label    = "Controls"
                )
                AuraNavItem(
                    selected = screen == Screen.DIAGNOSTICS,
                    onClick  = { screen = Screen.DIAGNOSTICS },
                    icon     = Icons.Outlined.BugReport,
                    selectedIcon = Icons.Filled.BugReport,
                    label    = "Diag"
                )
                AuraNavItem(
                    selected = screen == Screen.SETTINGS,
                    onClick  = { screen = Screen.SETTINGS },
                    icon     = Icons.Outlined.Settings,
                    selectedIcon = Icons.Filled.Settings,
                    label    = "Settings"
                )
            }
        }
    ) { padding ->
        when (screen) {
            Screen.HOME        -> HomeScreen(state, Modifier.padding(padding))
            Screen.CONTROLS    -> ControlsScreen(state, Modifier.padding(padding))
            Screen.DIAGNOSTICS -> DiagnosticsScreen(state, Modifier.padding(padding))
            Screen.SETTINGS    -> SettingsScreen(
                state, Modifier.padding(padding), onLogout, onOpenNotificationSettings
            )
        }
    }
}

@Composable
private fun RowScope.AuraNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    selectedIcon: ImageVector,
    label: String
) {
    NavigationBarItem(
        selected = selected,
        onClick  = onClick,
        icon = {
            Icon(
                if (selected) selectedIcon else icon,
                contentDescription = label,
                tint = if (selected) AVCyan else AVTextSub,
                modifier = Modifier.size(22.dp)
            )
        },
        label = {
            Text(
                label,
                color = if (selected) AVCyan else AVTextSub,
                style = MaterialTheme.typography.labelSmall
            )
        },
        colors = NavigationBarItemDefaults.colors(
            indicatorColor = AVCyan.copy(alpha = 0.12f),
            selectedIconColor   = AVCyan,
            unselectedIconColor = AVTextSub,
            selectedTextColor   = AVCyan,
            unselectedTextColor = AVTextSub
        )
    )
}

// ─── Login screen ─────────────────────────────────────────────────────────────
@Composable
private fun LoginScreen(
    state: AppState,
    onRegister: (String, String, String) -> Unit,
    onLogin: (String, String) -> Unit
) {
    var isRegisterMode  by remember { mutableStateOf(false) }
    var password        by remember { mutableStateOf("") }
    var username        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val emailRegex   = remember { Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$") }
    val isEmailValid = emailRegex.matches(state.email.value)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            // ── Logo area ─────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(AVCyan.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Visibility,
                    contentDescription = null,
                    tint = AVCyan,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "AuraVision",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Text(
                "AR Smart Glasses Companion",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(40.dp))

            // ── Card ──────────────────────────────────────────────────────
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    Text(
                        if (isRegisterMode) "Create your account" else "Sign in to your account",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Email
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("EMAIL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = state.email.value,
                            onValueChange = { state.email.value = it },
                            placeholder = { Text("you@example.com", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            colors = auraTextFieldColors()
                        )
                    }

                    // Username (register only)
                    if (isRegisterMode) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("USERNAME", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                placeholder = { Text("Your name", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                                colors = auraTextFieldColors()
                            )
                        }
                    }

                    // Password
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("PASSWORD", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            placeholder = { Text("••••••••", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            visualTransformation = if (passwordVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            colors = auraTextFieldColors()
                        )
                    }

                    // Primary CTA
                    Button(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        onClick = {
                            if (isRegisterMode) onRegister(state.email.value, password, username)
                            else onLogin(state.email.value, password)
                        },
                        enabled = isEmailValid && password.isNotBlank(),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AVCyan,
                            contentColor   = AVNavy
                        )
                    ) {
                        Icon(Icons.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isRegisterMode) "Create account" else "Sign In",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Toggle
                    TextButton(
                        onClick = { isRegisterMode = !isRegisterMode },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isRegisterMode) "Already have an account? Sign in"
                            else "Don't have an account? Register",
                            color = AVCyan,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─── BLE / scan screen ────────────────────────────────────────────────────────
@Composable
private fun BleScreen(state: AppState, modifier: Modifier = Modifier) {
    var isBtOn by remember { mutableStateOf(state.isBluetoothEnabled?.invoke() ?: true) }

    LaunchedEffect(Unit) {
        while (true) {
            val on = state.isBluetoothEnabled?.invoke() ?: true
            if (isBtOn != on) isBtOn = on
            if (!on && state.bleScanning.value) state.bleStopScan?.invoke()
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Welcome back", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    state.username.value.ifBlank { "User" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            // Connection dot
            StatusDot(connected = state.connected.value)
        }

        if (!isBtOn) {
            AuraBanner(
                message = "Bluetooth is off. Please enable it to connect.",
                isError = true
            )
        }

        // Scan button
        Button(
            onClick = { state.bleStartScan?.invoke() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = !state.bleScanning.value && isBtOn,
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = AVNavy3,
                contentColor   = AVCyan
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, AVBorder)
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (state.bleScanning.value) "Scanning…" else "Scan for Devices",
                style = MaterialTheme.typography.titleMedium
            )
        }

        // Discovered devices
        if (state.scannedDevices.isNotEmpty()) {
            state.scannedDevices.forEach { device ->
                DeviceRow(
                    name    = device.name.ifBlank { "AuraVision Device" },
                    address = device.address,
                    rssi    = device.rssi,
                    onPair  = { state.bleConnect?.invoke(device.address) }
                )
            }
        } else if (state.bleScanning.value) {
            // Shimmer placeholders
            repeat(3) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            ShimmerLine(height = 14.dp, modifier = Modifier.fillMaxWidth(0.5f))
                            ShimmerLine(height = 12.dp, modifier = Modifier.fillMaxWidth(0.35f))
                        }
                        ShimmerLine(height = 32.dp, modifier = Modifier.width(60.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        // Controls row
        if (state.connected.value) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Connected to ${state.connectedDeviceName.value.ifBlank { "device" }}", color = AVGreen, style = MaterialTheme.typography.bodyMedium)
                    Text(state.connectedDeviceAddress.value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    FilledTonalButton(
                        onClick = { state.bleDisconnect?.invoke() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            contentColor   = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Disconnect") }
                }
            }
        }
    }
}

// ─── Home screen ──────────────────────────────────────────────────────────────
@Composable
private fun HomeScreen(state: AppState, modifier: Modifier = Modifier) {
    val loading = !state.connected.value

    // Manual message state lives here so it's on the Home screen
    var manualMessage by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Welcome back", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    state.username.value.ifBlank { "User" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            StatusDot(connected = state.connected.value)
        }

        // ── Device Overview card ─────────────────────────────────────────
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionHeader("Device Overview")

                if (loading) {
                    repeat(3) { ShimmerLine(height = 16.dp); Spacer(Modifier.height(6.dp)) }
                } else {
                    // Battery
                    val neckBat = state.neckbandBatteryLevel.value
                    if (neckBat != null) {
                        BatteryBar("Battery", neckBat)
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Battery", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Reading…", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider(color = AVBorder)

                    // Temperature
                    // ESP32 must send a BLE notification in the format: "TEMP:25.3"
                    // Your Android BLE callback should then call: state.updateTemperature(25.3f)
                    val temp = state.temperatureC.value
                    val hasTemp = temp > 0f
                    val tempColor = when {
                        !hasTemp    -> MaterialTheme.colorScheme.onSurfaceVariant
                        temp >= 60f -> MaterialTheme.colorScheme.error
                        temp >= 45f -> MaterialTheme.colorScheme.tertiary
                        else        -> AVGreen
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Temperature",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (hasTemp) "${"%.1f".format(temp)} °C" else "Reading…",
                            style = MaterialTheme.typography.labelLarge,
                            color = tempColor
                        )
                    }

                    // ── CAMERA STATUS REMOVED ──────────────────────────
                    // Text("Camera: ${if (state.cameraActive.value) "Active" else "Inactive"}")

                    // Disconnect button
                    Button(
                        onClick  = { state.bleDisconnect?.invoke() },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        enabled  = state.connected.value,
                        shape    = MaterialTheme.shapes.medium,
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = AVCyan.copy(alpha = 0.12f),
                            contentColor   = AVCyan,
                            disabledContainerColor = AVNavy3,
                            disabledContentColor   = AVTextSub
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AVCyan.copy(alpha = 0.3f))
                    ) {
                        Icon(Icons.Filled.BluetoothDisabled, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Disconnect Device", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ── Manual Message card ───────────────────────────────────────────
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Manual Message")
                OutlinedTextField(
                    value         = manualMessage,
                    onValueChange = { manualMessage = it },
                    placeholder   = { Text("Message to send to glasses", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    shape         = MaterialTheme.shapes.medium,
                    colors        = auraTextFieldColors()
                )
                Button(
                    onClick = {
                        run {
                            if (!state.connected.value) { state.errors.add("BLE not connected"); return@run }
                            if (manualMessage.isNotBlank()) {
                                state.bleSendCommand?.invoke(manualMessage)
                                manualMessage = ""
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape    = MaterialTheme.shapes.medium,
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = AVCyan,
                        contentColor   = AVNavy
                    )
                ) {
                    Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send to Glasses", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ─── Controls screen ──────────────────────────────────────────────────────────
@Composable
private fun ControlsScreen(state: AppState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Controls", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Text("Manage brightness and device controls.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)

        // ── CAMERA CARD COMMENTED OUT ──────────────────────────────────────
        // Camera card
        // GlassCard(modifier = Modifier.fillMaxWidth()) {
        //     Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        //         SectionHeader("Camera")
        //
        //         AuraPermissionRow("Microphone", state.micEnabled.value) {
        //             if (it) state.bleCameraOn?.invoke() else state.bleCameraOff?.invoke()
        //         }
        //         HorizontalDivider(color = AVBorder)
        //
        //         val statusText = when {
        //             !state.cameraActive.value -> "Off"
        //             state.recording.value     -> "Recording"
        //             state.streaming.value     -> "Streaming"
        //             else                      -> "Ready"
        //         }
        //         Row(
        //             verticalAlignment = Alignment.CenterVertically,
        //             horizontalArrangement = Arrangement.spacedBy(8.dp)
        //         ) {
        //             Box(
        //                 Modifier.size(8.dp).background(
        //                     if (state.cameraActive.value) AVGreen else AVTextSub,
        //                     CircleShape
        //                 )
        //             )
        //             Text("Status: $statusText", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        //         }
        //
        //         Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        //             AuraTonalButton(
        //                 label   = if (state.cameraActive.value) "Camera OFF" else "Camera ON",
        //                 onClick = {
        //                     if (!state.connected.value) state.errors.add("BLE not connected")
        //                     else if (state.cameraActive.value) state.bleCameraOff?.invoke()
        //                     else state.bleCameraOn?.invoke()
        //                 },
        //                 enabled  = state.connected.value,
        //                 modifier = Modifier.weight(1f)
        //             )
        //             AuraTonalButton(
        //                 label    = if (state.notificationMirroring.value) "Mirror OFF" else "Mirror ON",
        //                 onClick  = { state.notificationMirroring.value = !state.notificationMirroring.value },
        //                 modifier = Modifier.weight(1f)
        //             )
        //         }
        //     }
        // }

        // ── CAMERA MODE CARD COMMENTED OUT ────────────────────────────────
        // Mode card
        // GlassCard(modifier = Modifier.fillMaxWidth()) {
        //     Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        //         SectionHeader("Mode")
        //         Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        //             listOf(CameraMode.PHOTO, CameraMode.VIDEO, CameraMode.STREAM).forEach { mode ->
        //                 val isSelected = state.cameraMode.value == mode
        //                 FilledTonalButton(
        //                     onClick  = { state.cameraMode.value = mode },
        //                     modifier = Modifier.weight(1f),
        //                     enabled  = state.cameraActive.value,
        //                     colors   = ButtonDefaults.filledTonalButtonColors(
        //                         containerColor = if (isSelected) AVCyan.copy(alpha = 0.18f) else AVNavy3,
        //                         contentColor   = if (isSelected) AVCyan else AVTextSub
        //                     ),
        //                     border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, AVCyan.copy(0.4f)) else null
        //                 ) { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium) }
        //             }
        //         }
        //
        //         when (state.cameraMode.value) {
        //             CameraMode.PHOTO -> AuraTonalButton(
        //                 label   = "Capture Photo",
        //                 onClick = {
        //                     val t = state.cameraTakePhoto
        //                     if (t == null) state.errors.add("Camera not wired yet")
        //                     else t({ state.errors.add("Photo saved: $it") }, { state.errors.add("Photo error: $it") })
        //                 },
        //                 enabled  = state.cameraActive.value,
        //                 modifier = Modifier.fillMaxWidth()
        //             )
        //             CameraMode.VIDEO -> AuraTonalButton(
        //                 label   = if (!state.recording.value) "Start Video" else "Stop Video",
        //                 onClick = {
        //                     if (!state.recording.value) {
        //                         val s = state.cameraStartVideo
        //                         if (s == null) state.errors.add("Camera not wired yet")
        //                         else { s(state.micEnabled.value) { state.errors.add(it) }; state.recording.value = true }
        //                     } else { state.cameraStopVideo?.invoke(); state.recording.value = false }
        //                 },
        //                 enabled  = state.cameraActive.value,
        //                 modifier = Modifier.fillMaxWidth()
        //             )
        //             CameraMode.STREAM -> AuraTonalButton(
        //                 label   = if (!state.streaming.value) "Start Stream" else "Stop Stream",
        //                 onClick = { state.streaming.value = !state.streaming.value },
        //                 enabled  = state.cameraActive.value,
        //                 modifier = Modifier.fillMaxWidth()
        //             )
        //         }
        //     }
        // }

        // Full quick controls (manual message removed — now on Home screen)
        QuickControlsCard(state = state, showManualMessage = false)

        Spacer(Modifier.height(8.dp))
    }
}

// ─── Shared Quick Controls card ───────────────────────────────────────────────
@Composable
private fun QuickControlsCard(state: AppState, showManualMessage: Boolean) {
    // NOTE: showManualMessage is kept as a parameter for compatibility but the
    // manual message block has been moved to HomeScreen. It is no longer rendered here.
    var expanded by remember { mutableStateOf(true) }

    GlassCard(
        modifier = Modifier.fillMaxWidth().animateContentSize(tween(200))
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                SectionHeader("Quick Controls")
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = expanded, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    // Brightness — send to device only when finger is lifted
                    SliderRow(
                        label    = "Brightness",
                        value    = state.brightness.value,
                        onChange = { state.brightness.value = it },
                        onChangeFinished = {
                            if (state.connected.value) {
                                val raw = (state.brightness.value * 255).toInt().coerceIn(0, 255)
                                state.bleSendCommand?.invoke("BRIGHT:$raw")
                            }
                        }
                    )

                    // ── VOLUME SLIDER HIDDEN ─────────────────────────────
                    // SliderRow(
                    //     label    = "Volume",
                    //     value    = state.volume.value,
                    //     onChange = { state.volume.value = it },
                    //     onChangeFinished = {
                    //         if (state.connected.value) {
                    //             val raw = (state.volume.value * 100).toInt().coerceIn(0, 100)
                    //             state.bleSendCommand?.invoke("VOL:$raw")
                    //         }
                    //     }
                    // )

                    // Mirror pill button
                    // ── CAMERA BUTTON IN QUICK CONTROLS COMMENTED OUT ─────────────
                    // AuraTonalButton(
                    //     label   = if (state.cameraActive.value) "Turn Camera OFF" else "Turn Camera ON",
                    //     onClick = {
                    //         if (!state.connected.value) state.errors.add("BLE not connected")
                    //         else if (state.cameraActive.value) state.bleCameraOff?.invoke()
                    //         else state.bleCameraOn?.invoke()
                    //     },
                    //     enabled  = state.connected.value,
                    //     modifier = Modifier.weight(1f)
                    // )
                    AuraTonalButton(
                        label    = if (state.notificationMirroring.value) "Turn Mirror OFF" else "Turn Mirror ON",
                        onClick  = { state.notificationMirroring.value = !state.notificationMirroring.value },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // ── MANUAL MESSAGE BLOCK COMMENTED OUT (moved to HomeScreen) ──
                    // if (showManualMessage) {
                    //     HorizontalDivider(color = AVBorder)
                    //     SectionHeader("Manual Message")
                    //     OutlinedTextField(
                    //         value         = manualMessage,
                    //         onValueChange = { manualMessage = it },
                    //         placeholder   = { Text("Message to send to glasses", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    //         modifier      = Modifier.fillMaxWidth(),
                    //         singleLine    = true,
                    //         shape         = MaterialTheme.shapes.medium,
                    //         colors        = auraTextFieldColors()
                    //     )
                    //     Button(
                    //         onClick = {
                    //             if (!state.connected.value) { state.errors.add("BLE not connected"); return@Button }
                    //             if (manualMessage.isNotBlank()) {
                    //                 state.bleSendCommand?.invoke(manualMessage)
                    //                 manualMessage = ""
                    //             }
                    //         },
                    //         modifier = Modifier.fillMaxWidth().height(46.dp),
                    //         shape    = MaterialTheme.shapes.medium,
                    //         colors   = ButtonDefaults.buttonColors(
                    //             containerColor = AVCyan,
                    //             contentColor   = AVNavy
                    //         )
                    //     ) {
                    //         Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    //         Spacer(Modifier.width(8.dp))
                    //         Text("Send to Glasses", fontWeight = FontWeight.SemiBold)
                    //     }
                    // }
                }
            }
        }
    }
}

// ─── Diagnostics screen ───────────────────────────────────────────────────────
@Composable
private fun DiagnosticsScreen(state: AppState, modifier: Modifier = Modifier) {
    val loading = state.lastChecked.value == "Never"

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Diagnostics", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Text("Check core components and view errors.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)

        // Controls card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Last checked: ${state.lastChecked.value}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AuraTonalButton("Run Check", { state.runDiagnosticsCheck() }, modifier = Modifier.weight(1f))
                    AuraTonalButton("Clear Errors", { state.clearErrors() }, modifier = Modifier.weight(1f))
                }
            }
        }

        // Component status
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Component Status")
                if (loading) {
                    repeat(5) { ShimmerLine(height = 16.dp); Spacer(Modifier.height(8.dp)) }
                } else {
                    listOf(
                        "BLE"      to state.bleOk.value,
                        "Camera"   to state.cameraOk.value,  // always FAIL until camera is connected
                        "Mic"      to state.micOk.value,     // always PASS (Bluetooth mic)
                        "Display"  to state.displayOk.value,
                        "Neckband" to state.neckbandConnected.value
                    ).forEachIndexed { index, (name, ok) ->
                        if (index > 0) HorizontalDivider(color = AVBorder)
                        AuraStatusRow(name, ok)
                    }
                }
            }
        }

        // Errors
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Errors")
                if (loading) {
                    ShimmerLine(height = 14.dp); Spacer(Modifier.height(6.dp)); ShimmerLine(height = 14.dp)
                } else if (state.errors.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = AVGreen, modifier = Modifier.size(16.dp))
                        Text("No errors found.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    state.errors.forEach { msg ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.error.copy(0.2f), MaterialTheme.shapes.small)
                        ) {
                            Text(msg, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ─── Settings screen ──────────────────────────────────────────────────────────
@Composable
private fun SettingsScreen(
    state: AppState,
    modifier: Modifier = Modifier,
    onLogout: () -> Unit,
    onOpenNotificationSettings: () -> Unit
) {
    var page by remember { mutableStateOf(SettingsPage.MAIN) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (page != SettingsPage.MAIN) {
            TextButton(
                onClick = { page = SettingsPage.MAIN },
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = AVCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Back", color = AVCyan)
            }
        } else {
            Text("Settings", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        }

        when (page) {
            SettingsPage.MAIN -> {
                // Account card
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader("Account")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape)
                                    .background(AVCyan.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    state.username.value.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = AVCyan
                                )
                            }
                            Column {
                                Text(state.username.value.ifBlank { "User" }, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                                Text(state.email.value.ifBlank { "—" }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        HorizontalDivider(color = AVBorder)
                        AuraPermissionRow("Dark mode", state.darkMode.value) { state.darkMode.value = it }
                    }
                }

                // Notification mirroring
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader("Notification Mirroring")
                        AuraPermissionRow("Enable mirroring", state.notificationMirroring.value) {
                            state.notificationMirroring.value = it
                        }
                        Button(
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            onClick  = { onOpenNotificationSettings() },
                            shape    = MaterialTheme.shapes.medium,
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = AVNavy3,
                                contentColor   = Color.White
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AVBorder)
                        ) { Text("Grant Notification Access") }
                        Text(
                            "You must allow notification access manually in system settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Navigation tiles
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple(Icons.Filled.Palette,       "Appearance")       { page = SettingsPage.APPEARANCE },
                        Triple(Icons.Filled.Build,         "Troubleshoot")     { page = SettingsPage.TROUBLESHOOT },
                        Triple(Icons.Filled.Info,          "About app")        { page = SettingsPage.ABOUT },
                        Triple(Icons.Filled.ExitToApp,     "Logout / Delete account") { page = SettingsPage.DELETE_ACCOUNT }
                    ).forEach { (icon, label, action) ->
                        SettingsTile(icon = icon, label = label, onClick = action)
                    }
                }
            }

            SettingsPage.APPEARANCE -> GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Appearance")
                    Text("Toggle dark mode with the switch in Account settings, or use the moon icon in the top bar.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }

            SettingsPage.TROUBLESHOOT -> GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Troubleshoot")
                    Text("Run diagnostics from the Diagnostics tab to check all components.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }

            SettingsPage.DELETE_ACCOUNT -> GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionHeader("Logout")
                    Text("You will be signed out of your account.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Button(
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        onClick  = { onLogout() },
                        shape    = MaterialTheme.shapes.medium,
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor   = Color.White
                        )
                    ) {
                        Icon(Icons.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Logout", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            SettingsPage.ABOUT -> GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("About")
                    Text("AuraVision — AR Glasses Companion", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                    Text("BLE connectivity · Notification Mirroring", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ─── Reusable small components ────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun StatusDot(connected: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(8.dp).background(
                if (connected) AVGreen else MaterialTheme.colorScheme.error,
                CircleShape
            )
        )
        Text(
            if (connected) "Connected" else "Disconnected",
            style = MaterialTheme.typography.labelSmall,
            color = if (connected) AVGreen else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun AuraBanner(message: String, isError: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = (if (isError) MaterialTheme.colorScheme.error else AVGreen).copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth()
            .border(
                1.dp,
                (if (isError) MaterialTheme.colorScheme.error else AVGreen).copy(alpha = 0.3f),
                MaterialTheme.shapes.medium
            )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                if (isError) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.error else AVGreen,
                modifier = Modifier.size(16.dp)
            )
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun DeviceRow(name: String, address: String, rssi: Int, onPair: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(AVCyan.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = AVCyan, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(address, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FilledTonalButton(
                onClick = onPair,
                shape   = MaterialTheme.shapes.small,
                colors  = ButtonDefaults.filledTonalButtonColors(
                    containerColor = AVCyan.copy(alpha = 0.12f),
                    contentColor   = AVCyan
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, AVCyan.copy(0.3f)),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) { Text("Pair", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    onChangeFinished: (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = AVCyan)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onChangeFinished,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor          = AVCyan,
                activeTrackColor    = AVCyan,
                inactiveTrackColor  = AVNavy3
            )
        )
    }
}

@Composable
private fun AuraTonalButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    FilledTonalButton(
        onClick  = onClick,
        modifier = modifier.height(44.dp),
        enabled  = enabled,
        shape    = MaterialTheme.shapes.medium,
        colors   = ButtonDefaults.filledTonalButtonColors(
            containerColor      = AVNavy3,
            contentColor        = Color.White,
            disabledContainerColor = AVNavy3.copy(alpha = 0.5f),
            disabledContentColor   = AVTextSub
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, AVBorder)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AuraPermissionRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor  = AVNavy,
                checkedTrackColor  = AVCyan,
                uncheckedThumbColor = AVTextSub,
                uncheckedTrackColor = AVNavy3
            )
        )
    }
}

@Composable
private fun AuraStatusRow(name: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(6.dp).background(if (ok) AVGreen else MaterialTheme.colorScheme.error, CircleShape))
            Text(
                if (ok) "PASS" else "FAIL",
                color = if (ok) AVGreen else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SettingsTile(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick   = onClick,
        shape     = MaterialTheme.shapes.large,
        color     = MaterialTheme.colorScheme.surface,
        modifier  = Modifier.fillMaxWidth()
            .border(1.dp, AVBorder, MaterialTheme.shapes.large)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, contentDescription = null, tint = AVCyan, modifier = Modifier.size(20.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun BatteryBar(label: String, percent: Int) {
    val barColor = when {
        percent <= 10 -> MaterialTheme.colorScheme.error
        percent <= 25 -> Color(0xFFFFA000)
        else          -> MaterialTheme.colorScheme.primary
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "$percent%",
                style = MaterialTheme.typography.labelLarge,
                color = barColor
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percent / 100f)
                    .height(8.dp)
                    .background(barColor)
            )
        }
    }
}

@Composable
private fun auraTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor    = MaterialTheme.colorScheme.outline,
    focusedTextColor        = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor      = MaterialTheme.colorScheme.onSurface,
    cursorColor             = MaterialTheme.colorScheme.primary,
    focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
)
