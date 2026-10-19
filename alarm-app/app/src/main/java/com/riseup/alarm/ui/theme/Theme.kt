package com.riseup.alarm.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF0E0E10)
val Surface = Color(0xFF18181B)
val SurfaceHigh = Color(0xFF232327)
val Sun = Color(0xFFF2C14E)
val Muted = Color(0xFF8A8A93)

private val colors = darkColorScheme(
    primary = Sun,
    onPrimary = Ink,
    secondary = Sun,
    onSecondary = Ink,
    background = Ink,
    onBackground = Color.White,
    surface = Ink,
    onSurface = Color.White,
    surfaceVariant = Surface,
    onSurfaceVariant = Muted,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHigh,
    outline = Color(0xFF3A3A40),
    outlineVariant = Color(0xFF2A2A2F),
    error = Color(0xFFFF6B6B),
)

private val typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.Thin, fontSize = 88.sp, letterSpacing = (-2).sp),
        displayMedium = displayMedium.copy(fontWeight = FontWeight.ExtraLight, fontSize = 48.sp),
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Light),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Light),
    )
}

/** Big, thin numerals for clock faces. */
val ClockStyle = TextStyle(fontWeight = FontWeight.ExtraLight, fontSize = 44.sp, letterSpacing = (-1).sp)

@Composable
fun RiseUpTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
