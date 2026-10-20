package com.riseup.alarm.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Accent colours as ARGB. [onDark] is used on dark backgrounds, [onLight] (a deeper shade) on the light one. */
enum class Accent(val title: String, val onDark: Long, val onLight: Long) {
    SUNRISE("Sunrise", 0xFFF2C14E, 0xFFB7860B),
    CORAL("Coral", 0xFFFF8A75, 0xFFD2543F),
    ROSE("Rose", 0xFFF59AC1, 0xFFC2457E),
    LAVENDER("Lavender", 0xFFB9A2FF, 0xFF6C4FD1),
    SKY("Sky", 0xFF7CC2FF, 0xFF1F78C9),
    MINT("Mint", 0xFF6DDBB2, 0xFF1E8F68),
    MONO("Mono", 0xFFF2F2F2, 0xFF1B1B1F),
}

enum class Backdrop(
    val title: String,
    val background: Long,
    val surface: Long,
    val surfaceHigh: Long,
    val content: Long,
    val muted: Long,
    val outline: Long,
    val isLight: Boolean,
) {
    BLACK("Black", 0xFF0E0E10, 0xFF18181B, 0xFF232327, 0xFFFFFFFF, 0xFF8A8A93, 0xFF3A3A40, false),
    MIDNIGHT("Midnight", 0xFF0C1324, 0xFF152036, 0xFF1D2A45, 0xFFF1F4FA, 0xFF8A96AE, 0xFF33415E, false),
    LIGHT("Light", 0xFFF7F5F0, 0xFFECE9E2, 0xFFE2DED5, 0xFF16161A, 0xFF6E6C68, 0xFFC9C5BC, true),
}

data class ThemeChoice(val accent: Accent = Accent.SUNRISE, val backdrop: Backdrop = Backdrop.BLACK)

class SettingsRepository private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(
        ThemeChoice(
            accent = enumOrDefault(prefs.getString(KEY_ACCENT, null), Accent.SUNRISE),
            backdrop = enumOrDefault(prefs.getString(KEY_BACKDROP, null), Backdrop.BLACK),
        ),
    )
    val theme: StateFlow<ThemeChoice> = _theme

    fun setAccent(accent: Accent) {
        _theme.value = _theme.value.copy(accent = accent)
        prefs.edit().putString(KEY_ACCENT, accent.name).apply()
    }

    fun setBackdrop(backdrop: Backdrop) {
        _theme.value = _theme.value.copy(backdrop = backdrop)
        prefs.edit().putString(KEY_BACKDROP, backdrop.name).apply()
    }

    companion object {
        private const val KEY_ACCENT = "accent"
        private const val KEY_BACKDROP = "backdrop"

        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}

inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
