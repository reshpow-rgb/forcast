package com.riseup.alarm.ui

fun formatTime(hour: Int, minute: Int, is24h: Boolean): Pair<String, String?> =
    if (is24h) {
        "%02d:%02d".format(hour, minute) to null
    } else {
        val h = if (hour % 12 == 0) 12 else hour % 12
        "%d:%02d".format(h, minute) to if (hour < 12) "AM" else "PM"
    }

fun formatDuration(millis: Long): String {
    val totalMinutes = ((millis + 59_999) / 60_000).coerceAtLeast(0)
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes / 60) % 24
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
