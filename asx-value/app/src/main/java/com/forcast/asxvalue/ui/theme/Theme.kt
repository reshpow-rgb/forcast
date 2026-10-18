package com.forcast.asxvalue.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(
    primary = Color(0xFF0E5E3C),
    secondary = Color(0xFF4E6355),
    tertiary = Color(0xFF8A6D00),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF7FD8A6),
    secondary = Color(0xFFB5CCBB),
    tertiary = Color(0xFFE9C349),
)

/** Semantic colours for gains/losses and value verdicts, readable in light and dark. */
object ValueColors {
    val Positive @Composable get() = if (isSystemInDarkTheme()) Color(0xFF6FD49A) else Color(0xFF137A44)
    val Negative @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFF8A80) else Color(0xFFC62828)
    val Neutral @Composable get() = if (isSystemInDarkTheme()) Color(0xFFE0C060) else Color(0xFF8A6D00)
}

@Composable
fun AsxValueTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
