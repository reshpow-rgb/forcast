package com.riseup.alarm.data

import java.time.LocalDate

enum class DayStatus { WOKE, MISSED, PENDING, NO_ALARM }

data class StreakStats(
    val current: Int,
    val best: Int,
    val totalWakeUps: Int,
) {
    /** The next milestone above the current streak, or null past the last one. */
    val nextMilestone get() = Streaks.MILESTONES.firstOrNull { it > current }

    companion object {
        val EMPTY = StreakStats(0, 0, 0)
    }
}

/**
 * A streak is the number of alarm days in a row on which the user completed the mission.
 * Days with no alarm (weekends off, holidays) are skipped rather than breaking the streak.
 * Today only counts once the mission is done - a ringing-but-unfinished alarm doesn't break it yet.
 */
object Streaks {

    val MILESTONES = listOf(3, 7, 14, 30, 50, 100, 200, 365)

    fun compute(rang: Set<LocalDate>, woke: Set<LocalDate>, today: LocalDate): StreakStats {
        val days = (rang + woke).filter { !it.isAfter(today) }.sorted()
        var run = 0
        var best = 0
        for (day in days) {
            when {
                day in woke -> {
                    run++
                    best = maxOf(best, run)
                }
                day != today -> run = 0
            }
        }
        return StreakStats(current = run, best = best, totalWakeUps = woke.count { !it.isAfter(today) })
    }

    fun status(day: LocalDate, rang: Set<LocalDate>, woke: Set<LocalDate>, today: LocalDate): DayStatus = when {
        day in woke -> DayStatus.WOKE
        day in rang && day == today -> DayStatus.PENDING
        day in rang -> DayStatus.MISSED
        else -> DayStatus.NO_ALARM
    }

    fun isMilestone(streak: Int) = streak in MILESTONES
}
