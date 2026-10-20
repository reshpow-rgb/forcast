package com.riseup.alarm.data

import com.riseup.alarm.mission.Difficulty
import com.riseup.alarm.mission.MissionType
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    /** ISO day numbers (1 = Monday .. 7 = Sunday). Empty means "ring once". */
    val days: Set<Int> = emptySet(),
    val enabled: Boolean = true,
    val label: String = "",
    val mission: MissionType = MissionType.MATH,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val rounds: Int = 1,
    /** See com.riseup.alarm.alarm.Sounds for the format. */
    val sound: String = "default",
    val soundTitle: String = "Phone's alarm sound",
    val animal: Animal = Animal.CORGI,
) {
    val repeats get() = days.isNotEmpty()

    fun nextTrigger(now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        var candidate = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
        if (days.isEmpty()) return candidate
        while (candidate.dayOfWeek.value !in days) candidate = candidate.plusDays(1)
        return candidate
    }

    fun nextTriggerMillis(now: LocalDateTime = LocalDateTime.now()): Long =
        nextTrigger(now).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun daysSummary(): String = when {
        days.isEmpty() -> "Once"
        days.size == 7 -> "Every day"
        days == setOf(1, 2, 3, 4, 5) -> "Weekdays"
        days == setOf(6, 7) -> "Weekends"
        else -> days.sorted().joinToString(" ") {
            DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }
    }

    companion object {
        const val FREE_MAX_ROUNDS = 3
        const val PREMIUM_MAX_ROUNDS = 5
        const val FREE_MAX_ALARMS = 3
    }
}
