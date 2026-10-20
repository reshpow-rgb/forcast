package com.riseup.alarm.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riseup.alarm.data.SettingsRepository
import com.riseup.alarm.data.ThemeChoice

/** The user's chosen accent colour. */
val AccentColor: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary

/** Secondary text colour for the current background. */
val Muted: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant

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

fun colorSchemeFor(choice: ThemeChoice) = choice.backdrop.run {
    val accent = Color(if (isLight) choice.accent.onLight else choice.accent.onDark)
    val onAccent = if (isLight) Color.White else Color(0xFF0E0E10)
    val bg = Color(background)
    val fg = Color(content)
    val base = if (isLight) lightColorScheme() else darkColorScheme()
    base.copy(
        primary = accent,
        onPrimary = onAccent,
        secondary = accent,
        onSecondary = onAccent,
        background = bg,
        onBackground = fg,
        surface = bg,
        onSurface = fg,
        surfaceVariant = Color(surface),
        onSurfaceVariant = Color(muted),
        surfaceContainer = Color(surface),
        surfaceContainerLow = Color(surface),
        surfaceContainerHigh = Color(surfaceHigh),
        surfaceContainerHighest = Color(surfaceHigh),
        outline = Color(outline),
        outlineVariant = Color(outline).copy(alpha = 0.6f),
        error = if (isLight) Color(0xFFC62828) else Color(0xFFFF6B6B),
    )
}

@Composable
fun RiseUpTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository.get(context) }
    val choice by settings.theme.collectAsStateWithLifecycle()
    val isLight = choice.backdrop.isLight

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = isLight
                    isAppearanceLightNavigationBars = isLight
                }
            }
        }
    }

    MaterialTheme(colorScheme = colorSchemeFor(choice), typography = typography, content = content)
}
