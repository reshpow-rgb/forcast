package com.riseup.alarm.alarm

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import androidx.annotation.RawRes
import com.riseup.alarm.R

/**
 * An alarm's sound is stored as a string:
 * [DEFAULT] (the phone's alarm sound), [SILENT] (vibrate only), "app:<name>" for a sound
 * bundled with RiseUp, or any content URI (a system tone or a file the user picked).
 */
object Sounds {
    const val DEFAULT = "default"
    const val SILENT = "silent"
    const val DEFAULT_TITLE = "Phone's alarm sound"

    data class Builtin(val key: String, val title: String, @RawRes val res: Int)

    val builtins = listOf(
        Builtin("app:chimes", "Gentle chimes", R.raw.riseup_chimes),
        Builtin("app:bells", "Morning bells", R.raw.riseup_bells),
        Builtin("app:marimba", "Marimba", R.raw.riseup_marimba),
        Builtin("app:birdsong", "Birdsong", R.raw.riseup_birdsong),
        Builtin("app:beeps", "Classic beeps", R.raw.riseup_beeps),
    )

    data class Tone(val key: String, val title: String)

    /** Playable URI for a stored sound, or null for [DEFAULT]/[SILENT]. */
    fun uriFor(context: Context, sound: String): Uri? = when {
        sound == DEFAULT || sound == SILENT -> null
        sound.startsWith("app:") -> builtins.firstOrNull { it.key == sound }
            ?.let { Uri.parse("android.resource://${context.packageName}/${it.res}") }
        else -> runCatching { Uri.parse(sound) }.getOrNull()
    }

    /** The phone's own sounds of a [RingtoneManager] type (alarms, ringtones). Varies by manufacturer. */
    fun systemTones(context: Context, type: Int): List<Tone> = runCatching {
        val manager = RingtoneManager(context).apply { setType(type) }
        val cursor = manager.cursor
        buildList {
            while (cursor.moveToNext()) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                add(Tone(manager.getRingtoneUri(cursor.position).toString(), title))
            }
        }
    }.getOrDefault(emptyList())

    /** Fallback chain used when no sound is chosen, or the chosen one can't be played. */
    fun fallbacks(context: Context): List<Uri> = listOfNotNull(
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
    )
}
