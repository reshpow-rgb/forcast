package com.riseup.alarm

import com.riseup.alarm.data.DayStatus
import com.riseup.alarm.data.StreakStats
import com.riseup.alarm.data.Streaks
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakTest {

    private val today = LocalDate.of(2026, 10, 23) // Friday
    private fun d(daysAgo: Long) = today.minusDays(daysAgo)

    @Test
    fun noHistoryIsZero() {
        assertEquals(StreakStats(0, 0, 0), Streaks.compute(emptySet(), emptySet(), today))
    }

    @Test
    fun consecutiveWakeUpsCount() {
        val days = setOf(d(0), d(1), d(2))
        assertEquals(StreakStats(3, 3, 3), Streaks.compute(days, days, today))
    }

    @Test
    fun daysWithoutAlarmDoNotBreakStreak() {
        // Rang and woke Mon-Fri last week and Mon-Fri this week; no alarms at the weekend.
        val days = (0L..4L).map(::d).toSet() + (7L..11L).map(::d).toSet()
        assertEquals(10, Streaks.compute(days, days, today).current)
    }

    @Test
    fun missedDayResetsStreakButKeepsBest() {
        val rang = (0L..6L).map(::d).toSet()
        val woke = rang - d(3)
        val stats = Streaks.compute(rang, woke, today)
        assertEquals(3, stats.current)
        assertEquals(3, stats.best)
        assertEquals(6, stats.totalWakeUps)
    }

    @Test
    fun ringingTodayButNotDoneYetKeepsYesterdaysStreak() {
        val rang = setOf(d(0), d(1), d(2))
        val woke = setOf(d(1), d(2))
        assertEquals(2, Streaks.compute(rang, woke, today).current)
        assertEquals(DayStatus.PENDING, Streaks.status(today, rang, woke, today))
        assertEquals(DayStatus.MISSED, Streaks.status(d(3), rang + d(3), woke, today))
        assertEquals(DayStatus.NO_ALARM, Streaks.status(d(5), rang, woke, today))
    }

    @Test
    fun nextMilestone() {
        assertEquals(3, StreakStats(0, 0, 0).nextMilestone)
        assertEquals(7, StreakStats(3, 3, 3).nextMilestone)
        assertEquals(null, StreakStats(400, 400, 400).nextMilestone)
    }
}
