package com.example.arglasses.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==============================
// AURAVISION COLORS
// ==============================

val AVNavy      = Color(0xFF0A0F1A)   // deep background
val AVNavy2     = Color(0xFF111827)   // card background
val AVNavy3     = Color(0xFF1A2535)   // elevated card
val AVCyan      = Color(0xFF00C2FF)   // primary accent
val AVCyanDim   = Color(0xFF0A84FF)   // secondary accent / buttons
val AVGreen     = Color(0xFF00D68F)   // success / connected
val AVAmber     = Color(0xFFFFA000)   // warning
val AVBorder    = Color(0xFF1E2D42)   // subtle border
val AVTextSub   = Color(0xFF8899AA)   // subtitle / secondary text

// ==============================
// DARK MODE (AURAVISION HUD)
// ==============================

private val DarkColors = darkColorScheme(
    primary             = AVCyan,
    onPrimary           = AVNavy,
    primaryContainer    = Color(0xFF003550),
    onPrimaryContainer  = AVCyan,

    secondary           = AVCyanDim,
    onSecondary         = Color.White,
    secondaryContainer  = Color(0xFF0A2040),
    onSecondaryContainer= AVCyanDim,

    tertiary            = AVGreen,
    onTertiary          = AVNavy,

    background          = AVNavy,
    onBackground        = Color.White,

    surface             = AVNavy2,
    onSurface           = Color.White,
    surfaceVariant      = AVNavy3,
    onSurfaceVariant    = AVTextSub,

    outline             = AVBorder,
    error               = Color(0xFFFF4C6A),
    onError             = Color.White,
)

// ==============================
// LIGHT MODE (kept for toggle)
// ==============================

private val LightColors = lightColorScheme(
    primary             = Color(0xFF0A84FF),
    secondary           = Color(0xFF0A84FF),
    tertiary            = Color(0xFF00C2FF),

    background          = Color(0xFFF2F5FA),
    surface             = Color(0xFFFFFFFF),
    surfaceVariant      = Color(0xCCFFFFFF),

    primaryContainer    = Color(0x220A84FF),
    outline             = Color(0x22000000),

    onPrimary           = Color.White,
    onSurface           = Color(0xFF1A1A1A),
    onSurfaceVariant    = Color(0xFF556677),
)

// ==============================
// TYPOGRAPHY
// ==============================

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp
    ),
    headlineMedium = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold
    ),
    headlineSmall = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleLarge = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.15.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        letterSpacing = 0.3.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        letterSpacing = 0.5.sp
    )
)

// ==============================
// SHAPES
// ==============================

private val AppShapes = Shapes(
    extraSmall  = RoundedCornerShape(8.dp),
    small       = RoundedCornerShape(12.dp),
    medium      = RoundedCornerShape(16.dp),
    large       = RoundedCornerShape(20.dp),
    extraLarge  = RoundedCornerShape(24.dp)
)

// ==============================
// THEME
// ==============================

@Composable
fun AppTheme(
    darkMode: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkMode) DarkColors else LightColors,
        typography  = AppTypography,
        shapes      = AppShapes,
        content     = content
    )
}
