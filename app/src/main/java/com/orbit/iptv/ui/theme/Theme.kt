package com.orbit.iptv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Orbit {
    val Bg = Color(0xFF09090D)
    val Surface = Color(0xFF15151C)
    val SurfaceHigh = Color(0xFF22222C)
    val Stroke = Color(0xFF2E2E3A)
    val Accent = Color(0xFFFFB547)
    val OnAccent = Color(0xFF1A1205)
    val Text = Color(0xFFF3F3F6)
    val Muted = Color(0xFF9C9CAA)
    val Live = Color(0xFFFF4D5E)
    val Danger = Color(0xFFFF6B6B)
}

/** Vrai sur Android TV : active le menu latéral et la navigation à la télécommande. */
val LocalTv = staticCompositionLocalOf { false }

object Dims {
    fun edge(tv: Boolean): Dp = if (tv) 48.dp else 16.dp
    fun poster(tv: Boolean): Dp = if (tv) 150.dp else 112.dp
    fun channel(tv: Boolean): Dp = if (tv) 220.dp else 150.dp
    fun wide(tv: Boolean): Dp = if (tv) 320.dp else 250.dp
}

private val typography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold, lineHeight = 42.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun OrbitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Orbit.Accent,
            onPrimary = Orbit.OnAccent,
            secondary = Orbit.Accent,
            onSecondary = Orbit.OnAccent,
            background = Orbit.Bg,
            onBackground = Orbit.Text,
            surface = Orbit.Surface,
            onSurface = Orbit.Text,
            surfaceVariant = Orbit.SurfaceHigh,
            onSurfaceVariant = Orbit.Muted,
            surfaceContainerHigh = Orbit.SurfaceHigh,
            outline = Orbit.Stroke,
            error = Orbit.Danger,
        ),
        typography = typography,
        content = content,
    )
}
